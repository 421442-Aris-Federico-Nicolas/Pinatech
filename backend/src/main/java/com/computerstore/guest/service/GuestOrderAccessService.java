package com.computerstore.guest.service;

import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.guest.domain.GuestCheckoutSession;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.repository.CustomerOrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestOrderAccessService {
    private final CustomerOrderRepository orders;
    private final GuestRequestSecurity security;

    public GuestOrderAccessService(CustomerOrderRepository orders, GuestRequestSecurity security) {
        this.orders = orders;
        this.security = security;
    }

    @Transactional(readOnly = true)
    public CustomerOrder require(UUID publicId, HttpServletRequest request, boolean mutation) {
        if (mutation) security.validateOrigin(request);
        CustomerOrder order = orders.findByPublicId(publicId).orElseThrow(this::notFound);
        authorize(order, request, mutation);
        return order;
    }

    public void authorize(CustomerOrder order, HttpServletRequest request, boolean mutation) {
        GuestCheckoutSession session = security.optionalSession(request, mutation);
        boolean sessionOwner = session != null && order.getGuestSession() != null
                && order.getGuestSession().getId().equals(session.getId());
        boolean tokenOwner = GuestRequestSecurity.constantEquals(order.getGuestAccessTokenHash(),
                security.orderAccessHash(request));
        if (!order.isGuest() || (!sessionOwner && !tokenOwner)) throw notFound();
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Guest order not found.");
    }
}
