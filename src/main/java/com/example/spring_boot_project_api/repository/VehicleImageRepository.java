package com.example.spring_boot_project_api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.spring_boot_project_api.model.VehicleImage;

public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

  List<VehicleImage> findByVehicleId(Long vehicleId);

  @Query("""
    SELECT vi 
    FROM VehicleImage vi 
    WHERE vi.vehicle.id = :vehicleId 
      AND vi.attachment.isPrimary = true
    """)
  Optional<VehicleImage> findPrimaryByVehicleId(Long vehicleId);
}