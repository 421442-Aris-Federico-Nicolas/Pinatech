package com.computerstore.catalog.repository;
import com.computerstore.catalog.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BrandRepository extends JpaRepository<Brand, Long> {}
