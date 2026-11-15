package com.bank.wallet.repository;

import com.bank.wallet.model.MobileWallet;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface MobileWalletRepository extends ReactiveMongoRepository<MobileWallet, String> {
    Mono<MobileWallet> findByPhoneNumber(String phoneNumber);
    Mono<MobileWallet> findByDocumentNumber(String documentNumber);
}
