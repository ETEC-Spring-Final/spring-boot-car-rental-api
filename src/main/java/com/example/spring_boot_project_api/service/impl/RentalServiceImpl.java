package com.example.spring_boot_project_api.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.rental.RentalRequestDTO;
import com.example.spring_boot_project_api.dto.response.rental.RentalResponseDTO;
import com.example.spring_boot_project_api.enums.RentalStatusEnum;
import com.example.spring_boot_project_api.enums.ReservationStatusEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.Rental;
import com.example.spring_boot_project_api.model.Reservation;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.RentalRepository;
import com.example.spring_boot_project_api.repository.ReservationRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.RentalService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RentalServiceImpl implements RentalService {
  private final RentalRepository rentalRepository;
  private final UserRepository userRepository;
  private final ReservationRepository reservationRepository;

  @Override
  public RentalResponseDTO createRental(RentalRequestDTO dto) {
    Reservation reservation = reservationRepository.findById(dto.getReservationId())
        .orElseThrow(() -> new RuntimeException("Reservation not found"));

    // if CANCELLED/PENDING, can't create rental
    if (reservation.getStatus() != ReservationStatusEnum.CONFIRMED) {
      throw new RuntimeException("Only confirmed reservations can be converted to a rental");
    }

    if (rentalRepository.findByReservationId(dto.getReservationId()).isPresent()) {
      throw new RuntimeException("A rental already exists for this reservation");
    }

    Rental rental = new Rental();
    rental.setReservation(reservation);
    rental.setVehicle(reservation.getVehicle());
    rental.setUser(reservation.getUser());
    rental.setPickUpLocation(reservation.getPickUpLocation());
    rental.setReturnLocation(reservation.getReturnLocation());
    rental.setPickUpDateTime(reservation.getPickUpDateTime());
    rental.setExpectedReturnDateTime(reservation.getReturnDateTime());
    rental.setBasePrice(reservation.getTotalPrice());
    rental
        .setDiscountAmount(dto.getDiscountAmount() != null ? dto.getDiscountAmount() : reservation.getDiscountAmount());
    rental.setAdditionalCharges(dto.getAdditionalCharges() != null ? dto.getAdditionalCharges() : BigDecimal.ZERO);
    rental.setTotalPrice(reservation.getTotalPrice()); // adjust if late fee/extra charges apply later
    rental.setNotes(dto.getNotes());

    Rental saved = rentalRepository.save(rental);
    return toResponse(saved);
  }

  @Override
  public RentalResponseDTO getRentalById(Long id) {
    Rental rental = rentalRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Rental not found"));

    User currentUser = getCurrentUser();
    boolean isOwner = rental.getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to view this rental");
    }

    return toResponse(rental);
  }

  @Override
  public List<RentalResponseDTO> getMyRentals() {
    User currentUser = getCurrentUser();
    return rentalRepository.findByUserId(currentUser.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public List<RentalResponseDTO> getAllRentals() {
    return rentalRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public RentalResponseDTO updateRental(Long id, RentalRequestDTO dto) {
    Rental rental = rentalRepository.findById(id).orElseThrow(() -> new RuntimeException("Rental not found"));

    // Only overwrite when the caller actually sent a value — otherwise a
    // partial update would silently null out the existing discount/charges.
    if (dto.getDiscountAmount() != null) {
      rental.setDiscountAmount(dto.getDiscountAmount());
    }
    if (dto.getAdditionalCharges() != null) {
      rental.setAdditionalCharges(dto.getAdditionalCharges());
    }
    rental.setNotes(dto.getNotes());

    Rental saved = rentalRepository.save(rental);
    return toResponse(saved);
  }

  @Override
  public void deleteRental(Long id) {
    if (!rentalRepository.existsById(id)) {
      throw new RuntimeException("Rental not found");
    }

    rentalRepository.deleteById(id);
  }

  @Override
  public RentalResponseDTO changeRentalStatus(Long id, RentalStatusEnum status) {
    Rental rental = rentalRepository.findById(id).orElseThrow(() -> new RuntimeException("Rental not found"));

    rental.setStatus(status);
    Rental saved = rentalRepository.save(rental);
    return toResponse(saved);
  }

  private RentalResponseDTO toResponse(Rental r) {
    User u = r.getUser();
    return new RentalResponseDTO(
        r.getId(), r.getReservation().getId(), r.getVehicle().getId(), r.getUser().getId(),
        u.getFirstName() + " " + u.getLastName(), u.getEmail(), u.getPhone(),
        r.getPickUpLocation().getId(), r.getReturnLocation().getId(), r.getPickUpDateTime(),
        r.getExpectedReturnDateTime(), r.getActualReturnDateTime(), r.getStatus(), r.getBasePrice(),
        r.getDiscountAmount(), r.getAdditionalCharges(), r.getLateFee(), r.getTotalPrice(), r.getNotes(),
        r.getCreatedAt(), r.getUpdatedAt());
  }

  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUsername = authentication.getName();
    return userRepository.findByEmail(currentUsername)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
  }
}