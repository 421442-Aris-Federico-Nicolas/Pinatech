package com.computerstore.service.repository;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository; import com.computerstore.service.domain.TechnicalServiceTicket;
public interface TechnicalServiceTicketRepository extends JpaRepository<TechnicalServiceTicket,Long>{List<TechnicalServiceTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);List<TechnicalServiceTicket> findAllByOrderByCreatedAtDesc();}
