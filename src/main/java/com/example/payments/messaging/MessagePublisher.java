package com.example.payments.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica a RabbitMQ solo despues de que la transaccion de BD se confirma, para no anunciar un pago
 * que luego se revierte. Es "dispara y sigue": si el broker no responde se registra el error y el
 * pago ya confirmado no se deshace. Con publisher confirms, un rechazo (nack) o un mensaje sin cola
 * destino (returned) tambien queda en el log.
 */
@Slf4j
@Component
public class MessagePublisher {

    private final RabbitTemplate rabbit;

    public MessagePublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
        rabbit.setMandatory(true);
        rabbit.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ rechazo el mensaje {}: {}", correlation == null ? "?" : correlation.getId(), cause);
            }
        });
        rabbit.setReturnsCallback(returned -> log.error(
                "Mensaje sin cola destino (reports-service aun no declaro su cola?): exchange={} key={} motivo={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(OutboundMessage message) {
        try {
            rabbit.convertAndSend(message.exchange(), message.routingKey(), message.payload(),
                    new CorrelationData(message.routingKey()));
            log.info("Publicado {} en {}", message.routingKey(), message.exchange());
        } catch (RuntimeException e) {
            log.error("No se pudo publicar {} en {}: {}", message.routingKey(), message.exchange(), e.getMessage());
        }
    }
}
