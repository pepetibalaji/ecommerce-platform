package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

public interface ProductRepositoryCustom {
    Page<Product> searchPublicProducts(String q, String category, String brand,
                                       BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
