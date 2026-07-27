package com.java2nb.novel.common.dao;

import com.java2nb.novel.common.entity.SysAuditLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysAuditLogDao {

    int insert(SysAuditLogDO auditLog);

    SysAuditLogDO selectById(@Param("id") Long id);

    List<SysAuditLogDO> selectList(Map<String, Object> params);

    int countList(Map<String, Object> params);
}
