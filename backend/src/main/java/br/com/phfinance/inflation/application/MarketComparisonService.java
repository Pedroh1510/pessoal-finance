package br.com.phfinance.inflation.application;

import br.com.phfinance.inflation.domain.MarketItem;
import br.com.phfinance.inflation.infra.MarketItemRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketComparisonService {

    private final MarketItemRepository itemRepository;

    public MarketComparisonService(MarketItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public NcmComparisonDTO getComparison(String ncm, String description,
                                           YearMonth from, YearMonth to) {
        LocalDate fromDate = from.atDay(1);
        LocalDate toDate = to.atEndOfMonth();

        List<MarketItem> items = itemRepository.findForComparison(
            ncm, description, fromDate, toDate);

        String desc = items.isEmpty()
            ? (description != null ? description : "")
            : items.get(0).getDescription();

        List<PricePointDTO> prices = items.stream()
            .map(item -> new PricePointDTO(
                item.getPurchase().getDate().toString().substring(0, 7),
                item.getUnitPrice(),
                item.getPurchase().getEmitente()
            ))
            .toList();

        return new NcmComparisonDTO(ncm != null ? ncm : "", desc, prices);
    }

    public List<MarketItemDTO> getItems(String ncm, String description, YearMonth period) {
        LocalDate fromDate = period != null ? period.atDay(1) : null;
        LocalDate toDate = period != null ? period.atEndOfMonth() : null;

        return itemRepository.findFiltered(ncm, description, fromDate, toDate).stream()
            .map(this::toDTO)
            .toList();
    }

    private MarketItemDTO toDTO(MarketItem item) {
        return new MarketItemDTO(
            item.getId(),
            item.getPurchase().getId(),
            item.getPurchase().getDate().toString().substring(0, 7),
            item.getPurchase().getEmitente(),
            item.getPurchase().getChave(),
            item.getProductCode(),
            item.getNcm(),
            item.getDescription(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getTotalPrice()
        );
    }
}
