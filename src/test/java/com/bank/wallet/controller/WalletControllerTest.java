package com.bank.wallet.controller;

import com.bank.wallet.exception.GlobalExceptionHandler;
import com.bank.wallet.model.MobileWallet;
import com.bank.wallet.model.WalletTransaction;
import com.bank.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@WebFluxTest(WalletController.class)
@Import(GlobalExceptionHandler.class)
class WalletControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private WalletService walletService;

    private MobileWallet mockWallet;
    private WalletTransaction mockTransaction;

    @BeforeEach
    void setUp() {
        mockWallet = new MobileWallet();
        mockWallet.setId("wal-001");
        mockWallet.setDocumentType("DNI");
        mockWallet.setDocumentNumber("71234568");
        mockWallet.setPhoneNumber("987654321");
        mockWallet.setImei("356938035643809");
        mockWallet.setEmail("usuario@email.com");
        mockWallet.setBalance(new BigDecimal("150.00"));
        mockWallet.setCurrency("PEN");
        mockWallet.setCardLinkStatus("NONE");
        mockWallet.setStatus("ACTIVE");

        mockTransaction = new WalletTransaction();
        mockTransaction.setId("wtx-001");
        mockTransaction.setTransactionCode("YAP-A1B2C3D4E5");
        mockTransaction.setSenderPhone("987654321");
        mockTransaction.setReceiverPhone("912345678");
        mockTransaction.setAmount(new BigDecimal("45.50"));
        mockTransaction.setCurrency("PEN");
        mockTransaction.setDescription("Almuerzo compartido");
        mockTransaction.setStatus("COMPLETED");
        mockTransaction.setTimestamp(LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /api/wallets - Debe registrar monedero móvil y devolver 201 Created")
    void testCreateWalletSuccess() {
        Mockito.when(walletService.createWallet(any(MobileWallet.class))).thenReturn(Mono.just(mockWallet));

        webTestClient.post()
                .uri("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockWallet)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.phoneNumber").isEqualTo("987654321")
                .jsonPath("$.documentType").isEqualTo("DNI")
                .jsonPath("$.imei").isEqualTo("356938035643809");
    }

    @Test
    @DisplayName("POST /api/wallets - Debe devolver 400 Bad Request si el celular ya está registrado")
    void testCreateWalletDuplicatePhoneReturnsBadRequest() {
        Mockito.when(walletService.createWallet(any(MobileWallet.class)))
                .thenReturn(Mono.error(new IllegalArgumentException("El número de celular ya tiene un monedero registrado")));

        webTestClient.post()
                .uri("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockWallet)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("El número de celular ya tiene un monedero registrado");
    }

    @Test
    @DisplayName("GET /api/wallets/phone/{phoneNumber} - Debe retornar monedero si el celular existe")
    void testGetWalletByPhoneSuccess() {
        Mockito.when(walletService.getWalletByPhone("987654321")).thenReturn(Mono.just(mockWallet));

        webTestClient.get()
                .uri("/api/wallets/phone/987654321")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.phoneNumber").isEqualTo("987654321")
                .jsonPath("$.balance").isEqualTo(150.00);
    }

    @Test
    @DisplayName("GET /api/wallets/phone/{phoneNumber} - Debe devolver 404 Not Found si el celular no existe")
    void testGetWalletByPhoneNotFound() {
        Mockito.when(walletService.getWalletByPhone("900000000")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/wallets/phone/900000000")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("POST /api/wallets/link-card - Debe asociar tarjeta de débito y devolver estado PENDING_VALIDATION")
    void testLinkDebitCardSuccess() {
        mockWallet.setLinkedDebitCardNumber("4558111122223333");
        mockWallet.setCardLinkStatus("PENDING_VALIDATION");

        Mockito.when(walletService.linkDebitCard("987654321", "4558111122223333"))
                .thenReturn(Mono.just(mockWallet));

        Map<String, String> payload = Map.of(
                "phoneNumber", "987654321",
                "cardNumber", "4558111122223333"
        );

        webTestClient.post()
                .uri("/api/wallets/link-card")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.linkedDebitCardNumber").isEqualTo("4558111122223333")
                .jsonPath("$.cardLinkStatus").isEqualTo("PENDING_VALIDATION");
    }

    @Test
    @DisplayName("POST /api/wallets/pay - Debe procesar pago entre celulares y devolver 201 Created")
    void testSendPaymentSuccess() {
        Mockito.when(walletService.sendPayment(
                        eq("987654321"),
                        eq("912345678"),
                        eq(new BigDecimal("45.50")),
                        eq("Almuerzo compartido")))
                .thenReturn(Mono.just(mockTransaction));

        Map<String, Object> payload = Map.of(
                "senderPhone", "987654321",
                "receiverPhone", "912345678",
                "amount", 45.50,
                "description", "Almuerzo compartido"
        );

        webTestClient.post()
                .uri("/api/wallets/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.transactionCode").isEqualTo("YAP-A1B2C3D4E5")
                .jsonPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("GET /api/wallets/phone/{phoneNumber}/transactions - Debe listar historial de pagos del celular")
    void testGetWalletHistorySuccess() {
        Mockito.when(walletService.getWalletHistory("987654321")).thenReturn(Flux.just(mockTransaction));

        webTestClient.get()
                .uri("/api/wallets/phone/987654321/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].transactionCode").isEqualTo("YAP-A1B2C3D4E5");
    }
}