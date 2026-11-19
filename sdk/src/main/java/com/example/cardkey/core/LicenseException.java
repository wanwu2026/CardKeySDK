package com.example.cardkey.core;

/** 激活/验卡过程中可判别的错误。 */
public final class LicenseException extends Exception {
    public enum Code {
        MALFORMED,            // 卡密格式错误
        INVALID_SIGNATURE,    // 签名不正确（伪造/篡改）
        UNSUPPORTED_VERSION,  // 版本不支持
        BAD_PUBLIC_KEY,       // 内置公钥配置错误
        DEVICE_MISMATCH,      // 设备不匹配
        EXPIRED,              // 已过期
        NETWORK,              // 网络错误
        SERVER_REJECTED,      // 服务器明确拒绝
        STORAGE_TAMPERED      // 本地激活状态被篡改
    }

    private final Code code;

    public LicenseException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public LicenseException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }

    /** 面向用户的友好文案（不含内部细节） */
    public String friendlyMessage() {
        switch (code) {
            case INVALID_SIGNATURE: return "卡密无效或已被篡改";
            case MALFORMED: return "卡密格式不正确";
            case UNSUPPORTED_VERSION: return "卡密版本不受支持，请升级应用";
            case BAD_PUBLIC_KEY: return "应用配置异常（公钥）";
            case DEVICE_MISMATCH: return "此卡密已在其他设备激活";
            case EXPIRED: return "卡密已过期";
            case NETWORK: return "网络连接失败，请稍后重试";
            case SERVER_REJECTED: return "服务器拒绝了激活请求";
            case STORAGE_TAMPERED: return "激活状态异常，请重新激活";
            default: return "激活失败";
        }
    }
}
