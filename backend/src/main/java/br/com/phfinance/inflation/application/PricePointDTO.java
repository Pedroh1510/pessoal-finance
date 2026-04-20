package br.com.phfinance.inflation.application;

import java.math.BigDecimal;

public record PricePointDTO(String period, BigDecimal unitPrice, String emitente) {}
