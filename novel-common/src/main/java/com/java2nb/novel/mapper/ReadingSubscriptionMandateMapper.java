package com.java2nb.novel.mapper;

import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import java.util.Date;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingSubscriptionMandateMapper {
    int insertPending(@Param("userId") long userId,
                      @Param("merchantReference") String merchantReference);
    ReadingSubscriptionMandateRow selectByMerchantReference(
        @Param("merchantReference") String merchantReference);
    ReadingSubscriptionMandateRow selectByMerchantReferenceForUpdate(
        @Param("merchantReference") String merchantReference);
    ReadingSubscriptionMandateRow selectActiveByUser(@Param("userId") long userId);
    int registerProviderTransaction(@Param("merchantReference") String merchantReference,
                                    @Param("providerRecurringId") String providerRecurringId);
    int activate(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                 @Param("providerTokenCiphertext") String providerTokenCiphertext,
                 @Param("tokenExpireAt") Date tokenExpireAt,
                 @Param("consentedAt") Date consentedAt);
    int fail(@Param("id") long id, @Param("expectedVersion") long expectedVersion);
}
