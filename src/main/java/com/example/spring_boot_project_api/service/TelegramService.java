package com.example.spring_boot_project_api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Thin wrapper around the Telegram Bot HTTP API. Every call is best-effort:
 * failures are logged and swallowed so a Telegram outage never blocks a
 * booking/payment/notification flow.
 */
@Service
@Slf4j
public class TelegramService {

  @Value("${telegram.bot.token}")
  private String botToken;

  private final RestTemplate restTemplate = new RestTemplate();

  private String apiUrl(String method) {
    return "https://api.telegram.org/bot" + botToken + "/" + method;
  }

  /** Plain text. chatId = Telegram numeric user id == User.providerId (TELEGRAM accounts only). */
  public void sendMessage(String chatId, String text) {
    try {
      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("chat_id", chatId);
      body.add("text", text);
      body.add("parse_mode", "HTML");

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

      restTemplate.postForEntity(apiUrl("sendMessage"), new HttpEntity<>(body, headers), String.class);
    } catch (Exception e) {
      log.warn("Telegram sendMessage failed for chatId={}: {}", chatId, e.getMessage());
    }
  }

  /** PDF (or any file) attachment + caption. */
  public void sendDocument(String chatId, byte[] fileBytes, String filename, String caption) {
    try {
      ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
        @Override
        public String getFilename() {
          return filename;
        }
      };

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("chat_id", chatId);
      body.add("document", fileResource);
      if (caption != null && !caption.isBlank()) {
        body.add("caption", caption);
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      restTemplate.postForEntity(apiUrl("sendDocument"), new HttpEntity<>(body, headers), String.class);
    } catch (Exception e) {
      log.warn("Telegram sendDocument failed for chatId={}: {}", chatId, e.getMessage());
    }
  }
}