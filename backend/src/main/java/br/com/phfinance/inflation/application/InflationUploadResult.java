package br.com.phfinance.inflation.application;

public record InflationUploadResult(int purchasesCreated, int purchasesSkipped, int itemsImported) {}
