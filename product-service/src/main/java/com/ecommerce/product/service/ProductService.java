package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;

import com.ecommerce.product.dto.CreateProductRequest;

import com.ecommerce.product.dto.ProductResponse;

import com.ecommerce.product.dto.UpdateProductRequest;

import com.ecommerce.product.entity.Product;

import com.ecommerce.product.mapper.ProductMapper;

import com.ecommerce.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;


import org.springframework.data.domain.Page;

import org.springframework.data.domain.PageRequest;

import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.List;

import java.util.UUID;
import com.ecommerce.product.kafka.ProductEventPublisher;

@Service
@RequiredArgsConstructor

public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductEventPublisher productEventPublisher;

    public ProductResponse createProduct(
            CreateProductRequest request
    ) {

        return createProduct(request, null);
    }

    public ProductResponse createProduct(CreateProductRequest request, UUID sellerId) {

            Product product = Product.builder()
                            .id(UUID.randomUUID())
                            .sellerId(sellerId)
                            .name(request.getName())
                            .description(request.getDescription())
                            .price(request.getPrice())
                            .category(request.getCategory())
                            .brand(request.getBrand())
                            .imageUrls(imageUrlsOrEmpty(request.getImageUrls()))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

            Product savedProduct = productRepository.save(product);
            productEventPublisher.publishProductCreated(savedProduct);
            return productMapper.toResponse(savedProduct);
    }

    public ProductResponse createSellerProduct(CreateProductRequest request, UUID sellerId) {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .sellerId(sellerId)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .brand(request.getBrand())
                .imageUrls(imageUrlsOrEmpty(request.getImageUrls()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Product saved = productRepository.save(product);
        productEventPublisher.publishProductCreated(saved);
        return productMapper.toResponse(saved);
    }
    
    public List<ProductResponse> createProducts(List<CreateProductRequest> requests, UUID sellerId) {

        List<Product> products = requests.stream()

                .map(request -> Product.builder()

                        .id(UUID.randomUUID())

                        .sellerId(sellerId)

                        .name(request.getName())

                        .description(request.getDescription())

                        .price(request.getPrice())

                        .category(request.getCategory())

                        .brand(request.getBrand())

                        .imageUrls(imageUrlsOrEmpty(request.getImageUrls()))

                        .createdAt(LocalDateTime.now())

                        .updatedAt(LocalDateTime.now())

                        .build())

                .toList();

        List<Product> savedProducts =
                productRepository.saveAll(products);

        savedProducts.forEach(productEventPublisher::publishProductCreated);

        return savedProducts.stream()

                .map(productMapper::toResponse)

                .toList();
        }
    public ProductResponse getProductById(UUID productId) {
        Product product =
                productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found"
                                )
                        );
        return productMapper.toResponse(product);
    }

    public Page<ProductResponse> getAllProducts(

            int page,

            int size,

            String category,

            BigDecimal minPrice,

            BigDecimal maxPrice
    ) {

        Pageable pageable =
                PageRequest.of(page, size);

        Page<Product> products;

        /*
        * Filtering combinations
        */
        if (category != null &&
                minPrice != null &&
                maxPrice != null) {

            products =
                    productRepository
                            .findByCategoryAndPriceBetween(
                                    category,
                                    minPrice,
                                    maxPrice,
                                    pageable
                            );

        } else if (category != null) {

            products =
                    productRepository.findByCategory(
                            category,
                            pageable
                    );

        } else if (minPrice != null &&
                maxPrice != null) {

            products =
                    productRepository.findByPriceBetween(
                            minPrice,
                            maxPrice,
                            pageable
                    );

        } else {

            products =
                    productRepository.findAll(pageable);
        }

        return products.map(productMapper::toResponse);
    }

    public ProductResponse updateProduct(
            UUID productId,
            UpdateProductRequest request
    ) {
        Product product =
                productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found"
                                )
                        );
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        product.setImageUrls(imageUrlsOrEmpty(request.getImageUrls()));
        product.setUpdatedAt(LocalDateTime.now());
        Product updatedProduct =
                productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    public void deleteProduct(UUID productId) {
        Product product =
                productRepository.findById(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Product not found"
                                )
                        );
        productRepository.delete(product);
    }

    public Page<ProductResponse> getSellerProducts(UUID sellerId, int page, int size) {
        return productRepository.findBySellerId(sellerId, PageRequest.of(page, size))
                .map(productMapper::toResponse);
    }

    public ProductResponse updateSellerProduct(UUID productId, UpdateProductRequest request, UUID sellerId,
            boolean admin) {
        Product product = getSellerOwnedProduct(productId, sellerId, admin);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        product.setImageUrls(imageUrlsOrEmpty(request.getImageUrls()));
        product.setUpdatedAt(LocalDateTime.now());
        return productMapper.toResponse(productRepository.save(product));
    }

    public void deleteSellerProduct(UUID productId, UUID sellerId, boolean admin) {
        productRepository.delete(getSellerOwnedProduct(productId, sellerId, admin));
    }

    private Product getSellerOwnedProduct(UUID productId, UUID sellerId, boolean admin) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!admin && !sellerId.equals(product.getSellerId())) {
            // Deliberately use not-found so seller IDs and product ownership are not enumerable.
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    private List<String> imageUrlsOrEmpty(List<String> imageUrls) {
        return imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
