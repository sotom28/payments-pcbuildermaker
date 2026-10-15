package com.example.payments.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @Column(name = "order_item_id", length = 36)
    private String orderItemId = UUID.randomUUID().toString();

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "publication_id", nullable = false, length = 36)
    private String publicationId;

    @Column(name = "seller_id", nullable = false, length = 36)
    private String sellerId;

    /** Titulo de la pieza al momento de la compra (lo ve el admin y va en el informe). */
    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(name = "platform_fee")
    private Integer platformFee;

    @Column(name = "seller_payout_amount")
    private Integer sellerPayoutAmount;

    @Column(name = "inspection_status", nullable = false, length = 20)
    private String inspectionStatus = "PENDING";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
