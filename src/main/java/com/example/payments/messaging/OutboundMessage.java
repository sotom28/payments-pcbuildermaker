package com.example.payments.messaging;

/** Mensaje a publicar tras el commit de la transaccion (ver MessagePublisher). */
public record OutboundMessage(String exchange, String routingKey, Object payload) {
}
