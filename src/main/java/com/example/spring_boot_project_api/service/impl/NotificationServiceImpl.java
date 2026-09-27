package com.example.spring_boot_project_api.service.impl;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.notification.NotificationRequestDTO;
import com.example.spring_boot_project_api.dto.response.notification.NotificationResponseDTO;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.Notification;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.NotificationRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;

  @Override
  public NotificationResponseDTO createNotification(Long userId, NotificationRequestDTO dto) {
    User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

    Notification notification = new Notification();
    notification.setUser(user);
    notification.setType(dto.getType());
    notification.setTitle(dto.getTitle());
    notification.setMessage(dto.getMessage());

    Notification saved = notificationRepository.save(notification);
    return toResponse(saved);
  }

  @Override
  public List<NotificationResponseDTO> getNotificationsForUser(Long userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public List<NotificationResponseDTO> getAllNotifications() {
    return notificationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
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
}