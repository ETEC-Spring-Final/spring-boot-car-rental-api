package com.example.spring_boot_project_api.dto.request.notification;

import jakarta.validation.constraints.NotBlank;

/**
 * UserDeviceTokenRequestDTO
 */
public record UserDeviceTokenRequestDTO(

        @NotBlank String token,

        String deviceType

) {
}