package br.com.phfinance.inflation.application;

import br.com.phfinance.inflation.domain.MarketItem;
import br.com.phfinance.inflation.domain.MarketPurchase;
import br.com.phfinance.inflation.infra.MarketPurchaseRepository;
import br.com.phfinance.inflation.infra.ParsedRow;
import br.com.phfinance.inflation.infra.SpreadsheetParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MarketUploadService {

    private final SpreadsheetParser spreadsheetParser;
    private final MarketPurchaseRepository purchaseRepository;

    public MarketUploadService(SpreadsheetParser spreadsheetParser,
                               MarketPurchaseRepository purchaseRepository) {
        this.spreadsheetParser = spreadsheetParser;
        this.purchaseRepository = purchaseRepository;
    }

    public InflationUploadResult upload(byte[] fileBytes, String fileName) {
        List<ParsedRow> parsedRows = spreadsheetParser.parse(fileBytes);

        Map<String, List<ParsedRow>> byChave = new LinkedHashMap<>();
        for (ParsedRow row : parsedRows) {
            byChave.computeIfAbsent(row.chave(), k -> new ArrayList<>()).add(row);
        }

        int purchasesCreated = 0, purchasesSkipped = 0, itemsImported = 0;

        for (Map.Entry<String, List<ParsedRow>> entry : byChave.entrySet()) {
            String chave = entry.getKey();
            if (purchaseRepository.existsByChave(chave)) {
                purchasesSkipped++;
                continue;
            }

            List<ParsedRow> rows = entry.getValue();
            ParsedRow first = rows.get(0);

            MarketPurchase purchase = new MarketPurchase();
            purchase.setChave(chave);
            purchase.setDate(first.date());
            purchase.setEmitente(first.emitente());
            purchase.setFileName(fileName);

            for (ParsedRow row : rows) {
                MarketItem item = new MarketItem();
                item.setPurchase(purchase);
                item.setProductCode(row.productCode());
                item.setNcm(row.ncm());
                item.setDescription(row.description());
                item.setQuantity(row.quantity());
                item.setUnitPrice(row.unitPrice());
                item.setTotalPrice(row.totalPrice());
                purchase.getItems().add(item);
            }

            purchaseRepository.save(purchase);
            purchasesCreated++;
            itemsImported += rows.size();
        }

        return new InflationUploadResult(purchasesCreated, purchasesSkipped, itemsImported);
    }
}
