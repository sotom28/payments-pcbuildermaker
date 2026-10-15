package com.example.payments.service;

import com.example.payments.dto.CardPaymentRequest;
import com.example.payments.dto.CheckoutRequest;
import com.example.payments.dto.CreateOrderRequest;
import com.example.payments.exception.InvalidStateException;
import com.example.payments.exception.NotFoundException;
import com.example.payments.messaging.Events.OrderPaid;
import com.example.payments.messaging.Events.PaidItem;
import com.example.payments.messaging.OutboundMessage;
import com.example.payments.messaging.RabbitConfig;
import com.example.payments.model.Order;
import com.example.payments.model.OrderItem;
import com.example.payments.model.OrderType;
import com.example.payments.model.PaymentStatus;
import com.example.payments.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class PaymentService {

    /** Estados en los que una publicacion ya esta vendida y no puede entrar a otra compra. */
    private static final Set<PaymentStatus> SOLD_STATES =
            EnumSet.of(PaymentStatus.HOLD_ESCROW, PaymentStatus.RELEASED_TO_SELLER);

    private final OrderRepository repository;
    private final ApplicationEventPublisher events;
    private final int feePercent;

    public PaymentService(OrderRepository repository,
                          ApplicationEventPublisher events,
                          @Value("${payments.platform-fee-percent:5}") int feePercent) {
        this.repository = repository;
        this.events = events;
        this.feePercent = feePercent;
    }

    /**
     * Compra en un solo paso (pago simulado): crea la orden, la deja como pagada (HOLD_ESCROW) y avisa a
     * reports-service con order.paid para que cada pieza aparezca en la bandeja del admin del taller.
     */
    public Order checkout(String buyerId, CheckoutRequest req) {
        List<CheckoutRequest.Item> items = req.items();
        if (items.stream().map(CheckoutRequest.Item::publicationId).distinct().count() != items.size()) {
            throw new InvalidStateException("La orden repite una publicacion");
        }
        for (CheckoutRequest.Item i : items) {
            if (i.sellerId().equals(buyerId)) {
                throw new InvalidStateException("No puedes comprar tu propia publicacion: " + i.publicationId());
            }
            if (repository.existsItemByPublicationIdAndPaymentStatusIn(i.publicationId(), SOLD_STATES)) {
                throw new InvalidStateException("La publicacion " + i.publicationId() + " ya fue comprada");
            }
        }
        Order order = create(new CreateOrderRequest(buyerId, req.buyerEmail(), req.orderType(),
                items.stream().map(i -> new CreateOrderRequest.Item(i.publicationId(), i.sellerId(), i.title(), i.price()))
                        .toList()));
        order.setPaymentStatus(PaymentStatus.HOLD_ESCROW);
        order.setPaymentMethod("SIMULATED");
        publishPaid(order);
        return order;
    }

    public Order create(CreateOrderRequest req) {
        Order order = new Order();
        order.setBuyerId(req.buyerId());
        order.setBuyerEmail(req.buyerEmail());
        order.setOrderType(req.orderType() != null ? req.orderType() : OrderType.SINGLE_PART);
        int total = 0;
        for (CreateOrderRequest.Item i : req.items()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setPublicationId(i.publicationId());
            item.setSellerId(i.sellerId());
            item.setDescription(i.title());
            item.setPrice(i.price());
            int fee = i.price() * feePercent / 100;
            item.setPlatformFee(fee);
            item.setSellerPayoutAmount(i.price() - fee);
            order.getItems().add(item);
            total += i.price();
        }
        order.setTotalAmount(total);
        return repository.save(order);
    }

    @Transactional(readOnly = true)
    public Order get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Orden no encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<Order> list(String buyerId) {
        return buyerId == null ? repository.findAll() : repository.findByBuyerId(buyerId);
    }

    /** Cobra con tarjeta (simulado) y retiene el dinero en escrow. Solo se guardan marca y ultimos 4 digitos. */
    public Order pay(String id, CardPaymentRequest card) {
        Order order = get(id);
        if (!luhnValid(card.cardNumber())) {
            throw new InvalidStateException("Numero de tarjeta invalido");
        }
        YearMonth exp = YearMonth.of(card.expYear(), card.expMonth());
        if (exp.isBefore(YearMonth.now())) {
            throw new InvalidStateException("Tarjeta vencida");
        }
        order = transition(id, PaymentStatus.PENDING, PaymentStatus.HOLD_ESCROW, null);
        order.setPaymentMethod("CARD");
        order.setCardBrand(brand(card.cardNumber()));
        order.setCardLast4(card.cardNumber().substring(card.cardNumber().length() - 4));
        publishPaid(order);
        return order;
    }

    /** Anuncia el pago confirmado; se publica a RabbitMQ solo si la transaccion hace commit. */
    private void publishPaid(Order order) {
        events.publishEvent(new OutboundMessage(RabbitConfig.EXCHANGE, RabbitConfig.KEY_ORDER_PAID,
                new OrderPaid(order.getOrderId(), order.getBuyerId(), order.getBuyerEmail(), Instant.now(),
                        order.getItems().stream()
                                .map(i -> new PaidItem(i.getOrderItemId(), i.getPublicationId(), i.getSellerId(),
                                        i.getDescription(), i.getPrice()))
                                .toList())));
    }

    private static boolean luhnValid(String number) {
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = number.charAt(i) - '0';
            if (alt && (d *= 2) > 9) {
                d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private static String brand(String n) {
        if (n.startsWith("4")) return "VISA";
        if (n.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (n.matches("^3[47].*")) return "AMEX";
        return "OTHER";
    }

    /** Libera el dinero retenido a los vendedores. */
    public Order release(String id) {
        return transition(id, PaymentStatus.HOLD_ESCROW, PaymentStatus.RELEASED_TO_SELLER, "COMPLETED");
    }

    /** Devuelve el dinero al comprador (solo si sigue retenido). */
    public Order refund(String id) {
        return transition(id, PaymentStatus.HOLD_ESCROW, PaymentStatus.REFUNDED, "CANCELLED");
    }

    private Order transition(String id, PaymentStatus from, PaymentStatus to, String orderStatus) {
        Order order = get(id);
        if (order.getPaymentStatus() != from) {
            throw new InvalidStateException("La orden esta en " + order.getPaymentStatus() + ", se esperaba " + from);
        }
        order.setPaymentStatus(to);
        if (orderStatus != null) {
            order.setStatus(orderStatus);
        }
        return order;
    }
}
