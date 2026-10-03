package com.example.spring_boot_project_api.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.spring_boot_project_api.dto.request.vehicle.VehicleRequestDTO;
import com.example.spring_boot_project_api.dto.response.reservation.BookedDateDTO;
import com.example.spring_boot_project_api.dto.response.vehicle.VehicleResponseDTO;
import com.example.spring_boot_project_api.enums.CarTypeEnum;
import com.example.spring_boot_project_api.enums.FuelTypeEnum;
import com.example.spring_boot_project_api.enums.ReservationStatusEnum;
import com.example.spring_boot_project_api.enums.TransmissionEnum;
import com.example.spring_boot_project_api.model.Brand;
import com.example.spring_boot_project_api.model.Vehicle;
import com.example.spring_boot_project_api.model.VehicleImage;
import com.example.spring_boot_project_api.repository.AttachmentRepository;
import com.example.spring_boot_project_api.repository.BrandRepository;
import com.example.spring_boot_project_api.repository.ReservationRepository;
import com.example.spring_boot_project_api.repository.VehicleImageRepository;
import com.example.spring_boot_project_api.repository.VehicleRepository;
import com.example.spring_boot_project_api.service.VehicleService;
import com.example.spring_boot_project_api.specification.VehicleSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor 
public class VehicleServiceImpl implements VehicleService {
  private final VehicleRepository vehicleRepository;
  private final BrandRepository brandRepository;
  private final VehicleImageRepository vehicleImageRepository;
  private final AttachmentRepository attachmentRepository;
  private final ReservationRepository reservationRepository;

  @Override
  public VehicleResponseDTO createVehicle(VehicleRequestDTO dto) {
    if (vehicleRepository.findByLicensePlate(dto.getLicensePlate()).isPresent()) {
      throw new RuntimeException("Vehicle with that license plate already exists");
    }

    Brand brand = brandRepository.findById(dto.getBrandId()).orElseThrow(() -> new RuntimeException("Brand not found"));

    Vehicle vehicle = new Vehicle();
    vehicle.setBrand(brand);
    vehicle.setModel(dto.getModel());
    vehicle.setYearOfManufacture(dto.getYearOfManufacture());
    vehicle.setLicensePlate(dto.getLicensePlate());
    vehicle.setColor(dto.getColor());
    vehicle.setDoors(dto.getDoors());
    vehicle.setLuggages(dto.getLuggages());
    vehicle.setType(dto.getType());
    vehicle.setTransmission(dto.getTransmission());
    vehicle.setFuelType(dto.getFuelType());
    vehicle.setSeats(dto.getSeats());
    vehicle.setPricePerDay(dto.getPricePerDay());
    vehicle.setMileAge(dto.getMileAge());
    vehicle.setDescription(dto.getDescription());
    vehicle.setStatus(dto.getStatus());

    Vehicle saved = vehicleRepository.save(vehicle);
    return toResponse(saved);
  }

  @Override
  public VehicleResponseDTO getVehicleById(Long id) {
    Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(() -> new RuntimeException("Vehicle not found"));
    return toResponse(vehicle);
  }

  // @Override
  // public List<VehicleResponseDTO> getAllVehicles() {
  // return vehicleRepository.findAll().stream()
  // .map(this::toResponse)
  // .toList();
  // }

  @Override
  public Page<VehicleResponseDTO> getAllVehicles(Pageable pageable) {
    return vehicleRepository.findAll(pageable).map(this::toResponse);
  }

  // FIX: replaces getAllVehiclesByBrand (was dead code — no controller route
  // ever called it). Now backs the filter bar + brand chips on the Explore
  // page: any param left null is simply skipped by VehicleSpecification.

  // @Override
  // public List<VehicleResponseDTO> searchVehicles(
  // Long brandId, CarTypeEnum type, TransmissionEnum transmission,
  // FuelTypeEnum fuelType, BigDecimal minPrice, BigDecimal maxPrice, Integer
  // seats) {

  // var spec = VehicleSpecification.withFilters(brandId, type, transmission,
  // fuelType, minPrice, maxPrice, seats);
  // return vehicleRepository.findAll(spec).stream()
  // .map(this::toResponse)
  // .toList();
  // }

  @Override
  public Page<VehicleResponseDTO> searchVehicles(
      Long brandId,
      CarTypeEnum type,
      TransmissionEnum transmission,
      FuelTypeEnum fuelType,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Integer seats,
      Pageable pageable) {

    var spec = VehicleSpecification.withFilters(
        brandId,
        type,
        transmission,
        fuelType,
        minPrice,
        maxPrice,
        seats);

    return vehicleRepository.findAll(spec, pageable)
        .map(this::toResponse);
  }

  @Override
  public VehicleResponseDTO updateVehicle(Long id, VehicleRequestDTO dto) {
    Vehicle vehicle = vehicleRepository.findById(id).orElseThrow(() -> new RuntimeException("Vehicle not found"));

    Brand brand = brandRepository.findById(dto.getBrandId())
        .orElseThrow(() -> new RuntimeException("Brand not found"));

    vehicle.setBrand(brand);
    vehicle.setModel(dto.getModel());
    vehicle.setYearOfManufacture(dto.getYearOfManufacture());
    vehicle.setLicensePlate(dto.getLicensePlate());
    vehicle.setColor(dto.getColor());
    vehicle.setDoors(dto.getDoors());
    vehicle.setLuggages(dto.getLuggages());
    vehicle.setType(dto.getType());
    vehicle.setTransmission(dto.getTransmission());
    vehicle.setFuelType(dto.getFuelType());
    vehicle.setSeats(dto.getSeats());
    vehicle.setPricePerDay(dto.getPricePerDay());
    vehicle.setMileAge(dto.getMileAge());
    vehicle.setDescription(dto.getDescription());
    vehicle.setStatus(dto.getStatus());

    Vehicle saved = vehicleRepository.save(vehicle);
    return toResponse(saved);
  }

  @Override
  public void deleteVehicle(Long id) {
    if (!vehicleRepository.existsById(id)) {
      throw new RuntimeException("Vehicle not found");
    }

    List<VehicleImage> images = vehicleImageRepository.findByVehicleId(id);
    for (VehicleImage image : images) {
      Long attachmentId = image.getAttachment().getId();
      vehicleImageRepository.delete(image);
      attachmentRepository.deleteById(attachmentId);
    }

    vehicleRepository.deleteById(id);
  }

  @Override
  public List<BookedDateDTO> getBookedDates(Long vehicleId) {
    if (!vehicleRepository.existsById(vehicleId)) {
      throw new RuntimeException("Vehicle not found");
    }

    return reservationRepository
        .findActiveBookings(vehicleId, ReservationStatusEnum.CANCELLED, LocalDateTime.now())
        .stream()
        .map(r -> new BookedDateDTO(r.getPickUpDateTime(), r.getReturnDateTime()))
        .toList();
  }

  private VehicleResponseDTO toResponse(Vehicle v) {
    return new VehicleResponseDTO(v.getId(), v.getBrand().getId(), v.getBrand().getName(),
        v.getBrand().getImageUrl(),
        v.getModel(),
        v.getYearOfManufacture(),
        v.getLicensePlate(),
        v.getColor(), v.getType(), v.getTransmission(), v.getFuelType(), v.getSeats(), v.getDoors(), v.getLuggages(),
        v.getPricePerDay(),
        v.getMileAge(), v.getDescription(), v.getStatus(), v.getCreatedAt(), v.getUpdatedAt());
  }

}