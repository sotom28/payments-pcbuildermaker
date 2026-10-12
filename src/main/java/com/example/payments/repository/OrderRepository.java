package com.example.payments.repository;

import com.example.payments.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByBuyerId(String buyerId);
}
