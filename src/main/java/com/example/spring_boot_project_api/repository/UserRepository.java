package com.example.spring_boot_project_api.repository;

import java.util.List; // ⬅ NEW
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.spring_boot_project_api.enums.AuthProviderEnum;
import com.example.spring_boot_project_api.enums.RoleEnum; // ⬅ NEW
import com.example.spring_boot_project_api.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  // Used by TelegramAuthService to find an existing Telegram-linked
  // account on repeat logins (Telegram never provides an email, so we
  // can't look these up by findByEmail like Google/Facebook accounts).
  Optional<User> findByProviderIdAndAuthProvider(String providerId, AuthProviderEnum authProvider);

  // ⬅ NEW — used by ReservationServiceImpl.notifyStaff() to fan out
  // booking notifications to every admin/manager/staff account.
  List<User> findByRoleIn(List<RoleEnum> roles);
}