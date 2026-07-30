# Kế hoạch triển khai All-in-One Gamified Universe

## Mục tiêu

Xây dựng hệ sinh thái gamification an toàn cho Novel Plus, kết nối hoạt động đọc truyện, nhiệm vụ, EXP, cảnh giới, Ngọn Đuốc, bảng xếp hạng tháng và quỹ thưởng tác giả mà không làm sai lệch sổ cái Xu, không trả thưởng trùng và có thể vận hành, kiểm toán, rollback trên production.

## Phạm vi

Bao gồm:

- Hồ sơ gamification, EXP, cấp độ, cảnh giới và khung ảnh đại diện.
- Điểm danh chuỗi, nhiệm vụ hằng ngày và nhận thưởng.
- Cấp, tiêu, hết hạn, thu hồi và đối soát Ngọn Đuốc.
- Bỏ phiếu cho truyện, bảng xếp hạng tháng và quỹ thưởng tác giả.
- API độc giả/tác giả, công cụ quản trị, chống gian lận và giám sát vận hành.
- Giao diện desktop/mobile trên `green`, `orange`, `dark`, `blue`.
- Migration, feature flag, rollout, rollback và kiểm thử toàn hệ thống.

Không bao gồm trong đợt đầu:

- Vé đọc mở chương, thuê bao và mã quà tặng; đây là các quyền lợi độc lập.
- Thị trường trao đổi hoặc chuyển nhượng Ngọn Đuốc.
- Quyền lợi kinh tế khác nhau giữa các hệ cảnh giới.
- Tự động chuyển tiền VND cho tác giả nếu quy trình KYC/payout chưa được bật.

## Trạng thái triển khai xác minh ngày 2026-07-30

- Đã hoàn tất và qua unit/MySQL concurrency: ledger Ngọn Đuốc, lot FIFO, vote, expiry, ranking tháng,
  snapshot/state machine, quỹ thưởng tác giả qua clearing, admin grant/reward và lịch sử thưởng tác giả.
- Đã nối ba nguồn event `CHAPTER_PURCHASED`, `TOP_UP_SETTLED`, `COMMENT_APPROVED` với source key
  idempotent; event mua chương chỉ phát sau bút toán `POSTED`, bình luận chỉ phát khi thật sự chuyển
  sang đã duyệt.
- Đã có profile lazy-create, ngưỡng level kế tiếp, đổi cảnh giới theo optimistic version, cooldown,
  level tối thiểu và audit.
- Đã có worker event theo `REQUIRES_NEW`, claim bằng version, retry/`FAILED`, trạng thái
  `PROCESSED`/`SKIPPED`, và projection quest cho `DAILY`/ISO `WEEKLY`/`ONE_TIME`. Acceptance MySQL
  chứng minh cùng event không tăng tiến độ lần hai.
- Đã có API danh sách/claim quest, sổ EXP bất biến, cập nhật profile/level, cấp Đuốc từ reward quest
  qua lot/ledger hiện hữu và event `LEVEL_REACHED`. Claim retry trả dữ liệu cũ, không phát thưởng lặp.
- Đã có trang profile/quest/claim desktop và mobile bằng asset dùng chung; bốn build `green`,
  `orange`, `dark`, `blue` đều đóng gói đủ file và liên kết tài khoản không bị overlay làm mất.
- Đã có `POST /user/gamification/check-in`, streak theo `Asia/Ho_Chi_Minh`, khóa server
  `CHECKIN:<userId>:<localDate>`, xử lý event và auto-claim `DAILY_CHECK_IN`. Unit test bao phủ ngày
  đầu/nối chuỗi/đứt chuỗi/replay/ngày lùi; acceptance MySQL chứng minh retry không nhân event,
  progress, claim hoặc EXP. UI điểm danh dùng chung đã có trên desktop/mobile.
- Đã có `POST /user/gamification/reading-heartbeat` xác thực riêng, session/sequence idempotent, receipt
  bất biến, bộ đếm trần 180 phút/ngày và event `READING_MINUTE_VERIFIED`. Client chỉ lấy mẫu khi tab
  hiển thị/có focus, giữ nguyên payload khi retry và chạy trên runtime cùng bốn theme; backend lấy
  elapsed time server làm trần thay vì tin tuyệt đối `activeSeconds` từ client.
- Đã có widget Thắp Đuốc trên chi tiết truyện và Bảng Đuốc tháng trên trang ranking, dùng chung cho
  desktop/mobile và bốn theme. UI hiển thị tổng Đuốc, số dư, lot sắp tắt, trần mỗi request từ server,
  xác nhận khi thắp nhiều Đuốc và giữ nguyên request ID khi chưa xác định kết quả do mất mạng.
- Đã có Top Đuốc trang chủ trên desktop/mobile và bốn theme: Top 5 kỳ hiện tại, trạng thái
  realtime/snapshot, mốc khóa sổ, đồng hồ còn lại và liên kết sang bảng đầy đủ. Widget giữ ẩn khi
  feature flag tắt.
- Đã có chọn campaign quest `ACTIVE` theo cửa sổ `[start_at, end_at)` bằng thời điểm server. Reward
  campaign ghi đè theo từng loại và fallback về `DEFAULT`; campaign chồng lấn làm request fail-closed,
  không phát thưởng mơ hồ. Unit test và MySQL IT bao phủ biên thời gian, fallback và policy snapshot.
- Đã có CRUD admin cho campaign/reward nhiệm vụ với quyền `novel:gamification:config`: tạo DRAFT,
  thay reward EXP/Đuốc, kích hoạt, đóng và tra cứu. Kích hoạt dùng transaction `SERIALIZABLE`;
  concurrency IT chứng minh hai campaign giao nhau chỉ có đúng một campaign trở thành `ACTIVE`.
- Chưa hoàn tất: consumer cấp Đuốc theo level, nội dung quy tắc/kết quả thưởng trên trang chủ và
  browser regression trên thiết bị thật. Đây là phần M6/M7 tiếp theo; các feature flag liên quan vẫn
  mặc định tắt.
- `/book/analytics/read-event` tiếp tục chỉ phục vụ analytics và không làm nguồn thưởng: API này ẩn
  danh, nhận `visitorId`/`durationSeconds` do client tự khai và không có receipt bất biến.

## Thuật ngữ bắt buộc

| Thuật ngữ | Ý nghĩa |
|---|---|
| Xu | Tiền nội bộ dùng mua chương và ghi trong sổ cái tài chính hiện có |
| Ngọn Đuốc | Quyền bỏ phiếu cho tác phẩm, không chuyển nhượng và không đổi ngược thành Xu/tiền |
| Vé đọc | Quyền mở nội dung; không dùng chung wallet hoặc ledger với Ngọn Đuốc |
| EXP | Điểm tiến trình gamification, không phải tài sản tài chính |
| Cảnh giới | Danh xưng/skin hiển thị theo cấp độ; mặc định không ảnh hưởng quyền lợi |
| Kỳ xếp hạng | Khoảng thời gian `[đầu tháng, đầu tháng kế tiếp)` theo `Asia/Ho_Chi_Minh` |

### Quy ước đặt tên Ngọn Đuốc

"Ngọn Đuốc" là **tên hiển thị**. Định danh kỹ thuật giữ nguyên tiếng Anh `monthly_ticket` theo quy ước sẵn có của repository, nên việc đổi tên hiển thị về sau chỉ là sửa file `.properties`, không phải migration.

| Ngữ cảnh | Giá trị bắt buộc |
|---|---|
| Danh từ đầy đủ | Ngọn Đuốc |
| Danh từ ngắn, dùng cho tiêu đề cột và tab | Đuốc |
| Động từ bỏ phiếu trên UI | thắp đuốc — "Thắp đuốc cho truyện" |
| Động từ hết hạn trên UI | tắt — "2 ngọn đuốc sẽ tắt ngày 31/07" |
| Động từ nhận thưởng trên UI | nhận — "Bạn nhận được 5 ngọn đuốc" |
| Bảng, cột, class, enum, khóa i18n | `monthly_ticket_*`, `MonthlyTicket*`, `monthlyTicket.*` |
| Business type trong sổ cái Xu | `MONTHLY_AUTHOR_REWARD` (không đổi) |

Trong tài liệu kỹ thuật này, "bỏ phiếu" và "vote" vẫn được dùng làm thuật ngữ trung tính mô tả cơ chế; "thắp đuốc" chỉ dùng cho chuỗi hiển thị tới người dùng. Không hard-code cả hai dạng trong code — mọi chuỗi đi qua i18n.

## Chính sách khuyến nghị cho phiên bản đầu

- Ngọn Đuốc được cấp từ nhiệm vụ, điểm danh, thăng cấp, khuyến mại đã cấu hình hoặc thao tác cấp thủ công của quản trị viên có audit; không bán trực tiếp trước khi có chính sách kinh tế được phê duyệt.
- Không cho tác giả hoặc cộng tác viên bỏ phiếu cho tác phẩm họ quản lý.
- Chỉ truyện đã duyệt, đang xuất bản và đạt điều kiện chiến dịch mới nhận phiếu; truyện nhập từ `novel-crawl` mặc định không đủ điều kiện.
- Một tác giả chỉ nhận giải cao nhất nếu có nhiều truyện trong nhóm được thưởng; thứ hạng tác phẩm vẫn được giữ nguyên.
- Tie-break lần lượt theo tổng phiếu, số người bỏ phiếu duy nhất, thời điểm đạt số phiếu cuối và `book_id`.
- Quỹ thưởng được phát bằng Xu qua tài khoản hệ thống `REWARD_CLEARING`, chỉ chuyển vào `AUTHOR_REVENUE_XU` sau cửa sổ khiếu nại; rút VND đi qua KYC và payout hiện có.
- Đổi cảnh giới tối đa một lần mỗi ngày; cảnh giới không thay đổi reward hoặc permission.
- Mọi điều chỉnh sai được thực hiện bằng sự kiện đảo, không sửa hoặc xóa lịch sử.

## Kiến trúc triển khai bám sát repository

Giữ kiến trúc modular monolith hiện tại, không tách microservice hoặc bổ sung message broker trong phiên bản đầu:

| Module | Trách nhiệm | Không được làm |
|---|---|---|
| `novel-common` | Domain dùng chung, mapper MyBatis, transaction cấp/tiêu phiếu, quest/EXP, ranking, reward và ledger Xu | Không chứa controller hoặc giao diện |
| `novel-front` | API độc giả/tác giả, thu nhận event, heartbeat đọc, page route và scheduler chốt kỳ | Không cập nhật trực tiếp bảng ledger/ranking |
| `novel-admin` | Cấu hình campaign, fraud review, finalize, approve reward và reconciliation | Không sao chép luật nghiệp vụ từ `novel-common` |
| `novel-crawl` | Không thay đổi trong phạm vi này | Không phát Ngọn Đuốc từ dữ liệu crawl; truyện crawl cũng không được nhận phiếu hoặc nhận thưởng khi chưa có quyết định chính sách ngược lại |

Sự kiện nghiệp vụ được ghi vào `gamification_event` trong cùng MySQL transaction với nghiệp vụ nguồn và được xử lý đồng bộ/idempotent. Chỉ cân nhắc queue sau khi đo được nhu cầu scale; không đưa Kafka/RabbitMQ vào P0.

### Bản đồ database và migration

| Thao tác | File | Nội dung | Xác minh |
|---|---|---|---|
| Thêm | `doc/sql/20260729_gamification_monthly_ticket.sql` | Tạo toàn bộ bảng, constraint, index, trigger bất biến, seed rule/campaign và `sys_menu` admin | Chạy trên DB mới, DB hiện hữu và chạy lại |
| Sửa | `compose.yaml` | Mount migration mới và thêm vào vòng lặp của service `migrate` sau migration `20260728` | `docker compose config` và smoke DB |
| Sửa | `doc/sql/readme.md` | Ghi thứ tự chạy, rollback bằng feature flag và truy vấn reconciliation | Đối chiếu với `compose.yaml` |
| Giữ nguyên | `doc/sql/Dockerfile` | Các migration mới sau `20260718` đang đi qua service `migrate`; chỉ sửa Dockerfile nếu thay đổi chính sách base image | Build compose vẫn chạy đúng chuỗi migration |

Migration mới phải tạo tối thiểu các nhóm bảng:

- Ticket: `monthly_ticket_account`, `monthly_ticket_lot`, `monthly_ticket_ledger`, `monthly_ticket_lot_allocation`, `monthly_ticket_vote`.
- Quest/EXP: `gamification_event`, `gamification_profile`, `user_exp_ledger`, `level_rule`, `realm_catalog`, `quest_definition`, `quest_campaign`, `quest_reward`, `user_quest_progress`, `quest_claim`.
- Heartbeat đọc: `reading_session`, `reading_daily_counter`, `reading_heartbeat_receipt`.
- Season/ranking/reward: `monthly_ticket_season`, `monthly_rank_snapshot`, `monthly_rank_entry`, `scheduled_job_run`, `reward_fund_campaign`, `author_reward_allocation`.

Ngoài bảng mới, migration còn phải seed một dòng `wallet_account` cho tài khoản hệ thống `REWARD_CLEARING` bằng `INSERT IGNORE`, theo đúng cách `20260717_wallet_ledger.sql` seed `SYSTEM_ISSUANCE`, `PLATFORM_REVENUE` và `PAYOUT_CLEARING`.

Các quy ước bắt buộc kế thừa từ migration hiện có, phải tái sử dụng chứ không phát minh lại:

- Trigger `BEFORE UPDATE`/`BEFORE DELETE` phát `SIGNAL SQLSTATE '45000'` cho mọi bảng ledger bất biến, như `trg_ledger_transaction_no_update`.
- Mọi bước dữ liệu chỉ được chạy một lần phải đi qua `platform_migration_history` với biến `@run_…`, để chạy lại migration là an toàn.
- Mọi `ALTER TABLE` kiểm tra `information_schema` trước khi thực thi.
- Không viết down-migration; rollback đi bằng feature flag.

### Backend dùng chung — `novel-common`

Các file/lớp đã thêm và đang là điểm mở rộng:

```text
novel-common/src/main/java/com/java2nb/novel/service/gamification/
    GamificationProgressService.java
    MonthlyTicketService.java
    MonthlyRankingService.java
    GamificationEventInput.java
    TicketPostResult.java
    TicketAccountRow.java
    TicketLotRow.java
    MonthlySeasonRow.java
    MonthlyRankRow.java

novel-common/src/main/java/com/java2nb/novel/service/impl/
    GamificationProgressServiceImpl.java
    MonthlyTicketServiceImpl.java
    MonthlyRankingServiceImpl.java

novel-common/src/main/java/com/java2nb/novel/mapper/
    GamificationProgressMapper.java
    MonthlyTicketMapper.java
    MonthlyRankingMapper.java

novel-common/src/main/resources/mybatis/mapping/
    GamificationProgressMapper.xml
    MonthlyTicketMapper.xml
    MonthlyRankingMapper.xml
```

XML mapper phải nằm **trực tiếp** trong `mybatis/mapping/`, không đặt trong thư mục con: `MyBatisMapperPackagingTest` chặn mọi `*Mapper.xml` nằm lạc ra ngoài thư mục đó. Test này **không** assert cứng danh sách file hiện có, nên thêm mapper mới không làm đỏ build; vẫn nên chủ động thêm assertion cho ba XML mới để giữ nguyên tác dụng bảo vệ.

Các file cần sửa:

| File | Thay đổi bắt buộc | Lý do |
|---|---|---|
| `novel-common/.../service/wallet/WalletLedgerService.java` | Thêm `creditAuthorRewardPending(...)` và `releaseAuthorReward(...)` | Interface hiện chưa có nghiệp vụ cấp thưởng vào ví tác giả; thưởng phải qua clearing trước khi rút được |
| `novel-common/.../service/impl/WalletLedgerServiceImpl.java` | Ghi `MONTHLY_AUTHOR_REWARD` từ `SYSTEM_ISSUANCE` sang `REWARD_CLEARING`, sau cửa sổ khiếu nại mới chuyển sang `AUTHOR_REVENUE_XU` | Không dùng `creditReaderReward()`, không update balance trực tiếp và không để thưởng rút được ngay khi vừa chốt |
| `novel-common/.../mapper/WalletLedgerMapper.java` và XML tương ứng | Không sửa | `ensureWallets()` đã tự tạo và đọc ví `AUTHOR/AUTHOR_REVENUE_XU`; chỉ cần seed thêm account hệ thống `REWARD_CLEARING` trong migration |
| `novel-common/src/main/resources/i18n/common/messages_vi_VN.properties` | Thêm lỗi domain gamification bằng tiếng Việt | Service dùng chung không hard-code message runtime |
| `novel-common/src/main/resources/i18n/common/messages_zh_CN.properties` | Thêm key parity | Duy trì fallback nội bộ |

Transaction boundary bắt buộc nằm trong service `novel-common`; controller front/admin không được tự ghép nhiều mapper để cấp/tiêu/reward.

### Backend sản phẩm — `novel-front`

Các file/lớp cần thêm:

```text
novel-front/src/main/java/com/java2nb/novel/controller/
    GamificationController.java
    MonthlyTicketController.java
    AuthorGamificationController.java
    ReadingHeartbeatController.java

novel-front/src/main/java/com/java2nb/novel/dto/gamification/
    RealmUpdateRequest.java
    QuestClaimRequest.java
    MonthlyTicketVoteRequest.java
    ReadingHeartbeatRequest.java
    ReadingHeartbeatResponse.java
    GamificationProfileResponse.java
    MonthlyTicketBalanceResponse.java
    MonthlyTicketRankingResponse.java

novel-front/src/main/java/com/java2nb/novel/service/gamification/
    GamificationEventService.java
    GamificationEventServiceImpl.java
    GamificationReadingHeartbeatService.java
    ReadingHeartbeatWriter.java
    ReadingHeartbeatCommand.java
    ReadingHeartbeatInput.java
    ReadingHeartbeatResult.java
    ReadingSessionRow.java
    ReadingDailyCounterRow.java
    ReadingHeartbeatReceiptRow.java

novel-front/src/main/java/com/java2nb/novel/mapper/
    ReadingHeartbeatMapper.java

novel-front/src/main/resources/mybatis/mapping/
    ReadingHeartbeatMapper.xml

novel-front/src/main/java/com/java2nb/novel/core/config/
    GamificationProperties.java

novel-front/src/main/java/com/java2nb/novel/core/schedule/
    MonthlyTicketSeasonSchedule.java
    MonthlyTicketExpirySchedule.java
    AuthorRewardReleaseSchedule.java
```

`MonthlyTicketExpirySchedule` đóng các lot quá hạn và ghi ledger `EXPIRED`; không có job này thì bất biến “tổng hết hạn” ở mục 2 không bao giờ được thực thi. `AuthorRewardReleaseSchedule` chuyển thưởng từ `REWARD_CLEARING` sang ví tác giả sau cửa sổ khiếu nại. Cả hai job dùng chung cơ chế claim trong `scheduled_job_run` như scheduler chốt kỳ.

Các file cần sửa:

| File | Điểm sửa | Mục đích |
|---|---|---|
| `controller/page/PageController.java` | Thêm route `user/quests.html`; nạp metadata kỳ/ranking vào trang chủ, chi tiết truyện và trang ranking | Render trang và SEO từ nguồn server |
| `controller/page/PageController.java` | Thêm route riêng cho `author/monthly_rewards.html` | Route generic `module2` đã chặn khách chưa đăng nhập và người chưa là tác giả, nhưng không áp `ThreadLocalUtil.getTemplateDir()` nên không render bản mobile và không nạp được model |
| `service/impl/OrderServiceImpl.java` | Sau `creditReaderTopUp()` thành công, ghi event nguồn nếu chính sách nạp Xu có cấp phiếu | Dùng source key theo `outTradeNo`, không cấp ở webhook/controller |
| `service/impl/UserServiceImpl.java` | Chỉ ghi event mua chương khi `purchaseChapter()` trả `POSTED`; bỏ qua ở các nhánh return sớm và khi trả `ALREADY_POSTED` | Quest mua/đọc trả phí không bị phát trước khi ledger Xu commit và không phát lại khi mua lặp |
| `controller/BookController.java` | Giữ endpoint analytics hiện tại; chỉ dùng cho gamification nếu đã chứng minh payload/heartbeat đủ tin cậy | Không mặc định tin event phía client |
| `application.yml` | Thêm kill switch, timezone, batch size, close cron, cửa sổ khiếu nại trước khi release thưởng, hạn dùng lot và anti-abuse limit theo khối `novel.gamification` | Mặc định reward/voting tắt, đồng nhất với cách `author.payout.enabled` mặc định tắt |
| `.env.example` | Tài liệu hóa biến môi trường mới | Deploy không phụ thuộc giá trị ngầm |
| `i18n/messages_vi_VN.properties` và `messages_zh_CN.properties` | Thêm key API/UI/JS với parity | Không hard-code text |

`MonthlyTicketSeasonSchedule` không tự chứa luật xếp hạng hoặc reward; scheduler chỉ claim phase rồi gọi `MonthlyRankingService`.

### Backend vận hành — `novel-admin`

Các file/lớp cần thêm:

```text
novel-admin/src/main/java/com/java2nb/novel/controller/
    GamificationAdminController.java

novel-admin/src/main/java/com/java2nb/novel/service/
    GamificationAdminService.java
    CommentModerationService.java

novel-admin/src/main/java/com/java2nb/novel/service/impl/
    GamificationAdminServiceImpl.java
    CommentModerationServiceImpl.java

novel-admin/src/main/java/com/java2nb/novel/dao/
    GamificationAdminDao.java

novel-admin/src/main/resources/templates/novel/gamification/
    gamification.html

novel-admin/src/main/resources/static/js/appjs/novel/gamification/
    gamification.js
```

Các file cần sửa:

| File | Điểm sửa | Mục đích |
|---|---|---|
| `controller/CommentModerationController.java` | Chuyển update audit trực tiếp sang `CommentModerationService`; chỉ phát event khi trạng thái thực sự chuyển sang đã duyệt | Quest bình luận không được cấp trước duyệt hoặc cấp lặp |
| `service/CommentModerationService.java` và implementation | Thực hiện conditional state transition, ghi `COMMENT_APPROVED` và gọi service event dùng chung trong transaction | Controller không điều phối transaction; retry không cấp thưởng lần hai |
| `i18n/messages_vi_VN.properties` và `messages_zh_CN.properties` | Thêm text quản trị với parity | Admin không hard-code chuỗi |
| Migration `sys_menu` trong file SQL mới | Thêm menu và permission tách biệt | Phân quyền theo nguyên tắc tối thiểu |

Permission admin bắt buộc:

```text
novel:gamification:view
novel:gamification:config
novel:gamification:review
novel:gamification:grant
novel:gamification:finalize
novel:gamification:reward
novel:gamification:adjust
```

`novel:gamification:grant` cấp Ngọn Đuốc thủ công hoặc theo khuyến mại. Đây là **hạng mục P0**, vì mọi nguồn cấp còn lại đều thuộc P1 và nếu thiếu nó thì hệ thống bỏ phiếu ra mắt mà không ai có phiếu. Thao tác cấp bắt buộc có lý do, audit và khóa idempotency dạng `GRANT:<batchId>:<userId>`; quyền cấp tách khỏi quyền duyệt thưởng để giữ nguyên tắc bốn mắt.

`CommentModerationController` hiện dùng permission `novel:bookComment:edit` và update `auditStatus` vô điều kiện cho cả lô. Khi tách sang service, giữ nguyên permission cũ cho thao tác kiểm duyệt và chỉ thêm permission gamification cho các màn hình mới; không mở rộng phạm vi quyền của kiểm duyệt viên.

### Frontend runtime — desktop và mobile

Các file cần thêm:

```text
novel-front/src/main/resources/templates/user/quests.html
novel-front/src/main/resources/templates/mobile/user/quests.html
novel-front/src/main/resources/templates/author/monthly_rewards.html
novel-front/src/main/resources/static/javascript/gamification.js
novel-front/src/main/resources/static/javascript/monthly-ticket.js
novel-front/src/main/resources/static/css/gamification.css
```

Các file cần sửa:

Đường dẫn dưới đây là bản base trong `novel-front/src/main/resources/`. Mỗi dòng có cột theme phải sửa song song, vì file base bị theme ghi đè lúc đóng gói.

| File | Thành phần UI | Theme phải sửa song song |
|---|---|---|
| `templates/common/header.html` | Badge level/realm và liên kết Trung tâm nhiệm vụ | `green`, `orange`, `dark`, `blue` |
| `templates/index.html` | Bảng Đuốc của kỳ hiện tại, trạng thái kỳ và thời gian còn lại | `green`, `orange`, `dark`, `blue` |
| `templates/mobile/index.html` | Khối ranking tối giản cho mobile | `green`, `orange`, `dark` |
| `templates/book/book_detail.html` | Widget số dư, tổng Đuốc, hạn dùng và hộp xác nhận khi thắp nhiều Đuốc | `green`, `orange`, `dark` |
| `templates/mobile/book/book_detail.html` | Widget/hộp xác nhận touch-friendly tương ứng | `green`, `orange`, `dark` |
| `templates/book/book_ranking.html` | Thêm tab Đuốc và kỳ lịch sử | `green`, `orange`, `dark` |
| `templates/mobile/book/book_ranking.html` | Ranking responsive tương ứng | `green`, `orange`, `dark` |
| `templates/author/author_income.html` | Liên kết thưởng tháng; không gộp reward dự kiến vào số dư rút được | `green`, `orange` |

Không nhồi logic mới vào `user.js` hoặc `bookdetail.js`. Ngoài lý do tách bạch trách nhiệm, đây còn là ràng buộc đóng gói: `bookdetail.js` bị `green` và `orange` ghi đè, `base.css` bị cả bốn theme ghi đè, nên mọi thay đổi trong hai file đó phải nhân bản sang từng theme, còn file mới thì không. `gamification.js` phụ trách profile/quest/check-in; `monthly-ticket.js` phụ trách balance/vote/ranking. File JS tĩnh đọc message từ `data-*` hoặc catalog `window` do Thymeleaf phát ra; Thymeleaf natural template chỉ dùng trong script inline được render, không giả định file tĩnh được Thymeleaf xử lý.

### Frontend theme overlay

Theme **không** được chọn lúc chạy. Overlay xảy ra ở build time: `novel-front/pom.xml` khai báo `<theme.name>green</theme.name>` và nạp hai resource directory `../templates/${theme.name}/html` → `templates`, `../templates/${theme.name}/static` → `static`, với `<overwrite>true</overwrite>`; execution `clear-theme-output` xóa `target/classes/templates` và `target/classes/static` ở pha `generate-resources`.

Hệ quả bắt buộc phải tuân thủ:

- Theme nằm ở **gốc repository** (`templates/<theme>/html`, `templates/<theme>/static`), không nằm trong `novel-front/src/main/resources`.
- Một artifact chỉ chứa **một** theme. Kiểm thử bốn theme nghĩa là bốn lần build với `-Dtheme.name=green|orange|dark|blue`, đúng như `ThemePackagingIsolationTest` đang xác minh.
- File nào theme không có thì bản base trong `novel-front/src/main/resources` được giữ lại. Đây là “fallback” duy nhất, và là fallback lúc đóng gói chứ không phải lúc chạy.
- Vì `green` là theme mặc định, mọi thay đổi chỉ nằm ở base cho các trang mà `green` ghi đè sẽ **biến mất khỏi bản build mặc định**.

Phạm vi ghi đè thực tế của từng theme:

| Theme | Trang ghi đè liên quan gamification | Việc phải làm |
|---|---|---|
| `green` (mặc định) | `index`, `common/header`, `book/book_detail`, `book/book_ranking`, `author/author_income` và bản `mobile/` tương ứng | Sửa song song base và theme cho tất cả các trang này |
| `orange` | Phạm vi giống `green` | Sửa song song như `green` |
| `dark` | `index`, `common/header`, `book/book_detail`, `book/book_ranking` và bản `mobile/` tương ứng; không ghi đè `author/` | Sửa các trang bị ghi đè; trang tác giả dùng base |
| `blue` | Chỉ `index`, `common/header`, `common/top`, `common/js`, `common/footer`, `404` | Chỉ sửa `index` và `common/header`; book detail/ranking/mobile dùng base |

Static asset cũng bị ghi đè theo cùng cơ chế: `static/css/base.css` bị **cả bốn** theme ghi đè, `static/javascript/bookdetail.js` bị `green` và `orange` ghi đè. Đây là lý do kỹ thuật bắt buộc để đặt logic mới vào **file JS/CSS mới** (`gamification.js`, `monthly-ticket.js`, `gamification.css`): file mới không bị theme nào ghi đè nên chỉ cần thêm một lần ở base. Ngược lại, một dòng CSS thêm vào `base.css` phải nhân bản sang bốn theme mới có tác dụng.

CSS gamification dùng design token hiện hữu. Chỉ thêm override theo theme khi contrast hoặc stacking thực sự khác; không sao chép toàn bộ CSS sang bốn theme.

### Hợp đồng API cần khóa trước khi code

| Method và endpoint | Request chính | Response chính | Idempotency |
|---|---|---|---|
| `GET /user/gamification/profile` | JWT/cookie hiện hữu | level, EXP, realm, frame, next threshold | Không |
| `PATCH /user/gamification/realm` | `realmType`, `expectedVersion` | profile và cooldown mới | Có, khóa server sinh |
| `POST /user/gamification/check-in` | Không nhận `userId` | streak, EXP/ticket reward, next check-in | Có, khóa theo `userId` và ngày địa phương |
| `POST /user/gamification/reading-heartbeat` | `sessionId`, `bookId`, `bookIndexId`, `sequence`, `activeSeconds` | số giây nhận, phút xác minh trong ngày, sequence kế tiếp và trạng thái đạt trần | Có, receipt theo `(sessionId, sequence)` và request hash |
| `GET /user/gamification/quests` | `date` tùy chọn | quest, progress, claim status, reward preview | Không |
| `POST /user/gamification/quests/{questCode}/claim` | Không nhận owner ID | reward và balance mới | Có, khóa server sinh |
| `GET /user/monthly-tickets` | JWT/cookie hiện hữu | available balance và các expiry lot gần nhất | Không |
| `GET /user/monthly-tickets/history` | page, limit | ledger history thuộc user hiện tại | Không |
| `GET /book/{bookId}/monthly-ticket-summary` | book ID | season, total votes, rank, eligibility | Không |
| `POST /book/{bookId}/monthly-ticket-votes` | `count`, `clientRequestId` | vote ID, total mới, balance mới | Có, khóa server sinh |
| `GET /book/monthly-ticket-ranking` | period, page, limit | season status, cutoff, rank entries | Không |
| `GET /author/monthly-rewards` | JWT/cookie hiện hữu | reward dự kiến/đã chốt của author hiện tại | Không |

Repository chưa có hạ tầng idempotency ở tầng HTTP: `WalletLedgerServiceImpl`, `UserServiceImpl.buyBookIndex()` và `AuthorFinanceServiceImpl` đều tự sinh khóa ở tầng service từ dữ liệu nghiệp vụ. Giữ nguyên quy ước đó thay vì thêm header mới:

- Khóa idempotency được **server sinh** từ danh tính đã xác thực và tham số nghiệp vụ, ví dụ `VOTE:<userId>:<seasonId>:<bookId>:<clientRequestId>`.
- Client chỉ được gửi `clientRequestId` dạng UUID để phân biệt hai lần bấm khác nhau; giá trị này không bao giờ được dùng làm danh tính.
- Service lưu request hash và so khớp như `WalletLedgerServiceImpl.validateExisting()`: cùng khóa và cùng payload trả kết quả cũ, cùng khóa khác payload bị từ chối.
- Client không được gửi `userId` hoặc `authorId` làm nguồn xác thực.

Chỉ cân nhắc header `Idempotency-Key` dùng chung khi có API công khai cho bên thứ ba; khi đó phải bổ sung tường minh interceptor và bảng lưu trữ vào phạm vi, không để mỗi controller tự xử lý.

### Điểm tích hợp event vào luồng hiện hữu

| Event | Điểm phát sinh đúng | Source/idempotency key | Phần chưa xác minh |
|---|---|---|---|
| `CHECK_IN_COMPLETED` | `GamificationProgressService.checkIn()` | `CHECKIN:<userId>:<localDate>` | Chính sách bù ngày |
| `READING_MINUTE_VERIFIED` | `ReadingHeartbeatWriter` sau heartbeat hợp lệ | `READ:<userId>:<sessionId>:<localDate>:<minuteBucket>` | Browser regression với timer bị throttle |
| `COMMENT_APPROVED` | Admin service khi `auditStatus` chuyển sang duyệt | `GAMIFY:COMMENT_APPROVED:<commentId>` | Bình luận bị gỡ sau khi claim có revoke EXP hay không |
| `CHAPTER_PURCHASED` | `UserServiceImpl.buyBookIndex()` sau ledger Xu | `GAMIFY:CHAPTER_PURCHASE:<userId>:<bookIndexId>` | Giới hạn quest/ngày |
| `TOP_UP_SETTLED` | `OrderServiceImpl.processPayOrder()` sau ledger Xu | `TOPUP:<outTradeNo>` | Nạp Xu có cấp Ngọn Đuốc hay không |
| `LEVEL_REACHED` | `GamificationProgressService` sau EXP post | `LEVEL:<userId>:<level>:<ruleVersion>` | Level nào được cấp phiếu |

Không phát event trước commit thành công và không dựa vào callback UI. Nếu transaction nguồn rollback thì event/reward tương ứng cũng phải rollback.

Ràng buộc đã xác minh trong mã nguồn hiện tại, phải xử lý tường minh khi tích hợp:

- `UserServiceImpl.buyBookIndex()` **return sớm mà không ném lỗi** khi chương đã mua hoặc khi chính sách không yêu cầu trả phí, và `walletLedgerService.purchaseChapter()` có thể trả `ALREADY_POSTED`. Chỉ phát `CHAPTER_PURCHASED` khi kết quả là `POSTED`; không phát ở nhánh return sớm.
- Khóa idempotency của sổ cái Xu cho mua chương đã là `CHAPTER_PURCHASE:<userId>:<bookIndexId>`. Khóa gamification phải có tiền tố `GAMIFY:` riêng để hai miền không dùng chung không gian khóa.
- `BookServiceImpl.addBookComment()` chỉ cho mỗi người **một bình luận cho mỗi truyện, vĩnh viễn** (`ResponseStatus.HAS_COMMENTS`), và `book_comment.audit_status` mặc định là chờ duyệt. Vì vậy `COMMENT_APPROVED` phát sinh tối đa một lần cho mỗi cặp người dùng/truyện và phụ thuộc thao tác thủ công của quản trị viên với độ trễ không xác định. Quest bình luận **không** được định nghĩa là nhiệm vụ hằng ngày trừ khi đổi sang đếm `book_comment_reply`; quyết định này phải nằm trong bảng chính sách.
- `CommentModerationController.batchAudit()` hiện update `auditStatus` vô điều kiện nên có thể chuyển đi chuyển lại giữa chờ duyệt và đã duyệt. Chỉ phát event ở lần chuyển trạng thái thực sự sang đã duyệt.

### Bản đồ kiểm thử và tài liệu

| Module | File thêm/sửa | Trọng tâm xác minh |
|---|---|---|
| `novel-common` | Thêm `GamificationProgressServiceImplTest`, `MonthlyTicketServiceImplTest`, `MonthlyRankingServiceImplTest`; sửa `WalletLedgerServiceImplTest` | Rule, idempotency, FIFO lot, reversal, tie-break, clearing thưởng và author reward ledger |
| `novel-front` | Thêm `GamificationControllerTest`, `MonthlyTicketControllerTest`, `GamificationMySqlIntegrationTest` | Auth, DTO, request hash, ràng buộc DB, biên tháng và timezone |
| `novel-front` | Thêm `MonthlyTicketConcurrencyIT`, `MonthlySeasonConcurrencyIT` | Hai request tiêu phiếu đồng thời, hai instance cùng chốt kỳ, restart giữa các trạng thái |
| `novel-front` | Thêm `GamificationPackagingTest` | Base/theme overlay, page route, static asset, i18n parity và migration compose |
| `novel-admin` | Thêm `GamificationAdminServiceImplTest`, `GamificationAdminControllerTest`, `CommentModerationServiceImplTest` | Permission, state transition, approval audit và comment event đúng một lần |
| Repository | Thêm `doc/gamification.md`; sửa `README.md`, `doc/sql/readme.md`, `.env.example` | Chính sách, API, runbook, feature flag, migration và quy trình vận hành |

Test integration dùng MySQL 8.4 như compose hiện tại; không dùng H2 để kết luận về `CHECK`, trigger, locking hoặc transaction. Test frontend phải parse JS tĩnh bằng Node và mô phỏng catalog/data attribute sau render.

Khuôn mẫu test tích hợp hiện có (`ReaderStateMySqlIntegrationTest`, `RefundMySqlIntegrationTest`, …) dùng `@SpringBootTest` + `@EnabledIfSystemProperty` + `@Transactional` + `@Rollback`. Khuôn mẫu này **không dùng được cho test đồng thời**: mọi thao tác chạy trong một transaction duy nhất nên hai luồng sẽ không bao giờ tranh chấp khóa thật, và test sẽ xanh mà không chứng minh điều gì. Vì vậy hai lớp `*ConcurrencyIT`:

- Không được đánh dấu `@Transactional`/`@Rollback`; mỗi luồng phải mở transaction riêng.
- Phải dùng thread thật cùng `CountDownLatch` để ép hai giao dịch chạm cùng hàng.
- Phải tự dọn dữ liệu ở `@AfterEach` bằng `JdbcTemplate` với dải ID dành riêng cho test.
- Được bật bằng system property riêng để không chạy trong build mặc định, cùng quy ước với các integration test hiện có.

## Kế hoạch công việc

### 1. Khóa chính sách sản phẩm và mô hình đe dọa

- [ ] Chốt nguồn cấp Ngọn Đuốc, giới hạn ngày/tháng, thời hạn, tự bỏ phiếu, điều kiện truyện và xử lý chargeback.
- [ ] Chốt ngân sách, cơ cấu Top, đơn vị thưởng, quy tắc tác giả có nhiều truyện và quy trình khiếu nại/clawback.
- [ ] Chốt điều kiện eligibility bằng **tên cột thật**, không mô tả chung chung: `book.audit_status = 1` (đã duyệt) và `book.status = 1` (đã lên kệ), `book.crawl_source_id`/`crawl_book_id` để loại nội dung crawl, phân loại độ tuổi qua `AgeRatingUtil` ở tầng service và cờ khóa truyện. Lưu ý `book.book_status` là trạng thái sáng tác (0 đang ra, 1 hoàn thành) và **không** dùng cho eligibility.
- [ ] Chốt việc truyện do `novel-crawl` nhập về có được nhận phiếu và nhận thưởng hay không. Trả tiền cho nội dung crawl là rủi ro pháp lý; mặc định phải là **không đủ điều kiện** cho tới khi có quyết định ngược lại.
- [ ] Chốt xử lý thuế cho tiền thưởng: `author.income.tax-rate`, `share-proportion`, `exchange-proportion` và `author.payout.vnd-per-xu` hiện áp cho doanh thu bản quyền; phải nêu rõ tiền thưởng có đi qua cùng công thức hay không.
- [ ] Xác định các hành vi gian lận: request lặp, nhiều tài khoản, giả lập đọc, spam bình luận, tự vote và thông đồng.
- [ ] Viết bảng quyết định có phiên bản; mọi campaign phải tham chiếu phiên bản chính sách.

**Xác minh:** không còn quyết định kinh tế hoặc điều kiện eligibility được hard-code ngầm trong service.

### 2. Xây nền dữ liệu bất biến cho Ngọn Đuốc

- [ ] Tạo migration mới cho `monthly_ticket_account`, `monthly_ticket_lot`, `monthly_ticket_ledger`, `monthly_ticket_lot_allocation`, `monthly_ticket_vote` và `gamification_event`.
- [ ] Thêm unique `idempotency_key`, source event, FK, index, check constraint, audit time và optimistic `version`.
- [ ] Dùng account làm projection; ledger và lot là nguồn kiểm toán.
- [ ] Tiêu lot theo FIFO; mỗi lot lưu nguồn, số cấp, số còn lại và thời điểm hết hạn.
- [ ] Chặn update/delete ledger bằng quy ước service và trigger phù hợp; sửa sai bằng reversal.

**Xác minh:** `số dư = tổng cấp - tổng tiêu - tổng hết hạn - tổng thu hồi + điều chỉnh`; không có lot hoặc account âm ngoài trạng thái debt đã được chính sách cho phép.

### 3. Xây quest, điểm danh, EXP và cảnh giới dạng cấu hình

- [ ] Tạo `quest_definition`, `quest_campaign`, `quest_reward`, `user_quest_progress`, `quest_claim`, `user_exp_ledger`, `gamification_profile`, `level_rule` và `realm_catalog`.
- [ ] Chuẩn hóa event từ đăng nhập, đọc, bình luận đã duyệt, mua chương và thanh toán thành source event có idempotency.
- [ ] Tính ngày và streak theo `Asia/Ho_Chi_Minh`; dùng Clock có thể thay thế trong test.
- [x] Đọc 30 phút dựa trên endpoint xác thực riêng, foreground/focus signal phía client, elapsed time và trần tích lũy phía server, receipt bất biến cùng kiểm tra request quá nhanh/quá trễ.
- [ ] Tính level từ EXP ledger theo rule version; đổi realm có audit và cooldown.

**Xác minh:** xử lý lại cùng source event không tăng tiến độ hoặc phát thưởng lần hai; thay đổi rule không âm thầm sửa lịch sử đã chốt.

### 4. Triển khai giao dịch bỏ phiếu bằng Ngọn Đuốc

- [ ] Trong cùng transaction: xác thực user, kiểm tra kỳ/eligibility/limit, lock account và lot, tiêu FIFO, ghi ledger, ghi vote và cập nhật projection.
- [ ] Bắt buộc idempotency key và request hash; cùng key/cùng payload trả kết quả cũ, cùng key/khác payload bị từ chối.
- [ ] Chặn tác giả và cộng tác viên tự vote. Tra chủ sở hữu qua `author.user_id` và cộng tác viên qua `author_book_collaborator`. `author.user_id` là cột **NULLABLE** và dữ liệu legacy có bản ghi `NULL`, nên phải xử lý `NULL` tường minh thay vì dựa vào phép so sánh bằng.
- [ ] Rate limit theo user, IP và device signal bằng `@RateLimit` sẵn có trong `novel-common`; **không** viết bộ giới hạn mới.
- [ ] Đặt giới hạn cứng ở tầng database (unique key hoặc counter row trong cùng transaction) làm nguồn sự thật, vì `RateLimitAspect` tụt về bộ đếm in-memory theo từng instance khi Redis lỗi. Redis chỉ là bộ lọc nhanh, không phải hàng rào cuối.
- [ ] Xử lý refund/chargeback bằng reversal; thu hồi lot chưa dùng hoặc ghi trạng thái debt theo chính sách đã chốt.

**Xác minh:** hai request đồng thời không thể tiêu vượt số dư; số phiếu hợp lệ luôn bằng lượng Ngọn Đuốc đã tiêu tương ứng; tắt Redis không làm vỡ giới hạn ngày.

### 5. Xây state machine kỳ xếp hạng và chốt tháng

- [ ] Tạo `monthly_ticket_season`, `monthly_rank_snapshot`, `monthly_rank_entry` và `scheduled_job_run`.
- [ ] Áp dụng luồng `OPEN → CLOSING → REVIEW → FINALIZED → REWARDED`.
- [ ] Claim từng phase bằng atomic update `WHERE status = :expectedStatus AND version = :expectedVersion`; thêm unique `(season_id, job_type)` và checkpoint trong `scheduled_job_run` để chạy lại an toàn sau restart.
- [ ] Chụp cutoff/snapshot bất biến; trong REVIEW xử lý fraud, truyện bị khóa và khiếu nại trước khi finalization.
- [ ] Cung cấp thao tác admin pause, retry, reconcile và finalize có audit; không sửa trực tiếp bảng kết quả.

**Xác minh:** hai instance cùng chạy chỉ tạo một snapshot; chạy lại sau lỗi không đổi kết quả và không phát thưởng trùng.

### 6. Tích hợp quỹ thưởng với tài chính tác giả

Ràng buộc đã xác minh: `wallet_account` có `chk_wallet_available_balance` cấm số dư âm với mọi ví không phải `SYSTEM`, và `WalletLedgerServiceImpl.post()` chỉ cho phép trạng thái nợ với ví `READER_XU` qua cờ `allowReaderDebt`. Nếu ghi thẳng tiền thưởng vào `AUTHOR_REVENUE_XU` thì tác giả rút được ngay, và khi cần thu hồi, bút toán đảo sẽ ném `InsufficientWalletBalanceException` — **clawback trở thành bất khả thi**. Vì vậy tiền thưởng phải đi qua tài khoản clearing.

- [ ] Tạo `reward_fund_campaign` và `author_reward_allocation` để snapshot ngân sách, cơ cấu giải, tỷ giá/chính sách và trạng thái duyệt.
- [ ] Seed tài khoản hệ thống `REWARD_CLEARING` trong migration mới, theo đúng mẫu `PAYOUT_CLEARING` của `20260717_wallet_ledger.sql`. Đây là biện pháp phòng vệ để truy vấn đối soát join được ngay sau khi deploy, không phải điều kiện tiên quyết: `ensureWallets()` vốn tự tạo ví hệ thống bằng `INSERT IGNORE` ở lần dùng đầu, và `REFUND_CLEARING` hiện chưa từng được seed bằng SQL.
- [ ] Chỉ tính thưởng từ ranking `FINALIZED`; dùng khóa `MONTHLY_AUTHOR_REWARD:<period>:<bookId>:<rank>`.
- [ ] Mở rộng `WalletLedgerService` bằng `creditAuthorRewardPending(...)`: ghi `MONTHLY_AUTHOR_REWARD` từ `SYSTEM_ISSUANCE` sang `REWARD_CLEARING`. Ở bước này tiền thưởng **chưa** nằm trong số dư rút được của tác giả.
- [ ] Mở rộng `WalletLedgerService` bằng `releaseAuthorReward(...)`: sau cửa sổ khiếu nại đã cấu hình, `AuthorRewardReleaseSchedule` chuyển từ `REWARD_CLEARING` sang `AUTHOR_REVENUE_XU` với khóa `MONTHLY_AUTHOR_REWARD_RELEASE:<period>:<bookId>:<rank>`.
- [ ] Trong cửa sổ khiếu nại, clawback được thực hiện bằng bút toán đảo trên nhánh clearing nên không chạm tới số dư tác giả và không vi phạm CHECK constraint.
- [ ] Ghi vào chính sách rằng sau khi đã release và tác giả đã rút, hệ thống **không** thu hồi tự động được; tranh chấp ở giai đoạn đó xử lý bằng quy trình vận hành ngoài sổ cái.
- [ ] Tách quyền tính, duyệt, ghi thưởng và payout; hỗ trợ hold, reversal, clawback và audit.
- [ ] `getAuthorAvailableBalance()` giữ nguyên ngữ nghĩa "số dư rút được"; trang tác giả hiển thị thưởng đang chờ release như một mục riêng, không cộng vào số dư.
- [ ] Giữ payout production tắt nếu KYC, nguyên tắc bốn mắt, đối soát và chính sách thuế chưa được phê duyệt.

**Xác minh:** mọi reward truy ngược được đến season/rank/book/author; ledger Xu cân bằng và retry không tạo bút toán thứ hai; clawback trong cửa sổ khiếu nại thành công mà không đẩy ví tác giả xuống âm.

### 7. Hoàn thiện API, admin, chống lạm dụng và observability

- [ ] Triển khai đúng các endpoint và DTO đã khóa trong mục “Hợp đồng API”; không thay đổi API cũ.
- [ ] Endpoint ghi phải xác thực, không nhận owner ID từ client, có idempotency, rate limit, validation và message i18n.
- [ ] Thêm admin quản lý catalog, campaign, season, quỹ, fraud review, reconciliation và reward approval với permission tách biệt.
- [ ] Ghi audit mọi điều chỉnh và thao tác nhạy cảm; không log token, PII hoặc payload tài chính nhạy cảm.
- [ ] Thêm job đối soát cho grant, spend, expire, revoke, duplicate, fraud reject, reconciliation drift, close duration và reward pending. Mở rộng `LedgerIntegrityCheckSchedule` thay vì viết cơ chế mới.
- [ ] Chốt kênh cảnh báo trước khi code: repository có `spring-boot-starter-actuator` nhưng **chưa** có `micrometer-registry-prometheus`, nên cảnh báo hiện tại là log-based (`log.error`). Hoặc đưa registry Prometheus vào phạm vi, hoặc ghi rõ rằng cảnh báo đi qua log và cấu hình thu thập log tương ứng. Không mô tả "dashboard" như thể hạ tầng đã có.

**Xác minh:** user không đọc/sửa được dữ liệu người khác; cơ chế cảnh báo đã chốt phát hiện được lệch projection, job thất bại và reward bị treo.

### 8. Triển khai UI desktop/mobile và bốn theme

- [ ] User center hiển thị EXP, level, realm, điểm danh, quest, lịch sử và hạn dùng Ngọn Đuốc.
- [x] Book detail hiển thị tổng Đuốc của truyện, số dư của người đọc, lot sắp tắt và hộp xác nhận khi thắp nhiều Đuốc một lần.
- [x] Trang ranking hiển thị kỳ hiện tại/lịch sử, trạng thái chốt, dữ liệu realtime/snapshot, số Đuốc và số độc giả.
- [x] Trang chủ hiển thị Top 5 Đuốc, kỳ, trạng thái realtime/snapshot, mốc khóa sổ và thời gian còn lại.
- [ ] Trang chủ hiển thị quy tắc và kết quả thưởng sau khi nội dung chính sách được chốt.
- [ ] Tác giả xem thứ hạng, thưởng dự kiến/đã chốt; admin xem fraud và reconciliation.
- [ ] Toàn bộ chuỗi hiển thị tuân thủ bảng quy ước đặt tên: danh từ "Ngọn Đuốc", dạng ngắn "Đuốc" cho tiêu đề cột và tab, động từ "thắp" cho hành động và "tắt" cho hết hạn.
- [ ] Sửa template base trong `novel-front/src/main/resources`, rồi sửa song song các file mà từng theme ở `templates/<theme>/html` ghi đè; nghiệm thu bằng bốn bản build `-Dtheme.name` riêng, cả desktop và mobile.
- [ ] Dùng i18n, design token, contrast đạt yêu cầu, `prefers-reduced-motion`; animation chỉ chạy sau commit thành công.
- [ ] Ticker P2 chỉ dùng nickname đã kiểm duyệt, cho phép ẩn danh/opt-out và không phát PII.

**Xác minh:** không tràn chữ hoặc mất chức năng trên bốn bản build theme; không có widget nào biến mất vì file base bị theme ghi đè; keyboard, screen reader và reduced-motion sử dụng được các luồng chính.

### 9. Migration, feature flag và rollout

- [ ] Chỉ thêm migration mới, không sửa migration lịch sử; profile được lazy-create và không backfill toàn bộ user nếu không cần.
- [ ] Thêm migration vào service `migrate` và volume của `compose.yaml`; cập nhật SQL readme, `.env.example` và kiểm tra `docker compose config`.
- [ ] Ghi rõ trong runbook rằng **không có down-migration**: repository không có tiền lệ rollback bằng SQL đảo. Rollback duy nhất được hỗ trợ là tắt feature flag; bảng và dữ liệu được giữ nguyên để không mất ledger.
- [ ] Tạo kill switch riêng cho event collection, quest reward, voting, ranking và reward posting; mặc định tắt.
- [ ] Rollout theo thứ tự: local/integration → nội bộ → shadow mode → beta giới hạn → production.
- [ ] So sánh ranking shadow với truy vấn nguồn trước khi bật thưởng.
- [ ] Chuẩn bị runbook pause season, khóa vote, reconcile, retry, reversal và rollback bằng feature flag.

**Xác minh:** tắt tính năng không làm mất ledger/vote; migration chạy được trên DB mới, DB hiện hữu và chạy lại an toàn.

### 10. Regression và nghiệm thu cuối

- [ ] Chạy unit test cho policy, quest, streak, EXP, eligibility, tie-break, expiry và state transition.
- [ ] Chạy MySQL integration test cho migration, transaction, concurrency, unique key, month boundary, năm nhuận và timezone.
- [ ] Kiểm thử duplicate event/request, chargeback trước/sau khi tiêu phiếu, truyện bị khóa và tác giả có nhiều truyện Top.
- [ ] Chạy thử hai application instance chốt cùng kỳ, restart giữa các trạng thái và reconciliation sau lỗi.
- [ ] Build Maven reactor bằng Java 17; chạy smoke Docker/API, render representative pages và parse JavaScript sau Thymeleaf.
- [ ] Kiểm thử theme bằng **bốn lần build riêng** với `-Dtheme.name=green`, `orange`, `dark`, `blue`; một artifact chỉ chứa một theme nên không thể xác minh cả bốn trong một lần build.
- [ ] Chạy `node scripts/verify-i18n.mjs` và `git diff --check`; lưu bằng chứng test trong báo cáo nghiệm thu.

**Xác minh:** toàn bộ tiêu chí “Hoàn tất khi” bên dưới đạt, không còn cảnh báo P0 chưa xử lý.

## Trình tự phát hành

| Mốc | Nội dung | Điều kiện chuyển mốc |
|---|---|---|
| P0 – Nền an toàn | Policy, ticket ledger, **cấp phiếu thủ công/khuyến mại từ admin**, expiry job, event idempotency, vote transaction, anti-abuse cơ bản, state machine và reconciliation | Invariant/concurrency/month-close test đạt; chưa mở reward production |
| P1 – Giá trị sản phẩm | Quest, check-in, EXP, realm, UI, ranking, quỹ thưởng và admin | Shadow ranking khớp nguồn; reward staging không trùng và ledger cân bằng |
| P2 – Trải nghiệm nâng cao | Ticker, animation, badge/frame, social sharing và season đặc biệt | Accessibility, privacy và hiệu năng đạt trên bốn theme/mobile |

Chính sách quy định Ngọn Đuốc chỉ được cấp từ nhiệm vụ, điểm danh, thăng cấp hoặc khuyến mại — mà nhiệm vụ, điểm danh và thăng cấp đều thuộc P1. Nếu P0 không có đường cấp phiếu nào thì hệ thống bỏ phiếu ra mắt trong trạng thái không ai có phiếu, và các bất biến về tiêu FIFO, hết hạn cùng thu hồi không thể kiểm chứng bằng dữ liệu thật. Vì vậy **đường cấp thủ công/khuyến mại từ admin, có audit và idempotency, là hạng mục bắt buộc của P0**, không phải tùy chọn.

## Hoàn tất khi

- [ ] Không thể cấp, nhận, tiêu, hết hạn, bỏ phiếu hoặc trả thưởng trùng khi retry.
- [ ] Mỗi vote truy ngược được tới ledger và các lot Ngọn Đuốc đã tiêu.
- [ ] Không có số dư âm ngoài trạng thái debt được chính sách cho phép và có đường xử lý rõ ràng.
- [ ] Kết quả tháng xác định, tái tạo được và không đổi sau `FINALIZED` nếu không có reversal được audit.
- [ ] Reward tác giả đi qua sổ cái Xu, KYC/payout hiện có và không cập nhật số dư trực tiếp.
- [ ] Thưởng đã chốt nằm ở `REWARD_CLEARING` cho tới hết cửa sổ khiếu nại, và clawback trong cửa sổ đó không đẩy ví tác giả xuống âm.
- [ ] Lot Ngọn Đuốc quá hạn được đóng bằng job có audit; tổng hết hạn khớp với đối soát.
- [ ] Event giả lập, tự vote, spam và rate abuse cơ bản bị chặn hoặc đưa vào REVIEW, kể cả khi Redis không khả dụng.
- [ ] Desktop/mobile hoạt động trên cả bốn bản build theme, i18n đầy đủ, accessibility/reduced-motion đạt yêu cầu.
- [ ] Migration, Maven test, MySQL integration, test đồng thời non-transactional, Docker smoke, JS parse và `git diff --check` đều đạt.
- [ ] Có cảnh báo theo kênh đã chốt, runbook, feature flag và rollback không phá hủy dữ liệu.

## Quyết định còn mở trước khi triển khai

1. Ngọn Đuốc chỉ nhận từ hoạt động hay được mua/cấp theo nạp Xu; chargeback có tạo debt khi phiếu đã được tiêu không?
2. Quỹ thưởng là ngân sách Xu cố định, VND cố định hay phần trăm doanh thu; một tác giả có được nhận nhiều giải trong một kỳ không?
3. Cảnh giới chỉ là danh xưng hay ảnh hưởng phần thưởng/quyền lợi; cooldown đổi cảnh giới cụ thể là bao lâu?
4. Tiền thưởng có chịu cùng công thức thuế và tỷ giá với doanh thu bản quyền (`author.income.tax-rate`, `share-proportion`, `exchange-proportion`, `author.payout.vnd-per-xu`) hay tách riêng? Cửa sổ khiếu nại trước khi release là bao nhiêu ngày?
5. Truyện do `novel-crawl` nhập về có được nhận phiếu và nhận thưởng không? Nếu không thì eligibility lọc bằng tiêu chí nào?
6. Quest bình luận được định nghĩa lại thành gì, khi mỗi người chỉ bình luận được một lần cho mỗi truyện và bình luận phải chờ quản trị viên duyệt thủ công?

Không được bắt đầu thay đổi code tài chính hoặc migration production cho đến khi sáu quyết định này được ghi thành chính sách có phiên bản.
