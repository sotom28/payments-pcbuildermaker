package com.example.payments.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Payments solo publica: declara el exchange (idempotente, mismos argumentos que reports-service) para que
 * exista aunque reports todavia no haya arrancado. Las colas y bindings los declara quien consume.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "pcmarketbuilder.direct";
    public static final String KEY_ORDER_PAID = "order.paid";

    @Bean
    DirectExchange directExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /** JSON entre servicios. */
    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
