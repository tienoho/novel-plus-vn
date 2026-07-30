package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.config.AuthorIncomeProperties;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.mapper.FrontUserBookshelfMapper;
import com.java2nb.novel.mapper.FrontUserMapper;
import com.java2nb.novel.mapper.FrontUserReadHistoryMapper;
import com.java2nb.novel.mapper.UserBuyRecordMapper;
import com.java2nb.novel.mapper.UserFeedbackMapper;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAgeVerificationServiceTest {

    @Test
    void userInfoLoadsAgeVerificationFieldsUsedByAccessControl() {
        FrontUserMapper userMapper = mock(FrontUserMapper.class);
        User expected = new User();
        expected.setDateOfBirth(new Date());
        expected.setIsAgeVerified((byte) 1);
        when(userMapper.selectMany(any(SelectStatementProvider.class))).thenReturn(List.of(expected));

        UserServiceImpl service = createService(userMapper);

        assertThat(service.userInfo(42L)).isSameAs(expected);
        ArgumentCaptor<SelectStatementProvider> statement = ArgumentCaptor.forClass(SelectStatementProvider.class);
        verify(userMapper).selectMany(statement.capture());
        assertThat(statement.getValue().getSelectStatement())
            .contains("date_of_birth", "is_age_verified");
    }

    @Test
    void profileUpdateCannotSelfVerifyAgeAndBirthDateChangeRevokesVerification() {
        FrontUserMapper userMapper = mock(FrontUserMapper.class);
        UserServiceImpl service = createService(userMapper);
        User update = new User();
        update.setDateOfBirth(new Date());
        update.setIsAgeVerified((byte) 1);

        service.updateUserInfo(42L, update);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateByPrimaryKeySelective(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(42L);
        assertThat(saved.getValue().getIsAgeVerified()).isZero();
    }

    @Test
    void unrelatedProfileUpdateDoesNotResetExistingAgeVerification() {
        FrontUserMapper userMapper = mock(FrontUserMapper.class);
        UserServiceImpl service = createService(userMapper);
        User update = new User();
        update.setNickName("Độc giả");
        update.setIsAgeVerified((byte) 1);

        service.updateUserInfo(42L, update);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateByPrimaryKeySelective(saved.capture());
        assertThat(saved.getValue().getIsAgeVerified()).isNull();
    }

    private UserServiceImpl createService(FrontUserMapper userMapper) {
        return new UserServiceImpl(
            userMapper,
            mock(FrontUserBookshelfMapper.class),
            mock(FrontUserReadHistoryMapper.class),
            mock(UserFeedbackMapper.class),
            mock(UserBuyRecordMapper.class),
            mock(WalletLedgerService.class),
            mock(AuthorIncomeProperties.class),
            mock(BookIndexMapper.class),
            mock(ChapterCommercialPolicyService.class),
            mock(com.java2nb.novel.service.entitlement.ReadingTicketService.class),
            mock(com.java2nb.novel.service.gamification.GamificationEventService.class)
        );
    }
}
