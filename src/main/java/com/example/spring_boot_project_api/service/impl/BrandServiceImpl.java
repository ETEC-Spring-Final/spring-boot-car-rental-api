package com.example.spring_boot_project_api.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.spring_boot_project_api.dto.request.brand.BrandRequestDTO;
import com.example.spring_boot_project_api.dto.response.brand.BrandResponseDTO;
import com.example.spring_boot_project_api.dto.response.cloudinary.CloudinaryUploadResponseDTO;
import com.example.spring_boot_project_api.mapper.BrandMapper;
import com.example.spring_boot_project_api.model.Brand;
import com.example.spring_boot_project_api.repository.BrandRepository;
import com.example.spring_boot_project_api.service.BrandService;
import com.example.spring_boot_project_api.service.CloudinaryUploadService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {
  private final BrandRepository brandRepository;
  private final BrandMapper brandMapper;
  private final CloudinaryUploadService cloudinaryUploadService;

  @Override
  public BrandResponseDTO createBrand(BrandRequestDTO dto) {
    if (brandRepository.existsByName(dto.getName())) {
      throw new RuntimeException("Brand name already existed");
    }

    Brand brand = new Brand();
    brand.setName(dto.getName());

    Brand saved = brandRepository.save(brand);
    return toResponse(saved);
  }

  @Override
  public BrandResponseDTO getBrandById(Long id) {
    Brand brand = brandRepository.findById(id).orElseThrow(() -> new RuntimeException("Brand not found"));

    return toResponse(brand);
  }

  @Override
  public Page<BrandResponseDTO> getAllBrands(Pageable pageable) {
    // return
    // brandRepository.findAll(pageable).stream().map(this::toResponse).toList();
    return brandRepository.findAll(pageable).map(brandMapper::toResponseDTO);
  }

  @Override
  public BrandResponseDTO updateBrand(Long id, BrandRequestDTO dto) {
    Brand brand = brandRepository.findById(id).orElseThrow(() -> new RuntimeException("Brand not found"));

    brandRepository.findByName(dto.getName()).ifPresent(existing -> {
      if (!existing.getId().equals(id)) {
        throw new RuntimeException("Brand name already exists");
      }
    });

    brand.setName(dto.getName());

    Brand saved = brandRepository.save(brand);
    return toResponse(saved);
  }

  @Override
  public BrandResponseDTO uploadBrandImage(Long id, MultipartFile image) {
    Brand brand = brandRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Brand not found"));

    CloudinaryUploadResponseDTO uploaded = cloudinaryUploadService.upload(image, "brand-images");
    brand.setImageUrl(uploaded.url());
    return toResponse(brandRepository.save(brand));
  }

  @Override
  public void deleteBrand(Long id) {
    if (!brandRepository.existsById(id)) {
      throw new RuntimeException("Brand not found");
    }
    brandRepository.deleteById(id);
  }

  private BrandResponseDTO toResponse(Brand b) {
    return new BrandResponseDTO(b.getId(), b.getName(), b.getImageUrl());
  }

}
