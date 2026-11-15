package com.bank.wallet.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletCardOperationEvent(
    String transactionCode,
    String cardNumber,
    String operationType, // DEBIT_PRIMARY_ACCOUNT, CREDIT_PRIMARY_ACCOUNT
    BigDecimal amount,
    String description,
    LocalDateTime timestamp
) {}
