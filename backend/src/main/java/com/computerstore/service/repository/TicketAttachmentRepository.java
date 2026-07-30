package com.computerstore.service.repository;

import com.computerstore.service.domain.TicketAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    long countByTicketId(Long ticketId);
    @Query("""
            select coalesce(sum(attachment.sizeBytes), 0)
            from TicketAttachment attachment
            where attachment.uploadedBy.id = :userId
              and attachment.uploaderRole = com.computerstore.service.domain.UploaderRole.CUSTOMER
            """)
    long sumCustomerSizeBytes(@Param("userId") Long userId);
    @EntityGraph(attributePaths = "uploadedBy")
    List<TicketAttachment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
    @EntityGraph(attributePaths = {"ticket", "uploadedBy"})
    List<TicketAttachment> findByTicketIdInOrderByTicketIdAscCreatedAtAscIdAsc(Collection<Long> ticketIds);
}
