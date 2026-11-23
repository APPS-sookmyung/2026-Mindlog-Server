package com.apps.mindlog.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Shared signing and protected response storage for account creation and bearer authentication. */
@Component
public class AccessTokenCodec {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] masterKey;
    private final ObjectMapper json;

    public AccessTokenCodec(@Value("${mindlog.security.master-key:}") String configuredKey, ObjectMapper json) {
        this.json = json;
        byte[] key;
        try { key = Base64.getDecoder().decode(configuredKey); }
        catch (IllegalArgumentException | NullPointerException ex) { key = new byte[0]; }
        this.masterKey = key.length == 32 ? key : null;
    }

    public String issue(long userId, Instant now) {
        return issue(userId, 0L, now);
    }

    public String issue(long userId, long sessionId, Instant now) {
        requireKey();
        try {
            String header = B64.encodeToString(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", "mindlog"); claims.put("sub", Long.toString(userId));
            claims.put("iat", now.getEpochSecond()); claims.put("exp", now.plusSeconds(3600).getEpochSecond());
            claims.put("jti", randomString(18));
            claims.put("sid", sessionId);
            String payload = B64.encodeToString(json.writeValueAsBytes(claims));
            String content = header + "." + payload;
            return content + "." + B64.encodeToString(hmac(derive("access-token"), content.getBytes(StandardCharsets.US_ASCII)));
        } catch (tools.jackson.core.JacksonException ex) { throw new IllegalStateException("Unable to encode access token", ex); }
    }

    public VerifiedToken verify(String token, Clock clock) {
        requireKey();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) throw new InvalidAccessTokenException(false);
            Map<String, Object> header = json.readValue(B64D.decode(parts[0]), new TypeReference<>() { });
            if (!"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) throw new InvalidAccessTokenException(false);
            String content = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(hmac(derive("access-token"), content.getBytes(StandardCharsets.US_ASCII)), B64D.decode(parts[2])))
                throw new InvalidAccessTokenException(false);
            Map<String, Object> claims = json.readValue(B64D.decode(parts[1]), new TypeReference<>() { });
            if (!"mindlog".equals(claims.get("iss"))) throw new InvalidAccessTokenException(false);
            long userId = Long.parseLong(String.valueOf(claims.get("sub")));
            long expires = Long.parseLong(String.valueOf(claims.get("exp")));
            long sessionId = Long.parseLong(String.valueOf(claims.getOrDefault("sid", "0")));
            if (userId <= 0) throw new InvalidAccessTokenException(false);
            if (!Instant.ofEpochSecond(expires).isAfter(Instant.now(clock))) throw new InvalidAccessTokenException(true);
            return new VerifiedToken(userId, sessionId, Instant.ofEpochSecond(expires));
        } catch (InvalidAccessTokenException ex) { throw ex; }
        catch (Exception ex) { throw new InvalidAccessTokenException(false); }
    }

    public byte[] fingerprint(String normalizedRequest) {
        return hmac(derive("signup-request-fingerprint"), normalizedRequest.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] encryptResponse(String response) {
        requireKey();
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derive("signup-response-encryption"), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(response.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length); System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        } catch (Exception ex) { throw new IllegalStateException("Unable to protect signup response", ex); }
    }

    public String decryptResponse(byte[] protectedResponse) {
        requireKey();
        try {
            byte[] iv = java.util.Arrays.copyOfRange(protectedResponse, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(protectedResponse, 12, protectedResponse.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derive("signup-response-encryption"), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to read protected signup response", ex); }
    }

    public String refreshTokenHash(String refreshToken) {
        return Base64.getEncoder().encodeToString(hmac(derive("refresh-token-hash"), refreshToken.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] derive(String purpose) { requireKey(); return hmac(masterKey, ("mindlog:" + purpose).getBytes(StandardCharsets.UTF_8)); }
    private static byte[] hmac(byte[] key, byte[] input) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(input); }
        catch (Exception ex) { throw new IllegalStateException("HMAC is unavailable", ex); }
    }
    private static String randomString(int bytes) { byte[] value = new byte[bytes]; RANDOM.nextBytes(value); return B64.encodeToString(value); }
    private void requireKey() {
        if (masterKey == null) throw new IllegalStateException("MINDLOG_AUTH_MASTER_KEY must be a base64-encoded 32-byte key");
    }

    public record VerifiedToken(long userId, long sessionId, Instant expiresAt) { }
    public static final class InvalidAccessTokenException extends RuntimeException {
        private final boolean expired;
        public InvalidAccessTokenException(boolean expired) { super(expired ? "expired" : "invalid"); this.expired = expired; }
        public boolean expired() { return expired; }
    }
}
