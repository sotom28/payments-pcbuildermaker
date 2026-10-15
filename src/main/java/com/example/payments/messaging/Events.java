package com.example.payments.messaging;

import java.time.Instant;
import java.util.List;

/** Payloads JSON que viajan por RabbitMQ. Deben calzar con los records de reports-service (mismos nombres de campo). */
public final class Events {

    private Events() {
    }

    /** Pago confirmado: reports crea una inspeccion por item y la deja en la bandeja del admin. */
    public record OrderPaid(
            String orderId,
            String buyerId,
            String buyerEmail,
            Instant timestamp,
            List<PaidItem> items) {
    }

    public record PaidItem(
            String orderItemId,
            String publicationId,
            String sellerId,
            String description,
            Integer price) {
    }
}
