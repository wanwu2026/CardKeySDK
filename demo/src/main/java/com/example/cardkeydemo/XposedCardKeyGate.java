package com.example.cardkeydemo;

import android.content.Context;

import com.example.cardkey.CardKey;

/** Xposed 模块接入示例：拿到目标 app 的 Context 后初始化并判断激活状态。 */
public final class XposedCardKeyGate {

    /** 在拿到目标 app 的 Context 后调用一次。 */
    public static void init(Context appContext, String publicKeyBase64, String productId) {
        try {
            CardKey.init(new CardKey.Config(appContext)
                    .productId(productId)
                    .publicKeyBase64(publicKeyBase64)
                    .mode(CardKey.Mode.OFFLINE));
        } catch (Exception e) {
            android.util.Log.e("CardKey", "初始化失败", e);
        }
    }

    /** 未激活时返回 false，hook 里据此决定是否放行。 */
    public static boolean isAllowed() {
        try {
            return CardKey.get().isActivated();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 弹出激活界面（需要 Activity Context）。 */
    public static void showActivation(android.app.Activity activity) {
        new com.example.cardkey.ui.ActivationDialog(activity).show();
    }
}
