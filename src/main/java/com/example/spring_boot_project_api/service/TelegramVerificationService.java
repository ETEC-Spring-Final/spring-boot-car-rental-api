package com.example.spring_boot_project_api.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.user.TelegramLoginRequestDTO;

/**
 * Verifies the Telegram Login Widget payload (per Telegram's official
 * spec: https://core.telegram.org/widgets/login#checking-authorization).
 *
 * Extracted out of TelegramAuthService so it can be reused by BOTH:
 *  - TelegramAuthService (login/register with Telegram as the primary
 *    account)
 *  - TelegramLinkController (linking Telegram to an existing
 *    LOCAL/GOOGLE/FACEBOOK account so it can also receive Telegram
 *    notifications)
 * Both flows must verify the exact same widget payload the exact same way.
 */
@Service
public class TelegramVerificationService {

  @Value("${telegram.bot.token}")
  private String botToken;

  // Reject a stale/replayed widget payload even if its hash is still valid.
  private static final long MAX_AUTH_AGE_SECONDS = 86_400; // 24h

  public void verify(TelegramLoginRequestDTO dto) {
    verifyHash(dto);
    verifyFreshness(dto);
  }

  /**
   * Recomputes HMAC-SHA256(data_check_string, SHA256(bot_token)) and
   * compares it against the hash the widget sent, using a constant-time
   * comparison so a timing attack can't be used to guess it byte-by-byte.
   */
  private void verifyHash(TelegramLoginRequestDTO dto) {
    Map<String, String> fields = new TreeMap<>();
    fields.put("id", String.valueOf(dto.getId()));
    fields.put("first_name", dto.getFirstName());
    if (dto.getLastName() != null) fields.put("last_name", dto.getLastName());
    if (dto.getUsername() != null) fields.put("username", dto.getUsername());
    if (dto.getPhotoUrl() != null) fields.put("photo_url", dto.getPhotoUrl());
    fields.put("auth_date", String.valueOf(dto.getAuthDate()));

    StringBuilder dataCheckString = new StringBuilder();
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      if (dataCheckString.length() > 0) dataCheckString.append('\n');
      dataCheckString.append(entry.getKey()).append('=').append(entry.getValue());
    }

    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      byte[] secretKey = sha256.digest(botToken.getBytes(StandardCharsets.UTF_8));

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
      byte[] computed = mac.doFinal(dataCheckString.toString().getBytes(StandardCharsets.UTF_8));
      String computedHex = bytesToHex(computed);

      boolean valid = MessageDigest.isEqual(
          computedHex.getBytes(StandardCharsets.UTF_8),
          dto.getHash().getBytes(StandardCharsets.UTF_8));

      if (!valid) {
        throw new OAuth2AuthenticationException("Invalid Telegram login signature.");
      }
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to verify Telegram login hash", e);
    }
  }

  private void verifyFreshness(TelegramLoginRequestDTO dto) {
    long now = Instant.now().getEpochSecond();
    if (now - dto.getAuthDate() > MAX_AUTH_AGE_SECONDS) {
      throw new OAuth2AuthenticationException("Telegram login payload has expired. Please try again.");
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }
}