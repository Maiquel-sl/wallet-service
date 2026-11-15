package com.bank.wallet.service;

import com.bank.wallet.events.WalletCardOperationEvent;
import com.bank.wallet.events.WalletLinkCardRequestEvent;
import com.bank.wallet.messaging.WalletEventPublisher;
import com.bank.wallet.model.MobileWallet;
import com.bank.wallet.model.WalletTransaction;
import com.bank.wallet.repository.MobileWalletRepository;
import com.bank.wallet.repository.WalletTransactionRepository;
import com.bank.wallet.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private MobileWalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository transactionRepository;
    @Mock
    private WalletEventPublisher eventPublisher;

    @InjectMocks
    private WalletServiceImpl walletService;

    private MobileWallet standaloneSenderWallet;
    private MobileWallet linkedReceiverWallet;

    @BeforeEach
    void setUp() {
        // Monedero independiente (sin tarjeta asociada, opera con saldo interno)
        standaloneSenderWallet = new MobileWallet();
        standaloneSenderWallet.setId("wal-001");
        standaloneSenderWallet.setDocumentType("DNI");
        standaloneSenderWallet.setDocumentNumber("71234568");
        standaloneSenderWallet.setPhoneNumber("987654321");
        standaloneSenderWallet.setImei("356938035643809");
        standaloneSenderWallet.setEmail("emisor@email.com");
        standaloneSenderWallet.setBalance(new BigDecimal("200.00"));
        standaloneSenderWallet.setCurrency("PEN");
        standaloneSenderWallet.setCardLinkStatus("NONE");
        standaloneSenderWallet.setStatus("ACTIVE");

        // Monedero vinculado a tarjeta de débito del banco
        linkedReceiverWallet = new MobileWallet();
        linkedReceiverWallet.setId("wal-002");
        linkedReceiverWallet.setDocumentType("PASSPORT");
        linkedReceiverWallet.setDocumentNumber("P9876543");
        linkedReceiverWallet.setPhoneNumber("912345678");
        linkedReceiverWallet.setImei("867530912345678");
        linkedReceiverWallet.setEmail("receptor@email.com");
        linkedReceiverWallet.setBalance(BigDecimal.ZERO);
        linkedReceiverWallet.setCurrency("PEN");
        linkedReceiverWallet.setLinkedDebitCardNumber("4558111122223333");
        linkedReceiverWallet.setCardLinkStatus("LINKED");
        linkedReceiverWallet.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("Debe crear un monedero móvil sin requerir cuenta bancaria previa")
    void testCreateStandaloneWalletSuccess() {
        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.empty());
        Mockito.when(walletRepository.save(any(MobileWallet.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(walletService.createWallet(standaloneSenderWallet))
                .expectNextMatches(saved -> "987654321".equals(saved.getPhoneNumber()) &&
                        "NONE".equals(saved.getCardLinkStatus()) &&
                        "ACTIVE".equals(saved.getStatus()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Debe rechazar registro si el tipo de documento no es DNI, CEX o PASSPORT")
    void testCreateWalletInvalidDocumentTypeFails() {
        standaloneSenderWallet.setDocumentType("RUC");

        StepVerifier.create(walletService.createWallet(standaloneSenderWallet))
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException &&
                        ex.getMessage().contains("Tipo de documento inválido"))
                .verify();

        Mockito.verify(walletRepository, Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar registro si el número de celular ya tiene un monedero")
    void testCreateWalletDuplicatePhoneFails() {
        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.just(standaloneSenderWallet));

        StepVerifier.create(walletService.createWallet(standaloneSenderWallet))
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException &&
                        ex.getMessage().contains("celular ya tiene un monedero"))
                .verify();
    }

    @Test
    @DisplayName("Debe solicitar asociación de tarjeta de débito y publicar evento a Kafka")
    void testLinkDebitCardPublishesKafkaEvent() {
        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.just(standaloneSenderWallet));
        Mockito.when(walletRepository.save(any(MobileWallet.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(walletService.linkDebitCard("987654321", "4558111122223333"))
                .expectNextMatches(updated -> "4558111122223333".equals(updated.getLinkedDebitCardNumber()) &&
                        "PENDING_VALIDATION".equals(updated.getCardLinkStatus()))
                .verifyComplete();

        Mockito.verify(eventPublisher).publishLinkCardRequest(
                argThat((WalletLinkCardRequestEvent event) -> "wal-001".equals(event.walletId()) &&
                        "4558111122223333".equals(event.cardNumber())));
    }

    @Test
    @DisplayName("Debe transferir dinero desde saldo interno hacia receptor con tarjeta vinculada (emite evento a Kafka)")
    void testSendPaymentFromStandaloneToLinkedCardWallet() {
        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.just(standaloneSenderWallet));
        Mockito.when(walletRepository.findByPhoneNumber("912345678")).thenReturn(Mono.just(linkedReceiverWallet));
        Mockito.when(walletRepository.save(any(MobileWallet.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Mockito.when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(walletService.sendPayment("987654321", "912345678", new BigDecimal("50.00"), "Almuerzo"))
                .expectNextMatches(tx -> tx.getTransactionCode().startsWith("YAP-") &&
                        "COMPLETED".equals(tx.getStatus()) &&
                        standaloneSenderWallet.getBalance().compareTo(new BigDecimal("150.00")) == 0)
                .verifyComplete();

        // Verifica que al receptor se le acredite en la cuenta principal de su tarjeta
        // de débito vía Kafka
        Mockito.verify(eventPublisher).publishCardOperation(
                argThat((WalletCardOperationEvent op) -> "4558111122223333".equals(op.cardNumber()) &&
                        "CREDIT_PRIMARY_ACCOUNT".equals(op.operationType()) &&
                        op.amount().compareTo(new BigDecimal("50.00")) == 0));
    }

    @Test
    @DisplayName("Debe debitar cuenta principal vía Kafka cuando el emisor tiene tarjeta vinculada")
    void testSendPaymentFromLinkedCardWalletDebitsPrimaryAccount() {
        Mockito.when(walletRepository.findByPhoneNumber("912345678")).thenReturn(Mono.just(linkedReceiverWallet));
        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.just(standaloneSenderWallet));
        Mockito.when(walletRepository.save(any(MobileWallet.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        Mockito.when(transactionRepository.save(any(WalletTransaction.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(walletService.sendPayment("912345678", "987654321", new BigDecimal("80.00"), "Pago taxi"))
                .expectNextMatches(tx -> "COMPLETED".equals(tx.getStatus()) &&
                        standaloneSenderWallet.getBalance().compareTo(new BigDecimal("280.00")) == 0)
                .verifyComplete();

        Mockito.verify(eventPublisher).publishCardOperation(
                argThat((WalletCardOperationEvent op) -> "4558111122223333".equals(op.cardNumber()) &&
                        "DEBIT_PRIMARY_ACCOUNT".equals(op.operationType())));
    }

    @Test
    @DisplayName("Debe rechazar pago si el monedero sin tarjeta no tiene saldo suficiente")
    void testSendPaymentInsufficientBalanceFails() {
        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.just(standaloneSenderWallet));
        Mockito.when(walletRepository.findByPhoneNumber("912345678")).thenReturn(Mono.just(linkedReceiverWallet));

        StepVerifier.create(walletService.sendPayment("987654321", "912345678", new BigDecimal("500.00"), "Exceso"))
                .expectErrorMatches(ex -> ex instanceof IllegalArgumentException &&
                        ex.getMessage().contains("Saldo insuficiente"))
                .verify();
    }

    @Test
    @DisplayName("Debe listar el historial de pagos enviados y recibidos de un celular")
    void testGetWalletHistorySuccess() {
        WalletTransaction mockTx = new WalletTransaction();
        mockTx.setTransactionCode("YAP-1234567890");
        mockTx.setSenderPhone("987654321");
        mockTx.setReceiverPhone("912345678");
        mockTx.setAmount(new BigDecimal("25.00"));
        mockTx.setTimestamp(LocalDateTime.now());

        Mockito.when(walletRepository.findByPhoneNumber("987654321")).thenReturn(Mono.just(standaloneSenderWallet));
        Mockito.when(
                transactionRepository.findBySenderPhoneOrReceiverPhoneOrderByTimestampDesc("987654321", "987654321"))
                .thenReturn(Flux.just(mockTx));
        StepVerifier.create(walletService.getWalletHistory("987654321"))
                .expectNext(mockTx)
                .verifyComplete();
    }
}
