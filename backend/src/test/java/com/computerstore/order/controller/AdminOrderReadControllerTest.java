package com.computerstore.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.web.server.ResponseStatusException;

class AdminOrderReadControllerTest {
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final AdminOrderController controller = new AdminOrderController(orders, mock(OrderStockService.class));

    @Test
    void rejectsInvalidPageSizesBeforeQuerying() {
        assertThatThrownBy(() -> controller.page(-1, 20, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.page(0, 0, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.page(0, 101, null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(orders);
    }

    @Test
    void emptySummaryHasSevenBaselineBarsAndNoDetailQuery() {
        when(orders.findPageIds(isNull(), any())).thenReturn(Page.empty());
        var summary = controller.summary();
        assertThat(summary.soldOrders()).isZero();
        assertThat(summary.revenue()).isZero();
        assertThat(summary.averageTicket()).isZero();
        assertThat(summary.activeOrders()).isZero();
        assertThat(summary.statusCounts().values()).allMatch(count -> count == 0);
        assertThat(summary.salesChart()).hasSize(7).allSatisfy(day -> {
            assertThat(day.total()).isZero();
            assertThat(day.height()).isEqualTo(4);
        });
        assertThat(summary.recentOrders()).isEmpty();
        verify(orders, never()).findDetailsByIds(anyList());
        verify(orders, never()).findAllByOrderByCreatedAtDesc();
    }
}
