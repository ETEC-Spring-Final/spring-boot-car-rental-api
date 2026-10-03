package com.example.spring_boot_project_api.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.example.spring_boot_project_api.dto.request.brand.BrandRequestDTO;
import com.example.spring_boot_project_api.dto.response.brand.BrandResponseDTO;

public interface BrandService {
  BrandResponseDTO createBrand(BrandRequestDTO dto);

  Page<BrandResponseDTO> getAllBrands(Pageable pageable);

  BrandResponseDTO getBrandById(Long id);

  BrandResponseDTO updateBrand(Long id, BrandRequestDTO dto);

  BrandResponseDTO uploadBrandImage(Long id, MultipartFile image);

  void deleteBrand(Long id);
}
