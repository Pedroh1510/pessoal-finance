package br.com.phfinance.inflation.application;

import java.util.List;

public record NcmComparisonDTO(String ncm, String description, List<PricePointDTO> prices) {}
