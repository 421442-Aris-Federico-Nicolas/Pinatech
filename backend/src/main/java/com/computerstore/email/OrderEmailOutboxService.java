package com.computerstore.email;

import com.computerstore.order.domain.CustomerOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class OrderEmailOutboxService {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE);

    private final EmailOutboxRepository entries;
    private final TransactionalEmailService email;
    private final EmailOutboxCompletionService completion;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final String sellerRecipient;

    public OrderEmailOutboxService(EmailOutboxRepository entries, TransactionalEmailService email,
                                   EmailOutboxCompletionService completion, Clock clock, ObjectMapper objectMapper,
                                   @Value("${app.email.seller-recipient:}") String sellerRecipient) {
        this.entries = entries;
        this.email = email;
        this.completion = completion;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.sellerRecipient = sellerRecipient == null ? "" : sellerRecipient.trim();
        String localPart = this.sellerRecipient.contains("@")
                ? this.sellerRecipient.substring(0, this.sellerRecipient.indexOf('@')) : "";
        if (!this.sellerRecipient.isEmpty() && (this.sellerRecipient.length() > 254 || localPart.length() > 64
                || localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")
                || !EMAIL.matcher(this.sellerRecipient).matches())) {
            throw new IllegalStateException("app.email.seller-recipient must be a valid single email address.");
        }
    }

    public void enqueue(CustomerOrder order, OrderEmailEventType event) { enqueue(order, event, null); }

    public void enqueue(CustomerOrder order, OrderEmailEventType event, String reason) {
        Instant now = Instant.now(clock);
        entries.save(new EmailOutboxEntry(order, event, reason, now));
        sellerEvent(event).ifPresent(sellerEvent -> entries.save(new EmailOutboxEntry(
                order, sellerEvent, sellerRecipient, serialize(SellerOrderSnapshot.from(order, now)), now)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Instruction> claim() {
        Instant now = Instant.now(clock);
        return entries.findNextDueForUpdate(now).map(entry -> {
            UUID leaseToken = entry.lease(now.plusSeconds(60));
            return new Instruction(entry.getId(), leaseToken, entry.getEventType(), entry.getOrder().getId(),
                    entry.getRecipient(), entry.getCustomerName(), entry.getRejectionReason(), entry.getSellerPayload());
        });
    }

    public void deliver(Instruction instruction) {
        try {
            if (isSellerEvent(instruction.eventType())) {
                if (instruction.sellerPayload() == null) {
                    throw new IllegalStateException("Seller order email snapshot is missing.");
                }
                email.sendSellerOrderEvent(instruction.id(), instruction.recipient(), instruction.eventType(),
                        deserialize(instruction.sellerPayload()));
            } else {
                if (instruction.sellerPayload() != null) {
                    throw new IllegalStateException("Customer order email cannot contain a seller snapshot.");
                }
                email.sendOrderEvent(instruction.id(), instruction.recipient(), instruction.customerName(),
                        instruction.eventType(), instruction.orderId(), instruction.rejectionReason());
            }
            completion.success(instruction.id(), instruction.leaseToken());
        } catch (RuntimeException exception) {
            completion.failure(instruction.id(), instruction.leaseToken(), exception);
        }
    }

    private Optional<OrderEmailEventType> sellerEvent(OrderEmailEventType event) {
        if (sellerRecipient.isEmpty()) return Optional.empty();
        return switch (event) {
            case ORDER_CREATED -> Optional.of(OrderEmailEventType.SELLER_ORDER_CREATED);
            case PAYMENT_APPROVED -> Optional.of(OrderEmailEventType.SELLER_PAYMENT_APPROVED);
            default -> Optional.empty();
        };
    }

    private boolean isSellerEvent(OrderEmailEventType event) {
        return event == OrderEmailEventType.SELLER_ORDER_CREATED
                || event == OrderEmailEventType.SELLER_PAYMENT_APPROVED;
    }

    private String serialize(SellerOrderSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Seller order email snapshot could not be encoded.", exception);
        }
    }

    private SellerOrderSnapshot deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, SellerOrderSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Seller order email snapshot could not be decoded.", exception);
        }
    }

    public record Instruction(UUID id, UUID leaseToken, OrderEmailEventType eventType, Long orderId, String recipient,
                                String customerName, String rejectionReason, String sellerPayload) {}
}
