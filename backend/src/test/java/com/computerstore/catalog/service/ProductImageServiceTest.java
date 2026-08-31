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
    void rejectsUploadAtMaximumImageCountBeforeStoring() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(images.countByProductId(1L)).thenReturn(6L);

        assertThrows(BusinessRuleException.class,
                () -> service.upload(1L, new MockMultipartFile("file", new byte[]{1}), null));

        verify(storage, never()).store(any());
    }

    @Test
    void uploadDeletesStoredFileWhenTransactionRollsBack() {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(images.countByProductId(1L)).thenReturn(0L);
        when(images.findFirstByProductIdOrderByDisplayOrderDesc(1L)).thenReturn(Optional.empty());
        when(product.getName()).thenReturn("Notebook");
        when(storage.store(file)).thenReturn(new LocalImageStorage.StoredImage(
                STORAGE_KEY, "image.png", "image/png", 100));
        when(images.saveAndFlush(any(ProductImage.class))).thenAnswer(invocation -> {
            ProductImage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });
        TransactionSynchronizationManager.initSynchronization();

        var response = service.upload(1L, file, null);
        assertEquals("image.png", response.originalFilename());
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
