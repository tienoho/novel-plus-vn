package com.java2nb.novel.common.dao;

import com.java2nb.novel.common.entity.FinancialVoucherDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FinancialVoucherDao {

    int insert(FinancialVoucherDO voucher);

    FinancialVoucherDO selectById(@Param("id") Long id);

    FinancialVoucherDO selectByVoucherNo(@Param("voucherNo") String voucherNo);

    FinancialVoucherDO selectByReference(@Param("referenceType") String referenceType, @Param("referenceId") String referenceId);

    List<FinancialVoucherDO> selectList(Map<String, Object> params);

    int countList(Map<String, Object> params);

    int updateStatus(@Param("voucherNo") String voucherNo, @Param("status") String status);
}
