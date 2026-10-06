package com.example.spring_boot_project_api.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.spring_boot_project_api.dto.request.vehicle.VehicleRequestDTO;
import com.example.spring_boot_project_api.dto.response.reservation.BookedDateDTO;
import com.example.spring_boot_project_api.dto.response.vehicle.VehicleResponseDTO;
import com.example.spring_boot_project_api.enums.CarTypeEnum;
import com.example.spring_boot_project_api.enums.FuelTypeEnum;
import com.example.spring_boot_project_api.enums.TransmissionEnum;

public interface VehicleService {
  VehicleResponseDTO createVehicle(VehicleRequestDTO dto);

  VehicleResponseDTO getVehicleById(Long id);

  Page<VehicleResponseDTO> getAllVehicles(Pageable pageable);

  // FIX: replaces getAllVehiclesByBrand (was dead code — had no controller
  // endpoint). All params optional; pass null for whichever aren't filtered.
  Page<VehicleResponseDTO> searchVehicles(
      Long brandId, CarTypeEnum type, TransmissionEnum transmission,
      FuelTypeEnum fuelType, BigDecimal minPrice, BigDecimal maxPrice, Integer seats, Pageable pageable);

  VehicleResponseDTO updateVehicle(Long id, VehicleRequestDTO dto);

  void deleteVehicle(Long id);

  /**
   * Date windows already reserved for a vehicle (excluding cancelled
   * bookings and windows that have fully ended). Powers the "unavailable
   * dates" hints on the booking form. The authoritative overlap check stays
   * server-side on reservation creation.
   */
  List<BookedDateDTO> getBookedDates(Long vehicleId);
}