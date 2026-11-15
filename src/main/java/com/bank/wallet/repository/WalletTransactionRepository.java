package com.bank.wallet.repository;

import com.bank.wallet.model.WalletTransaction;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface WalletTransactionRepository extends ReactiveMongoRepository<WalletTransaction, String> {
    Flux<WalletTransaction> findBySenderPhoneOrReceiverPhoneOrderByTimestampDesc(String senderPhone, String receiverPhone);
}
