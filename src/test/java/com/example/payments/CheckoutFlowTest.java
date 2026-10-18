package com.example.payments;

import com.example.payments.messaging.Events.OrderPaid;
import com.example.payments.messaging.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin @Transactional a proposito: order.paid se publica AFTER_COMMIT, asi que el test necesita commits reales. */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutFlowTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RabbitTemplate rabbit;

    static String pub() {
        return UUID.randomUUID().toString();
    }

    static String checkoutBody(String email, String publicationId, String sellerId) {
        return """
                {"buyerEmail":"%s","items":[{"publicationId":"%s","sellerId":"%s","title":"RTX 4070 usada","price":450000}]}
                """.formatted(email, publicationId, sellerId);
    }

    @Test
    void checkoutDejaLaOrdenPagadaYPublicaOrderPaid() throws Exception {
        String publicationId = pub();
        mvc.perform(post("/api/payments/orders/checkout")
                        .header("X-User-Id", "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("comprador@correo.cl", publicationId, "seller-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.buyerId").value("buyer-1")) // sale del header, no del body
                .andExpect(jsonPath("$.buyerEmail").value("comprador@correo.cl"))
                .andExpect(jsonPath("$.paymentStatus").value("HOLD_ESCROW"))
                .andExpect(jsonPath("$.items[0].description").value("RTX 4070 usada"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.KEY_ORDER_PAID),
                payload.capture(), any(CorrelationData.class));
        OrderPaid event = (OrderPaid) payload.getValue();
        assertEquals("buyer-1", event.buyerId());
        assertEquals("comprador@correo.cl", event.buyerEmail());
        assertEquals(1, event.items().size());
        assertEquals(publicationId, event.items().get(0).publicationId());
        assertEquals("seller-1", event.items().get(0).sellerId());
        assertEquals("RTX 4070 usada", event.items().get(0).description());
        assertEquals(450000, event.items().get(0).price());
    }

    @Test
    void sinIdentidadNoHayCompra() throws Exception {
        mvc.perform(post("/api/payments/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("comprador@correo.cl", pub(), "seller-1")))
                .andExpect(status().isUnauthorized());
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    @Test
    void correoInvalidoEs400() throws Exception {
        mvc.perform(post("/api/payments/orders/checkout")
                        .header("X-User-Id", "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("no-es-un-correo", pub(), "seller-1")))
                .andExpect(status().isBadRequest());
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    @Test
    void unaPublicacionYaCompradaNoSeVendeDosVeces() throws Exception {
        String publicationId = pub();
        mvc.perform(post("/api/payments/orders/checkout").header("X-User-Id", "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("uno@correo.cl", publicationId, "seller-1")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/payments/orders/checkout").header("X-User-Id", "buyer-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("dos@correo.cl", publicationId, "seller-1")))
                .andExpect(status().isConflict());
        verify(rabbit, times(1)).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    @Test
    void noSePuedeComprarLaPropiaPublicacion() throws Exception {
        mvc.perform(post("/api/payments/orders/checkout").header("X-User-Id", "seller-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("yo@correo.cl", pub(), "seller-1")))
                .andExpect(status().isConflict());
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
    }

    @Test
    void releaseYRefundSoloLosHaceElAdmin() throws Exception {
        String body = mvc.perform(post("/api/payments/orders/checkout").header("X-User-Id", "buyer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("comprador@correo.cl", pub(), "seller-1")))
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(body, "$.orderId");

        mvc.perform(post("/api/payments/orders/" + orderId + "/release")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/orders/" + orderId + "/release").header("X-User-Id", "buyer-1")
                .header("X-User-Role", "BUYER_SELLER")).andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/orders/" + orderId + "/release").header("X-User-Id", "admin-1")
                        .header("X-User-Role", "WORKSHOP_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("RELEASED_TO_SELLER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].inspectionStatus").value("PASSED"));
    }

    @Test
    void elReembolsoCancelaLaOrdenYMarcaLosItemsComoRechazados() throws Exception {
        String body = mvc.perform(post("/api/payments/orders/checkout").header("X-User-Id", "buyer-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody("comprador@correo.cl", pub(), "seller-1")))
                .andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(body, "$.orderId");

        mvc.perform(post("/api/payments/orders/" + orderId + "/refund").header("X-User-Id", "admin-1")
                        .header("X-User-Role", "WORKSHOP_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].inspectionStatus").value("FAILED"));
    }

    @Test
    void elPagoConTarjetaDelFlujoAnteriorTambienPublicaOrderPaid() throws Exception {
        String create = mvc.perform(post("/api/payments/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerId":"buyer-9","buyerEmail":"nueve@correo.cl","items":[{"publicationId":"%s","sellerId":"seller-1","title":"SSD","price":30000}]}
                                """.formatted(pub())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = com.jayway.jsonpath.JsonPath.read(create, "$.orderId");
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        mvc.perform(post("/api/payments/orders/" + orderId + "/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"holderName":"Test","cardNumber":"4242424242424242","expMonth":12,"expYear":2099,"cvv":"123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("HOLD_ESCROW"));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.KEY_ORDER_PAID),
                payload.capture(), any(CorrelationData.class));
        assertEquals("nueve@correo.cl", ((OrderPaid) payload.getValue()).buyerEmail());
    }
}
