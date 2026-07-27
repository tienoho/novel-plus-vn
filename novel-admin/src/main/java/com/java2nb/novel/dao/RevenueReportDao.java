package com.java2nb.novel.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RevenueReportDao {

    List<Map<String, Object>> selectRecharges(@Param("startDate") String startDate, @Param("endDate") String endDate);

    List<Map<String, Object>> selectPayouts(@Param("startDate") String startDate, @Param("endDate") String endDate);

    List<Map<String, Object>> selectXuConsumptions(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
