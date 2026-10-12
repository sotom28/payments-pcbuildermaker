package com.example.payments.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CardPaymentRequest(
        @NotBlank String holderName,
        @Pattern(regexp = "\\d{13,19}", message = "debe tener entre 13 y 19 digitos") String cardNumber,
        @Min(1) @Max(12) int expMonth,
        @Min(2000) int expYear,
        @Pattern(regexp = "\\d{3,4}", message = "debe tener 3 o 4 digitos") String cvv) {
}
