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
import com.example.spring_boot_project_api.enums.InvoiceStatusEnum;
import com.example.spring_boot_project_api.enums.NotificationTypeEnum;
import com.example.spring_boot_project_api.enums.PaymentMethodEnum;
import com.example.spring_boot_project_api.enums.RoleEnum;
import com.example.spring_boot_project_api.model.Invoice;
import com.example.spring_boot_project_api.model.Rental;
import com.example.spring_boot_project_api.model.User;
import com.example.spring_boot_project_api.repository.InvoiceRepository;
import com.example.spring_boot_project_api.repository.RentalRepository;
import com.example.spring_boot_project_api.repository.UserRepository;
import com.example.spring_boot_project_api.service.BakongService;
import com.example.spring_boot_project_api.service.InvoicePdfService;
import com.example.spring_boot_project_api.service.InvoiceService;
import com.example.spring_boot_project_api.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {
  private final InvoiceRepository invoiceRepository;
  private final UserRepository userRepository;
  private final RentalRepository rentalRepository;
  private final BakongService bakongService;
  private final NotificationService notificationService;
  private final InvoicePdfService invoicePdfService;

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

    // FIX: additionalServices was missing from the total.
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
    return invoiceRepository.findByRentalUserEmail(currentUser.getEmail()) // find thru Rental -> User to get who's
                                                                           // invoices it is
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

    // FIX: additionalServices was missing from the total.
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
  public InvoiceResponseDTO confirmPayment(
      Long id,
      InvoicePaymentConfirmDTO dto) {

    // 1. Find invoice
    Invoice invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Invoice not found"));

    // 2. Get logged-in customer
    User currentUser = getCurrentUser();

    // 3. Check invoice ownership
    boolean isOwner = invoice.getRental()
        .getUser()
        .getId()
        .equals(currentUser.getId());

    if (!isOwner) {
      throw new RuntimeException(
          "You are not authorized to pay this invoice");
    }

    // 4. Prevent duplicate payment processing
    if (invoice.getStatus() == InvoiceStatusEnum.PAID) {
      return toResponse(invoice);
    }

    // 5. Don't allow cancelled invoice
    if (invoice.getStatus() == InvoiceStatusEnum.CANCELLED) {
      throw new RuntimeException(
          "This invoice has been cancelled");
    }

    // 6. Check payment with Bakong
    com.example.spring_boot_project_api.dto.response.bakong.BakongResponse bakongResponse = bakongService
        .checkTransactionByMD5(
            new com.example.spring_boot_project_api.dto.request.bakong.CheckTransactionRequest(
                dto.md5()));

    // 7. Bakong says payment is not successful
    if (!bakongResponse.isSuccess()) {
      throw new RuntimeException(
          "Payment has not been confirmed");
    }

    // =====================================================
    // PAYMENT SUCCESS
    // =====================================================

    // 8. Mark invoice as PAID
    invoice.setStatus(InvoiceStatusEnum.PAID);

    // 9. Save payment time
    invoice.setPaidAt(LocalDateTime.now());

    // 10. Save invoice
    Invoice saved = invoiceRepository.save(invoice);

    // =====================================================
    // SEND PAYMENT SUCCESS NOTIFICATION
    // =====================================================

    NotificationRequestDTO notification = new NotificationRequestDTO();

    notification.setType(
        NotificationTypeEnum.PAYMENT_SUCCESS);

    notification.setTitle(
        "Payment successful 🎉");

    notification.setMessage(
        "Your payment for invoice "
            + saved.getInvoiceNumber()
            + " was received. Thank you!");

    notificationService.createNotification(
        currentUser.getId(),
        notification);

    return toResponse(saved);
  }

  // @Override
  // @Transactional
  // public InvoiceResponseDTO confirmPayment(Long id, InvoicePaymentConfirmDTO
  // dto) {
  // Invoice invoice = invoiceRepository.findById(id)
  // .orElseThrow(() -> new RuntimeException("Invoice not found"));

  // User currentUser = getCurrentUser();
  // boolean isOwner =
  // invoice.getRental().getUser().getId().equals(currentUser.getId());

  // if (!isOwner) {
  // throw new RuntimeException("You are not authorized to pay this invoice");
  // }

  // if (invoice.getStatus() == InvoiceStatusEnum.PAID) {
  // return toResponse(invoice);
  // }
  // if (invoice.getStatus() == InvoiceStatusEnum.CANCELLED) {
  // throw new RuntimeException("This invoice has been cancelled");
  // }

  // com.example.spring_boot_project_api.dto.response.bakong.BakongResponse
  // bakongResponse =
  // bakongService.checkTransactionByMD5(
  // new
  // com.example.spring_boot_project_api.dto.request.bakong.CheckTransactionRequest(dto.md5()));

  // if (!bakongResponse.isSuccess()) {
  // throw new RuntimeException("Payment has not been confirmed");
  // }

  // invoice.setStatus(InvoiceStatusEnum.PAID);
  // Invoice saved = invoiceRepository.save(invoice);

  // NotificationRequestDTO notification = new NotificationRequestDTO();
  // notification.setType(NotificationTypeEnum.PAYMENT_SUCCESS);
  // notification.setTitle("Payment successful");
  // notification.setMessage("Your payment for invoice " +
  // saved.getInvoiceNumber() + " was received. Thank you!");
  // notificationService.createNotification(currentUser.getId(), notification);

  // return toResponse(saved);
  // }

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

    // Choosing CASH records intent only: the invoice stays UNPAID until a
    // staff/admin member marks it PAID at pickup. KHQR keeps the QR flow.
    invoice.setPaymentMethod(dto.paymentMethod());
    if (dto.paymentMethod() == PaymentMethodEnum.CASH) {
      invoice.setPaidAt(null);
    }

    Invoice saved = invoiceRepository.save(invoice);
    return toResponse(saved);
  }

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

    // CASH payments were never confirmed online — notify the customer that
    // their in-person payment has been recorded.
    if (saved.getPaymentMethod() == PaymentMethodEnum.CASH) {
      User customer = saved.getRental().getUser();
      NotificationRequestDTO notification = new NotificationRequestDTO();
      notification.setType(NotificationTypeEnum.PAYMENT_SUCCESS);
      notification.setTitle("Cash payment recorded");
      notification
          .setMessage("Your cash payment for invoice " + saved.getInvoiceNumber() + " has been recorded. Thank you!");
      notificationService.createNotification(customer.getId(), notification);
    }

    return toResponse(saved);
  }

  @Override
  public byte[] downloadInvoicePdf(Long id) {
    Invoice invoice = invoiceRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Invoice not found"));
    assertCanView(invoice);
    return invoicePdfService.generate(invoice);
  }

  private void assertCanView(Invoice invoice) {
    User currentUser = getCurrentUser();
    boolean isOwner = invoice.getRental().getUser().getId().equals(currentUser.getId());
    boolean isStaff = currentUser.getRole() != RoleEnum.CUSTOMER;

    if (!isOwner && !isStaff) {
      throw new RuntimeException("You are not authorized to view this invoice");
    }
  }

  // Generate Invoice Number
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