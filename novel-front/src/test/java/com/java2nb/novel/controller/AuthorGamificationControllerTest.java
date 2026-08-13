package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.novel.service.gamification.AuthorRewardAllocationRow;
import com.java2nb.novel.service.gamification.AuthorRewardService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorGamificationControllerTest {

    @Test
    void derivesAuthorFromAuthenticatedUserInsteadOfRequestParameters() {
        AuthorRewardService rewardService = mock(AuthorRewardService.class);
        AuthorService authorService = mock(AuthorService.class);
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(101L);
        Author author = new Author();
        author.setId(202L);
        when(authorService.queryAuthor(101L)).thenReturn(author);
        AuthorRewardAllocationRow allocation = new AuthorRewardAllocationRow();
        allocation.setAllocationNo("2026-08:301:1:202");
        allocation.setSeasonId(71L);
        allocation.setPeriodCode("2026-08");
        allocation.setBookId(301L);
        allocation.setBookName("Mùa Hạ Cuối");
        allocation.setRankNo(1);
        allocation.setAmountXu(500L);
        allocation.setStatus("POSTED_PENDING");
        when(rewardService.listAuthorRewards(202L, 100)).thenReturn(List.of(allocation));
        AuthorGamificationController controller = new AuthorGamificationController(
            rewardService, authorService) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return user;
            }
        };

        var result = controller.listRewards(new MockHttpServletRequest());

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).amountXu()).isEqualTo(500L);
        assertThat(result.getData().get(0).periodCode()).isEqualTo("2026-08");
        assertThat(result.getData().get(0).bookName()).isEqualTo("Mùa Hạ Cuối");
        verify(authorService).queryAuthor(101L);
        verify(rewardService).listAuthorRewards(202L, 100);
    }
}
