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
        Mode mode = Mode.OFFLINE;
        RemoteVerifier remoteVerifier;
        String deviceId;

        public Config(Context context) { this.context = context.getApplicationContext(); }
        public Config productId(String v) { this.productId = v; return this; }
        public Config publicKeyBase64(String v) { this.publicKeyBase64 = v; return this; }
        public Config mode(Mode v) { this.mode = v; return this; }
        public Config remoteVerifier(RemoteVerifier v) { this.remoteVerifier = v; return this; }
        public Config deviceId(String v) { this.deviceId = v; return this; }
    }

    private final Context context;
    private final String productId;
    private final PublicKey publicKey;
    private final Mode mode;
    private final RemoteVerifier remoteVerifier;
    private final String deviceId;
    private final ActivationStore store;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private CardKey(Config c) throws LicenseException {
        this.context = c.context;
        this.productId = c.productId;
        if (c.publicKeyBase64 == null || c.publicKeyBase64.trim().length() == 0) {
            throw new LicenseException(LicenseException.Code.BAD_PUBLIC_KEY, "必须在配置中提供公钥");
        }
        this.publicKey = LicenseVerifier.publicKeyFromBase64(c.publicKeyBase64);
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
    public PublicKey getPublicKey() { return publicKey; }

    /** 是否已激活（重新验签持久化凭证）。 */
    public boolean isActivated() {
        return store.isActivated(publicKey);
    }

    /** 返回当前有效载荷；未激活/无效返回 null。 */
    public LicensePayload currentLicense() {
        return store.loadVerified(publicKey);
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
                    ok[0] = LicenseVerifier.verify(license, publicKey, deviceId);
                    store.save(license);
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

    /** 清空本地激活状态。 */
    public void deactivate() {
        store.clear();
    }
}
