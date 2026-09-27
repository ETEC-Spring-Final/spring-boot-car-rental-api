package com.example.spring_boot_project_api.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.spring_boot_project_api.dto.request.user.TelegramLoginRequestDTO;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.TelegramVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Lets an already-logged-in user (LOCAL/GOOGLE/FACEBOOK/TELEGRAM — any
 * provider) link their Telegram account so they start receiving Telegram
 * notifications + invoice PDFs, without changing how they actually log in.
 * Sits under /api/user-profiles/me, which requires JWT like the rest of that
 * resource (not in SecurityConfig's public allow-list).
 */
@RestController
@RequestMapping("/api/user-profiles/me")
@RequiredArgsConstructor
public class TelegramLinkController {

  private final UserRepository userRepository;
  private final TelegramVerificationService telegramVerificationService;

  @PostMapping("/connect-telegram")
  public void connectTelegram(@Valid @RequestBody TelegramLoginRequestDTO dto) {
    telegramVerificationService.verify(dto);

    User currentUser = getCurrentUser();
    currentUser.setTelegramChatId(String.valueOf(dto.getId()));
    userRepository.save(currentUser);
  }

  @DeleteMapping("/connect-telegram")
  public void disconnectTelegram() {
    User currentUser = getCurrentUser();
    currentUser.setTelegramChatId(null);
    userRepository.save(currentUser);
  }

  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUsername = authentication.getName();
    return userRepository.findByEmail(currentUsername)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
  }
}