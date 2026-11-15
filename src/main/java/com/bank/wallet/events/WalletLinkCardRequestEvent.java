package com.bank.wallet.events;

import java.time.LocalDateTime;

public record WalletLinkCardRequestEvent(
    String walletId,
    String phoneNumber,
    String cardNumber,
    LocalDateTime timestamp
) {}
