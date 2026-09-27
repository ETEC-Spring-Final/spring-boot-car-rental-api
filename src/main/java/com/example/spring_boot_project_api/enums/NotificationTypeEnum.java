package com.example.spring_boot_project_api.enums;

public enum NotificationTypeEnum {
  BOOKING_CONFIRMED,
  BOOKING_CANCELLED,
  RENTAL_STARTING_SOON,
  RENTAL_ENDING_SOON,
  PAYMENT_SUCCESS,
  PAYMENT_FAILED,
  RETURN_REMINDER,
  LATE_RETURN,
  PROMOTION,
  NEW_REVIEW, // ⬅ NEW — staff notification when a customer submits a review
}