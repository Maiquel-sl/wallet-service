package com.bank.wallet.controller;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.bank.wallet.model.MobileWallet;
import com.bank.wallet.model.WalletTransaction;
import com.bank.wallet.service.WalletService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // 1. Registrar monedero móvil (no requiere ser cliente del banco)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MobileWallet> createWallet(@RequestBody MobileWallet wallet) {
        return walletService.createWallet(wallet);
    }

    // 2. Consultar monedero por número de celular
    @GetMapping("/phone/{phoneNumber}")
    public Mono<ResponseEntity<MobileWallet>> getWalletByPhone(@PathVariable("phoneNumber") String phoneNumber) {
        return walletService.getWalletByPhone(phoneNumber)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // 3. Asociar monedero a una tarjeta de débito del banco
    @PostMapping("/link-card")
    public Mono<MobileWallet> linkDebitCard(@RequestBody Map<String, String> payload) {
        String phoneNumber = payload.get("phoneNumber");
        String cardNumber = payload.get("cardNumber");
        if (phoneNumber == null || cardNumber == null) {
            return Mono.error(new IllegalArgumentException("Los campos 'phoneNumber' y 'cardNumber' son obligatorios"));
        }
        return walletService.linkDebitCard(phoneNumber, cardNumber);
    }

    // 4. Enviar pago móvil usando solo número de celular
    @PostMapping("/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WalletTransaction> sendPayment(@RequestBody Map<String, Object> payload) {
        if (payload.get("senderPhone") == null || payload.get("receiverPhone") == null
                || payload.get("amount") == null) {
            return Mono.error(new IllegalArgumentException(
                    "Los campos 'senderPhone', 'receiverPhone' y 'amount' son obligatorios"));
        }
        String senderPhone = payload.get("senderPhone").toString();
        String receiverPhone = payload.get("receiverPhone").toString();
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String description = payload.get("description") != null ? payload.get("description").toString() : null;

        return walletService.sendPayment(senderPhone, receiverPhone, amount, description);
    }

    // 5. Consultar historial de movimientos del monedero por celular
    @GetMapping("/phone/{phoneNumber}/transactions")
    public Flux<WalletTransaction> getWalletHistory(@PathVariable("phoneNumber") String phoneNumber) {
        return walletService.getWalletHistory(phoneNumber);
    }
}
