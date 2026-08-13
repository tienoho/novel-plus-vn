# Gamification — vận hành

Tài liệu vận hành cho hệ Ngọn Đuốc, nhiệm vụ, cảnh giới, xếp hạng tháng và quỹ thưởng tác giả.

- Chính sách kinh tế và chống lạm dụng: [gamification-policy-v1.md](gamification-policy-v1.md)
- Đặc tả yêu cầu: [gamified-universe-plan.md](../gamified-universe-plan.md)

Tài liệu này được điền dần theo từng mốc triển khai. Mục nào ghi *chưa triển khai* nghĩa là mã tương ứng chưa có, không phải là đã có nhưng chưa viết tài liệu.

## Thuật ngữ hiển thị

Tên kỹ thuật trong bảng, lớp Java và khóa i18n giữ nguyên tiếng Anh là `monthly_ticket`. Tên hiển thị tới người dùng là **Ngọn Đuốc**.

| Ngữ cảnh | Giá trị |
|---|---|
| Danh từ đầy đủ | Ngọn Đuốc |
| Dạng ngắn cho tiêu đề cột và tab | Đuốc |
| Động từ bỏ phiếu | thắp — "Thắp đuốc cho truyện" |
| Động từ hết hạn | tắt — "2 ngọn đuốc sẽ tắt ngày 31/07" |
| Động từ nhận thưởng | nhận — "Bạn nhận được 5 ngọn đuốc" |

Đổi tên hiển thị về sau chỉ là sửa file `.properties`, không phải migration.

## Nguồn cấu hình runtime

Nguồn sự thật production là snapshot có kiểu dữ liệu trong bảng `gamification_runtime_config`.
`novel-front`, `novel-admin` và worker dùng chung `GamificationConfigProvider`; mỗi request/job chụp
`provider.current()` đúng một lần và giữ revision đó đến hết transaction. Thay đổi ACTIVE được poll
mỗi 2 giây và không cần restart ứng dụng.

ENV chỉ còn hai vai trò: bootstrap/cutover và nguồn đối chiếu trong `DB_SHADOW`. Sáu biến được phép
truyền vào container là:

```text
GAMIFICATION_CONFIG_SOURCE=ENV|DB_SHADOW|DB
GAMIFICATION_CONFIG_REFRESH_MS=2000
GAMIFICATION_CONFIG_MAX_STALE_MS=60000
GAMIFICATION_FORCE_DISABLE=false
GAMIFICATION_VOTE_IP_HASH_KEY_ID=v1
GAMIFICATION_VOTE_IP_HASH_SALT_FILE=/run/secrets/gamification_vote_ip_hash_salt
```

Salt hash IP nằm trong Docker secret, không vào database, API, HTML, log hoặc audit. Database chỉ
lưu `vote_ip_hash_key_id`; vote bị khóa nếu key ID không khớp secret đang mount. Last-known-good vẫn
phục vụ API đọc khi refresh lỗi, nhưng đường ghi fail-closed khi snapshot stale quá giới hạn.

**Mọi cờ trong revision bootstrap mặc định tắt.** Bốn cờ không có hành vi policy v1
`TICKET_GRANT_ON_TOPUP`, `QUEST_REPLY_ENABLED`, `REALM_AFFECTS_BENEFITS` và
`SEASON_AUTO_FINALIZE` luôn bị khóa `false`. Mục tiêu đọc thuộc
`quest_definition.target_count`, không còn là runtime setting.

| Cờ | Bật thì cho phép | Phụ thuộc |
|---|---|---|
| `event.enabled` | Thu nhận sự kiện vào `gamification_event` | không |
| `ticket.enabled` | Cấp, hết hạn và tra cứu Ngọn Đuốc | không |
| `vote.enabled` | Thắp đuốc cho tác phẩm | cần `ticket.enabled` |
| `season.enabled` | Mở kỳ, chốt kỳ, chụp snapshot xếp hạng | cần `vote.enabled` |
| `quest.enabled` | Nhiệm vụ, điểm danh, EXP | cần `event.enabled` |
| `realm.enabled` | Đổi cảnh giới | cần `quest.enabled` |
| `reward.enabled` | Tính và ghi thưởng tác giả | cần `season.enabled` |

`GamificationConfigValidator` từ chối cron/zone/ngưỡng hoặc quan hệ phụ thuộc không hợp lệ trước khi
submit/activate. Không có trường hợp âm thầm bỏ qua giá trị ENV trái policy v1.

### Thứ tự bật an toàn

```
EVENT -> TICKET -> VOTE -> SEASON -> (shadow mode một kỳ đầy đủ) -> QUEST -> REALM -> REWARD
```

**Không bật `reward.enabled` trước khi shadow mode xác nhận xếp hạng khớp truy vấn nguồn.** Xem mục Shadow mode bên dưới.

## Lifecycle, hiệu lực và rollback

Revision đi qua `DRAFT → PENDING_APPROVAL → APPROVED → SCHEDULED → ACTIVE → ARCHIVED`, kèm
`REJECTED/CANCELLED`. Snapshot bất biến sau submit; muốn sửa phải clone thành draft mới. Thay đổi
rủi ro cao bắt buộc người tạo và người duyệt khác nhau. Backend tự phân loại diff:

- `IMMEDIATE`: tuning worker/cron/delay và tắt feature;
- `NEXT_DAY`: quota/heartbeat theo ngày;
- `NEXT_SEASON`: zone, policy, eligibility, vote limit và luật kinh tế.

Tắt khẩn cấp dùng `GAMIFICATION_FORCE_DISABLE=true`; nó luôn thắng database, chỉ khóa đường ghi và
không sửa dữ liệu. Rollback ứng dụng dùng `GAMIFICATION_CONFIG_SOURCE=ENV` rồi restart. Rollback
cấu hình phải clone revision cũ thành **revision mới** và đi qua đúng approval; tuyệt đối không
UPDATE bản ACTIVE/ARCHIVED cũ, xóa audit hoặc chạy down-migration.

## Mã cảnh báo

Compose có Prometheus, Alertmanager và dashboard Grafana `Novel Plus - Tổng quan vận hành`. Worker cập nhật các gauge `novel_gamification_queue` và `novel_ledger_mismatch_records`; rule trong `deploy/observability/rules/khoi-thu-alerts.yml` cảnh báo backlog event, reward chờ, job stale, kỳ review quá hạn và sai lệch ledger. Các mã log dưới đây vẫn được giữ để điều tra chi tiết và đối chiếu audit.

Mã nằm ở **đầu** thông điệp để cấu hình grep phía thu thập log.

| Mã | Điều kiện | Hành động |
|---|---|---|
| `GAMIFY-ALERT-001` | Số dư tài khoản lệch tổng lot còn hiệu lực | Dừng cấp phiếu, chạy đối soát, sửa bằng bút toán điều chỉnh có audit |
| `GAMIFY-ALERT-002` | Tổng phân bổ lot lệch số tiền trên bút toán | Điều tra ngay; đây là dấu hiệu lỗi logic tiêu lot |
| `GAMIFY-ALERT-003` | Lot mồ côi hoặc bút toán cấp không sinh lot | Điều tra migration hoặc lỗi transaction dở dang |
| `GAMIFY-ALERT-004` | Bộ đếm xếp hạng lệch tổng tính từ bảng vote | Snapshot vẫn dùng số từ vote; sửa bộ đếm bằng job đối soát |
| `GAMIFY-ALERT-005` | Job kẹt ở trạng thái đang chạy quá thời hạn thuê | Instance khác sẽ tự tiếp quản; chỉ can thiệp nếu lặp lại |
| `GAMIFY-ALERT-006` | Kỳ nằm ở trạng thái REVIEW quá thời hạn cấu hình | Nhắc quản trị viên chốt kỳ |
| `GAMIFY-ALERT-007` | Thưởng chờ quá cửa sổ khiếu nại mà chưa được giải phóng | Kiểm tra worker giải phóng thưởng |
| `GAMIFY-ALERT-008` | Giao dịch thưởng trong sổ cái Xu không cân bằng | Nghiêm trọng. Dừng trả thưởng, đối soát thủ công |

## Quyền quản trị

| Quyền | Cho phép |
|---|---|
| `novel:gamification:view` | Xem tài khoản, sổ cái, lot, kỳ, phân bổ thưởng và báo cáo |
| `novel:gamification:config` | Sửa danh mục nhiệm vụ, chiến dịch, quy tắc cấp độ, danh mục cảnh giới |
| `novel:gamification:review` | Xử lý gian lận, khóa tác phẩm khỏi gamification, hủy phiếu |
| `novel:gamification:grant` | Cấp Ngọn Đuốc thủ công hoặc theo khuyến mại trong hạn mức |
| `novel:gamification:finalize` | Chốt kỳ, chụp snapshot, tạm dừng và chạy lại job |
| `novel:gamification:reward` | Duyệt chiến dịch thưởng và ghi thưởng |
| `novel:gamification:adjust` | Bút toán đảo, thu hồi, sửa lệch, cấp vượt hạn mức, hủy kỳ |
| `novel:gamification:settings:view/edit/approve/activate` | Xem, tạo draft, phê duyệt và hẹn runtime revision |
| `novel:gamification:policy:view/edit/approve/publish` | Xem, soạn, phê duyệt và phát hành policy bundle |

Quyền cấp phiếu, quyền chốt kỳ và quyền duyệt tiền được tách nhau để giữ nguyên tắc bốn mắt: người chốt kỳ không phải người duyệt tiền.

## Runbook

### Cấp Ngọn Đuốc thủ công

1. Trong **Cấu hình Gamification**, clone ACTIVE, bật `ticketEnabled`, submit, phê duyệt và hẹn
   revision sau khi migration đã chạy.
2. Mở **Gamification** trong trang quản trị bằng quyền `novel:gamification:view`.
3. Người cấp cần `novel:gamification:grant`; nhập mã người dùng, số Đuốc và lý do từ 10 đến 255 ký tự.
4. Lệnh vượt `ticketMaxGrantPerBatch` của ACTIVE revision còn cần quyền
   `novel:gamification:adjust`.
5. Nếu trình duyệt báo không xác định được kết quả do lỗi mạng, bấm lại trên cùng trang. Giao diện
   giữ nguyên request ID, thời điểm hiệu lực và payload để ledger trả `ALREADY_POSTED` thay vì cấp
   lô thứ hai.

Mọi lệnh cấp ghi `operator_type=ADMIN`, `operator_id`, lý do và khóa idempotency. DAO của trang
admin chỉ đọc báo cáo; nó không có câu lệnh ghi trực tiếp vào account, lot hoặc ledger.

### Job đóng Đuốc hết hạn

- Cron lấy từ `ticketExpiryCron` của ACTIVE revision, mặc định 03:20 theo `zoneId`.
- Mỗi ngày có đúng một claim `LOT_EXPIRY/DATE/yyyy-MM-dd` trong `scheduled_job_run`.
- Job lấy danh sách theo `user_id`; mỗi người dùng được xử lý trong một transaction riêng với thứ
  tự khóa account trước, lot sau. Sau mỗi người dùng, job ghi checkpoint và heartbeat.
- Instance khác chỉ tiếp quản khi heartbeat cũ hơn `jobLeaseSeconds`; job đã
  `SUCCEEDED` không được chạy lại.
- Khi account không đủ bao phủ các lot hết hạn, transaction dừng và phát
  `GAMIFY-ALERT-001`; không tự sửa projection hoặc cho số dư âm.

### Kỳ xếp hạng và snapshot tháng

- Vòng duy trì chạy theo `seasonResumeDelayMs` (mặc định 30 giây), tạo lười kỳ
  `REGULAR` hiện tại và tự claim kỳ cũ đã qua cutoff. Vì vậy ứng dụng khởi động lại sau khi lỡ cron
  đầu tháng vẫn tự phục hồi.
- Biên tháng được tính ở Java với `Clock` và `zoneId` của revision, rồi lưu `zone_id`, `start_at`,
  `end_at`, `vote_cutoff_at`; SQL nghiệp vụ không dùng `NOW()` hoặc `CURDATE()`.
- State machine: `OPEN → CLOSING → REVIEW → FINALIZED → REWARDED`. Sau khi claim CLOSING, hệ
  thống chờ `seasonCloseDrainSeconds` để transaction vote đang chạy kết thúc.
- Snapshot luôn tổng hợp từ `monthly_ticket_vote`, không lấy `monthly_rank_counter`. Thứ tự là
  `total_tickets DESC`, `distinct_voter_count DESC`, `last_vote_at ASC`, `book_id ASC`.
- Mỗi batch entry và checkpoint được commit trong cùng transaction `REQUIRES_NEW`. Job lỗi giữ
  checkpoint; retry tiếp tục từ batch cuối đã commit. Khi hoàn tất, snapshot được niêm phong bằng
  SHA-256 `content_hash` rồi season mới chuyển sang REVIEW.
- Trang quản trị **Kỳ xếp hạng** cần quyền `novel:gamification:view` để xem/đối soát và quyền
  `novel:gamification:finalize` để close, pause, retry hoặc finalize. Pause được lưu bền bằng trạng
  thái job `PAUSED`, scheduler không tự tiếp quản cho tới khi quản trị viên retry.
- API đọc công khai: `GET /book/monthly-ticket-ranking?period=yyyy-MM&page=1&limit=20`. Kỳ đang
  chạy đọc projection realtime; từ REVIEW trở đi đọc snapshot bất biến.

### Backend quỹ thưởng tác giả

- `AuthorRewardService` chỉ nhận season `FINALIZED` có snapshot. Cơ cấu từ 1–10 hạng được admin
  truyền bằng basis point, tổng bắt buộc đúng 10.000 và được snapshot canonical vào campaign;
  backend không tự đoán tỷ lệ giải.
- Phân bổ dùng floor, phần lẻ cộng hạng nhất. Một tác giả chỉ nhận giải cao nhất; tác phẩm sau của
  cùng tác giả giữ rank nhưng allocation là `SKIPPED_DUPLICATE_AUTHOR`, số tiền 0 và không tái phân bổ.
- Người `finalized_by` không được là `approved_by`. Khi post, tiền chỉ đi
  `SYSTEM_ISSUANCE → REWARD_CLEARING`; chưa nằm trong số dư rút được của tác giả.
- `AuthorRewardReleaseSchedule` chỉ chọn allocation có `posted_at` cũ hơn cửa sổ khiếu nại rồi
  chuyển `REWARD_CLEARING → AUTHOR_REVENUE_XU`. Mỗi allocation là một transaction độc lập.
- Trong cửa sổ khiếu nại, clawback tái dùng `reverseTransaction()` và chỉ chạm hai ví SYSTEM.
  API `GET /author/monthly-rewards` tự suy ra author từ phiên đăng nhập, không nhận `authorId`.

### Đối soát tự động

`LedgerIntegrityCheckSchedule` hiện kiểm tra sổ Xu và các nhóm dữ liệu gamification: account/lot,
allocation/ledger, lot mồ côi, counter/vote, job hoặc kỳ REVIEW quá hạn, và thưởng chờ release quá
hạn. Các lỗi được ghi bằng `GAMIFY-ALERT-001` đến `007` tương ứng để hệ thống thu thập log lọc
theo mã.

### M5 — Quỹ thưởng tác giả đã hoàn tất

- Tab admin có form tạo cơ cấu, bảng campaign/allocation cùng nút duyệt, post và clawback theo
  quyền `reward`/`adjust`.
- Tác giả mở `/author/monthly_rewards.html` từ trang tổng hợp thu nhập để xem kỳ, truyện, hạng,
  số Xu, trạng thái và thời điểm hạch toán/phát hành. Trang gọi `GET /author/monthly-rewards`;
  danh tính tác giả luôn được suy ra từ phiên đăng nhập.
- Trạng thái `POSTED_PENDING` được hiển thị là **Chờ phát hành** và không được mô tả như số dư có
  thể rút. Chỉ `RELEASED` mới phản ánh thưởng đã chuyển sang ví doanh thu tác giả.
- Template mới nằm ở runtime nền; các theme không có bản ghi đè vẫn nhận trang này. Liên kết từ
  trang thu nhập đã được đồng bộ ở runtime, `green` và `orange`, là ba file thực sự tồn tại.
- Nghiệm thu tự động bao gồm API/route, catalog Việt–Trung, parse JavaScript sau mô phỏng render,
  artifact `green`/`orange`, migration chạy lặp, MySQL/concurrency và đối soát zero-sum.

`reward.enabled` vẫn mặc định `false` theo chính sách rollout, không phải vì thiếu giao diện. Chỉ
bật sau shadow mode và phê duyệt vận hành; không thao tác trực tiếp bằng SQL để thay thế service.
Kiểm tra trực quan đầy đủ trên bốn theme được thực hiện cùng cổng M7.

## Cutover `ENV → DB_SHADOW → DB`

1. Giữ `GAMIFICATION_CONFIG_SOURCE=ENV`, mở **Cấu hình Gamification** và chọn **Import ENV**.
   `source_hash` làm thao tác import idempotent.
2. Rà soát diff, submit, dùng tài khoản thứ hai phê duyệt rồi schedule revision. Không bật feature
   mới trong revision cutover đầu tiên.
3. Chuyển một instance sang `DB_SHADOW`. Hành vi vẫn theo ENV; metric
   `gamification_config_shadow_diff_keys` phải về 0. Giá trị `-1` nghĩa là không đọc/validate được
   snapshot DB: instance vẫn phục vụ bằng ENV nhưng cutover phải dừng cho đến khi metric refresh
   failure ngừng tăng và phép so sánh trở lại 0.
4. Theo dõi revision, tuổi snapshot, refresh/activation/scheduler error ít nhất một chu kỳ vận hành.
5. Chuyển một front instance sang `DB`, chạy regression đọc/ghi, sau đó chuyển front/admin/crawl còn
   lại. `max(gamification_config_revision)-min(gamification_config_revision)` phải bằng 0.
6. Khi ổn định, xóa 38 biến runtime cũ khỏi file deploy; vẫn giữ sáu bootstrap control và salt file.

Nếu DB snapshot lỗi hoặc stale, chuyển source về `ENV` và restart. Không sửa trực tiếp bảng config.
Riêng `DB_SHADOW`, lỗi DB không được làm mất snapshot ENV; nguồn `DB` vẫn fail-closed nếu chưa từng
có last-known-good và khóa đường ghi khi snapshot quá stale.

Nguyên tắc: bật `season.enabled` và `vote.enabled`, giữ `reward.enabled` tắt, chạy trọn một kỳ, rồi so sánh snapshot xếp hạng với một truy vấn SQL nguồn **viết độc lập** — không tái sử dụng mapper của ứng dụng. Nếu tái dùng mapper thì một lỗi trong mapper sẽ tự xác nhận chính nó.

## Migration

Schema nền gamification bắt đầu tại `doc/sql/20260729_gamification_monthly_ticket.sql`; các lần mở
rộng kỳ đặc biệt, level reward, risk/public policy và runtime config tiếp tục bằng migration tăng dần.
`doc/sql/20260818_gamification_runtime_config.sql` tạo snapshot/audit DB, policy bundle version hóa,
trigger bất biến, backfill `policy_version`, `runtime_config_revision` và `release_eligible_at`.
`doc/sql/20260819_gamification_dynamic_config_p1_hardening.sql` khóa state transition, giữ public policy
theo đúng phiên bản runtime, ngăn chuyển dữ liệu vào policy đã phát hành và giới hạn throughput cấu hình.

Image `novel-migrations` đóng gói hai migration này thành `V2026081801__gamification_runtime_config.sql`
và `V2026081901__gamification_dynamic_config_p1_hardening.sql`. Compose chạy Flyway migrate rồi validate trước khi
front/admin/crawl được khởi động; không còn shell loop hoặc volume mount SQL rời. Không sửa checksum
migration đã phát hành và không UPDATE/DELETE audit để rollback.

### Nghiệm thu MySQL và concurrency

Trên Windows, sau khi Docker Desktop đã chạy:

```powershell
.\scripts\verify-gamification.ps1
```

Script kiểm tra Compose, khởi động MySQL, chạy migration hai lần rồi chạy
`GamificationMySqlIntegrationTest`, `MonthlyTicketConcurrencyIT` và `MonthlySeasonConcurrencyIT`
bằng datasource MySQL trực tiếp. Bộ test chứng minh vote không tiêu kép, hai closer chỉ tạo một
snapshot, batch 3 lỗi có thể resume đúng checkpoint/hash, pause→retry hoạt động và trigger cấm
FINALIZED quay về REVIEW. MySQL chỉ được publish trên `127.0.0.1:${MYSQL_HOST_PORT:-3307}`, không
mở ra LAN. Dùng `-ConfigOnly` để chỉ kiểm tra cấu hình hoặc `-StopAfter` để dừng MySQL sau khi test.

Workflow `.github/workflows/gamification-mysql.yml` gọi lại chính script này trên pull request và
nhánh chính với credential MySQL tạm thời. Release không được coi là đã qua cổng P0 nếu job
`Gamification MySQL invariants` chưa xanh.

## Xử lý event và tiến độ nhiệm vụ

`GamificationEventDrainSchedule` chỉ chạy khi ACTIVE revision đồng thời bật `eventEnabled`,
`questEnabled` và đã qua `GamificationConfigValidator`. Mỗi lượt lấy tối đa
`eventDrainBatchSize` event `PENDING` có `attempt < eventMaxAttempt`, theo thứ tự `id` tăng dần.

Mỗi event được xử lý trong transaction `REQUIRES_NEW`:

1. Đọc event và claim bằng `version`; worker khác claim trước thì trả `NOT_OWNER` và không ghi tiến độ.
2. Tìm các `quest_definition` đang hoạt động theo `event_type`.
3. Tạo projection bằng `INSERT IGNORE`, rồi tăng tối đa tới `target_count`. Chu kỳ `DAILY` dùng
   `yyyy-MM-dd`, `WEEKLY` dùng ISO week `yyyy-Www`, `ONE_TIME` dùng `ALL`.
4. Có quest khớp thì event thành `PROCESSED`; không có quest khớp thì thành `SKIPPED`.
5. Nếu transaction lỗi, claim và tiến độ rollback. `GamificationEventFailureWriter` mở transaction
   riêng để tăng `attempt`, lưu tối đa 500 ký tự lỗi và chuyển sang `FAILED` khi hết lượt thử.

Không xóa event để chạy lại: trigger `trg_gamification_event_no_delete` bảo vệ event như bằng chứng
nghiệp vụ. Điều tra lỗi bằng `source_key`, `status`, `attempt`, `processed_at` và `error_message`.
Alert worker có mã `GAMIFY-ALERT-009`.

Acceptance MySQL còn chứng minh `CHAPTER_PURCHASED` hoàn tất `DAILY_PAID_CHAPTER` đúng một lần,
worker chạy lại trả `NOT_OWNER`, và event không có quest tương ứng được lưu `SKIPPED`.

### Danh sách và nhận thưởng nhiệm vụ

- `GET /user/gamification/quests` dùng danh tính từ JWT/cookie. Không truyền ngày thì server lấy
  ngày theo `zoneId` của revision; tham số `date` chỉ dùng để xem tiến độ, không dùng để claim.
- `POST /user/gamification/quests/{questCode}/claim` luôn claim chu kỳ hiện tại theo một snapshot
  `Instant` duy nhất. Client không được gửi `userId`, ngày, reward hoặc idempotency key.
- Service khóa `user_quest_progress ... FOR UPDATE`, xác minh đủ `target_count`, rồi ghi EXP ledger,
  cập nhật profile/level, cấp lot Đuốc qua `MonthlyTicketService.grant()` và cuối cùng ghi
  `quest_claim` trong cùng transaction.
- Khóa lần nhận là `QUEST_CLAIM:<userId>:<questCode>:<periodKey>`; EXP và Đuốc có khóa riêng
  `QUEST_EXP:...` và `QUEST_TICKET:...`. Retry trả lại claim cũ với `alreadyClaimed=true`, không ghi
  ledger hoặc lot thứ hai.
- Khi level tăng, service ghi event `LEVEL_REACHED` theo khóa
  `GAMIFY:LEVEL_REACHED:<userId>:<level>:<ruleVersion>`. Chưa có consumer cấp Đuốc theo level trong
  chính sách v1, nên không được coi event này là phần thưởng đã phát.
- Khi liệt kê hoặc nhận thưởng, service chọn tối đa một `quest_campaign` có `status=ACTIVE` và thời
  điểm server thuộc `[start_at, end_at)`. Không có campaign phù hợp thì dùng `DEFAULT`; reward
  campaign được hợp nhất theo từng loại với reward mặc định, nên campaign chỉ ghi đè `TICKET` vẫn
  giữ EXP mặc định. `quest_claim`, EXP ledger, Đuốc ledger và event lên cấp cùng snapshot
  `policy_version` của campaign đã chọn.
- Nếu có hơn một campaign ACTIVE chồng lấn, service fail-closed trước khi phát thưởng. MySQL IT kiểm
  tra biên trái bao gồm, biên phải loại trừ và trường hợp chồng lấn.

### Quản trị campaign nhiệm vụ

- Tab **Campaign nhiệm vụ** yêu cầu quyền xem `novel:gamification:view`; mọi thao tác ghi yêu cầu
  `novel:gamification:config` và được `@Log` ghi audit quản trị.
- Tạo campaign luôn bắt đầu ở `DRAFT`. Mã `DEFAULT` được giữ riêng cho reward nền và không được dùng
  làm mã campaign. Cửa sổ thời gian cùng `policy_version` không được sửa sau khi tạo.
- Reward chỉ thay được khi campaign còn `DRAFT`; mỗi nhiệm vụ có thể cấu hình EXP, Đuốc hoặc cả hai.
  Giá trị 0 nghĩa là loại đó fallback về reward `DEFAULT` khi runtime hợp nhất kết quả.
- Kích hoạt yêu cầu campaign còn thời hạn, có ít nhất một reward và không giao với campaign ACTIVE
  khác. Transaction kích hoạt dùng isolation `SERIALIZABLE`; concurrency IT chạy hai admin đồng thời
  và chứng minh chỉ một campaign trở thành `ACTIVE`. Runtime vẫn giữ hàng rào fail-closed độc lập.
- Campaign `ACTIVE` chỉ chuyển tiếp sang `CLOSED`; campaign đã đóng không mở lại. Muốn chạy lại phải
  tạo campaign mới để giữ lịch sử cấu hình đã phát thưởng.

Acceptance MySQL chứng minh claim `DAILY_READING` chỉ tạo một `quest_claim`, một bút toán EXP, một
bút toán/lot Đuốc, profile vượt ngưỡng lên level 2 và retry không nhân đôi. Trigger bất biến vẫn chặn
sửa `user_exp_ledger` và `quest_claim`.

### Điểm danh và auto-claim

- `POST /user/gamification/check-in` không nhận body hoặc `userId`; danh tính luôn lấy từ phiên đăng
  nhập. Endpoint chỉ hoạt động khi đồng thời bật `event.enabled` và `quest.enabled`.
- Service chụp đúng một `Instant`, tính ngày theo `zoneId` của revision, khóa profile rồi cập nhật
  `last_checkin_date`, streak hiện tại, longest streak và version trong transaction nguồn.
- Lần đầu bắt đầu streak 1; ngày kế tiếp tăng một; bỏ ngày thì reset về 1. Cùng ngày trả
  `alreadyCheckedIn=true`; ngày cũ hơn lịch sử bị từ chối. Phiên bản v1 không bù ngày.
- Event dùng khóa `CHECKIN:<userId>:<localDate>` và loại `CHECK_IN_COMPLETED`. Orchestrator cố ý không
  mở transaction bao ngoài: transaction nguồn phải commit trước khi processor `REQUIRES_NEW` đọc
  event, cập nhật `DAILY_CHECK_IN` và auto-claim reward.
- Retry sau khi request trước đã xử lý event nhận `NOT_OWNER`, rồi đọc lại claim idempotent; không
  tăng streak, progress, EXP hoặc Đuốc lần hai. `SKIPPED`, event thiếu hoặc lỗi processor đều
  fail-closed và không claim reward.
- Response trả streak, longest streak, EXP/Đuốc của claim, số dư mới và `nextCheckIn` là đầu ngày kế
  tiếp theo `Asia/Ho_Chi_Minh`.

Acceptance MySQL chứng minh hai request cùng ngày chỉ tạo một event `PROCESSED`, một progress, một
`quest_claim` và một bút toán EXP 10. Bộ unit test bao phủ lần đầu, nối chuỗi, đứt chuỗi, replay,
timestamp lệch ngày, ngày lùi, event thiếu, `SKIPPED`, lỗi processor và `NOT_OWNER`.

### Heartbeat đọc đã xác minh

- `POST /user/gamification/reading-heartbeat` là endpoint đăng nhập riêng; request không nhận
  `userId`. Server kiểm tra lại quyền đọc `bookId`/`bookIndexId` trước khi ghi.
- Mỗi lần mở trang tạo `sessionId` ngẫu nhiên 32 ký tự hex. Request đầu có `sequence=0` và
  `activeSeconds=0`, chỉ mở session; các sequence sau tăng đúng một.
- `reading_heartbeat_receipt` bất biến theo `(session_id, sequence_no)`. Cùng sequence/cùng payload
  trả replay; cùng sequence/payload khác trả idempotency conflict. Client giữ nguyên pending payload
  khi lỗi mạng và không gửi request mới khi request cũ đang chạy.
- Client lấy mẫu mỗi giây và chỉ tích lũy khi `document.visibilityState !== 'hidden'` cùng
  `document.hasFocus()`. Heartbeat thường chạy mỗi 60 giây, gửi tối đa 120 active seconds; sự kiện
  `visibilitychange`/`pagehide` dùng `keepalive` khi không có request đang chạy.
- Server render `readingHeartbeatEnabled=true` chỉ khi đồng thời bật event, quest và toàn bộ cấu hình
  hợp lệ. Khi flag tắt, client không tạo session và không gọi endpoint; sau khi bật, client chỉ khởi
  động heartbeat khi `/user/reader-state` xác nhận phiên đăng nhập và chương được phép đọc. Client
  dừng khi mất đăng nhập, heartbeat không hợp lệ hoặc đạt trần ngày.
- Server không tin tuyệt đối `activeSeconds`: elapsed time giữa hai request là trần. Với chu kỳ mặc
  định 60 giây, request đến trước 30 giây hoặc sau hơn 120 giây chỉ cập nhật sequence/session mà
  không cộng thời gian.
- Test biên xác nhận khoảng cách đúng 120 giây vẫn được tính, còn 121 giây bị dừng theo chính sách
  `2 × interval`; không dùng ngưỡng 180 giây cũ.
- `reading_daily_counter` được khóa trước session để nhiều tab của cùng người dùng không vượt trần
  cấu hình, mặc định 180 phút/ngày. Mỗi phút tròn tạo đúng một event
  `READ:<userId>:<sessionId>:<localDate>:<minuteBucket>` loại `READING_MINUTE_VERIFIED`.
- Transaction ghi session/counter/receipt/event commit trước; orchestrator xử lý event bằng
  `REQUIRES_NEW`. Acceptance MySQL chứng minh replay không nhân receipt, counter, event, progress,
  claim hoặc EXP; trigger chặn sửa/xóa receipt.

Không nối reward vào `/book/analytics/read-event`: endpoint analytics vẫn cố ý ẩn danh và nhận
`visitorId`/`durationSeconds` do client tự khai. Dữ liệu đó phù hợp thống kê tổng hợp nhưng không đủ
làm bằng chứng phát EXP.

Giới hạn chống gian lận v1: focus/visibility là tín hiệu phía client nên vẫn có thể bị giả lập;
backend chỉ giảm rủi ro bằng xác thực, quyền đọc, sequence, receipt, elapsed time, ngắt quãng và trần
ngày. Chưa có device attestation, phát hiện nhiều tài khoản hoặc mô hình bất thường. Consumer thưởng
khi lên level vẫn chưa triển khai; không bật thưởng level production cho tới khi đường này được nghiệm
thu. Browser regression thật cho timer throttle, `pagehide` và mobile background vẫn còn pending.

### Trang hành trình đọc

Trang `/user/quests.html` có bản desktop và mobile. Hai template chỉ chứa cấu trúc cùng catalog
`data-*`; `gamification-api.js` phụ trách HTTP, `gamification.js` phụ trách state/render và
`gamification.css` phụ trách layout. UI dùng `textContent`/`createElement`, không dựng dữ liệu API
bằng `innerHTML`; action claim là `button`, có focus ring, trạng thái loading/error/empty và
`aria-live`.

Khối điểm danh dùng cùng asset và catalog `data-*`. Khi bấm, UI khóa nút, hiển thị trạng thái đang
xử lý, gọi endpoint rồi render reward/streak/thời điểm kế tiếp bằng locale Việt Nam. Replay cùng ngày
hiển thị **Đã điểm danh hôm nay** thay vì mô tả reward như vừa được cấp lại.

Asset nằm ở runtime base nên theme thiếu file sẽ fallback; liên kết từ trang tài khoản được đồng bộ
vào hai overlay thực sự tồn tại là `green` và `orange`. Bốn build riêng `green`, `orange`, `dark`,
`blue` đã được kiểm tra đều chứa trang desktop/mobile, API JS, UI JS và CSS. Việc này chưa thay cho
browser regression về kích thước chữ, contrast và thao tác cảm ứng trên thiết bị thật.

### Widget Thắp Đuốc và Bảng Đuốc tháng

- Chi tiết truyện desktop/mobile nạp fragment chung `common/monthly_ticket :: book_widget`. Green,
  orange và dark gọi cùng fragment từ overlay; blue dùng runtime fallback.
- Widget chỉ hiện khi `ticket.enabled` và `vote.enabled` hoạt động. Khách chưa đăng nhập thấy lời mời
  đăng nhập nhưng không bị tự chuyển khỏi trang truyện; feature tắt thì widget giữ ẩn.
- UI hiển thị kỳ, tổng Đuốc của truyện, số dư, lot còn dư có `expireAt` gần nhất và
  `maxTicketsPerRequest` do server trả. Không hard-code trần 10 ở JavaScript.
- Khi thắp hơn một Đuốc, UI yêu cầu xác nhận. Lỗi mạng sau submit giữ nguyên `clientRequestId`, khóa
  số lượng và cho phép bấm lại cùng payload; lỗi nghiệp vụ có mã thì bỏ pending ID để người dùng sửa.
- Sau vote thành công, tổng và số dư lấy từ response transaction; account được đọc lại để cập nhật lot
  sắp tắt sau khi FIFO đã tiêu.
- Trang xếp hạng gọi API công khai theo `period`, phân trang 20 dòng, hiển thị realtime cho kỳ OPEN và
  snapshot cho kỳ đã chốt. Mọi tên truyện/tác giả/ảnh được tạo bằng DOM + `textContent`, không đưa dữ
  liệu API vào `innerHTML`.
- Trang chủ desktop/mobile gọi cùng API với `limit=5`, hiển thị Top Đuốc, kỳ, trạng thái
  realtime/snapshot, mốc khóa sổ và thời gian còn lại tính từ `cutoffAt` tuyệt đối của server. Đồng hồ
  cập nhật mỗi phút và dừng khi kỳ khóa sổ; mã feature-disabled `7001` giữ toàn bộ khối ẩn.
- `gamification-api.js` là tầng HTTP, `monthly-ticket.js` quản lý state/render và
  `gamification.css` quản lý giao diện. Toàn bộ chuỗi đi qua catalog `data-*` Việt/Trung parity.

Bốn build theme đã xác nhận artifact chứa fragment, JS, CSS và lời gọi fragment trong cả chi tiết
truyện, ranking và trang chủ desktop/mobile (blue mobile dùng runtime fallback). Chưa chạy browser
thật với dữ liệu kỳ mở, nên contrast theo theme, focus order, đồng hồ và modal xác nhận native vẫn cần
regression thủ công trước khi bật production.

## Hạn chế đã biết

- **Không có CSRF token.** Toàn bộ API hiện có của `novel-front` dựa vào cookie `Authorization` và `credentials: 'same-origin'`. Các endpoint gamification theo đúng khuôn đó và không mở rộng thêm bề mặt tấn công, nhưng cũng không khắc phục hạn chế sẵn có.
- **Chống nhiều tài khoản chưa tự động.** Hàm băm địa chỉ IP được lưu trên bản ghi vote để phân tích sau, nhưng không có luật chặn tự động ở phiên bản v1.
- **Thu hồi sau khi tác giả đã rút tiền là thủ công.** Xem QĐ-2 trong tài liệu chính sách.
