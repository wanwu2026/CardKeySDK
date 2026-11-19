package com.example.cardkeydemo;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedCardKeyModule implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.target.app";
    private static final String PUBLIC_KEY_BASE64 = "REPLACE_WITH_YOUR_PUBLIC_KEY_BASE64";
    private static final String PRODUCT_ID = "my_app";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        String appClass = lpparam.appInfo != null ? lpparam.appInfo.className : null;
        String hookClass = (appClass != null && !appClass.isEmpty())
                ? appClass : "android.app.Application";

        try {
            XposedHelpers.findAndHookMethod(hookClass, lpparam.classLoader, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context ctx = (Context) param.thisObject;
                            XposedCardKeyGate.init(ctx, PUBLIC_KEY_BASE64, PRODUCT_ID);
                        }
                    });
        } catch (Throwable t) {
            android.util.Log.e("CardKey", "hook failed", t);
        }
    }
}
