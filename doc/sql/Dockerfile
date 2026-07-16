FROM mysql:8.4

COPY --chmod=0444 novel_plus.sql /docker-entrypoint-initdb.d/001-novel-plus.sql
COPY --chmod=0444 20260712_vi_localization.sql /docker-entrypoint-initdb.d/002-vi-localization.sql
COPY --chmod=0444 20260716_vnpay_hardening.sql /docker-entrypoint-initdb.d/003-vnpay-hardening.sql
