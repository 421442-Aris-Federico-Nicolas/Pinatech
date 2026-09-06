package com.computerstore.order.domain;

public enum OrderCancellationReason {
    CUSTOMER_REQUEST("Solicitud del cliente"),
    INVALID_DELIVERY_DATA("Datos de entrega incorrectos"),
    PRODUCT_UNAVAILABLE("Producto sin disponibilidad"),
    LOGISTICS_PROBLEM("Problema logistico"),
    DUPLICATE_OR_ERROR("Pedido duplicado o generado por error"),
    OTHER("Otro motivo");

    private final String customerLabel;

    OrderCancellationReason(String customerLabel) {
        this.customerLabel = customerLabel;
    }

    public String customerLabel() {
        return customerLabel;
    }
}
