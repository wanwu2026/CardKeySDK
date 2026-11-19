package com.example.cardkeydemo;

import android.app.Application;

import com.example.cardkey.CardKey;

/** 全局初始化 SDK。 */
public class DemoApp extends Application {
    private static final String PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAq5ofawgT+MBduDPRs/Tjc3aKTqcpe4nQ/Efq3thPmg5uG8+F/h0rfOBFSRwVUoxGlTvpyLuJWGdPYJaG5Q6RQ2FG35Z6gycA6cFgRc5Fe+VYrSnfSMYgJpcGJGSYsfqtOoq7eLhFS48jpwkC2AvXB5M+MLiEa0QmlpHTX7PcsGN6aFj246AJh84ry4krMBThm2UzQQwP90J2lBPcwEgLkj53CAf+f523nrbytWG6xxmx7V/AzwtZN//A/iIRAa4qrNsdosrQh8UHNo07WaN4ylE7NK5hlqMD0agybz7M6QL1H9L/J3fDrhQF9jfrhn6OEsL+CVTlFQ3uVUgV8uuyGQIDAQAB";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            CardKey.init(new CardKey.Config(this)
                    .productId("demo_app")
                    .publicKeyBase64(PUBLIC_KEY_BASE64)
                    .mode(CardKey.Mode.OFFLINE));
        } catch (Exception e) {
            throw new RuntimeException("CardKey 初始化失败", e);
        }
    }
}
