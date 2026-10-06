package com.example.spring_boot_project_api.mapper;

import org.springframework.stereotype.Component;

import com.example.spring_boot_project_api.dto.response.brand.BrandResponseDTO;
import com.example.spring_boot_project_api.model.Brand;

/**
 * brand_mapper
 */
@Component
public class BrandMapper {

    public BrandResponseDTO toResponseDTO(Brand brand) {
        return new BrandResponseDTO(brand.getId(), brand.getName(), brand.getImageUrl());
    }
}