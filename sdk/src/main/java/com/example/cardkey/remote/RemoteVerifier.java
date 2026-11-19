package com.example.cardkey.remote;

/** 远程验卡后端接口：注入自定义实现即可接入自己的服务器。 */
public interface RemoteVerifier {
    /** 向服务器验证卡密，返回其签发的凭证字符串。 */
    String verifyRemote(String code, String deviceId, String productId) throws Exception;
}
