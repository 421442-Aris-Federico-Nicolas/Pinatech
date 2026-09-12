package com.computerstore.catalog.service;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductImage;
import com.computerstore.catalog.repository.ProductImageRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.storage.LocalImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {
    private static final String STORAGE_KEY = "3d45a4c2-a70c-4e87-99d3-bd26e2601e15";

    @Mock ProductRepository products;
    @Mock ProductImageRepository images;
    @Mock LocalImageStorage storage;

    private ProductImageService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductImageService(products, images, storage);
        product = org.mockito.Mockito.mock(Product.class);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void thumbnailAndDetailReturnWebpMetadata(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        ProductImage image = new ProductImage(product, "Notebook", 0, STORAGE_KEY,
                "image.png", "image/png", 100);
        ReflectionTestUtils.setField(image, "id", 5L);
        when(images.findByIdAndProductActiveTrue(5L)).thenReturn(Optional.of(image));
        var path = java.nio.file.Files.write(directory.resolve("thumbnail.webp"), new byte[]{1, 2, 3});
        when(storage.thumbnail(STORAGE_KEY)).thenReturn(path);
        when(storage.publicWebp(STORAGE_KEY)).thenReturn(new LocalImageStorage.StoredContent(path, 3));

        var thumbnail = service.thumbnail(5L);
        var detail = service.content(5L);

        assertEquals(path, thumbnail.path());
        assertEquals("image/webp", thumbnail.contentType());
        assertEquals("image-5.webp", thumbnail.fileName());
        assertEquals(3, thumbnail.sizeBytes());
        assertEquals("image/webp", detail.contentType());
        assertEquals("image.webp", detail.fileName());
        assertEquals("/api/products/images/5/content?v=webp-1", service.response(image).contentUrl());
        verify(storage, never()).load(any());
    }

    @Test
    void thumbnailRejectsInactiveMissingAndExternalImagesBeforeStorageAccess() {
        when(images.findByIdAndProductActiveTrue(5L)).thenReturn(Optional.empty());
        assertThrows(com.computerstore.common.exception.ResourceNotFoundException.class, () -> service.thumbnail(5L));
        var external = new ProductImage(product, "External", 0, null, null, null, 0);
        when(images.findByIdAndProductActiveTrue(6L)).thenReturn(Optional.of(external));
        assertThrows(com.computerstore.common.exception.ResourceNotFoundException.class, () -> service.thumbnail(6L));
        org.mockito.Mockito.verifyNoInteractions(storage);
    }

    @Test
    void rejectsUploadAtMaximumImageCountBeforeStoring() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(images.countByProductId(1L)).thenReturn(6L);

        assertThrows(BusinessRuleException.class,
                () -> service.upload(1L, new MockMultipartFile("file", new byte[]{1}), null));

        verify(storage, never()).storeWebp(any());
    }

    @Test
    void uploadDeletesStoredFileWhenTransactionRollsBack() {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(images.countByProductId(1L)).thenReturn(0L);
        when(images.findFirstByProductIdOrderByDisplayOrderDesc(1L)).thenReturn(Optional.empty());
        when(product.getName()).thenReturn("Notebook");
        when(storage.storeWebp(file)).thenReturn(new LocalImageStorage.StoredImage(
                STORAGE_KEY, "image.webp", "image/webp", 100, 800, 600));
        when(images.saveAndFlush(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });
        TransactionSynchronizationManager.initSynchronization();

        var response = service.upload(1L, file, null);
        assertEquals("image.webp", response.originalFilename());
        verify(storage, never()).delete(STORAGE_KEY);

        synchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete(STORAGE_KEY);
    }

    @Test
    void deleteRemovesFileOnlyAfterCommit() {
        ProductImage image = new ProductImage(product, "Notebook", 0, STORAGE_KEY,
                "image.png", "image/png", 100);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(images.findByIdAndProductId(5L, 1L)).thenReturn(Optional.of(image));
        TransactionSynchronizationManager.initSynchronization();

        service.delete(1L, 5L);

        verify(images).delete(image);
        verify(images).flush();
        verify(storage, never()).delete(STORAGE_KEY);

        synchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete(STORAGE_KEY);
    }

    private List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}
