package com.example.cardkeydemo;

import android.content.Context;

import com.example.cardkey.CardKey;

/**
 * Xposed 模块接入示例（概念 + 骨架）。
 *
 * 用法：在 handleLoadPackage 里拿不到正规的 Application Context，常见做法是
 * hook 目标 App 的 Application.onCreate（或某个 Activity.onCreate）拿到 Context，
 * 然后在这里初始化并判断激活状态。
 *
 * 注意：Xposed 里对你代码的"防破解"要求更高，本示例仅展示如何把 CardKey 接进去。
 * 真正对抗逆向/破解需要发卡方配合在线验卡、设备绑定与服务端封禁（见 README 安全章节）。
 */
public final class XposedCardKeyGate {

    /** 在拿到目标 app 的 Context 后调用一次。 */
    public static void init(Context appContext, String publicKeyBase64, String productId) {
        try {
            CardKey.init(new CardKey.Config(appContext)
                    .productId(productId)
                    .publicKeyBase64(publicKeyBase64)
                    .mode(CardKey.Mode.OFFLINE));
        } catch (Exception e) {
            // Xposed 环境里不要轻易抛异常打断目标 app
            android.util.Log.e("CardKey", "初始化失败", e);
        }
    }

    /** 未激活时返回 false，你的 hook 里据此决定是否放行原功能。 */
    public static boolean isAllowed() {
        try {
            return CardKey.get().isActivated();
        } catch (Throwable t) {
            return false; // fail-closed：拿不到状态就当作未激活
        }
    }

    /** 供你弹出激活界面用（需要一个 Activity Context）。 */
    public static void showActivation(android.app.Activity activity) {
        new com.example.cardkey.ui.ActivationDialog(activity).show();
    }
}
