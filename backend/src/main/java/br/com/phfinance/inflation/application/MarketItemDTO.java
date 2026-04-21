package br.com.phfinance.inflation.application;

import java.math.BigDecimal;
import java.util.UUID;

public record MarketItemDTO(
    UUID id,
    UUID purchaseId,
    String period,
    String emitente,
    String chave,
    String productCode,
    String ncm,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice
) {}
