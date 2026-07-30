package com.computerstore.service.repository;

import com.computerstore.service.domain.TechnicalServiceTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TechnicalServiceTicketRepository extends JpaRepository<TechnicalServiceTicket, Long> {
    List<TechnicalServiceTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    @EntityGraph(attributePaths = {"customer", "technician"})
    List<TechnicalServiceTicket> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TechnicalServiceTicket t where t.id = :id")
    Optional<TechnicalServiceTicket> findByIdForUpdate(Long id);
}
