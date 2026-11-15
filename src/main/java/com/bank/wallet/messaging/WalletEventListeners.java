package com.bank.wallet.messaging;

import com.bank.wallet.events.WalletLinkCardResultEvent;
import com.bank.wallet.repository.MobileWalletRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.function.Function;

@Configuration
public class WalletEventListeners {

    @Bean
    public Function<Flux<WalletLinkCardResultEvent>, Mono<Void>> walletLinkCardResultConsumer(MobileWalletRepository repository) {
        return flux -> flux.flatMap(event ->
            repository.findById(event.walletId())
                .flatMap(wallet -> {
                    if (event.valid()) {
                        wallet.setCardLinkStatus("LINKED");
                    } else {
                        wallet.setLinkedDebitCardNumber(null);
                        wallet.setCardLinkStatus("REJECTED");
                    }
                    wallet.setUpdatedAt(LocalDateTime.now());
                    return repository.save(wallet);
                })
        ).then();
    }
}
