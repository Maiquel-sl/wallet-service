package com.bank.wallet.service;

import com.bank.wallet.model.MobileWallet;
import com.bank.wallet.model.WalletTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;

public interface WalletService {

    Mono<MobileWallet> createWallet(MobileWallet wallet);

    Mono<MobileWallet> getWalletByPhone(String phoneNumber);

    Mono<MobileWallet> linkDebitCard(String phoneNumber, String cardNumber);

    Mono<WalletTransaction> sendPayment(String senderPhone, String receiverPhone, BigDecimal amount,
            String description);

    Flux<WalletTransaction> getWalletHistory(String phoneNumber);
}
