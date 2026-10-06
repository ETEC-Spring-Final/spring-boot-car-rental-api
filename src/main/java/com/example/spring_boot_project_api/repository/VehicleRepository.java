package com.example.spring_boot_project_api.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.spring_boot_project_api.enums.CarTypeEnum;
import com.example.spring_boot_project_api.enums.StatusEnum;
import com.example.spring_boot_project_api.model.Vehicle;

// FIX: added JpaSpecificationExecutor so VehicleSpecification (below) can
// combine any subset of filters without a separate findBy... method per
// combination.
public interface VehicleRepository extends JpaRepository<Vehicle, Long>,
    JpaSpecificationExecutor<Vehicle> {
  Page<Vehicle> findByStatus(StatusEnum status, Pageable pageable);

  Page<Vehicle> findByType(CarTypeEnum type, Pageable pageable);

  Page<Vehicle> findByBrandId(Long brandId, Pageable pageable);

  Optional<Vehicle> findByLicensePlate(String licensePlate);
}