package com.computerstore.order.controller;

import java.time.Instant;
import java.util.List;

import com.computerstore.common.exception.ReservationExpiredException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.dto.OrderResponseMapper;
import com.computerstore.order.dto.OrderStatusRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final CustomerOrderRepository orders;
    private final OrderStockService stock;

    public AdminOrderController(CustomerOrderRepository orders, OrderStockService stock) {
        this.orders = orders;
        this.stock = stock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponseMapper::toResponse)
                .toList();
    }

    @PatchMapping("/{id}/status")
    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        var order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (order.isReservationExpired(Instant.now())) {
            stock.release(order);
            order.expire();
            if (request.status() != OrderStatus.CANCELLED) {
                throw new ReservationExpiredException("The order reservation has expired.");
            }
            return OrderResponseMapper.toResponse(order);
        }

        OrderStatus previous = order.getStatus();
        order.transitionTo(request.status());
        if (request.status() == OrderStatus.CANCELLED && previous != OrderStatus.CANCELLED) {
            if (previous == OrderStatus.PENDING_PAYMENT || previous == OrderStatus.PAID) {
                stock.release(order);
            } else if (previous == OrderStatus.PREPARING || previous == OrderStatus.READY) {
                stock.restore(order);
            }
        } else if (request.status() == OrderStatus.PREPARING && previous != OrderStatus.PREPARING) {
            stock.consume(order);
        }
        return OrderResponseMapper.toResponse(order);
    }
}
