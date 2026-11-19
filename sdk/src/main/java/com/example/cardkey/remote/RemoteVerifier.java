package com.example.cardkey.remote;

/**
 * 远程验卡后端接口 —— 后端开发者实现这个接口即可接入自己的服务器。
 *
 * 默认提供 HttpRemoteVerifier（用 HttpURLConnection，零依赖）。也允许传入任意
 * 实现（Retrofit、私有协议、本地 mock 都行）。SDK 只依赖这个接口。
 *
 * 安全模型：服务器用私钥签发凭证，客户端拿到后用内置公钥**再验一次签**，
 * 所以即使服务器返回被中间人篡改，也过不了客户端这一关。
 */
public interface RemoteVerifier {
    /**
     * 向服务器验证卡密。
     *
     * @param code       用户输入的卡密/激活码
     * @param deviceId   当前设备码
     * @param productId  产品标识
     * @return 服务器签发的凭证字符串（见 LicenseVerifier.assemble 的格式）
     * @throws Exception 网络/服务器错误；SDK 会包装成 LicenseException
     */
    String verifyRemote(String code, String deviceId, String productId) throws Exception;
}
