package com.example.payments.dto;

import com.example.payments.model.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String buyerId,
        String buyerEmail,
        OrderType orderType,
        @NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotBlank String publicationId,
            @NotBlank String sellerId,
            String title,
            @Min(1) int price) {
    }
}
