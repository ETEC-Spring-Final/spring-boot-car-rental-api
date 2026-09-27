package com.example.spring_boot_project_api.dto.response.user;

import java.time.LocalDateTime;

import com.example.spring_boot_project_api.enums.RoleEnum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
  private Long id;
  private String firstName;
  private String lastName;
  private String email;
  private String phone;
  private String gender;
  private RoleEnum role;
  private String profilePicture;
  private Boolean active;
  private String authProvider;
  private Boolean isTelegramConnected; // ⬅ NEW — never expose the raw telegramChatId, only whether it's linked
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}