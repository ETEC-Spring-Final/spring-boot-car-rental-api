package com.example.spring_boot_project_api.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.spring_boot_project_api.dto.request.vehicle.VehicleRequestDTO;
import com.example.spring_boot_project_api.dto.response.reservation.BookedDateDTO;
import com.example.spring_boot_project_api.dto.response.vehicle.VehicleResponseDTO;
import com.example.spring_boot_project_api.enums.CarTypeEnum;
import com.example.spring_boot_project_api.enums.FuelTypeEnum;
import com.example.spring_boot_project_api.enums.TransmissionEnum;
import com.example.spring_boot_project_api.service.VehicleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
  @Autowired
  private VehicleService vehicleService;

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public VehicleResponseDTO createVehicle(@Valid @RequestBody VehicleRequestDTO dto) {
    return vehicleService.createVehicle(dto);
  }

  @GetMapping("/{id}")
  public VehicleResponseDTO getVehicleById(@PathVariable Long id) {
    return vehicleService.getVehicleById(id);
  }

  // Date windows already reserved for this vehicle (powers the "unavailable
  // dates" hints on the booking form). Public read; the authoritative
  // overlap check stays on reservation creation.
  @GetMapping("/{id}/booked-dates")
  public List<BookedDateDTO> getBookedDates(@PathVariable Long id) {
    return vehicleService.getBookedDates(id);
  }

  // FIX: now actually applies the filters the frontend (Explore page) sends.
  // All params optional — calling GET /api/vehicles with none still returns
  // everything.
  // @GetMapping
  // public List<VehicleResponseDTO> getAllVehicles(
  // @RequestParam(required = false) Long brandId,
  // @RequestParam(required = false) CarTypeEnum type,
  // @RequestParam(required = false) TransmissionEnum transmission,
  // @RequestParam(required = false) FuelTypeEnum fuelType,
  // @RequestParam(required = false) BigDecimal minPrice,
  // @RequestParam(required = false) BigDecimal maxPrice,
  // @RequestParam(required = false) Integer seats) {
  // return vehicleService.searchVehicles(brandId, type, transmission, fuelType,
  // minPrice, maxPrice, seats);
  // }

  @GetMapping
  public Page<VehicleResponseDTO> getAllVehicles(
      @RequestParam(required = false) Long brandId,
      @RequestParam(required = false) CarTypeEnum type,
      @RequestParam(required = false) TransmissionEnum transmission,
      @RequestParam(required = false) FuelTypeEnum fuelType,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(required = false) Integer seats,
      @PageableDefault(size = 10) Pageable pageable) {
    return vehicleService.searchVehicles(brandId, type, transmission, fuelType,
        minPrice, maxPrice, seats, pageable);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PutMapping("/{id}")
  public VehicleResponseDTO updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleRequestDTO dto) {
    return vehicleService.updateVehicle(id, dto);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public void deleteVehicle(@PathVariable Long id) {
    vehicleService.deleteVehicle(id);
  }
}