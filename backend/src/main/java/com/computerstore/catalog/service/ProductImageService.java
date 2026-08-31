package com.computerstore.catalog.service;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductImage;
import com.computerstore.catalog.dto.ProductImageResponse;
import com.computerstore.catalog.repository.ProductImageRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.storage.LocalImageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

@Service
public class ProductImageService {
    private static final int MAX_IMAGES = 6;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final LocalImageStorage storage;

    public ProductImageService(ProductRepository products, ProductImageRepository images, LocalImageStorage storage) {
        this.products = products;
        this.images = images;
        this.storage = storage;
    }

    @Transactional
    public ProductImageResponse upload(Long productId, MultipartFile file, String altText) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        if (images.countByProductId(productId) >= MAX_IMAGES) {
            throw new BusinessRuleException("A product cannot have more than 6 images.");
        }
        int nextOrder = images.findFirstByProductIdOrderByDisplayOrderDesc(productId)
                .map(image -> image.getDisplayOrder() + 1)
                .orElse(0);
        String normalizedAlt = altText == null || altText.isBlank() ? product.getName() : altText.trim();
        if (normalizedAlt.length() > 250) {
            throw new InvalidRequestException("Alternative text must not exceed 250 characters.");
        }

        LocalImageStorage.StoredImage stored = storage.store(file);
        cleanupOnRollback(stored.storageKey());
        try {
            ProductImage image = images.saveAndFlush(new ProductImage(product, normalizedAlt, nextOrder,
                    stored.storageKey(), stored.originalFilename(), stored.contentType(), stored.sizeBytes()));
            image.setContentUrl(contentUrl(image.getId()));
            images.flush();
            return response(image);
        } catch (RuntimeException exception) {
            try {
                storage.delete(stored.storageKey());
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    @Transactional
    public void delete(Long productId, Long imageId) {
        products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        ProductImage image = images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found."));
        images.delete(image);
        images.flush();
        if (image.getStorageKey() != null) {
            deleteAfterCommit(image.getStorageKey());
        }
    }

    @Transactional(readOnly = true)
    public ProductImageContent content(Long imageId) {
        ProductImage image = images.findByIdAndProductActiveTrue(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found."));
        if (image.getStorageKey() == null || image.getContentType() == null
                || image.getOriginalFilename() == null || image.getSizeBytes() == null) {
            throw new ResourceNotFoundException("Image content not found.");
        }
        return new ProductImageContent(storage.load(image.getStorageKey()), image.getContentType(),
                image.getOriginalFilename(), image.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> responsesForProduct(Long productId) {
        return images.findByProductIdOrderByDisplayOrderAsc(productId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ProductImageResponse>> responsesByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return images.findByProductIdInOrderByProductIdAscDisplayOrderAsc(productIds).stream()
                .collect(Collectors.groupingBy(image -> image.getProduct().getId(),
                        Collectors.mapping(this::response, Collectors.toList())));
    }

    public ProductImageResponse response(ProductImage image) {
        String url = image.getStorageKey() == null ? image.getImageUrl() : contentUrl(image.getId());
        return new ProductImageResponse(image.getId(), url, image.getAltText(), image.getOriginalFilename(), image.getDisplayOrder());
    }

    private String contentUrl(Long imageId) {
        return "/api/products/images/" + imageId + "/content";
    }

    private void cleanupOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(storageKey);
                    } catch (RuntimeException ignored) {
                        // Database consistency takes priority; cleanup is best-effort.
                    }
                }
            }
        });
    }

    private void deleteAfterCommit(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(storageKey);
                } catch (RuntimeException ignored) {
                    // The database commit takes priority; filesystem cleanup is best-effort.
                }
            }
        });
    }

    public record ProductImageContent(Path path, String contentType, String fileName, long sizeBytes) {}
}
