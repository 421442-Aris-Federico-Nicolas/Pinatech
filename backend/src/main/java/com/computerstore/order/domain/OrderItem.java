package com.computerstore.order.domain;
import java.math.BigDecimal;
import com.computerstore.catalog.domain.Product;
import jakarta.persistence.*;
@Entity @Table(name="order_items") public class OrderItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",nullable=false) private CustomerOrder order; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="product_id",nullable=false) private Product product; @Column(name="product_name",nullable=false) private String productName; @Column(name="unit_price",nullable=false,precision=19,scale=2) private BigDecimal unitPrice; @Column(nullable=false) private int quantity; @Column(nullable=false,precision=19,scale=2) private BigDecimal subtotal;
 protected OrderItem(){} public OrderItem(Product product,int quantity){this.product=product;this.productName=product.getName();this.unitPrice=product.getPrice();this.quantity=quantity;this.subtotal=unitPrice.multiply(BigDecimal.valueOf(quantity));} void setOrder(CustomerOrder order){this.order=order;} public Product getProduct(){return product;} public String getProductName(){return productName;} public BigDecimal getUnitPrice(){return unitPrice;} public int getQuantity(){return quantity;} public BigDecimal getSubtotal(){return subtotal;}
}
