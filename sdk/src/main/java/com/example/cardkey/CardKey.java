package com.example.cardkey;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.cardkey.core.ActivationStore;
import com.example.cardkey.core.DeviceId;
import com.example.cardkey.core.LicenseException;
import com.example.cardkey.core.LicensePayload;
import com.example.cardkey.core.LicenseVerifier;
import com.example.cardkey.remote.HttpRemoteVerifier;
import com.example.cardkey.remote.RemoteVerifier;

import java.security.PublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 卡密激活 SDK 入口：离线/在线模式统一走验签、设备时间校验与持久化。 */
public final class CardKey {
    private static volatile CardKey instance;

    public enum Mode { OFFLINE, ONLINE }

    /** 激活结果回调（主线程）。 */
    public interface Callback {
        void onSuccess(LicensePayload payload);
        void onError(LicenseException error);
    }

    public static final class Config {
        Context context;
        String productId = "default";
        String publicKeyBase64;
        String[] publicKeysBase64; // 多公钥轮换
        Mode mode = Mode.OFFLINE;
        RemoteVerifier remoteVerifier;
        String deviceId;

        public Config(Context context) { this.context = context.getApplicationContext(); }
        public Config productId(String v) { this.productId = v; return this; }
        public Config publicKeyBase64(String v) { this.publicKeyBase64 = v; return this; }
        public Config publicKeyBase64s(String... v) { this.publicKeysBase64 = v; return this; }
        public Config mode(Mode v) { this.mode = v; return this; }
        public Config remoteVerifier(RemoteVerifier v) { this.remoteVerifier = v; return this; }
        public Config deviceId(String v) { this.deviceId = v; return this; }
    }

    private final Context context;
    private final String productId;
    private final PublicKey[] publicKeys;
    private final Mode mode;
    private final RemoteVerifier remoteVerifier;
    private final String deviceId;
    private final ActivationStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private CardKey(Config c) throws LicenseException {
        this.context = c.context;
        this.productId = c.productId;
        if ((c.publicKeyBase64 == null || c.publicKeyBase64.trim().length() == 0)
                && (c.publicKeysBase64 == null || c.publicKeysBase64.length == 0)) {
            throw new LicenseException(LicenseException.Code.BAD_PUBLIC_KEY, "必须在配置中提供公钥");
        }
        java.util.List<PublicKey> pkList = new java.util.ArrayList<>();
        if (c.publicKeyBase64 != null && c.publicKeyBase64.trim().length() > 0) {
            pkList.add(LicenseVerifier.publicKeyFromBase64(c.publicKeyBase64));
        }
        if (c.publicKeysBase64 != null) {
            for (String b64 : c.publicKeysBase64) {
                if (b64 != null && b64.trim().length() > 0) {
                    pkList.add(LicenseVerifier.publicKeyFromBase64(b64));
                }
            }
        }
        this.publicKeys = pkList.toArray(new PublicKey[0]);
        this.mode = c.mode;
        this.deviceId = (c.deviceId != null && c.deviceId.length() > 0)
                ? c.deviceId : DeviceId.get(c.context);
        this.remoteVerifier = c.remoteVerifier != null ? c.remoteVerifier : new HttpRemoteVerifier("");
        this.store = new ActivationStore(c.context, this.deviceId);
    }

    /** 初始化，全局调用一次。 */
    public static synchronized void init(Config config) throws LicenseException {
        instance = new CardKey(config);
    }

    public static CardKey get() {
        if (instance == null) throw new IllegalStateException("请先调用 CardKey.init()");
        return instance;
    }

    public String getDeviceId() { return deviceId; }
    public String getProductId() { return productId; }
    public Mode getMode() { return mode; }
    public PublicKey getPublicKey() { return publicKeys[0]; }
    public PublicKey[] getPublicKeys() { return publicKeys; }

    /** 是否已激活（重新验签持久化凭证）。 */
    public boolean isActivated() {
        return store.isActivated(publicKeys);
    }

    /** 返回当前有效载荷；未激活/无效返回 null。 */
    public LicensePayload currentLicense() {
        return store.loadVerified(publicKeys);
    }

    /** 返回已保存的凭证字符串。 */
    public String savedLicense() {
        return store.loadRaw();
    }

    /** 异步激活，回调在主线程。 */
    public void activate(final String code, final Callback callback) {
        executor.execute(new Runnable() {
            @Override public void run() {
                final LicensePayload[] ok = new LicensePayload[1];
                final LicenseException[] err = new LicenseException[1];
                try {
                    String license;
                    if (mode == Mode.ONLINE) {
                        try {
                            license = remoteVerifier.verifyRemote(code, deviceId, productId);
                        } catch (Exception e) {
                            if (e instanceof HttpRemoteVerifier.ServerRejectedException) {
                                throw new LicenseException(LicenseException.Code.SERVER_REJECTED, e.getMessage(), e);
                            }
                            throw new LicenseException(LicenseException.Code.NETWORK, e.getMessage(), e);
                        }
                    } else {
                        license = code;
                    }
                    ok[0] = LicenseVerifier.verifyAny(license, publicKeys, deviceId);
                    // 在线模式保存原始卡密，便于事后向服务器复核撤销/封禁
                    store.save(license, mode == Mode.ONLINE ? code : null);
                } catch (LicenseException e) {
                    err[0] = e;
                } catch (Throwable t) {
                    err[0] = new LicenseException(LicenseException.Code.MALFORMED, "激活异常: " + t.getMessage(), t);
                }
                final LicensePayload payload = ok[0];
                final LicenseException error = err[0];
                main.post(new Runnable() {
                    @Override public void run() {
                        if (callback == null) return;
                        if (error != null) callback.onError(error);
                        else callback.onSuccess(payload);
                    }
                });
            }
        });
    }

    /**
     * 在线模式下向服务器复核当前激活状态（用于感知封禁/撤销）。
     * 服务器拒绝时立即清空本地激活；网络失败保留本地状态（不回滚已激活）。
     * 离线模式或未保存原始卡密时回 UNSUPPORTED_VERSION / MALFORMED 错误。
     */
    public void refreshWithBackend(final Callback callback) {
        executor.execute(new Runnable() {
            @Override public void run() {
                final LicensePayload[] ok = new LicensePayload[1];
                final LicenseException[] err = new LicenseException[1];
                try {
                    if (mode != Mode.ONLINE) {
                        throw new LicenseException(LicenseException.Code.UNSUPPORTED_VERSION,
                                "离线模式无法向服务器复核撤销");
                    }
                    String code = store.loadCode();
                    if (code == null || code.length() == 0) {
                        throw new LicenseException(LicenseException.Code.MALFORMED,
                                "本地没有原始卡密，无法复核（请在在线模式重新激活）");
                    }
                    String license;
                    try {
                        license = remoteVerifier.verifyRemote(code, deviceId, productId);
                    } catch (Exception e) {
                        if (e instanceof HttpRemoteVerifier.ServerRejectedException) {
                            // 服务器明确拒绝（封禁/过期/换设备）→ 立即失效
                            store.clear();
                            throw new LicenseException(LicenseException.Code.SERVER_REJECTED, e.getMessage(), e);
                        }
                        throw new LicenseException(LicenseException.Code.NETWORK, e.getMessage(), e);
                    }
                    ok[0] = LicenseVerifier.verifyAny(license, publicKeys, deviceId);
                    store.save(license, code);
                } catch (LicenseException e) {
                    err[0] = e;
                } catch (Throwable t) {
                    err[0] = new LicenseException(LicenseException.Code.MALFORMED, "复核异常: " + t.getMessage(), t);
                }
                final LicensePayload payload = ok[0];
                final LicenseException error = err[0];
                main.post(new Runnable() {
                    @Override public void run() {
                        if (callback == null) return;
                        if (error != null) callback.onError(error);
                        else callback.onSuccess(payload);
                    }
                });
            }
        });
    }

    /** 清空本地激活状态。 */
    public void deactivate() {
        store.clear();
    }
}
