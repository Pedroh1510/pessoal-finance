package br.com.phfinance.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.phfinance.finance.infra.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalTransferDetectorTest {

    private InternalTransferDetector detector;

    @Mock
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        detector = new InternalTransferDetector();
    }

    @Nested
    @DisplayName("matchesInternalAccountRule")
    class MatchesInternalAccountRuleTests {

        @Test
        @DisplayName("returns false when recipient is null")
        void returnsfalse_whenRecipientIsNull() {
            Transaction tx = transactionWithRecipient(null);
            List<InternalAccountRule> rules = List.of(ruleWithIdentifier("Nubank"));

            boolean result = detector.matchesInternalAccountRule(tx, rules);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when rules list is empty")
        void returnsFalse_whenRulesListIsEmpty() {
            Transaction tx = transactionWithRecipient("João Silva");

            boolean result = detector.matchesInternalAccountRule(tx, Collections.emptyList());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false when no rule matches the recipient")
        void returnsFalse_whenNoRuleMatchesRecipient() {
            Transaction tx = transactionWithRecipient("Mercado Pago");
            List<InternalAccountRule> rules = List.of(
                ruleWithIdentifier("Nubank"),
                ruleWithIdentifier("Neon")
            );

            boolean result = detector.matchesInternalAccountRule(tx, rules);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true when recipient contains identifier (case-insensitive)")
        void returnsTrue_whenRecipientContainsIdentifier_caseInsensitive() {
            Transaction tx = transactionWithRecipient("Transferência para NUBANK S/A");
            List<InternalAccountRule> rules = List.of(ruleWithIdentifier("nubank"));

            boolean result = detector.matchesInternalAccountRule(tx, rules);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("findAutoMatch")
    class FindAutoMatchTests {

        @Test
        @DisplayName("returns Optional.empty() when no match found")
        void returnsEmpty_whenNoMatchFound() {
            Transaction candidate = transactionWithDateAndAmount(
                OffsetDateTime.now(), BigDecimal.valueOf(100.00));
            when(transactionRepository.findPotentialTransferMatch(
                any(), any(), any(), any())).thenReturn(Collections.emptyList());

            Optional<Transaction> result = detector.findAutoMatch(candidate, transactionRepository);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns first match when multiple exist")
        void returnsFirstMatch_whenMultipleExist() {
            Transaction candidate = transactionWithDateAndAmount(
                OffsetDateTime.now(), BigDecimal.valueOf(200.00));
            Transaction match1 = new Transaction();
            match1.setId(UUID.randomUUID());
            Transaction match2 = new Transaction();
            match2.setId(UUID.randomUUID());

            when(transactionRepository.findPotentialTransferMatch(
                any(), any(), any(), any())).thenReturn(List.of(match1, match2));

            Optional<Transaction> result = detector.findAutoMatch(candidate, transactionRepository);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(match1.getId());
        }

        @Test
        @DisplayName("uses ±1 minute window around candidate date")
        void usesOneMinuteWindowAroundCandidateDate() {
            OffsetDateTime candidateDate = OffsetDateTime.parse("2024-01-15T10:30:00+00:00");
            Transaction candidate = transactionWithDateAndAmount(
                candidateDate, BigDecimal.valueOf(150.00));

            when(transactionRepository.findPotentialTransferMatch(
                any(),
                eq(BigDecimal.valueOf(150.00)),
                eq(candidateDate.minusMinutes(1)),
                eq(candidateDate.plusMinutes(1))
            )).thenReturn(Collections.emptyList());

            detector.findAutoMatch(candidate, transactionRepository);

            verify(transactionRepository).findPotentialTransferMatch(
                any(),
                eq(BigDecimal.valueOf(150.00)),
                eq(candidateDate.minusMinutes(1)),
                eq(candidateDate.plusMinutes(1))
            );
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Transaction transactionWithRecipient(String recipient) {
        Transaction tx = new Transaction();
        tx.setRecipient(recipient);
        return tx;
    }

    private Transaction transactionWithDateAndAmount(OffsetDateTime date, BigDecimal amount) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());

        Transaction tx = new Transaction();
        tx.setDate(date);
        tx.setAmount(amount);
        tx.setAccount(account);
        return tx;
    }

    private InternalAccountRule ruleWithIdentifier(String identifier) {
        InternalAccountRule rule = new InternalAccountRule();
        rule.setIdentifier(identifier);
        return rule;
    }
}
