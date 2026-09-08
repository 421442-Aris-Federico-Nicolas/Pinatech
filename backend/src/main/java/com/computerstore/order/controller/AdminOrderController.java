package com.computerstore.order.controller;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.computerstore.order.dto.OrderSummaryResponse;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.common.exception.ReservationExpiredException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.domain.PaymentStatus;
import com.computerstore.order.domain.FulfillmentMethod;
import com.computerstore.order.dto.OrderResponse;
import com.computerstore.order.dto.OrderResponseMapper;
import com.computerstore.order.dto.OrderStatusRequest;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.email.OrderEmailEventType;
import com.computerstore.email.OrderEmailOutboxService;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final CustomerOrderRepository orders;
    private final OrderStockService stock;
    private final OrderEmailOutboxService outbox;

    @Autowired
    public AdminOrderController(CustomerOrderRepository orders, OrderStockService stock,
                                OrderEmailOutboxService outbox) {
        this.orders = orders;
        this.stock = stock;
        this.outbox = outbox;
    }

    public AdminOrderController(CustomerOrderRepository orders, OrderStockService stock) {
        this(orders, stock, null);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public OrderResponse detail(@PathVariable Long id) {
        return orders.findDetailsById(id).map(OrderResponseMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }

    @GetMapping("/page")
    @Transactional(readOnly = true)
    public Page<OrderResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size (1-100).");
        }
        var ids = orders.findPageIds(status, PageRequest.of(page, size));
        var content = ids.isEmpty() ? List.<OrderResponse>of() : orders.findDetailsByIds(ids.getContent())
                .stream().map(OrderResponseMapper::toResponse).toList();
        return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public OrderSummaryResponse summary() {
        var counts = new LinkedHashMap<String, Long>();
        for (var status : OrderStatus.values()) counts.put(status.name(), 0L);
        long sold = 0;
        long active = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (var row : orders.summarizeStatuses()) {
            counts.merge(row.getStatus().name(), row.getQuantity(), Long::sum);
            if (row.getStatus() != OrderStatus.DELIVERED && row.getStatus() != OrderStatus.CANCELLED) {
                active += row.getQuantity();
            }
            if (row.getPaymentStatus() == PaymentStatus.APPROVED) {
                sold += row.getQuantity();
                revenue = revenue.add(row.getTotal());
            }
        }
        var zone = ZoneId.of("America/Argentina/Buenos_Aires");
        var today = LocalDate.now(zone);
        var totals = new LinkedHashMap<LocalDate, BigDecimal>();
        for (int i = 6; i >= 0; i--) totals.put(today.minusDays(i), BigDecimal.ZERO);
        for (var row : orders.summarizeDays(today.minusDays(6).atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant())) {
            totals.put(row.getDay(), row.getTotal());
        }
        double maximum = totals.values().stream().mapToDouble(BigDecimal::doubleValue).max().orElse(1);
        maximum = Math.max(maximum, 1);
        var chart = new ArrayList<OrderSummaryResponse.SalesDay>();
        var labels = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("es-AR"));
        for (var entry : totals.entrySet()) {
            double total = entry.getValue().doubleValue();
            chart.add(new OrderSummaryResponse.SalesDay(labels.format(entry.getKey()).replace(".", ""),
                    entry.getValue(), total == 0 ? 4 : Math.max(12, total / maximum * 100)));
        }
        return new OrderSummaryResponse(sold, revenue,
                sold == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(sold), MathContext.DECIMAL128),
                active, counts, chart, page(0, 5, null).getContent());
    }

    @PatchMapping("/{id}/status")
    @Transactional(noRollbackFor = ReservationExpiredException.class)
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        if (request.status() == OrderStatus.PAID) {
            throw new InvalidStateTransitionException("Payment approval can only be reported by the payment provider.");
        }
        var order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        if (order.getFulfillmentMethod() == FulfillmentMethod.DELIVERY
                && (request.status() == OrderStatus.SHIPPED || request.status() == OrderStatus.DELIVERED)) {
            throw new InvalidStateTransitionException("Zipnova is authoritative for delivery shipment states.");
        }
        if (request.status() == OrderStatus.CANCELLED && order.getPaymentStatus() == PaymentStatus.APPROVED) {
            throw new InvalidStateTransitionException(
                    "A paid order cannot be cancelled until its payment is refunded.");
        }
        if (request.status() == OrderStatus.CANCELLED
                && order.getPaymentStatus() == PaymentStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException(
                    "A transfer under review must be rejected with a reason.");
        }
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
        if (request.status() == OrderStatus.DELIVERED && previous != OrderStatus.DELIVERED && outbox != null) {
            outbox.enqueue(order, OrderEmailEventType.ORDER_DELIVERED);
        }
        return OrderResponseMapper.toResponse(order);
    }
}
