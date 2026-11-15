package com.bank.wallet.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "mobile_wallets")
public class MobileWallet {

    @MongoId(FieldType.OBJECT_ID)
    private String id;
    private String documentType; // DNI, CEX, PASSPORT
    private String documentNumber;
    @Indexed(unique = true)
    private String phoneNumber;
    private String imei;
    private String email;
    private BigDecimal balance; // Saldo propio cuando no está asociado a tarjeta
    private String currency; 
    private String linkedDebitCardNumber; // Asociación opcional a tarjeta de débito del banco
    private String cardLinkStatus; // NONE, PENDING_VALIDATION, LINKED, REJECTED
    private String status; // ACTIVE, BLOCKED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLinkedDebitCardNumber() {
        return linkedDebitCardNumber;
    }

    public void setLinkedDebitCardNumber(String linkedDebitCardNumber) {
        this.linkedDebitCardNumber = linkedDebitCardNumber;
    }

    public String getCardLinkStatus() {
        return cardLinkStatus;
    }

    public void setCardLinkStatus(String cardLinkStatus) {
        this.cardLinkStatus = cardLinkStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
