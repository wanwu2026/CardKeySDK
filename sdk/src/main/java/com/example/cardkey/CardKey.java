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

/**
 * CardKey —— 卡密激活 SDK 的唯一入口。
 *
 * 两种工作模式：
 *   · 离线模式（offline）：卡密本身就是服务器私钥签发的凭证，客户端用内置公钥直接验签。
 *     适合"卡密即授权"、无需联网的场景。
 *   · 在线模式（online）：卡密发给服务器验证，服务器返回签名凭证，客户端再用公钥验签后保存。
 *     适合需要服务器记录/一机一码/实时封禁的场景。
 *
 * 两种模式最终都落到同一套「验签 → 校验设备/时间 → 安全持久化」流程。
 *
 * 用法：
 *   CardKey.init(new CardKey.Config(context)
 *       .productId("my_app")
 *       .publicKeyBase64("...."));
 *   boolean ok = CardKey.get().isActivated();
 *   CardKey.get().activate(input, new CardKey.Callback() {...});
 */
public final class CardKey {
    private static volatile CardKey instance;

    public enum Mode { OFFLINE, ONLINE }

    /** 激活结果回调（onSuccess/onError 均在主线程）。 */
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
        String deviceId; // 可覆盖默认设备码

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

    /** 初始化（全局调用一次，建议在 Application.onCreate）。 */
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

    /** 当前是否已激活（读取持久化凭证并重新验签）。同步方法。 */
    public boolean isActivated() {
        return store.isActivated(publicKey);
    }

    /** 返回当前有效载荷；未激活/无效返回 null。 */
    public LicensePayload currentLicense() {
        return store.loadVerified(publicKey);
    }

    /** 读取当前保存的凭证字符串（可用于"我的激活"展示）。 */
    public String savedLicense() {
        return store.loadRaw();
    }

    /**
     * 激活。异步执行（在线模式涉及网络），回调在主线程。
     * 流程：拿到凭证串 → 本地验签（防伪造/篡改）→ 校验设备与时间 → 持久化。
     */
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
                        license = code; // 离线模式：卡密本身就是凭证
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

    /** 退出登录/解绑（清空本地激活状态）。 */
    public void deactivate() {
        store.clear();
    }
}
