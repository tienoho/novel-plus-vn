package com.java2nb.novel.common.dao;

import com.java2nb.novel.common.entity.User2faDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface User2faDao {

    int insert(User2faDO user2fa);

    User2faDO selectByUserId(@Param("userId") Long userId);

    int updateStatusAndBackupCodes(User2faDO user2fa);

    int deleteByUserId(@Param("userId") Long userId);
}
