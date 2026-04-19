package br.com.phfinance.finance.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.phfinance.finance.domain.Transaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class TransactionSpecificationsTest {

    @Test
    @DisplayName("withFilters returns non-null spec when all params are null")
    void withFilters_allNull_returnsNonNullSpec() {
        Specification<Transaction> spec = TransactionSpecifications.withFilters(
                null, null, null, null, null, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("withFilters returns non-null spec with a search term")
    void withFilters_withSearch_returnsNonNullSpec() {
        Specification<Transaction> spec = TransactionSpecifications.withFilters(
                null, null, null, null, null, "mercado");
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("withFilters returns non-null spec when search is blank")
    void withFilters_blankSearch_returnsNonNullSpec() {
        Specification<Transaction> spec = TransactionSpecifications.withFilters(
                null, null, null, null, null, "  ");
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("withFilters escapes LIKE metacharacters in hasSearch pattern")
    @SuppressWarnings("unchecked")
    void withFilters_searchWithMetacharacters_escapesPattern() {
        // Arrange mocks
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        CriteriaQuery<?> cq = org.mockito.Mockito.mock(CriteriaQuery.class);
        Root<Transaction> root = org.mockito.Mockito.mock(Root.class);

        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Path<String> recipientPath =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Path.class);
        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Path<String> descriptionPath =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Path.class);
        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Expression<String> lowerRecipient =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Expression.class);
        @SuppressWarnings("unchecked")
        jakarta.persistence.criteria.Expression<String> lowerDescription =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Expression.class);
        jakarta.persistence.criteria.Predicate stubPredicate =
                org.mockito.Mockito.mock(jakarta.persistence.criteria.Predicate.class);

        when(root.<String>get("recipient")).thenReturn(recipientPath);
        when(root.<String>get("description")).thenReturn(descriptionPath);
        when(cb.lower(recipientPath)).thenReturn(lowerRecipient);
        when(cb.lower(descriptionPath)).thenReturn(lowerDescription);
        when(cb.isNotNull(org.mockito.ArgumentMatchers.any())).thenReturn(stubPredicate);
        when(cb.like(
                org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<String>>any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyChar()))
            .thenReturn(stubPredicate);
        when(cb.and(org.mockito.ArgumentMatchers.any(jakarta.persistence.criteria.Predicate.class),
                    org.mockito.ArgumentMatchers.any(jakarta.persistence.criteria.Predicate.class)))
            .thenReturn(stubPredicate);
        when(cb.or(org.mockito.ArgumentMatchers.any(jakarta.persistence.criteria.Predicate.class),
                   org.mockito.ArgumentMatchers.any(jakarta.persistence.criteria.Predicate.class)))
            .thenReturn(stubPredicate);

        // Act — search term contains %, _, and backslash
        Specification<Transaction> spec = TransactionSpecifications.withFilters(
                null, null, null, null, null, "50% off_item");
        spec.toPredicate(root, cq, cb);

        // Assert — verify cb.like was called with correctly escaped pattern and escape char '\'
        String expectedPattern = "%50\\% off\\_item%";
        org.mockito.Mockito.verify(cb, org.mockito.Mockito.atLeastOnce())
                .like(
                    org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<String>>any(),
                    org.mockito.ArgumentMatchers.eq(expectedPattern),
                    org.mockito.ArgumentMatchers.eq('\\'));
    }
}
