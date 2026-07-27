package com.java2nb.novel.service.recommendation;

import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.mapper.FrontBookMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {

    @Test
    void memberRecommendationsUseOnlyCurrentUserAndExcludeKnownBooks() {
        FrontBookMapper mapper = mock(FrontBookMapper.class);
        Book personal = book(101L);
        when(mapper.listRecommendations(eq(7L), isNull(), eq((byte) 0), isNull(), eq(true), eq(2)))
            .thenReturn(List.of(personal));

        RecommendationServiceImpl service = new RecommendationServiceImpl(mapper);

        assertThat(service.recommendBooks(7L, null, null, null, 2))
            .extracting(Book::getId)
            .containsExactly(101L);
        verify(mapper).listRecommendations(7L, null, (byte) 0, null, true, 2);
    }

    @Test
    void anonymousRecommendationNeverPassesAccountIdentityToMapper() {
        FrontBookMapper mapper = mock(FrontBookMapper.class);
        when(mapper.listRecommendations(isNull(), eq(3), eq((byte) 0), eq(99L), eq(false), eq(4)))
            .thenReturn(List.of(book(101L)));

        RecommendationServiceImpl service = new RecommendationServiceImpl(mapper);

        service.recommendBooks(null, null, 3, 99L, 4);

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        verify(mapper).listRecommendations(userId.capture(), eq(3), eq((byte) 0), eq(99L), eq(false), eq(4));
        assertThat(userId.getValue()).isNull();
    }

    @Test
    void ageCeilingRequiresVerifiedProfile() {
        User adult = new User();
        adult.setDateOfBirth(dateYearsAgo(25));
        adult.setIsAgeVerified((byte) 1);
        assertThat(RecommendationServiceImpl.resolveMaximumAgeRating(adult)).isEqualTo((byte) 18);

        User unverified = new User();
        unverified.setDateOfBirth(dateYearsAgo(25));
        unverified.setIsAgeVerified((byte) 0);
        assertThat(RecommendationServiceImpl.resolveMaximumAgeRating(unverified)).isZero();
        assertThat(RecommendationServiceImpl.resolveMaximumAgeRating(null)).isZero();
    }

    @Test
    void homeRecommendationUsesBookIdExpectedByExistingThemes() {
        FrontBookMapper mapper = mock(FrontBookMapper.class);
        Book source = book(101L);
        source.setBookName("Tác phẩm thử nghiệm");
        when(mapper.listRecommendations(isNull(), isNull(), eq((byte) 0), isNull(), eq(false), eq(1)))
            .thenReturn(List.of(source));

        RecommendationServiceImpl service = new RecommendationServiceImpl(mapper);

        assertThat(service.recommendHomeBooks(null, null, 1)).singleElement().satisfies(item -> {
            assertThat(item.getBookId()).isEqualTo(101L);
            assertThat(item.getBookName()).isEqualTo("Tác phẩm thử nghiệm");
            assertThat(item.getType()).isEqualTo((byte) 4);
        });
    }

    private Book book(long id) {
        Book book = new Book();
        book.setId(id);
        return book;
    }

    private Date dateYearsAgo(int years) {
        return Date.from(LocalDate.now().minusYears(years).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
