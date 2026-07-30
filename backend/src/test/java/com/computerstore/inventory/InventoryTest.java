package com.computerstore.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.computerstore.catalog.domain.Product;
import com.computerstore.common.exception.InsufficientStockException;
import com.computerstore.inventory.domain.Inventory;
import org.junit.jupiter.api.Test;

class InventoryTest {

    @Test
    void reservesAndReleasesAvailableStock() {
        Inventory inventory = new Inventory((Product) null);
        inventory.adjust(8);

        inventory.reserve(3);
        assertEquals(5, inventory.getAvailableQuantity());
        assertEquals(3, inventory.getReservedQuantity());

        inventory.release(3);
        assertEquals(8, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
    }

    @Test
    void preventsReservationsBeyondAvailableStock() {
        Inventory inventory = new Inventory((Product) null);
        inventory.adjust(2);

        assertThrows(InsufficientStockException.class, () -> inventory.reserve(3));
    }

    @Test
    void consumesReservedStockWithoutReturningItToAvailableStock() {
        Inventory inventory = new Inventory((Product) null);
        inventory.adjust(8);
        inventory.reserve(3);

        inventory.consumeReserved(3);

        assertEquals(5, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
    }

    @Test
    void restoresPreviouslyConsumedStock() {
        Inventory inventory = new Inventory((Product) null);
        inventory.adjust(8);
        inventory.reserve(3);
        inventory.consumeReserved(3);

        inventory.restore(3);

        assertEquals(8, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
    }
}
