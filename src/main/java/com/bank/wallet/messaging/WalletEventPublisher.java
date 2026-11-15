package com.bank.wallet.messaging;

import com.bank.wallet.events.WalletCardOperationEvent;
import com.bank.wallet.events.WalletLinkCardRequestEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.util.function.Supplier;

@Component
public class WalletEventPublisher {

    private final Sinks.Many<WalletLinkCardRequestEvent> linkCardSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<WalletCardOperationEvent> cardOpSink = Sinks.many().multicast().onBackpressureBuffer();

    public void publishLinkCardRequest(WalletLinkCardRequestEvent event) {
        linkCardSink.tryEmitNext(event);
    }

    public void publishCardOperation(WalletCardOperationEvent event) {
        cardOpSink.tryEmitNext(event);
    }

    @Bean
    public Supplier<Flux<WalletLinkCardRequestEvent>> walletLinkCardProducer() {
        return linkCardSink::asFlux;
    }

    @Bean
    public Supplier<Flux<WalletCardOperationEvent>> walletCardOperationProducer() {
        return cardOpSink::asFlux;
    }
}
