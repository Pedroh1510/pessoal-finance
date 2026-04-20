package br.com.phfinance.inflation.infra;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedRow(
    String chave,
    LocalDate date,
    String emitente,
    String productCode,
    String ncm,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice
) {}
