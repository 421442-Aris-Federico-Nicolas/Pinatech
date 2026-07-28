package com.computerstore.catalog.repository;
import com.computerstore.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, Long> {}
