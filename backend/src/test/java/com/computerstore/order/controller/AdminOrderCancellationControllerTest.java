package com.computerstore.order.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computerstore.order.dto.ConfirmBankTransferRefundRequest;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.shipping.service.ShipmentDispatchService;
import org.junit.jupiter.api.Test;

class AdminOrderCancellationControllerTest {

    @Test
    void confirmsTheManualRefundWithTheAuthenticatedAdmin() {
        ShipmentDispatchService shipments = mock(ShipmentDispatchService.class);
        AdminOrderCancellationController controller = new AdminOrderCancellationController(shipments);
        AuthenticatedUser admin = mock(AuthenticatedUser.class);
        OrderResponse response = mock(OrderResponse.class);
        when(admin.id()).thenReturn(8L);
        when(shipments.confirmBankTransferRefund(42L, "receipt-123", 8L)).thenReturn(response);

        OrderResponse result = controller.confirmBankTransferRefund(42L,
                new ConfirmBankTransferRefundRequest("receipt-123"), admin);

        assertSame(response, result);
        verify(shipments).confirmBankTransferRefund(42L, "receipt-123", 8L);
    }
}
