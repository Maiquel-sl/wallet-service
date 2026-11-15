package com.bank.wallet.service.impl;

import com.bank.wallet.events.WalletCardOperationEvent;
import com.bank.wallet.events.WalletLinkCardRequestEvent;
import com.bank.wallet.messaging.WalletEventPublisher;
import com.bank.wallet.model.MobileWallet;
import com.bank.wallet.model.WalletTransaction;
import com.bank.wallet.repository.MobileWalletRepository;
import com.bank.wallet.repository.WalletTransactionRepository;
import com.bank.wallet.service.WalletService;
import com.bank.wallet.util.ReactiveFallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private final MobileWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletEventPublisher eventPublisher;
    private static final List<String> ALLOWED_DOCS = List.of("DNI", "CEX", "PASSPORT");

    public WalletServiceImpl(MobileWalletRepository walletRepository,
            WalletTransactionRepository transactionRepository,
            WalletEventPublisher eventPublisher) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<MobileWallet> createWallet(MobileWallet wallet) {
        if (wallet.getDocumentType() == null || !ALLOWED_DOCS.contains(wallet.getDocumentType().toUpperCase())) {
            return Mono.error(new IllegalArgumentException("Tipo de documento inválido. Use DNI, CEX o PASSPORT"));
        }
        if (wallet.getDocumentNumber() == null || wallet.getDocumentNumber().isBlank() ||
                wallet.getPhoneNumber() == null || wallet.getPhoneNumber().isBlank() ||
                wallet.getImei() == null || wallet.getImei().isBlank() ||
                wallet.getEmail() == null || wallet.getEmail().isBlank()) {
            return Mono.error(
                    new IllegalArgumentException("Documento, celular, IMEI y correo electrónico son obligatorios"));
        }

        return walletRepository.findByPhoneNumber(wallet.getPhoneNumber().trim())
                .flatMap(existing -> Mono.<MobileWallet>error(
                        new IllegalArgumentException("El número de celular ya tiene un monedero registrado")))
                .switchIfEmpty(Mono.defer(() -> {
                    wallet.setDocumentType(wallet.getDocumentType().toUpperCase());
                    wallet.setPhoneNumber(wallet.getPhoneNumber().trim());
                    wallet.setBalance(wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO);
                    wallet.setCurrency(wallet.getCurrency() != null ? wallet.getCurrency() : "PEN");
                    wallet.setCardLinkStatus("NONE");
                    wallet.setStatus("ACTIVE");
                    wallet.setCreatedAt(LocalDateTime.now());
                    wallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(wallet);
                }));
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<MobileWallet> linkDebitCard(String phoneNumber, String cardNumber) {
        return walletRepository.findByPhoneNumber(phoneNumber.trim())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Monedero móvil no encontrado")))
                .flatMap(wallet -> {
                    wallet.setLinkedDebitCardNumber(cardNumber.trim());
                    wallet.setCardLinkStatus("PENDING_VALIDATION");
                    wallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(wallet)
                            .doOnSuccess(saved -> eventPublisher.publishLinkCardRequest(
                                    new WalletLinkCardRequestEvent(saved.getId(), saved.getPhoneNumber(),
                                            saved.getLinkedDebitCardNumber(), LocalDateTime.now())));
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<WalletTransaction> sendPayment(String senderPhone, String receiverPhone, BigDecimal amount,
            String description) {
        if (senderPhone.trim().equals(receiverPhone.trim())) {
            return Mono.error(new IllegalArgumentException("No puedes enviarte dinero a tu propio número de celular"));
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("El monto a transferir debe ser mayor a cero"));
        }

        return Mono.zip(
                walletRepository.findByPhoneNumber(senderPhone.trim())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Monedero emisor no encontrado"))),
                walletRepository.findByPhoneNumber(receiverPhone.trim())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Monedero receptor no encontrado"))))
                .flatMap(tuple -> {
                    MobileWallet sender = tuple.getT1();
                    MobileWallet receiver = tuple.getT2();

                    String txCode = "YAP-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                    String desc = (description != null && !description.isBlank()) ? description : "Pago móvil";

                    // 1. Procesar débito del emisor (Tarjeta de débito principal o saldo interno
                    // del monedero)
                    Mono<Void> debitStep;
                    if ("LINKED".equals(sender.getCardLinkStatus()) && sender.getLinkedDebitCardNumber() != null) {
                        eventPublisher.publishCardOperation(new WalletCardOperationEvent(
                                txCode, sender.getLinkedDebitCardNumber(), "DEBIT_PRIMARY_ACCOUNT", amount,
                                "Pago móvil enviado a " + receiver.getPhoneNumber() + ": " + desc,
                                LocalDateTime.now()));
                        debitStep = Mono.empty();
                    } else {
                        if (sender.getBalance().compareTo(amount) < 0) {
                            return Mono.error(new IllegalArgumentException("Saldo insuficiente en el monedero móvil"));
                        }
                        sender.setBalance(sender.getBalance().subtract(amount));
                        sender.setUpdatedAt(LocalDateTime.now());
                        debitStep = walletRepository.save(sender).then();
                    }

                    // 2. Procesar abono al receptor (Cuenta principal de su tarjeta asociada o
                    // saldo interno del monedero)
                    Mono<Void> creditStep;
                    if ("LINKED".equals(receiver.getCardLinkStatus()) && receiver.getLinkedDebitCardNumber() != null) {
                        eventPublisher.publishCardOperation(new WalletCardOperationEvent(
                                txCode, receiver.getLinkedDebitCardNumber(), "CREDIT_PRIMARY_ACCOUNT", amount,
                                "Pago móvil recibido de " + sender.getPhoneNumber() + ": " + desc,
                                LocalDateTime.now()));
                        creditStep = Mono.empty();
                    } else {
                        receiver.setBalance(receiver.getBalance().add(amount));
                        receiver.setUpdatedAt(LocalDateTime.now());
                        creditStep = walletRepository.save(receiver).then();
                    }

                    WalletTransaction tx = new WalletTransaction();
                    tx.setTransactionCode(txCode);
                    tx.setSenderPhone(sender.getPhoneNumber());
                    tx.setReceiverPhone(receiver.getPhoneNumber());
                    tx.setAmount(amount);
                    tx.setCurrency(sender.getCurrency());
                    tx.setDescription(desc);
                    tx.setStatus("COMPLETED");
                    tx.setTimestamp(LocalDateTime.now());

                    return debitStep.then(creditStep).then(transactionRepository.save(tx));
                });
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Mono<MobileWallet> getWalletByPhone(String phoneNumber) {
        return walletRepository.findByPhoneNumber(phoneNumber.trim());
    }

    @Override
    @CircuitBreaker(name = "bankServiceCB", fallbackMethod = "genericMonoFallback")
    @TimeLimiter(name = "bankServiceCB")
    public Flux<WalletTransaction> getWalletHistory(String phoneNumber) {
        String cleanPhone = phoneNumber.trim();
        return walletRepository.findByPhoneNumber(cleanPhone)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Monedero móvil no encontrado")))
                .flatMapMany(w -> transactionRepository.findBySenderPhoneOrReceiverPhoneOrderByTimestampDesc(cleanPhone,
                        cleanPhone));
    }

    // =========================================================================
    // MÉTODOS FALLBACK GENÉRICOS PARA TODA LA CLASE
    // =========================================================================

    @SuppressWarnings("unused")
    private <T> Mono<T> genericMonoFallback(Throwable ex) {
        return ReactiveFallbackHandler.handleMonoFallback("CustomerService", ex);
    }

    @SuppressWarnings("unused")
    private <T> Flux<T> genericFluxFallback(Throwable ex) {
        return ReactiveFallbackHandler.handleFluxFallback("CustomerService", ex);
    }
}
