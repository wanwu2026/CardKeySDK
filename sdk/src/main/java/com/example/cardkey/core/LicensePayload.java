package com.example.cardkey.core;

/** 已签名凭证的载荷（不可变）。 */
public final class LicensePayload {
    private final String productId;
    private final String deviceId;   // 空字符串表示不绑定设备
    private final long issuedAt;     // 签发时间 epoch millis
    private final long expiresAt;    // 到期时间 epoch millis，0 表示永久
    private final long serial;       // 随机序列号，避免同参数生成相同凭证

    public LicensePayload(String productId, String deviceId, long issuedAt, long expiresAt, long serial) {
        this.productId = productId == null ? "" : productId;
        this.deviceId = deviceId == null ? "" : deviceId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.serial = serial;
    }

    public String getProductId() { return productId; }
    public String getDeviceId() { return deviceId; }
    public long getIssuedAt() { return issuedAt; }
    public long getExpiresAt() { return expiresAt; }
    public long getSerial() { return serial; }

    public boolean isExpired() {
        return expiresAt != 0 && System.currentTimeMillis() > expiresAt;
    }

    public boolean isBoundToDevice() {
        return deviceId != null && deviceId.length() > 0;
    }

    public boolean matchesDevice(String actual) {
        if (!isBoundToDevice()) return true;
        return deviceId.equals(actual == null ? "" : actual);
    }
}
