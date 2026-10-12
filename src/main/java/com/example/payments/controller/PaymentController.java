package com.example.payments.controller;

import com.example.payments.dto.CardPaymentRequest;
import com.example.payments.dto.CreateOrderRequest;
import com.example.payments.model.Order;
import com.example.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments/orders")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody CreateOrderRequest req) {
        return service.create(req);
    }

    @GetMapping
    public List<Order> list(@RequestParam(required = false) String buyerId) {
        return service.list(buyerId);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping("/{id}/pay")
    public Order pay(@PathVariable String id, @Valid @RequestBody CardPaymentRequest card) {
        return service.pay(id, card);
    }

    @PostMapping("/{id}/release")
    public Order release(@PathVariable String id) {
        return service.release(id);
    }

    @PostMapping("/{id}/refund")
    public Order refund(@PathVariable String id) {
        return service.refund(id);
    }
}
