package com.java2nb.novel.controller.page;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.BookContentService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.NewsService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.recommendation.RecommendationService;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PageControllerGamificationTest {

    @Test
    void giftCodePageRequiresLogin() {
        PageController controller = controller(mock(AuthorService.class), null);

        String view = controller.userGiftCodes(mock(HttpServletRequest.class));

        assertThat(view).isEqualTo(
            "redirect:/user/login.html?originUrl=/user/gift_codes.html");
    }

    @Test
    void readingTicketPageRequiresLogin() {
        PageController controller = controller(mock(AuthorService.class), null);

        String view = controller.userReadingTickets(mock(HttpServletRequest.class));

        assertThat(view).isEqualTo(
            "redirect:/user/login.html?originUrl=/user/reading_tickets.html");
    }

    @Test
    void rewardPageRequiresLoginBeforeCheckingAuthorRole() {
        AuthorService authorService = mock(AuthorService.class);
        PageController controller = controller(authorService, null);

        String view = controller.authorMonthlyRewards(mock(HttpServletRequest.class));

        assertThat(view).isEqualTo("redirect:/user/login.html?originUrl=/author/monthly_rewards.html");
        verifyNoInteractions(authorService);
    }

    @Test
    void rewardPageUsesAuthenticatedAuthorAndReturnsDedicatedTemplate() {
        AuthorService authorService = mock(AuthorService.class);
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(101L);
        when(authorService.isAuthor(101L)).thenReturn(true);
        PageController controller = controller(authorService, user);

        String view = controller.authorMonthlyRewards(mock(HttpServletRequest.class));

        assertThat(view).isEqualTo("author/monthly_rewards");
        verify(authorService).isAuthor(101L);
    }

    private PageController controller(AuthorService authorService, UserDetails user) {
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        when(provider.current()).thenReturn(GamificationConfigSnapshot.bootstrapDisabled());
        return new PageController(
            mock(BookService.class),
            mock(NewsService.class),
            authorService,
            mock(UserService.class),
            mock(RecommendationService.class),
            mock(ChapterCommercialPolicyService.class),
            mock(com.java2nb.novel.service.entitlement.ReadingTicketService.class),
            mock(ThreadPoolExecutor.class),
            Map.<String, BookContentService>of(),
            new VnpayProperties(),
            provider) {
            @Override
            protected UserDetails getUserDetails(HttpServletRequest request) {
                return user;
            }
        };
    }
}
