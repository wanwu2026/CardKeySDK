package com.example.cardkey.core;

/** 已签名凭证的载荷（不可变）。 */
public final class LicensePayload {
    private final String productId;
    private final String deviceId;
    private final long issuedAt;
    private final long expiresAt;
    private final long serial;

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
