package com.example.spring_boot_project_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.spring_boot_project_api.model.UserDevice;

import java.util.List;
import java.util.Optional;
public interface UserDeviceRepository
        extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findByUserId(Long userId);

    Optional<UserDevice> findByFcmToken(String fcmToken);

    void deleteByFcmToken(String fcmToken);
}