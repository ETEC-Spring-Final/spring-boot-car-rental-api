package com.example.spring_boot_project_api.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.spring_boot_project_api.dto.request.notification.NotificationRequestDTO;
import com.example.spring_boot_project_api.dto.request.reservation.ReservationRequestDTO;
import com.example.spring_boot_project_api.dto.response.additional_service.AdditionalServiceResponseDTO;
import com.example.spring_boot_project_api.dto.response.reservation.ReservationResponseDTO;
import com.example.spring_boot_project_api.enums.InvoiceStatusEnum;
import com.example.spring_boot_project_api.enums.NotificationTypeEnum;
import com.example.spring_boot_project_api.enums.PaymentMethodEnum;
import com.example.spring_boot_project_api.enums.RentalStatusEnum;
import com.example.spring_boot_project_api.enums.ReservationStatusEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.AdditionalService;
import com.example.spring_boot_project_api.model.Invoice;
import com.example.spring_boot_project_api.model.Location;
import com.example.spring_boot_project_api.model.Rental;
import com.example.spring_boot_project_api.model.Reservation;
import com.example.spring_boot_project_api.model.ReservationAdditionalService;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.model.Vehicle;
import com.example.spring_boot_project_api.repository.AdditionalServiceRepository;
import com.example.spring_boot_project_api.repository.InvoiceRepository;
import com.example.spring_boot_project_api.repository.LocationRepository;
import com.example.spring_boot_project_api.repository.RentalRepository;
import com.example.spring_boot_project_api.repository.ReservationAdditionalServiceRepository;
import com.example.spring_boot_project_api.repository.ReservationRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.repository.VehicleRepository;
import com.example.spring_boot_project_api.service.DiscountService;
import com.example.spring_boot_project_api.service.NotificationService;
import com.example.spring_boot_project_api.service.ReservationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {
  private final ReservationRepository reservationRepository;
  private final VehicleRepository vehicleRepository;
  private final UserRepository userRepository;
  private final LocationRepository locationRepository;
  private final AdditionalServiceRepository additionalServiceRepository;
  private final ReservationAdditionalServiceRepository reservationAdditionalServiceRepository;
  private final RentalRepository rentalRepository;
  private final InvoiceRepository invoiceRepository;
  private final DiscountService discountService;
  private final NotificationService notificationService;

  @Override
  @Transactional
  public ReservationResponseDTO createReservation(ReservationRequestDTO dto) {
    User currentUser = getCurrentUser();

    Vehicle vehicle = vehicleRepository.findById(dto.getVehicleId())
        .orElseThrow(() -> new RuntimeException("Vehicle not found"));

    Location pickUpLocation = locationRepository.findById(dto.getPickUpLocationId())
        .orElseThrow(() -> new RuntimeException("Pick-up location not found"));

    Location returnLocation = locationRepository.findById(dto.getReturnLocationId())
        .orElseThrow(() -> new RuntimeException("Return location not found"));

    long days = Duration.between(dto.getPickUpDateTime(), dto.getReturnDateTime()).toDays();

    if (days <= 0) {
      throw new RuntimeException("Return date must be after pick-up date");
    }

    if (reservationRepository.hasOverlappingReservation(vehicle.getId(), ReservationStatusEnum.CANCELLED,
        dto.getPickUpDateTime(), dto.getReturnDateTime())) {
      throw new RuntimeException("Vehicle is already booked for the selected dates");
    }

    BigDecimal basePrice = vehicle.getPricePerDay().multiply(BigDecimal.valueOf(days));

    // Server-side add-ons + discount total (client values are ignored here).
    // Add-ons are priced PER DAY: each selected AdditionalService contributes
    // pricePerDay × days to the subtotal (extra driver, GPS, child seat, etc.).
    List<ReservationAdditionalService> addsOns = new ArrayList<>();
    BigDecimal servicesTotal = BigDecimal.ZERO;
    if (dto.getServiceIds() != null) {
      for (Long serviceId : dto.getServiceIds()) {
        AdditionalService service = additionalServiceRepository.findById(serviceId)
            .orElseThrow(() -> new RuntimeException("Service not found"));
        if (!Boolean.TRUE.equals(service.getActive())) {
          throw new RuntimeException("Service is currently unavailable");
        }
        BigDecimal priceAtBooking = service.getPricePerDay();
        servicesTotal = servicesTotal.add(priceAtBooking.multiply(BigDecimal.valueOf(days)));

        ReservationAdditionalService ras = new ReservationAdditionalService();
        ras.setAdditionalService(service);
        ras.setPricePerDayAtBooking(priceAtBooking);
        ras.setQuantity(1);
        addsOns.add(ras);
      }
    }

    BigDecimal subtotal = basePrice.add(servicesTotal);
    BigDecimal discountAmount = discountService.applyDiscount(dto.getDiscountCode(), subtotal);
    BigDecimal totalPrice = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

    Reservation reservation = new Reservation();
    reservation.setUser(currentUser);
    reservation.setVehicle(vehicle);
    reservation.setPickUpLocation(pickUpLocation);
    reservation.setReturnLocation(returnLocation);
    reservation.setPickUpDateTime(dto.getPickUpDateTime());
    reservation.setReturnDateTime(dto.getReturnDateTime());
    reservation.setTotalPrice(totalPrice);
    reservation.setDepositAmount(dto.getDepositAmount() != null ? dto.getDepositAmount() : BigDecimal.ZERO);
    reservation.setDiscountAmount(discountAmount);
    reservation.setAdditionalCharges(dto.getAdditionalCharges() != null ? dto.getAdditionalCharges() : BigDecimal.ZERO);
    reservation.setNotes(dto.getNotes());

    Reservation saved = reservationRepository.save(reservation);

    for (ReservationAdditionalService ras : addsOns) {
      ras.setReservation(saved);
      reservationAdditionalServiceRepository.save(ras);
    }

    // CUSTOMER bookings: auto-create the Rental + Invoice so the customer can
    // pay immediately. Staff/admin-created reservations keep the existing flow
    // (admin converts a confirmed reservation to a rental, then invoices it).
    if (currentUser.getRole() == RoleEnum.CUSTOMER) {
      Rental rental = new Rental();
      rental.setReservation(saved);
      rental.setVehicle(vehicle);
      rental.setUser(currentUser);
      rental.setPickUpLocation(pickUpLocation);
      rental.setReturnLocation(returnLocation);
      rental.setPickUpDateTime(dto.getPickUpDateTime());
      rental.setExpectedReturnDateTime(dto.getReturnDateTime());
      rental.setBasePrice(subtotal);
      rental.setDiscountAmount(discountAmount);
      rental.setAdditionalCharges(BigDecimal.ZERO);
      rental.setLateFee(BigDecimal.ZERO);
      rental.setTotalPrice(totalPrice);
      rental.setStatus(RentalStatusEnum.PENDING);
      rental.setNotes(dto.getNotes());

      Rental savedRental = rentalRepository.save(rental);

      Invoice invoice = new Invoice();
      invoice.setRental(savedRental);
      invoice.setInvoiceNumber(generateInvoiceNumber());
      invoice.setDueDate(dto.getReturnDateTime());
      invoice.setSubtotal(subtotal);
      invoice.setAdditionalServicesTotal(servicesTotal);
      invoice.setDiscountAmount(discountAmount);
      invoice.setTaxAmount(BigDecimal.ZERO);
      invoice.setLateFee(BigDecimal.ZERO);
      invoice.setTotalAmount(totalPrice);
      invoice.setStatus(InvoiceStatusEnum.UNPAID);
      invoice.setPaymentMethod(PaymentMethodEnum.KHQR);

      invoiceRepository.save(invoice);

      NotificationRequestDTO notification = new NotificationRequestDTO();
      notification.setType(NotificationTypeEnum.BOOKING_CONFIRMED);
      notification.setTitle("Booking created");
      notification.setMessage("Your booking request has been received. Please complete your payment.");
      notificationService.createNotification(currentUser.getId(), notification);
    }

    return toResponse(saved);
  }

  @Override
  public ReservationResponseDTO getReservationById(Long id) {
    Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Reservation not found"));

    User currentUser = getCurrentUser();

    boolean isOwner = reservation.getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to view this reservation");
    }

    return toResponse(reservation);
  }

  @Override
  public List<ReservationResponseDTO> getAllReservations() {
    return reservationRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
        .map(r -> toResponse(r))
        .toList();
  }

  // Customer get their reservations
  @Override
  public List<ReservationResponseDTO> getMyReservations() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUsername = authentication.getName();
    User currentUser = userRepository.findByEmail(currentUsername)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

    return reservationRepository.findByUserId(currentUser.getId()).stream()
        .map(r -> toResponse(r))
        .toList();
  }

  @Override
  @Transactional
  public ReservationResponseDTO changeReservationStatus(Long id, ReservationStatusEnum status) {
    Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Reservation not found"));

    reservation.setStatus(status);
    Reservation saved = reservationRepository.save(reservation);

    if (status == ReservationStatusEnum.CONFIRMED) {
      NotificationRequestDTO notification = new NotificationRequestDTO();
      notification.setType(NotificationTypeEnum.BOOKING_CONFIRMED);
      notification.setTitle("Booking confirmed");
      notification.setMessage("Your booking has been confirmed. We look forward to seeing you!");
      notificationService.createNotification(saved.getUser().getId(), notification);
    }

    return toResponse(saved);
  }

  @Override
  @Transactional
  public ReservationResponseDTO cancelReservation(Long id) {
    Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Reservation not found"));

    User currentUser = getCurrentUser();
    boolean isOwner = reservation.getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to cancel this reservation");
    }

    if (reservation.getStatus() != ReservationStatusEnum.PENDING) {
      throw new RuntimeException("Only pending reservations can be cancelled");
    }

    reservation.setStatus(ReservationStatusEnum.CANCELLED);
    Reservation saved = reservationRepository.save(reservation);

    // If a Rental+Invoice was auto-created, void the unpaid invoice so the
    // customer can no longer pay for a cancelled booking.
    rentalRepository.findByReservationId(id).ifPresent(rental -> {
      invoiceRepository.findByRentalId(rental.getId()).ifPresent(invoice -> {
        if (invoice.getStatus() == InvoiceStatusEnum.UNPAID) {
          invoice.setStatus(InvoiceStatusEnum.CANCELLED);
          invoiceRepository.save(invoice);
        }
      });
    });

    NotificationRequestDTO notification = new NotificationRequestDTO();
    notification.setType(NotificationTypeEnum.BOOKING_CANCELLED);
    notification.setTitle("Booking cancelled");
    notification.setMessage("Your booking has been cancelled.");
    notificationService.createNotification(saved.getUser().getId(), notification);

    return toResponse(saved);
  }

  @Override
  @Transactional
  public ReservationResponseDTO updateReservation(Long id, ReservationRequestDTO dto) {
    Reservation reservation = reservationRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Reservation not found"));

    Vehicle vehicle = vehicleRepository.findById(dto.getVehicleId())
        .orElseThrow(() -> new RuntimeException("Vehicle not found"));

    Location pickUpLocation = locationRepository.findById(dto.getPickUpLocationId())
        .orElseThrow(() -> new RuntimeException("Pick-up location not found"));

    Location returnLocation = locationRepository.findById(dto.getReturnLocationId())
        .orElseThrow(() -> new RuntimeException("Return location not found"));

    long days = Duration.between(dto.getPickUpDateTime(), dto.getReturnDateTime()).toDays();

    if (days <= 0) {
      throw new RuntimeException("Return date must be after pick-up date");
    }

    BigDecimal basePrice = vehicle.getPricePerDay().multiply(BigDecimal.valueOf(days));

    BigDecimal servicesTotal = BigDecimal.ZERO;
    if (dto.getServiceIds() != null) {
      for (Long serviceId : dto.getServiceIds()) {
        AdditionalService service = additionalServiceRepository.findById(serviceId)
            .orElseThrow(() -> new RuntimeException("Service not found"));
        if (!Boolean.TRUE.equals(service.getActive())) {
          throw new RuntimeException("Service is currently unavailable");
        }
        servicesTotal = servicesTotal.add(service.getPricePerDay().multiply(BigDecimal.valueOf(days)));
      }
    }

    BigDecimal subtotal = basePrice.add(servicesTotal);
    BigDecimal discountAmount = discountService.applyDiscount(dto.getDiscountCode(), subtotal);
    BigDecimal totalPrice = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

    reservation.setVehicle(vehicle);
    reservation.setPickUpLocation(pickUpLocation);
    reservation.setReturnLocation(returnLocation);
    reservation.setPickUpDateTime(dto.getPickUpDateTime());
    reservation.setReturnDateTime(dto.getReturnDateTime());
    reservation.setTotalPrice(totalPrice);
    reservation
        .setDepositAmount(dto.getDepositAmount() != null ? dto.getDepositAmount() : reservation.getDepositAmount());
    reservation.setDiscountAmount(discountAmount);
    reservation.setAdditionalCharges(
        dto.getAdditionalCharges() != null ? dto.getAdditionalCharges() : reservation.getAdditionalCharges());
    reservation.setNotes(dto.getNotes());

    Reservation saved = reservationRepository.save(reservation);

    // Keep the selected per-day add-ons in sync with the updated request.
    reservationAdditionalServiceRepository.deleteByReservationId(saved.getId());
    if (dto.getServiceIds() != null) {
      for (Long serviceId : dto.getServiceIds()) {
        AdditionalService service = additionalServiceRepository.findById(serviceId)
            .orElseThrow(() -> new RuntimeException("Service not found"));
        ReservationAdditionalService ras = new ReservationAdditionalService();
        ras.setReservation(saved);
        ras.setAdditionalService(service);
        ras.setPricePerDayAtBooking(service.getPricePerDay());
        ras.setQuantity(1);
        reservationAdditionalServiceRepository.save(ras);
      }
    }

    return toResponse(saved);
  }

  @Override
  public void deleteReservation(Long id) {
    if (!reservationRepository.existsById(id)) {
      throw new RuntimeException("Reservation not found");
    }

    reservationRepository.deleteById(id);
  }

  private ReservationResponseDTO toResponse(Reservation r) {
    List<ReservationAdditionalService> addsOns = reservationAdditionalServiceRepository.findByReservationId(r.getId());
    List<AdditionalServiceResponseDTO> selectedServices = addsOns.stream()
        .map(ras -> toAdditionalServiceResponse(ras.getAdditionalService()))
        .toList();
    long days = Duration.between(r.getPickUpDateTime(), r.getReturnDateTime()).toDays();
    BigDecimal additionalServicesTotal = addsOns.stream()
        .map(ras -> ras.getPricePerDayAtBooking().multiply(BigDecimal.valueOf(days)))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);

    return new ReservationResponseDTO(
        r.getId(), r.getUser().getId(), r.getVehicle().getId(), r.getPickUpLocation().getId(),
        r.getReturnLocation().getId(), r.getPickUpDateTime(), r.getReturnDateTime(), r.getStatus(), r.getTotalPrice(),
        r.getDepositAmount(),
        r.getDiscountAmount(), r.getAdditionalCharges(), resolveRentalId(r), resolveInvoiceId(r), r.getNotes(),
        r.getCreatedAt(),
        r.getUpdatedAt(), selectedServices, additionalServicesTotal);
  }

  private AdditionalServiceResponseDTO toAdditionalServiceResponse(AdditionalService s) {
    return new AdditionalServiceResponseDTO(s.getId(), s.getName(), s.getNameKh(), s.getDescription(),
        s.getPricePerDay(), s.getIcon(), s.getActive());
  }

  private Long resolveRentalId(Reservation r) {
    return rentalRepository.findByReservationId(r.getId()).map(Rental::getId).orElse(null);
  }

  private Long resolveInvoiceId(Reservation r) {
    return rentalRepository.findByReservationId(r.getId())
        .flatMap(rental -> invoiceRepository.findByRentalId(rental.getId()))
        .map(Invoice::getId)
        .orElse(null);
  }

  private String generateInvoiceNumber() {
    String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String invoiceNum;
    do {
      long randomPart = (long) (Math.random() * 9000L) + 1000L;
      invoiceNum = "INV-" + datePart + "-" + randomPart;
    } while (invoiceRepository.existsByInvoiceNumber(invoiceNum));
    return invoiceNum;
  }

  // Ownership function
  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUsername = authentication.getName();
    return userRepository.findByEmail(currentUsername)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
  }
}