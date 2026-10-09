package com.example.spring_boot_project_api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.spring_boot_project_api.dto.request.notification.NotificationRequestDTO;
import com.example.spring_boot_project_api.dto.request.notification.UserDeviceTokenRequestDTO;
import com.example.spring_boot_project_api.dto.response.notification.NotificationResponseDTO;
import com.example.spring_boot_project_api.enums.NotificationTypeEnum;
import com.example.spring_boot_project_api.service.NotificationService;
import com.example.spring_boot_project_api.service.impl.CustomUserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  @Autowired
  private NotificationService notificationService;

  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @PostMapping("/{userId}/notify")
  public NotificationResponseDTO createNotification(@PathVariable Long userId,
      @Valid @RequestBody NotificationRequestDTO dto) {
    return notificationService.createNotification(userId, dto);
  }

  @GetMapping("/me/inbox")
  public List<NotificationResponseDTO> getNotificationsForUser(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return notificationService.getNotificationsForUser(userDetails.getId());
  }

  // Badge count of unread notifications for the current user
  @GetMapping("/me/unread-count")
  public long getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return notificationService.getUnreadCount(userDetails.getId());
  }

  // Mark a single notification as read
  @PatchMapping("/{id}/read")
  public NotificationResponseDTO markAsRead(@PathVariable Long id,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return notificationService.markAsRead(id, userDetails.getId());
  }

  // Mark every notification belonging to the current user as read
  @PatchMapping("/me/read-all")
  public void markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
    notificationService.markAllAsRead(userDetails.getId());
  }

  @DeleteMapping("/{id}")
  public void deleteNotification(@PathVariable Long id,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    notificationService.deleteNotification(id, userDetails.getId());
  }

  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @GetMapping
  public List<NotificationResponseDTO> getAllNotifications() {
    return notificationService.getAllNotifications();
  }

  @PostMapping("/device")
  public ResponseEntity<Void> registerDevice(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody UserDeviceTokenRequestDTO request) {

    notificationService.registerDevice(
        userDetails.getId(),
        request.token(),
        request.deviceType());

    return ResponseEntity.ok().build();
  }

  // Test Firebase Push Notification
  @PostMapping("/test")
  public ResponseEntity<Void> testNotification(
      @RequestParam Long userId) {

    notificationService.sendNotification(
        userId,
        "Test Notification 🔔",
        "Firebase notification is working!",
        NotificationTypeEnum.PAYMENT_SUCCESS);

    return ResponseEntity.ok().build();
  }

}