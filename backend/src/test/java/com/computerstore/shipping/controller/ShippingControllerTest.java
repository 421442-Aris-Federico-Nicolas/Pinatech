package com.computerstore.shipping.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.computerstore.order.domain.OrderCancellationReason;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.payment.service.PaymentRefundReconciliationService;
import com.computerstore.payment.service.RefundInstruction;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.shipping.domain.ShipmentCancellationScope;
import com.computerstore.shipping.dto.CancelShipmentRequest;
import com.computerstore.shipping.gateway.ZipnovaGateway;
import com.computerstore.shipping.repository.OrderShipmentRepository;
import com.computerstore.shipping.repository.ShipmentEventRepository;
import com.computerstore.shipping.service.ShipmentDispatchService;
import org.junit.jupiter.api.Test;

class ShippingControllerTest {

    @Test
    void cancellationCallsZipnovaThenStartsThePersistedRefund() {
        ShipmentDispatchService dispatch = mock(ShipmentDispatchService.class);
        ZipnovaGateway gateway = mock(ZipnovaGateway.class);
        PaymentRefundReconciliationService refunds = mock(PaymentRefundReconciliationService.class);
        ShippingController controller = new ShippingController(mock(OrderShipmentRepository.class),
                mock(ShipmentEventRepository.class), mock(CustomerOrderRepository.class), dispatch, gateway, refunds);
        AuthenticatedUser admin = mock(AuthenticatedUser.class);
        when(admin.id()).thenReturn(7L);
        var request = new CancelShipmentRequest(ShipmentCancellationScope.ORDER,
                OrderCancellationReason.CUSTOMER_REQUEST, "Requested by support");
        var cancellation = new ShipmentDispatchService.CancellationInstruction(42L, 99L, true, true);
        var refund = new RefundInstruction(UUID.randomUUID(), "payment-123", UUID.randomUUID(), null, null);
        OrderResponse response = mock(OrderResponse.class);
        when(dispatch.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.CUSTOMER_REQUEST, "Requested by support", 7L)).thenReturn(cancellation);
        when(dispatch.cancellationSucceeded(42L, 99L)).thenReturn(Optional.of(refund));
        when(dispatch.orderResponse(42L)).thenReturn(response);

        OrderResponse result = controller.cancel(42L, request, admin);

        assertSame(response, result);
        verify(gateway).cancel(99L);
        verify(refunds).executeRefund(refund);
    }

    @Test
    void alreadyCancelledProviderShipmentSkipsTheSecondZipnovaCall() {
        ShipmentDispatchService dispatch = mock(ShipmentDispatchService.class);
        ZipnovaGateway gateway = mock(ZipnovaGateway.class);
        PaymentRefundReconciliationService refunds = mock(PaymentRefundReconciliationService.class);
        ShippingController controller = new ShippingController(mock(OrderShipmentRepository.class),
                mock(ShipmentEventRepository.class), mock(CustomerOrderRepository.class), dispatch, gateway, refunds);
        AuthenticatedUser admin = mock(AuthenticatedUser.class);
        when(admin.id()).thenReturn(7L);
        var request = new CancelShipmentRequest(ShipmentCancellationScope.ORDER,
                OrderCancellationReason.LOGISTICS_PROBLEM, null);
        when(dispatch.requestCancellation(42L, ShipmentCancellationScope.ORDER,
                OrderCancellationReason.LOGISTICS_PROBLEM, null, 7L))
                .thenReturn(new ShipmentDispatchService.CancellationInstruction(42L, 99L, false, true));
        when(dispatch.cancellationSucceeded(42L, 99L)).thenReturn(Optional.empty());
        when(dispatch.orderResponse(42L)).thenReturn(mock(OrderResponse.class));

        controller.cancel(42L, request, admin);

        verify(gateway, org.mockito.Mockito.never()).cancel(99L);
        verify(refunds, org.mockito.Mockito.never()).executeRefund(org.mockito.ArgumentMatchers.any());
    }
}
