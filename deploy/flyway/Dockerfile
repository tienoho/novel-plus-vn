FROM flyway/flyway:13.1.0

# Khởi Thư chỉ dùng MySQL. Flyway nạp plugin qua ServiceLoader, vì vậy driver
# tùy chọn phải được loại cùng plugin tương ứng để không làm hỏng CLI.
RUN rm -f \
  /flyway/drivers/databricks-jdbc-*.jar \
  /flyway/drivers/mssql-jdbc-*.jar \
  /flyway/lib/flyway/flyway-database-databricks-*.jar \
  /flyway/lib/flyway/flyway-sqlserver-*.jar \
  /flyway/lib/flyway/flyway-database-nc-couchbase-*.jar \
  && rm -rf /flyway/drivers/couchbase

COPY --chmod=0444 doc/sql/novel_plus.sql /flyway/sql/V0001__baseline.sql
COPY --chmod=0444 doc/sql/20260712_vi_localization.sql /flyway/sql/V2026071201__vi_localization.sql
COPY --chmod=0444 doc/sql/20260716_vnpay_hardening.sql /flyway/sql/V2026071601__vnpay_hardening.sql
COPY --chmod=0444 doc/sql/20260717_wallet_ledger.sql /flyway/sql/V2026071701__wallet_ledger.sql
COPY --chmod=0444 doc/sql/20260718_author_payout.sql /flyway/sql/V2026071801__author_payout.sql
COPY --chmod=0444 doc/sql/20260720_refund_reconciliation_vietqr.sql /flyway/sql/V2026072001__refund_reconciliation_vietqr.sql
COPY --chmod=0444 doc/sql/20260725_moderation_copyright.sql /flyway/sql/V2026072501__moderation_copyright.sql
COPY --chmod=0444 doc/sql/20260725_reports_security.sql /flyway/sql/V2026072502__reports_security.sql
COPY --chmod=0444 doc/sql/20260726_author_editor.sql /flyway/sql/V2026072601__author_editor.sql
COPY --chmod=0444 doc/sql/20260726_simhash_storage.sql /flyway/sql/V2026072602__simhash_storage.sql
COPY --chmod=0444 doc/sql/20260726_cover_moderation.sql /flyway/sql/V2026072603__cover_moderation.sql
COPY --chmod=0444 doc/sql/20260726_author_analytics.sql /flyway/sql/V2026072604__author_analytics.sql
COPY --chmod=0444 doc/sql/20260726_vietnamese_search.sql /flyway/sql/V2026072605__vietnamese_search.sql
COPY --chmod=0444 doc/sql/20260726_chapter_notifications.sql /flyway/sql/V2026072606__chapter_notifications.sql
COPY --chmod=0444 doc/sql/20260727_recommendation.sql /flyway/sql/V2026072701__recommendation.sql
COPY --chmod=0444 doc/sql/20260727_author_story_bible.sql /flyway/sql/V2026072702__author_story_bible.sql
COPY --chmod=0444 doc/sql/20260727_author_collaboration.sql /flyway/sql/V2026072703__author_collaboration.sql
COPY --chmod=0444 doc/sql/20260727_reader_annotations.sql /flyway/sql/V2026072704__reader_annotations.sql
COPY --chmod=0444 doc/sql/20260728_author_ai.sql /flyway/sql/V2026072801__author_ai.sql
COPY --chmod=0444 doc/sql/20260728_chapter_commercial_policy.sql /flyway/sql/V2026072802__chapter_commercial_policy.sql
COPY --chmod=0444 doc/sql/20260729_gamification_monthly_ticket.sql /flyway/sql/V2026072901__gamification_monthly_ticket.sql
COPY --chmod=0444 doc/sql/20260730_reader_entitlements.sql /flyway/sql/V2026073001__reader_entitlements.sql
COPY --chmod=0444 doc/sql/20260731_reader_subscriptions.sql /flyway/sql/V2026073101__reader_subscriptions.sql
COPY --chmod=0444 doc/sql/20260801_reader_subscription_admin.sql /flyway/sql/V2026080101__reader_subscription_admin.sql
COPY --chmod=0444 doc/sql/20260802_gift_codes.sql /flyway/sql/V2026080201__gift_codes.sql
COPY --chmod=0444 doc/sql/20260803_gift_code_revoke.sql /flyway/sql/V2026080301__gift_code_revoke.sql
COPY --chmod=0444 doc/sql/20260804_reader_subscription_checkout.sql /flyway/sql/V2026080401__reader_subscription_checkout.sql
COPY --chmod=0444 doc/sql/20260805_reader_subscription_paid_review.sql /flyway/sql/V2026080501__reader_subscription_paid_review.sql
COPY --chmod=0444 doc/sql/20260806_gift_code_hmac_rotation.sql /flyway/sql/V2026080601__gift_code_hmac_rotation.sql
COPY --chmod=0444 doc/sql/20260807_vi_friend_link.sql /flyway/sql/V2026080701__vi_friend_link.sql
COPY --chmod=0444 doc/sql/20260808_security_passwords.sql /flyway/sql/V2026080801__security_passwords.sql
COPY --chmod=0444 doc/sql/20260809_reading_subscription_recurring.sql /flyway/sql/V2026080901__reading_subscription_recurring.sql
COPY --chmod=0444 doc/sql/20260810_gamification_special_seasons.sql /flyway/sql/V2026081001__gamification_special_seasons.sql
COPY --chmod=0444 doc/sql/20260811_gamification_level_rewards.sql /flyway/sql/V2026081101__gamification_level_rewards.sql
COPY --chmod=0444 doc/sql/20260812_gamification_abuse_review.sql /flyway/sql/V2026081201__gamification_abuse_review.sql
COPY --chmod=0444 doc/sql/20260813_gamification_public_policy.sql /flyway/sql/V2026081301__gamification_public_policy.sql
COPY --chmod=0444 doc/sql/20260814_reading_subscription_mandate_revocation.sql /flyway/sql/V2026081401__reading_subscription_mandate_revocation.sql
COPY --chmod=0444 doc/sql/20260815_reading_subscription_mandate_admin.sql /flyway/sql/V2026081501__reading_subscription_mandate_admin.sql
COPY --chmod=0444 doc/sql/20260816_reading_subscription_mandate_idempotency.sql /flyway/sql/V2026081601__reading_subscription_mandate_idempotency.sql
COPY --chmod=0444 doc/sql/20260817_author_payout_four_eyes.sql /flyway/sql/V2026081701__author_payout_four_eyes.sql
COPY --chmod=0444 doc/sql/20260818_gamification_runtime_config.sql /flyway/sql/V2026081801__gamification_runtime_config.sql
COPY --chmod=0444 doc/sql/20260819_gamification_dynamic_config_p1_hardening.sql /flyway/sql/V2026081901__gamification_dynamic_config_p1_hardening.sql

COPY --chmod=0555 deploy/flyway/entrypoint.sh /usr/local/bin/novel-flyway

ENTRYPOINT ["/usr/local/bin/novel-flyway"]
