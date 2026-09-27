package com.example.spring_boot_project_api.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.spring_boot_project_api.dto.request.invoice.InvoicePaymentConfirmDTO;
import com.example.spring_boot_project_api.dto.request.invoice.InvoicePaymentMethodDTO;
import com.example.spring_boot_project_api.dto.request.invoice.InvoiceRequestDTO;
import com.example.spring_boot_project_api.dto.request.notification.NotificationRequestDTO;
import com.example.spring_boot_project_api.dto.response.invoice.InvoiceResponseDTO;
import com.example.spring_boot_project_api.enums.AuthProviderEnum;
import com.example.spring_boot_project_api.enums.InvoiceStatusEnum;
import com.example.spring_boot_project_api.enums.NotificationTypeEnum;
import com.example.spring_boot_project_api.enums.PaymentMethodEnum;
import com.example.spring_boot_project_api.enums.ReservationStatusEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.Invoice;
import com.example.spring_boot_project_api.model.Rental;
import com.example.spring_boot_project_api.model.Reservation;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.InvoiceRepository;
import com.example.spring_boot_project_api.repository.RentalRepository;
import com.example.spring_boot_project_api.repository.ReservationRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.BakongService;
import com.example.spring_boot_project_api.service.InvoicePdfService;
import com.example.spring_boot_project_api.service.InvoiceService;
import com.example.spring_boot_project_api.service.NotificationService;
import com.example.spring_boot_project_api.service.TelegramService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {
  private final InvoiceRepository invoiceRepository;
  private final UserRepository userRepository;
  private final RentalRepository rentalRepository;
  // ⬇ needed to cascade PENDING -> CONFIRMED onto the Reservation once
  // its Rental's invoice is paid. See syncRentalReservationOnPaid() below.
  private final ReservationRepository reservationRepository;
  private final BakongService bakongService;
  private final NotificationService notificationService;
  private final InvoicePdfService invoicePdfService;
  private final TelegramService telegramService;

  @Override
  public InvoiceResponseDTO createInvoice(InvoiceRequestDTO dto) {
    if (invoiceRepository.existsByRentalId(dto.getRentalId())) {
      throw new RuntimeException("An invoice already exists for this rental");
    }

    Rental rental = rentalRepository.findById(dto.getRentalId())
        .orElseThrow(() -> new RuntimeException("Rental not found"));

    BigDecimal subtotal = dto.getSubtotal();
    BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
    BigDecimal tax = dto.getTaxAmount() != null ? dto.getTaxAmount() : BigDecimal.ZERO;
    BigDecimal lateFee = dto.getLateFee() != null ? dto.getLateFee() : BigDecimal.ZERO;
    BigDecimal additionalServices = dto.getAdditionalServicesTotal() != null ? dto.getAdditionalServicesTotal()
        : BigDecimal.ZERO;

    BigDecimal totalAmount = subtotal.add(additionalServices).subtract(discount).add(tax).add(lateFee);

    Invoice invoice = new Invoice();
    invoice.setRental(rental);
    invoice.setInvoiceNumber(generateInvoiceNumber());
    invoice.setDueDate(dto.getDueDate());
    invoice.setSubtotal(subtotal);
    invoice.setAdditionalServicesTotal(additionalServices);
    invoice.setDiscountAmount(discount);
    invoice.setTaxAmount(tax);
    invoice.setLateFee(lateFee);
    invoice.setTotalAmount(totalAmount);
    invoice.setStatus(dto.getStatus() != null ? dto.getStatus() : InvoiceStatusEnum.UNPAID);
    invoice.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : PaymentMethodEnum.KHQR);

    Invoice saved = invoiceRepository.save(invoice);
    return toResponse(saved);
  }

  @Override
  public InvoiceResponseDTO getInvoiceById(Long id) {
    Invoice invoice = invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Invoice not found"));
    assertCanView(invoice);
    return toResponse(invoice);
  }

  @Override
  public List<InvoiceResponseDTO> getMyInvoices() {
    User currentUser = getCurrentUser();
    return invoiceRepository.findByRentalUserEmail(currentUser.getEmail())
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public List<InvoiceResponseDTO> getAllInvoices() {
    return invoiceRepository.findAll().stream()
        .map(this::toResponse).toList();
  }

  @Override
  public InvoiceResponseDTO updateInvoice(Long id, InvoiceRequestDTO dto) {
    Invoice invoice = invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Invoice not found"));

    Rental rental = rentalRepository.findById(dto.getRentalId())
        .orElseThrow(() -> new RuntimeException("Rental not found"));

    BigDecimal subtotal = dto.getSubtotal();
    BigDecimal discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
    BigDecimal tax = dto.getTaxAmount() != null ? dto.getTaxAmount() : BigDecimal.ZERO;
    BigDecimal lateFee = dto.getLateFee() != null ? dto.getLateFee() : BigDecimal.ZERO;
    BigDecimal additionalServices = dto.getAdditionalServicesTotal() != null ? dto.getAdditionalServicesTotal()
        : BigDecimal.ZERO;

    BigDecimal totalAmount = subtotal.add(additionalServices).subtract(discount).add(tax).add(lateFee);

    invoice.setRental(rental);
    invoice.setDueDate(dto.getDueDate());
    invoice.setSubtotal(subtotal);
    invoice.setAdditionalServicesTotal(additionalServices);
    invoice.setDiscountAmount(discount);
    invoice.setTaxAmount(tax);
    invoice.setLateFee(lateFee);
    invoice.setTotalAmount(totalAmount);
    invoice.setStatus(dto.getStatus() != null ? dto.getStatus() : InvoiceStatusEnum.UNPAID);
    invoice.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : PaymentMethodEnum.KHQR);

    Invoice saved = invoiceRepository.save(invoice);

    // The admin "Invoices" table lets staff flip status via a plain dropdown
    // (InvoiceManagement.vue's onStatusChange -> invoiceService.update() for
    // anything other than newStatus === 'PAID'). If staff pick PAID through
    // THIS path rather than the dedicated markPaid endpoint, still cascade
    // the same PENDING -> CONFIRMED sync so both paths behave consistently.
    if (saved.getStatus() == InvoiceStatusEnum.PAID) {
      syncRentalReservationOnPaid(saved);
    }

    return toResponse(saved);
  }

  @Override
  public void deleteInvoice(Long id) {
    if (!invoiceRepository.existsById(id)) {
      throw new RuntimeException("Invoice not found");
    }

    invoiceRepository.deleteById(id);
  }

  private InvoiceResponseDTO toResponse(Invoice i) {
    User u = i.getRental().getUser();
    return new InvoiceResponseDTO(
        i.getId(), i.getRental().getId(),
        u.getFirstName() + " " + u.getLastName(), u.getEmail(), u.getPhone(),
        i.getInvoiceNumber(), i.getIssueDate(),
        i.getDueDate(), i.getSubtotal(), i.getAdditionalServicesTotal(), i.getDiscountAmount(), i.getTaxAmount(),
        i.getLateFee(), i.getTotalAmount(), i.getStatus(), i.getPaymentMethod(), i.getPaidAt(), i.getCreatedAt());
  }

  @Override
  @Transactional
  public InvoiceResponseDTO confirmPayment(Long id, InvoicePaymentConfirmDTO dto) {
    Invoice invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Invoice not found"));

    User currentUser = getCurrentUser();
    boolean isOwner = invoice.getRental().getUser().getId().equals(currentUser.getId());

    if (!isOwner) {
      throw new RuntimeException("You are not authorized to pay this invoice");
    }

    if (invoice.getStatus() == InvoiceStatusEnum.PAID) {
      return toResponse(invoice);
    }
    if (invoice.getStatus() == InvoiceStatusEnum.CANCELLED) {
      throw new RuntimeException("This invoice has been cancelled");
    }

    com.example.spring_boot_project_api.dto.response.bakong.BakongResponse bakongResponse =
        bakongService.checkTransactionByMD5(
            new com.example.spring_boot_project_api.dto.request.bakong.CheckTransactionRequest(dto.md5()));

    if (!bakongResponse.isSuccess()) {
      throw new RuntimeException("Payment has not been confirmed");
    }

    invoice.setStatus(InvoiceStatusEnum.PAID);
    // ⬇ FIXED: this path (customer pays via Bakong QR) never set paidAt,
    // so a Bakong-confirmed invoice kept paidAt = null forever even though
    // status was PAID — unlike markPaid() (staff/cash), which always set
    // it. Any report or PDF that sorts/reads paidAt was silently wrong for
    // every Bakong payment. Set it here so both payment paths agree.
    invoice.setPaidAt(LocalDateTime.now());
    Invoice saved = invoiceRepository.save(invoice);

    // customer paid via Bakong QR — same downstream effect as staff
    // marking it paid by hand, so cascade the same PENDING -> CONFIRMED sync.
    syncRentalReservationOnPaid(saved);

    NotificationRequestDTO notification = new NotificationRequestDTO();
    notification.setType(NotificationTypeEnum.PAYMENT_SUCCESS);
    notification.setTitle("Payment successful");
    notification.setMessage("Your payment for invoice " + saved.getInvoiceNumber() + " was received. Thank you!");
    notificationService.createNotification(currentUser.getId(), notification);

    sendInvoiceToTelegram(saved, currentUser, "✅ Payment received — here is your invoice.");

    return toResponse(saved);
  }

  @Override
  @Transactional
  public InvoiceResponseDTO setPaymentMethod(Long id, InvoicePaymentMethodDTO dto) {
    Invoice invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Invoice not found"));

    User currentUser = getCurrentUser();
    boolean isOwner = invoice.getRental().getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to update this invoice");
    }

    if (invoice.getStatus() == InvoiceStatusEnum.PAID) {
      throw new RuntimeException("This invoice is already paid");
    }
    if (invoice.getStatus() == InvoiceStatusEnum.CANCELLED) {
      throw new RuntimeException("This invoice has been cancelled");
    }

    invoice.setPaymentMethod(dto.paymentMethod());
    if (dto.paymentMethod() == PaymentMethodEnum.CASH) {
      invoice.setPaidAt(null);
    }

    Invoice saved = invoiceRepository.save(invoice);
    return toResponse(saved);
  }

  // Sends the Telegram invoice for ANY manually-confirmed payment, not just
  // CASH. A KHQR invoice marked paid by hand (e.g. while Bakong auto-confirm
  // isn't wired up for it) still notifies the customer on Telegram.
  @Override
  @Transactional
  public InvoiceResponseDTO markPaid(Long id) {
    Invoice invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Invoice not found"));

    if (invoice.getStatus() == InvoiceStatusEnum.PAID) {
      return toResponse(invoice);
    }
    if (invoice.getStatus() == InvoiceStatusEnum.CANCELLED) {
      throw new RuntimeException("Cannot mark a cancelled invoice as paid");
    }

    invoice.setStatus(InvoiceStatusEnum.PAID);
    invoice.setPaidAt(LocalDateTime.now());
    Invoice saved = invoiceRepository.save(invoice);

    // staff marked this paid by hand — cascade PENDING -> CONFIRMED
    // onto the Rental's Reservation. See syncRentalReservationOnPaid.
    syncRentalReservationOnPaid(saved);

    User customer = saved.getRental().getUser();
    NotificationRequestDTO notification = new NotificationRequestDTO();
    notification.setType(NotificationTypeEnum.PAYMENT_SUCCESS);
    notification.setTitle("Payment recorded");
    notification.setMessage("Your payment for invoice " + saved.getInvoiceNumber() + " has been recorded. Thank you!");
    notificationService.createNotification(customer.getId(), notification);

    // Send Telegram invoice regardless of payment method (CASH or a
    // manually-verified KHQR payment) whenever staff mark an invoice paid.
    sendInvoiceToTelegram(saved, customer, "✅ Payment recorded — here is your invoice.");

    return toResponse(saved);
  }

  // Cascades a PAID invoice onto its Reservation (through the invoice's
  // Rental) — but ONLY moves it from PENDING to CONFIRMED.
  //
  // Deliberately Reservation-only. We do NOT touch Rental.status here:
  // Rental's states (PICKED_UP, ACTIVE, RETURNED, COMPLETED) represent
  // physical handover events a staff member confirms by hand at the
  // counter — a payment landing tells us nothing about whether the car has
  // actually been picked up. RentalStatusEnum.CONFIRMED is explicitly
  // documented as unused in practice, precisely because Rental is only
  // ever created from an already-CONFIRMED Reservation — so setting it here
  // would just introduce a status value nothing else expects.
  //
  // Reservation, on the other hand, represents booking *commitment* —
  // payment landing IS strong evidence the booking is real, not tentative,
  // so PENDING -> CONFIRMED makes sense there.
  //
  // Deliberately narrow even for Reservation: we never touch one that's
  // already past PENDING (CONFIRMED or CANCELLED) — a paid-but-cancelled
  // reservation still needs a human decision (refund?), not a silent flip.
  //
  // Called from every path that can set an invoice to PAID: markPaid(),
  // confirmPayment() (Bakong), and updateInvoice() when staff pick PAID
  // through the plain status dropdown instead of the dedicated endpoint.
  private void syncRentalReservationOnPaid(Invoice invoice) {
    Rental rental = invoice.getRental();
    if (rental == null) {
      return;
    }

    Reservation reservation = rental.getReservation();
    if (reservation != null && reservation.getStatus() == ReservationStatusEnum.PENDING) {
      reservation.setStatus(ReservationStatusEnum.CONFIRMED);
      reservationRepository.save(reservation);
    }
  }

  @Override
  public byte[] downloadInvoicePdf(Long id) {
    Invoice invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Invoice not found"));
    assertCanView(invoice);
    return invoicePdfService.generate(invoice);
  }

  private void sendInvoiceToTelegram(Invoice invoice, User customer, String caption) {
    if (customer.getAuthProvider() == AuthProviderEnum.TELEGRAM && customer.getProviderId() != null) {
      byte[] pdf = invoicePdfService.generate(invoice);
      telegramService.sendDocument(customer.getProviderId(), pdf, invoice.getInvoiceNumber() + ".pdf", caption);
    }
  }

  private void assertCanView(Invoice invoice) {
    User currentUser = getCurrentUser();
    boolean isOwner = invoice.getRental().getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to view this invoice");
    }
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

  private User getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String currentUsername = authentication.getName();
    return userRepository.findByEmail(currentUsername)
        .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
  }
}