package com.example.spring_boot_project_api.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.user.TelegramLoginRequestDTO;
import com.example.spring_boot_project_api.dto.response.user.AuthResponseDTO;
import com.example.spring_boot_project_api.enums.AuthProviderEnum;
import com.example.spring_boot_project_api.enums.GenderEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.util.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Finds-or-creates a User the same way CustomOAuth2UserService does for
 * Google/Facebook, once TelegramVerificationService has confirmed the
 * widget payload is authentic and fresh.
 *
 * Telegram never gives us an email, so we synthesize a stable placeholder
 * (telegram_<id>@telegram.local) purely to satisfy the NOT NULL/unique
 * email column — it is never used to contact the user or matched against
 * a LOCAL account by email (unlike Google/Facebook linking).
 */
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;
  private final TelegramVerificationService telegramVerificationService; // ⬅ NEW — extracted hash/freshness check

  public AuthResponseDTO loginOrRegister(TelegramLoginRequestDTO dto) {
    telegramVerificationService.verify(dto); // ⬅ CHANGED — replaces old verifyHash(dto) + verifyFreshness(dto)

    String providerId = String.valueOf(dto.getId());
    String syntheticEmail = "telegram_" + dto.getId() + "@telegram.local";

    User user = userRepository.findByProviderIdAndAuthProvider(providerId, AuthProviderEnum.TELEGRAM)
        .orElseGet(() -> {
          User created = User.builder()
              .firstName(blankToUser(dto.getFirstName()))
              .lastName(dto.getLastName() == null ? "" : dto.getLastName())
              .email(syntheticEmail)
              .password(UUID.randomUUID().toString()) // unusable — never logged in with a password
              .phone("0000000000") // placeholder — prompt user to complete profile afterward
              .gender(GenderEnum.MALE) // placeholder — prompt user to complete profile afterward
              .profilePicture(dto.getPhotoUrl())
              .role(RoleEnum.CUSTOMER)
              .authProvider(AuthProviderEnum.TELEGRAM)
              .providerId(providerId)
              .telegramChatId(providerId) // ⬅ NEW — Telegram-native accounts get this set right away
              .active(true)
              .build();
          return userRepository.save(created);
        });

    // ⬅ NEW — backfill: accounts created BEFORE the telegramChatId column
    // existed won't have it set yet. Fix it on their next login so old
    // Telegram users start receiving notifications too, with no manual
    // SQL migration needed.
    if (user.getTelegramChatId() == null) {
      user.setTelegramChatId(providerId);
      userRepository.save(user);
    }

    String token = jwtUtil.generateToken(user);

    // AuthResponseDTO only has @Data + @AllArgsConstructor (no @Builder),
    // so it's constructed positionally here to match its declared field
    // order: (id, email, role, token).
    return new AuthResponseDTO(user.getId(), user.getEmail(), user.getRole(), token);
  }

  private String blankToUser(String value) {
    return (value == null || value.isBlank()) ? "User" : value;
  }
}