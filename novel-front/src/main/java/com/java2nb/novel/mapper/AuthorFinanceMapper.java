package com.java2nb.novel.mapper;

import com.java2nb.novel.service.finance.AuthorKycRow;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthorFinanceMapper {

    AuthorKycRow selectKycByAuthorId(@Param("authorId") long authorId);

    int insertKyc(AuthorKycRow row);

    int updateRejectedKyc(AuthorKycRow row);

    int insertKycAudit(@Param("kycProfileId") long kycProfileId, @Param("authorId") long authorId,
                       @Param("userId") long userId, @Param("eventType") String eventType,
                       @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                       @Param("actorType") String actorType, @Param("actorId") Long actorId,
                       @Param("reason") String reason);

    AuthorWithdrawalRow selectWithdrawalByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    int insertWithdrawal(@Param("withdrawalNo") String withdrawalNo,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("authorId") long authorId, @Param("userId") long userId,
                         @Param("kycProfileId") long kycProfileId,
                         @Param("requestedXu") long requestedXu, @Param("vndPerXu") long vndPerXu,
                         @Param("grossAmountVnd") long grossAmountVnd,
                         @Param("bankCode") String bankCode,
                         @Param("bankAccountCiphertext") String bankAccountCiphertext,
                         @Param("bankAccountLast4") String bankAccountLast4,
                         @Param("bankAccountNameCiphertext") String bankAccountNameCiphertext,
                         @Param("holdIdempotencyKey") String holdIdempotencyKey);

    int insertWithdrawalAudit(@Param("withdrawalRequestId") long withdrawalRequestId,
                              @Param("withdrawalNo") String withdrawalNo,
                              @Param("eventType") String eventType,
                              @Param("fromStatus") String fromStatus,
                              @Param("toStatus") String toStatus,
                              @Param("actorType") String actorType,
                              @Param("actorId") Long actorId,
                              @Param("reason") String reason);

    List<AuthorWithdrawalRow> listPendingWithdrawalActions(@Param("limit") int limit);

    AuthorWithdrawalRow selectWithdrawalById(@Param("id") long id);

    int claimWithdrawalAction(@Param("id") long id, @Param("status") String status,
                              @Param("expectedVersion") long expectedVersion);

    int completeWithdrawalAction(@Param("id") long id, @Param("fromStatus") String fromStatus,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("toStatus") String toStatus);
}
