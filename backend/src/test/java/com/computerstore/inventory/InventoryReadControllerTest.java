package com.computerstore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.computerstore.inventory.controller.InventoryController;
import com.computerstore.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

class InventoryReadControllerTest {
    private final InventoryRepository repository = mock(InventoryRepository.class);
    private final InventoryController controller = new InventoryController(repository, null, null, null);

    @Test
    void trimsSearchAndPreservesPageMetadata() {
        when(repository.findActivePage("keyboard", PageRequest.of(2, 20)))
                .thenReturn(Page.empty(PageRequest.of(2, 20)));
        var page = controller.page(" keyboard ", 2, 20);
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void rejectsInvalidPaginationWithoutQuerying() {
        assertThatThrownBy(() -> controller.page("", -1, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.page("", 0, 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.page("", 0, 101)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
