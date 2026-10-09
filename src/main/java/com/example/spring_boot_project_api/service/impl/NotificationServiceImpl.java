package com.example.spring_boot_project_api.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.notification.NotificationRequestDTO;
import com.example.spring_boot_project_api.dto.response.notification.NotificationResponseDTO;
import com.example.spring_boot_project_api.enums.NotificationTypeEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.Notification;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.model.UserDevice;
import com.example.spring_boot_project_api.repository.NotificationRepository;
import com.example.spring_boot_project_api.repository.UserDeviceRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.NotificationService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final UserDeviceRepository userDeviceRepository;

  @Override
  @Transactional
  public NotificationResponseDTO createNotification(
      Long userId,
      NotificationRequestDTO dto) {

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // Create DB notification
    Notification notification = new Notification();

    notification.setUser(user);
    notification.setType(dto.getType());
    notification.setTitle(dto.getTitle());
    notification.setMessage(dto.getMessage());
    notification.setIsRead(false);

    Notification saved = notificationRepository.save(notification);

    // Send Firebase notification
    sendNotification(
        userId,
        dto.getTitle(),
        dto.getMessage(),
        dto.getType());

    return toResponse(saved);
  }

  // @Override
  // public NotificationResponseDTO createNotification(Long userId,
  // NotificationRequestDTO dto) {
  // User user = userRepository.findById(userId).orElseThrow(() -> new
  // RuntimeException("User not found"));

  // Notification notification = new Notification();
  // notification.setUser(user);
  // notification.setType(dto.getType());
  // notification.setTitle(dto.getTitle());
  // notification.setMessage(dto.getMessage());

  // Notification saved = notificationRepository.save(notification);
  // return toResponse(saved);
  // }

  @Override
  public List<NotificationResponseDTO> getNotificationsForUser(Long userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public List<NotificationResponseDTO> getAllNotifications() {
    return notificationRepository.findAll().stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public NotificationResponseDTO markAsRead(Long id, Long requestingUserId) {
    Notification notification = notificationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Notification not found"));

    assertOwnerOrStaff(notification, requestingUserId);

    notification.setIsRead(true);
    Notification saved = notificationRepository.save(notification);
    return toResponse(saved);
  }

  @Override
  public void markAllAsRead(Long userId) {
    List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    notifications.forEach(n -> n.setIsRead(true));
    notificationRepository.saveAll(notifications);
  }

  @Override
  public long getUnreadCount(Long userId) {
    return notificationRepository.countByUserIdAndIsReadFalse(userId);
  }

  @Override
  public void deleteNotification(Long id, Long requestingUserId) {
    Notification notification = notificationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Notification not found"));

    assertOwnerOrStaff(notification, requestingUserId);

    notificationRepository.deleteById(id);
  }

  // Only the notification's own recipient, or staff, may mark-as-read / delete it
  private void assertOwnerOrStaff(Notification notification, Long requestingUserId) {
    User requestingUser = userRepository.findById(requestingUserId)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

    boolean isOwner = notification.getUser() != null
        && notification.getUser().getId().equals(requestingUserId);
    boolean isStaff = requestingUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to modify this notification");
    }
  }

  private NotificationResponseDTO toResponse(Notification n) {
    return new NotificationResponseDTO(
        n.getId(),
        n.getUser() != null ? n.getUser().getId() : null,
        n.getUser() != null ? n.getUser().getEmail() : null,
        n.getType(),
        n.getTitle(),
        n.getMessage(),
        n.getIsRead(),
        n.getCreatedAt());
  }

  @Override
  @Transactional
  public void registerDevice(
      Long userId,
      String token,
      String deviceType) {

    // Find the user
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // Check whether this FCM token already exists
    UserDevice device = userDeviceRepository
        .findByFcmToken(token)
        .orElse(null);

    if (device == null) {

      // New device
      device = UserDevice.builder()
          .user(user)
          .fcmToken(token)
          .deviceType(deviceType)
          .createdAt(LocalDateTime.now())
          .updatedAt(LocalDateTime.now())
          .build();

    } else {

      // Existing device
      device.setUser(user);
      device.setDeviceType(deviceType);
      device.setUpdatedAt(LocalDateTime.now());
    }

    userDeviceRepository.save(device);
  }

  @Override
  @Transactional
  public void sendNotification(
      Long userId,
      String title,
      String message,
      NotificationTypeEnum type) {

    // ==========================================
    // 1. Find user
    // ==========================================

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));

    // ==========================================
    // 2. Save notification to database
    // ==========================================

    Notification notification = new Notification();

    notification.setUser(user);
    notification.setType(type);
    notification.setTitle(title);
    notification.setMessage(message);
    notification.setIsRead(false);

    notificationRepository.save(notification);

    // ==========================================
    // 3. Find user's devices
    // ==========================================

    List<UserDevice> devices = userDeviceRepository.findByUserId(userId);

    // ==========================================
    // 4. Send Firebase push notification
    // ==========================================

    for (UserDevice device : devices) {

      sendFirebaseNotification(
          device.getFcmToken(),
          title,
          message,
          type);
    }
  }

  /**
   * Send notification to Firebase FCM.
   */
  private void sendFirebaseNotification(
      String fcmToken,
      String title,
      String message,
      NotificationTypeEnum type) {

    try {

      Message firebaseMessage = Message.builder()

          // Device FCM token
          .setToken(fcmToken)

          // Notification shown to the user
          .setNotification(
              com.google.firebase.messaging.Notification
                  .builder()
                  .setTitle(title)
                  .setBody(message)
                  .build())

          // Extra data Flutter can use
          .putData(
              "type",
              type.name())

          .build();

      String response = FirebaseMessaging
          .getInstance()
          .send(firebaseMessage);

      System.out.println(
          "FCM notification sent: " + response);

    } catch (Exception e) {

      System.err.println(
          "Failed to send FCM notification: "
              + e.getMessage());
    }
  }
}