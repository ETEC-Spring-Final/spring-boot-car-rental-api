package com.example.spring_boot_project_api.service.impl;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.notification.NotificationRequestDTO;
import com.example.spring_boot_project_api.dto.request.review.ReviewRequestDTO;
import com.example.spring_boot_project_api.dto.request.review.ReviewVisibilityRequestDTO;
import com.example.spring_boot_project_api.dto.response.review.ReviewResponseDTO;
import com.example.spring_boot_project_api.enums.NotificationTypeEnum;
import com.example.spring_boot_project_api.enums.RentalStatusEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.Rental;
import com.example.spring_boot_project_api.model.Review;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.RentalRepository;
import com.example.spring_boot_project_api.repository.ReviewRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.NotificationService;
import com.example.spring_boot_project_api.service.ReviewService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
  private final ReviewRepository reviewRepository;
  private final RentalRepository rentalRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService; // ⬅ NEW

  @Override
  public ReviewResponseDTO createReview(ReviewRequestDTO dto) {
    User currentUser = getCurrentUser();

    Rental rental = rentalRepository.findById(dto.getRentalId())
        .orElseThrow(() -> new RuntimeException("Rental not found"));

    if (!rental.getUser().getId().equals(currentUser.getId())) {
      throw new RuntimeException("You are not authorized to review this rental");
    }

    // Validate rental status before allowing review creation
    if (rental.getStatus() != RentalStatusEnum.COMPLETED && rental.getStatus() != RentalStatusEnum.RETURNED) {
      throw new RuntimeException("You can only review a vehicle after completing the rental");
    }

    // 3. Prevent duplicate reviews for the same rental
    if (reviewRepository.existsByRentalIdAndUserId(dto.getRentalId(), currentUser.getId())) {
      throw new RuntimeException("You have already submitted a review for this rental");
    }

    Review review = new Review();
    review.setUser(currentUser);
    review.setRental(rental);
    review.setVehicle(rental.getVehicle());
    review.setRating(dto.getRating());
    review.setComment(dto.getComment());

    Review saved = reviewRepository.save(review);

    // ⬅ NEW: notify every admin/manager/staff that a new review came in
    notifyStaffOfNewReview(saved, currentUser);

    return toResponse(saved);
  }

  // ⬅ NEW
  private void notifyStaffOfNewReview(Review review, User author) {
    String authorName = (author.getFirstName() + " " + author.getLastName()).trim();
    String vehicleLabel = review.getVehicle().getBrand() != null
        ? review.getVehicle().getBrand().getName() + " " + review.getVehicle().getModel()
        : review.getVehicle().getModel();

    NotificationRequestDTO notification = new NotificationRequestDTO();
    notification.setType(NotificationTypeEnum.NEW_REVIEW);
    notification.setTitle("New review received");
    notification.setMessage(
        authorName + " left a " + review.getRating() + "★ review for " + vehicleLabel + ".");

    List<User> staff = userRepository.findByRoleIn(List.of(RoleEnum.ADMIN, RoleEnum.MANAGER, RoleEnum.STAFF));
    for (User recipient : staff) {
      notificationService.createNotification(recipient.getId(), notification);
    }
  }

  @Override
  public ReviewResponseDTO updateReview(Long id, ReviewRequestDTO dto) {
    User currentUser = getCurrentUser();

    Review review = reviewRepository.findById(id).orElseThrow(() -> new RuntimeException("Review not found"));

    if (!review.getUser().getId().equals(currentUser.getId())) {
      throw new RuntimeException("You are not authorized to edit this review");
    }

    review.setRating(dto.getRating());
    review.setComment(dto.getComment());

    Review saved = reviewRepository.save(review);
    return toResponse(saved);
  }

  @Override
  public void deleteReview(Long id) {
    User currentUser = getCurrentUser();

    Review review = reviewRepository.findById(id).orElseThrow(() -> new RuntimeException("Review not found"));

    boolean isOwner = review.getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to delete this review");
    }

    reviewRepository.deleteById(id);
  }

  @Override
  public ReviewResponseDTO updateVisibility(Long id, ReviewVisibilityRequestDTO dto) {
    // Endpoint is restricted to ADMIN/MANAGER/STAFF via @PreAuthorize on the
    // controller, so no ownership check is needed here — this is a moderation
    // action, not something a customer can do to their own review.
    Review review = reviewRepository.findById(id).orElseThrow(() -> new RuntimeException("Review not found"));

    review.setIsVisible(dto.getIsVisible());

    Review saved = reviewRepository.save(review);
    return toResponse(saved);
  }

  @Override
  public ReviewResponseDTO getReviewById(Long id) {
    Review review = reviewRepository.findById(id).orElseThrow(() -> new RuntimeException("Review not found"));

    return toResponse(review);
  }

  @Override
  public Page<ReviewResponseDTO> getAllReviews(Pageable pageable) {
    return reviewRepository.findAll(pageable).map(this::toResponse);
  }

  @Override
  public Page<ReviewResponseDTO> getReviewsByVehicleId(Long vehicleId, Pageable pageable) {
    return reviewRepository.findByVehicleIdAndIsVisibleTrue(vehicleId, pageable).map(this::toResponse);
  }

  @Override
  public Page<ReviewResponseDTO> getMyReviews(Pageable pageable) {
    User currentUser = getCurrentUser();
    return reviewRepository.findByUserId(currentUser.getId(), pageable)
        .map(this::toResponse);
  }

  @Override
  public Long countReviewsByRating(Long vehicleId, Integer rating) {
    // Fixed parameter mapping (vehicleId + rating)
    return reviewRepository.countByVehicleIdAndRatingAndIsVisibleTrue(vehicleId, rating);
  }

  private ReviewResponseDTO toResponse(Review r) {
    String fullName = (r.getUser().getFirstName() + " " + r.getUser().getLastName()).trim();
    return ReviewResponseDTO.builder()
        .id(r.getId())
        .rentalId(r.getRental().getId())
        .vehicleId(r.getVehicle().getId())
        .userId(r.getUser().getId())
        .userName(fullName)
        .rating(r.getRating())
        .comment(r.getComment())
        .isVisible(r.getIsVisible())
        .createdAt(r.getCreatedAt())
        .updatedAt(r.getUpdatedAt())
        .build();
  }

  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUsername = authentication.getName();
    return userRepository.findByEmail(currentUsername)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
  }
}