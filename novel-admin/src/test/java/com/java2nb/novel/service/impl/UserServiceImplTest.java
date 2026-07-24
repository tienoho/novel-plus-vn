package com.java2nb.novel.service.impl;

import com.java2nb.novel.dao.UserDao;
import com.java2nb.novel.domain.UserDO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @Test
    void newAdminCreatedUserAlwaysStartsWithZeroBalance() {
        UserDao dao = mock(UserDao.class);
        when(dao.save(org.mockito.ArgumentMatchers.any(UserDO.class))).thenReturn(1);
        UserServiceImpl service = new UserServiceImpl(dao);
        UserDO user = new UserDO();
        user.setAccountBalance(99_999L);

        assertThat(service.save(user)).isEqualTo(1);

        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(dao).save(captor.capture());
        assertThat(captor.getValue().getAccountBalance()).isZero();
    }

    @Test
    void adminUserUpdateCannotWriteBalanceProjection() {
        UserDao dao = mock(UserDao.class);
        when(dao.update(org.mockito.ArgumentMatchers.any(UserDO.class))).thenReturn(1);
        UserServiceImpl service = new UserServiceImpl(dao);
        UserDO user = new UserDO();
        user.setId(11L);
        user.setAccountBalance(99_999L);

        assertThat(service.update(user)).isEqualTo(1);

        ArgumentCaptor<UserDO> captor = ArgumentCaptor.forClass(UserDO.class);
        verify(dao).update(captor.capture());
        assertThat(captor.getValue().getAccountBalance()).isNull();
    }
}
