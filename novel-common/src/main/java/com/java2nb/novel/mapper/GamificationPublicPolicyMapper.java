package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GamificationPublicPolicyMapper {
    GamificationPublicPolicyRow selectPublished(@Param("policyVersion") String policyVersion);
}
