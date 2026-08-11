# Chính sách gamification — phiên bản v1

Trạng thái: **đang hiệu lực**. Mã phiên bản: `v1`.

Tài liệu này chốt sáu quyết định kinh tế và chống lạm dụng mà [gamified-universe-plan.md](../gamified-universe-plan.md) để mở. Không được viết code tài chính hoặc chạy migration production khi chưa có tài liệu này.

Mọi bản ghi season, campaign, lot, bút toán và allocation đều lưu cột `policy_version` cùng
`runtime_config_revision` khi phù hợp. Policy được quản lý bằng `gamification_policy_bundle`; bản đã
`PUBLISHED` và dữ liệu con bất biến. Đổi chính sách nghĩa là clone draft `v2`, maker-checker rồi phát
hành và áp cho bản ghi mới; **không sửa lịch sử đã chốt theo `v1`**.

## Nguyên tắc bao trùm

1. Ngọn Đuốc **không phải tài sản tài chính**. Nó không đổi ngược thành Xu hay tiền, không chuyển nhượng, và **không có trạng thái nợ**. Đây là khác biệt cố ý so với ví Xu độc giả (ví Xu cho phép số dư âm trong tình huống chargeback).
2. Mọi ngưỡng kinh tế đều là typed runtime snapshot hoặc dữ liệu policy trong bảng. Không hard-code
   trong service và không đọc lại ENV sau cutover DB.
3. Mọi thao tác sai được sửa bằng **sự kiện đảo**, không sửa và không xóa lịch sử.
4. Mọi tính năng mặc định **TẮT**. Bật từng cờ theo lộ trình rollout.

---

## QĐ-1 — Nguồn cấp Ngọn Đuốc và xử lý chargeback

**Quyết định:** Ngọn Đuốc chỉ đến từ hoạt động của người đọc (điểm danh, nhiệm vụ, thăng cấp) và từ thao tác cấp thủ công hoặc khuyến mại của quản trị viên có audit.

`ticket.grant-on-top-up-enabled = false` ở v1. Không bán Ngọn Đuốc, không cấp theo số Xu đã nạp.

**Hệ quả và cũng là lý do chọn mặc định này:** vì không tồn tại đường nạp tiền dẫn tới Ngọn Đuốc, một giao dịch nạp Xu bị chargeback **không tạo ra khoản nợ Ngọn Đuốc nào**. Toàn bộ lớp phức tạp khó nhất của giai đoạn nền — thu hồi phiếu đã tiêu cho một truyện đã lên bảng xếp hạng — bị loại bỏ khỏi phạm vi thay vì được xử lý bằng logic tinh vi.

**Nếu bật cờ này ở phiên bản sau**, đường xử lý bắt buộc là:

- Thu hồi theo **LIFO** (`ORDER BY id DESC`) trên các lot còn `remaining_amount > 0` có `source_ref` trỏ tới `outTradeNo` bị chargeback.
- Phần đã tiêu ghi bản ghi `REVOKE_SHORTFALL` và đưa vào hàng đợi REVIEW để xử lý bằng biện pháp tài khoản.
- **Không bao giờ** đẩy `monthly_ticket_account.available_balance` xuống âm. Ràng buộc `CHECK` ở tầng lưu trữ sẽ từ chối.
- Vote tương ứng chuyển `VOIDED` nếu kỳ chưa `FINALIZED`; nếu đã `FINALIZED` thì giữ nguyên kết quả và xử lý ở tầng vận hành.

### Hạn dùng

Mỗi lot có hạn dùng `ticket.lot-validity-days` ngày kể từ lúc cấp, mặc định **60 ngày**. Lot hết hạn được đóng bằng job có audit, ghi bút toán `EXPIRE`.

Khi tiêu, hệ thống lấy **lot sắp tắt trước** (`expire_at` nhỏ nhất, tie-break theo `id` tăng dần). Quy tắc này có lợi cho người dùng vì giảm tối đa số Ngọn Đuốc bị tắt mà chưa dùng.

### Giới hạn chống lạm dụng

| Ngưỡng | Khóa cấu hình | Mặc định |
|---|---|---|
| Số phiếu tối đa mỗi lần gửi | `vote.max-tickets-per-request` | 10 |
| Số lần bỏ phiếu mỗi ngày | `vote.max-votes-per-day` | 20 |
| Số phiếu tiêu mỗi ngày | `vote.max-tickets-per-day` | 50 |
| Số phiếu tối đa cho một truyện trong một kỳ | `vote.max-tickets-per-book-per-season` | 100 |

Ngày được tính theo `Asia/Ho_Chi_Minh`.

**Giới hạn có hiệu lực ở tầng database**, không chỉ ở Redis. Bảng `monthly_ticket_daily_counter` cập nhật bằng câu `UPDATE` có điều kiện trong cùng transaction với việc tiêu phiếu. Lý do: `RateLimitAspect` hiện có tụt về bộ đếm trong bộ nhớ theo từng instance khi Redis không khả dụng, nên nó không đủ làm hàng rào cuối cho một tài sản có giá trị.

---

## QĐ-2 — Quỹ thưởng tác giả

**Quyết định:** ngân sách **Xu cố định** cho mỗi kỳ, khai báo trong `reward_fund_campaign`.

Mặc định `enabled = false`, `budget_xu = 0`. Không có thưởng nào được chi cho tới khi một chiến dịch được duyệt tường minh.

- **Cơ cấu giải:** Top 10.
- **Tỷ lệ chia được snapshot vào campaign lúc phê duyệt.** Lúc chi tiền, hệ thống đọc tỷ lệ từ campaign, **không** đọc lại cấu hình. Điều này khiến kết quả tái tạo được kể cả sau khi cấu hình thay đổi.
- **Một tác giả chỉ nhận một giải cao nhất trong mỗi kỳ.** Các tác phẩm còn lại của tác giả đó vẫn giữ nguyên thứ hạng trên bảng, nhưng allocation ghi `status = 'SKIPPED_DUPLICATE_AUTHOR'` và `amount_xu = 0`.
- **Phần ngân sách dôi ra do quy tắc trên không được tái phân bổ** ở v1. Tái phân bổ tạo vòng lặp tính toán và làm kết quả khó tái tạo.
- **Làm tròn:** chia theo tỷ lệ rồi làm tròn xuống (`floor`) về Xu nguyên. Phần dư cộng vào giải nhất và ghi ở cột `rounding_adjustment_xu`, sao cho `SUM(amount_xu) = budget_xu` đúng đến từng Xu.

### Tie-break xếp hạng

Áp dụng lần lượt, bốn tiêu chí cho một thứ tự toàn phần nên kết quả tái tạo được đúng từng dòng:

1. Tổng phiếu giảm dần.
2. Số người bỏ phiếu duy nhất giảm dần.
3. Thời điểm đạt số phiếu cuối cùng **sớm hơn** thì thắng.
4. `book_id` tăng dần.

### Vòng đời tiền thưởng

Thưởng **không** được ghi thẳng vào ví doanh thu tác giả.

```
FINALIZED → ghi vào REWARD_CLEARING (POSTED_PENDING)
          → hết cửa sổ khiếu nại → chuyển sang AUTHOR_REVENUE_XU (RELEASED)
```

Lý do bắt buộc phải có tài khoản clearing: ràng buộc `chk_wallet_available_balance` cấm số dư âm với mọi ví không thuộc hệ thống, và `WalletLedgerServiceImpl.post()` chỉ cho phép trạng thái nợ với ví `READER_XU`. Nếu ghi thẳng vào ví tác giả thì tác giả rút được ngay, và khi cần thu hồi, bút toán đảo sẽ thất bại vì đẩy ví xuống âm. Đi qua clearing khiến bút toán đảo chỉ chạm hai ví hệ thống — luôn thành công.

**Cửa sổ khiếu nại: 7 ngày** (`reward.claim-window-days`).

Sau khi đã release và tác giả đã rút, hệ thống **không** thu hồi tự động. Tranh chấp ở giai đoạn đó xử lý bằng quy trình vận hành ngoài sổ cái. Rủi ro này được giảm bằng `auto-finalize = false` và cửa sổ khiếu nại 7 ngày.

### Tách quyền

Người chốt kỳ (`novel:gamification:finalize`) không phải người duyệt tiền (`novel:gamification:reward`), và không phải người cấp phiếu (`novel:gamification:grant`).

---

## QĐ-3 — Cảnh giới

**Quyết định:** cảnh giới thuần túy là danh xưng và skin hiển thị. `realm.affects-benefits = false`.

Cảnh giới **không** thay đổi phần thưởng, không thay đổi giới hạn, không thay đổi quyền truy cập nội dung. Điều này giữ hệ cấp độ nằm ngoài đường tài chính.

**Cooldown đổi cảnh giới: 24 giờ, và tối đa một lần mỗi ngày địa phương.** Điều kiện nào chặt hơn thì áp dụng — đổi lúc 23:00 thì không đổi lại được lúc 00:30 hôm sau dù đã qua ngày mới, vì chưa đủ 24 giờ.

Mỗi lần đổi ghi một dòng vào `gamification_profile_audit` gồm cảnh giới cũ, cảnh giới mới, loại người thao tác và lý do.

Cấp độ được tính lại từ `user_exp_ledger` theo `rule_version` của **từng bút toán**, không theo bảng quy tắc hiện hành. Vì vậy phát hành `level_rule` phiên bản mới không làm thay đổi cấp độ đã đạt trong quá khứ.

---

## QĐ-4 — Thuế, tỷ giá và cửa sổ khiếu nại

**Quyết định:** tiền thưởng xếp hạng là **Xu thuần**. Nó **không** đi qua `author.income.tax-rate`, `author.income.share-proportion` hay `author.income.exchange-proportion`.

Lý do: ba tỷ lệ đó là công thức chia doanh thu bản quyền theo chương, được áp lúc `purchaseChapter()` tách phần của tác giả khỏi phần nền tảng. Tiền thưởng xếp hạng không phát sinh từ một giao dịch bán hàng nào, nên áp lại công thức chia doanh thu sẽ cắt đôi khoản thưởng lần thứ hai mà không có cơ sở kế toán.

Khi tác giả rút tiền, khoản thưởng đã release nằm chung trong `AUTHOR_REVENUE_XU` nên tự động đi qua đúng `author.payout.vnd-per-xu`, `author.payout.minimum-xu`, quy trình KYC và nguyên tắc bốn mắt hiện có. **Không tạo đường chuyển tiền VND riêng cho tiền thưởng.**

Nghĩa vụ thuế thu nhập cá nhân được xử lý ở tầng payout bằng công cụ tính thuế hiện có, giống hệt thu nhập bản quyền.

---

## QĐ-5 — Điều kiện tác phẩm đủ tư cách nhận phiếu

**Quyết định:** tác phẩm nhập từ `novel-crawl` **không đủ điều kiện** nhận phiếu và không đủ điều kiện nhận thưởng.

Lý do: trả tiền cho nội dung không phải do tác giả trên nền tảng sáng tác là rủi ro pháp lý về bản quyền.

| Điều kiện | Biểu thức |
|---|---|
| Đã được duyệt | `book.audit_status = 1` |
| Đã lên kệ | `book.status = 1` |
| Không phải nội dung crawl | `book.crawl_source_id IS NULL AND book.crawl_book_id IS NULL` |
| Có tác giả xác định | `book.author_id IS NOT NULL` |
| Không bị khóa khỏi gamification | không có bản ghi `active` trong `gamification_book_block` |

Lưu ý về tên cột: `book.status` là trạng thái lên kệ (0 trong kho, 1 lên kệ). `book.book_status` là trạng thái sáng tác (0 đang ra, 1 đã hoàn thành) và **không** được dùng cho eligibility.

Phân loại độ tuổi được kiểm ở tầng service bằng `AgeRatingUtil`, không đưa vào câu SQL eligibility, vì kết quả phụ thuộc vào người đọc chứ không phụ thuộc vào tác phẩm.

### Chống tự bỏ phiếu

Tác giả và cộng tác viên không được bỏ phiếu cho tác phẩm họ quản lý.

Cột `author.user_id` là NULLABLE và dữ liệu lịch sử có bản ghi giá trị NULL. Vì vậy phép so sánh phải viết bằng `NOT EXISTS` kèm `IS NOT NULL`:

```sql
AND NOT EXISTS (
    SELECT 1 FROM author a
     WHERE a.id = book.author_id
       AND a.user_id IS NOT NULL
       AND a.user_id = :userId)
AND NOT EXISTS (
    SELECT 1 FROM author_book_collaborator c
      JOIN author ca ON ca.id = c.collaborator_author_id
     WHERE c.book_id = book.id
       AND ca.user_id IS NOT NULL
       AND ca.user_id = :userId)
```

**Không dùng `a.user_id <> :userId` hay `NOT IN`.** Với `user_id` là NULL, cả hai biểu thức trả về NULL, điều kiện bị loại khỏi mệnh đề `WHERE`, và chủ sở hữu tác phẩm được coi là hợp lệ. Đây là lỗ hổng thật, không phải rủi ro lý thuyết.

---

## QĐ-6 — Nhiệm vụ bình luận

**Quyết định:** bình luận **không** phải nhiệm vụ hằng ngày. Nó là thành tựu một lần với mã `FIRST_COMMENT_APPROVED` và `period_type = 'ONE_TIME'`.

Lý do đã được xác minh trong mã nguồn:

- `BookServiceImpl.addBookComment()` chỉ cho mỗi người **một bình luận cho mỗi tác phẩm, vĩnh viễn**. Lần thứ hai bị từ chối với mã `HAS_COMMENTS`.
- `book_comment.audit_status` mặc định là chờ duyệt, và chỉ chuyển sang đã duyệt bằng thao tác thủ công của quản trị viên với độ trễ không xác định.

Một nhiệm vụ hằng ngày phụ thuộc vào thao tác thủ công của quản trị viên là nhiệm vụ mà người dùng không thể hoàn thành một cách đáng tin cậy. Giữ nó ở dạng hằng ngày sẽ tạo ra khiếu nại mà đội vận hành không có cách giải quyết.

Nhiệm vụ hằng ngày dựa trên trả lời bình luận (`book_comment_reply`) chỉ được bật khi `quest.reply-quest-enabled = true`, mặc định **false**.

**Bộ nhiệm vụ hằng ngày v1** gồm ba nguồn sự kiện tự động và đáng tin cậy:

1. Điểm danh.
2. Đọc đủ số phút đã được máy chủ xác minh (`quest_definition.target_count`, mặc định 30).
3. Mua hoặc đọc chương trả phí.

### Bình luận bị gỡ sau khi đã nhận thưởng

**Không thu hồi** EXP hoặc Ngọn Đuốc ở v1. Ngọn Đuốc không có trạng thái nợ, nên thu hồi ngược sẽ tạo số dư âm mà ràng buộc `CHECK` từ chối. Trường hợp này đưa vào hàng đợi REVIEW để xử lý bằng biện pháp tài khoản.

---

## Mô hình đe dọa

| Hành vi | Biện pháp ở v1 |
|---|---|
| Gửi lặp cùng một yêu cầu | Khóa idempotency do máy chủ sinh, cộng ràng buộc `UNIQUE` trên sổ cái làm trọng tài cuối |
| Hai yêu cầu đồng thời tiêu vượt số dư | Khóa hàng bằng `SELECT ... FOR UPDATE`, câu `UPDATE` có điều kiện kèm phiên bản lạc quan, và ràng buộc `CHECK` ở tầng lưu trữ |
| Nhiều tài khoản | Chấm điểm theo tuổi tài khoản, velocity, số tài khoản dùng chung device/IP và số vote theo device/IP. Kết quả `REVIEW` vào hàng đợi thủ công; chỉ `BLOCK` khi rule được quản trị viên cấu hình rõ `hard_block = 1`, mọi assessment và quyết định đều được audit |
| Giả lập hành vi đọc | Nhịp tim do máy chủ tính mốc phút, dừng đếm khi khoảng cách hai nhịp vượt hai lần chu kỳ, và trần cứng số phút mỗi ngày |
| Spam bình luận | Đã bị chặn sẵn bởi giới hạn một bình luận cho mỗi tác phẩm và bởi hàng đợi kiểm duyệt |
| Tự bỏ phiếu | Kiểm tra chủ sở hữu và cộng tác viên bằng `NOT EXISTS` an toàn với NULL |
| Thông đồng đẩy hạng | Phát hiện thủ công trong giai đoạn REVIEW trước khi chốt. `auto-finalize = false` khiến mọi kỳ đều phải qua mắt người |

Địa chỉ IP và device ID ẩn danh được lưu dưới dạng SHA-256 kèm muối và domain separation, **không lưu dạng thô**. Không có rule hard-block mặc định; nếu chưa cấu hình rule, hệ thống chỉ ghi nhận và cho phép giao dịch.

---

## Ánh xạ quyết định sang Runtime settings và Policy Studio

Runtime production lấy từ ACTIVE revision trong database. `application.yml` chỉ giữ giá trị ENV cho
bootstrap/`DB_SHADOW`; quản trị viên thay đổi bằng **Cấu hình Gamification** và **Bộ policy**.
Luật chơi công khai là một phần của policy bundle: chỉ được soạn, duyệt và phát hành qua **Bộ
policy (Policy Studio)**. Trang vận hành chỉ đọc lịch sử luật đã phát hành; không còn endpoint publish
độc lập có thể đi vòng maker-checker.

| Quyết định | Khóa | Mặc định |
|---|---|---|
| QĐ-1 nguồn cấp | Cờ khóa `TICKET_GRANT_ON_TOPUP` | `false`, không chỉnh được ở v1 |
| QĐ-1 hạn dùng | `ticketLotValidityDays` | `60` |
| QĐ-1 giới hạn | `voteMaxVotesPerDay`, `voteMaxTicketsPerDay`, `voteMaxTicketsPerBookPerSeason` | `20`, `50`, `100` |
| QĐ-2 quỹ thưởng | `rewardEnabled` | `false` |
| QĐ-2 cửa sổ khiếu nại | `rewardClaimWindowDays`; allocation chụp `release_eligible_at` | `7` |
| QĐ-3 cảnh giới | Cờ khóa `REALM_AFFECTS_BENEFITS`; `realmChangeCooldownHours` | `false`, `24` |
| QĐ-5 truyện crawl | `voteAllowCrawledBooks` | `false` |
| QĐ-6 nhiệm vụ trả lời | Cờ khóa `QUEST_REPLY_ENABLED` | `false` |
| QĐ-6 mục tiêu đọc | `quest_definition.target_count` trong policy bundle | `30` |
| Chốt kỳ tự động | Cờ khóa `SEASON_AUTO_FINALIZE` | `false` |

Phiên bản chính sách hiện hành nằm ở `novel.gamification.policy-version`, giá trị `v1`. Giá trị này được ghi vào mọi bản ghi mới.

## Lịch sử phiên bản

| Phiên bản | Ngày | Thay đổi |
|---|---|---|
| v1 | 2026-07-29 | Bản đầu tiên. Chốt sáu quyết định còn mở trong kế hoạch gốc. |
