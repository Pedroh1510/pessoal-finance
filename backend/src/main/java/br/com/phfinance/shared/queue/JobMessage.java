package br.com.phfinance.shared.queue;

import java.util.UUID;

public record JobMessage(UUID jobId, String type, String filePath, String bankName) {}
