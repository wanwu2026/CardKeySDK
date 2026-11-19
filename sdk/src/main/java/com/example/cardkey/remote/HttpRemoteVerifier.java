package com.example.cardkey.remote;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 默认远程验证实现：POST JSON 取回服务器签发的凭证。 */
public class HttpRemoteVerifier implements RemoteVerifier {
    private final String endpoint;
    private final String token;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpRemoteVerifier(String endpoint) {
        this(endpoint, null, 8000, 8000);
    }

    public HttpRemoteVerifier(String endpoint, String token, int connectTimeoutMs, int readTimeoutMs) {
        this.endpoint = endpoint;
        this.token = token;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public String verifyRemote(String code, String deviceId, String productId) throws Exception {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("code", code);
            body.put("device_id", deviceId);
            body.put("product_id", productId);

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && token.length() > 0) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int status = conn.getResponseCode();
            InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(is);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status + (text.isEmpty() ? "" : ": " + text));
            }

            JSONObject resp = new JSONObject(text);
            if (!resp.optBoolean("ok", false)) {
                String reason = resp.optString("reason", "rejected");
                throw new ServerRejectedException(reason, resp.optString("message", "服务器拒绝"));
            }
            String license = resp.optString("license", "");
            if (license.length() == 0) throw new IllegalStateException("服务器未返回凭证");
            return license;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    /** 服务器明确拒绝（凭证错误、卡密无效、已用、过期等）。 */
    public static class ServerRejectedException extends Exception {
        public final String reason;
        public ServerRejectedException(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }
}
