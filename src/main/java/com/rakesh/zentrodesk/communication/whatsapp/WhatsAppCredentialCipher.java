package com.rakesh.zentrodesk.communication.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
class WhatsAppCredentialCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    WhatsAppCredentialCipher(@Value("${app.whatsapp.token-encryption-key}") String encodedKey) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("WhatsApp token encryption key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    String encrypt(String token) {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt WhatsApp credential", exception);
        }
    }

    String decrypt(String ciphertext) {
        byte[] payload = Base64.getDecoder().decode(ciphertext);
        byte[] iv = new byte[IV_LENGTH];
        byte[] encrypted = new byte[payload.length - IV_LENGTH];
        ByteBuffer.wrap(payload).get(iv).get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt WhatsApp credential", exception);
        }
    }
}
