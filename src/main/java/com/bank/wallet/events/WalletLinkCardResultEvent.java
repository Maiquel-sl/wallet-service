package com.bank.wallet.events;

import java.time.LocalDateTime;

public record WalletLinkCardResultEvent(
    String walletId,
    String cardNumber,
    boolean valid,
    String reason,
    LocalDateTime timestamp
) {}
