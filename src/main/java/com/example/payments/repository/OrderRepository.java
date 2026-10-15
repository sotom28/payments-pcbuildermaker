package com.example.payments.repository;

import com.example.payments.model.Order;
import com.example.payments.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByBuyerId(String buyerId);

    /** true si la publicacion ya esta en una orden en esos estados de pago (para no venderla dos veces). */
    @Query("select count(i) > 0 from Order o join o.items i "
            + "where i.publicationId = :publicationId and o.paymentStatus in :statuses")
    boolean existsItemByPublicationIdAndPaymentStatusIn(@Param("publicationId") String publicationId,
                                                        @Param("statuses") Collection<PaymentStatus> statuses);
}
