package com.computerstore.order.dto;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.List;
public record CreateOrderRequest(@NotEmpty List<@Valid Item> items){ public record Item(@NotNull Long productId,@NotNull @Min(1) Integer quantity){} }
