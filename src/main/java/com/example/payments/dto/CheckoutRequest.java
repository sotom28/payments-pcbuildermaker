package com.example.payments.dto;

import com.example.payments.model.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Compra en un solo paso (pago simulado). El comprador sale del header X-User-Id, no del body.
 * El BFF arma los items con el precio y el titulo reales de cada publicacion.
 */
public record CheckoutRequest(
        @NotBlank @Email @Size(max = 150) String buyerEmail,
        OrderType orderType,
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotBlank String publicationId,
            @NotBlank String sellerId,
            @NotBlank @Size(max = 255) String title,
            @Min(1) int price) {
    }
}
