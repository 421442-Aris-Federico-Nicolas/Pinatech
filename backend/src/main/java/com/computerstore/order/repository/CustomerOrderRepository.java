package com.computerstore.order.repository;
import com.computerstore.order.domain.CustomerOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder,Long>{ List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(Long userId); }
