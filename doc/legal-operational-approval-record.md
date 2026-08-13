# Biên bản phê duyệt pháp lý và vận hành trước production

> Tài liệu kiểm soát phát hành, không thay thế ý kiến tư vấn pháp lý hoặc thuế của đơn vị hành nghề
> đủ thẩm quyền. Bản chưa có đủ chữ ký và bằng chứng luôn có trạng thái **NO-GO**.

## 1. Thông tin kiểm soát tài liệu

| Trường | Giá trị |
|---|---|
| Mã biên bản | `NP-PROD-LEGAL-OPS-2026-01` |
| Phiên bản | `0.2-draft` |
| Ngày lập | 09/08/2026 |
| Trạng thái | **DỰ THẢO — NO-GO** |
| Release candidate | `[[BẮT BUỘC: Git commit SHA hoặc tag]]` |
| Môi trường | Production Việt Nam |
| Pháp nhân vận hành | `[[BẮT BUỘC: tên pháp nhân/hộ kinh doanh]]` |
| Mã số thuế | `[[BẮT BUỘC: mã số thuế đã xác minh]]` |
| Chủ sở hữu biên bản | `[[BẮT BUỘC: họ tên, chức danh]]` |
| Ngày hết hiệu lực gần nhất | `[[BẮT BUỘC: ngày rà soát lại, tối đa 12 tháng sau ngày ký]]` |

Quy ước trạng thái:

- `CHỜ PHÊ DUYỆT`: có nền kỹ thuật nhưng thiếu quyết định hoặc bằng chứng bên ngoài.
- `BLOCKED`: tồn tại lỗ hổng kỹ thuật/pháp lý không thể bù bằng chữ ký.
- `APPROVED CÓ ĐIỀU KIỆN`: chỉ hợp lệ khi điều kiện, thời hạn và người chịu trách nhiệm được ghi rõ.
- `APPROVED`: đủ bằng chứng, chữ ký và không còn điều kiện mở.
- `REVOKED`: phê duyệt bị thu hồi; tính năng liên quan phải quay về fail-closed.

Checkbox không phải chữ ký. Tên gõ tay không phải bằng chứng phê duyệt nếu thiếu chữ ký số, chữ ký
trực tiếp hoặc liên kết tới hệ thống phê duyệt nội bộ có audit.

## 2. Phạm vi và điều kiện No-Go

Biên bản bao phủ:

1. Xác minh danh tính tác giả/người nhận tiền (KYC nội bộ).
2. Thuế, hóa đơn/chứng từ và phân loại thu nhập.
3. Bản quyền, tiếp nhận thông báo và gỡ bỏ nội dung.
4. Bảo vệ dữ liệu cá nhân.
5. Chấp thuận thanh toán định kỳ và thay đổi giá.
6. Hoàn tiền, chargeback và khiếu nại.
7. Payout thủ công theo nguyên tắc bốn mắt.

Production là **NO-GO** nếu có ít nhất một điều kiện sau:

- một miền trong bảng quyết định chưa ở trạng thái `APPROVED` hoặc `APPROVED CÓ ĐIỀU KIỆN` còn hiệu lực;
- thiếu chữ ký của Pháp lý, Thuế/Tài chính, Bảo vệ dữ liệu/An toàn thông tin và người đại diện có thẩm quyền;
- bằng chứng không gắn với đúng release candidate hoặc đã hết hiệu lực;
- `AUTHOR_PAYOUT_ENABLED`, `FINANCIAL_VOUCHER_ISSUANCE_ENABLED` hoặc
  `VNPAY_RECURRING_ENABLED` được bật trước khi miền tương ứng được duyệt;
- chưa diễn tập thu hồi consent, hoàn tiền, gỡ nội dung, sự cố dữ liệu hoặc payout bốn mắt;
- cùng một người có thể tự duyệt và hoàn tất payout mà không có kiểm soát kỹ thuật độc lập.

Việc tồn tại tài liệu này không tự làm tăng điểm sẵn sàng production. Chỉ bằng chứng đã kiểm tra và
chữ ký hợp lệ mới được dùng khi tính lại khả năng phát hành trên 85%.

## 3. Tóm tắt quyết định hiện tại

| Miền | Trạng thái ban đầu | Nền kỹ thuật đã xác minh | Thiếu để được ký |
|---|---|---|---|
| KYC | `CHỜ PHÊ DUYỆT` | Mã hóa PII, HMAC chống trùng, che dữ liệu, audit bất biến | Quy trình/provider xác minh danh tính, xử lý sai lệch và retention |
| Thuế/chứng từ | `CHỜ PHÊ DUYỆT` | Có snapshot số tiền, trường khấu trừ, cờ phát hành chứng từ | Ý kiến thuế, phân loại thu nhập, tỷ lệ áp dụng, hóa đơn/chứng từ hợp pháp |
| Bản quyền | `CHỜ PHÊ DUYỆT` | Có report, evidence, moderation và trạng thái gỡ bài | Chính sách công khai, SLA, counter-notice và người chịu trách nhiệm |
| Dữ liệu cá nhân | `CHỜ PHÊ DUYỆT` | Mã hóa KYC, audit truy cập và hạn chế log | Hồ sơ xử lý dữ liệu, đánh giá tác động, retention/deletion, DPA và chuyển dữ liệu |
| Recurring consent | `CHỜ PHÊ DUYỆT` | Snapshot plan/giá, mandate, price consent, cancel/revoke | Nội dung consent được duyệt, credential/provider smoke và bằng chứng hiển thị |
| Refund/chargeback | `CHỜ PHÊ DUYỆT` | Refund toàn phần, clearing, reversal và audit bất biến | Chính sách công khai, tiêu chí, SLA, quyền duyệt và đối soát provider |
| Payout bốn mắt | `CHỜ PHÊ DUYỆT` | Quyền/actor approve–execute tách biệt; service, SQL guard và check constraint chặn cùng actor; MySQL concurrency đạt | Diễn tập hai người thật, đối soát sao kê/provider, gắn bằng chứng với RC và chữ ký Tài chính/Vận hành |

## 4. Căn cứ cần được đơn vị pháp lý rà soát

Danh sách dưới đây là điểm bắt đầu cho luật sư/chuyên gia phụ trách, không phải kết luận áp dụng:

- [Luật Bảo vệ dữ liệu cá nhân 91/2025/QH15](https://vbpl.vn/TW/Pages/ivbpq-luocdo.aspx?ItemID=179252),
  có hiệu lực từ 01/01/2026.
- [Nghị định 356/2025/NĐ-CP](https://vbpl.vn/TW/Pages/vbpq-toanvan.aspx?ItemID=187276),
  quy định chi tiết Luật Bảo vệ dữ liệu cá nhân và thay thế Nghị định 13/2023/NĐ-CP từ 01/01/2026.
- [Luật Bảo vệ quyền lợi người tiêu dùng 19/2023/QH15](https://vbpl.vn/bocongan/Pages/vbpq-toanvan.aspx?ItemID=161263)
  và [Nghị định 55/2024/NĐ-CP](https://vbpl.vn/TW/Pages/vbpq-toanvan.aspx?ItemID=167051&Keyword=).
- [Luật Giao dịch điện tử 20/2023/QH15](https://vbpl.vn/TW/Pages/vbpq-luocdo.aspx?ItemID=165913)
  cho giá trị và lưu giữ bằng chứng điện tử.
- [Luật sửa đổi Luật Sở hữu trí tuệ 07/2022/QH15](https://vbpl.vn/TW/Pages/vbpq-vanbanlienquan.aspx?ItemID=164373)
  và [Nghị định 17/2023/NĐ-CP](https://vbpl.vn/boyte/Pages/vbpq-toanvan.aspx?ItemID=160235)
  về quyền tác giả, quyền liên quan.
- [Luật Thuế thu nhập cá nhân 109/2025/QH15](https://vanban.chinhphu.vn/?docid=216495&pageid=27160),
  có hiệu lực từ 01/07/2026; cách phân loại khoản trả cho tác giả phải có ý kiến thuế riêng.
- [Luật Phòng, chống rửa tiền 14/2022/QH15](https://vanban.chinhphu.vn/?classid=1&docid=207710&pageid=27160&typegroupid=3).
  Pháp lý phải xác định Khởi Thư có phải đối tượng báo cáo hay chỉ cần xác minh người nhận tiền theo
  hợp đồng/kiểm soát gian lận; không tự gọi quy trình nội bộ là KYC tuân thủ AML nếu chưa có kết luận.

Người rà soát phải ghi thêm văn bản mới, văn bản chuyển tiếp hoặc văn bản chuyên ngành áp dụng tại
ngày ký. Nếu căn cứ thay đổi, biên bản chuyển về `CHỜ PHÊ DUYỆT` cho tới khi rà soát lại.

## 5. Danh mục bằng chứng

Không lưu CCCD, tài khoản ngân hàng, secret, dữ liệu thẻ hoặc bản kê khai thuế trực tiếp trong Git.
Chỉ ghi mã tài liệu, checksum và vị trí kho tài liệu được kiểm soát truy cập.

| Mã | Bằng chứng bắt buộc | Chủ sở hữu | Vị trí/checksum | Trạng thái |
|---|---|---|---|---|
| `E-ORG-01` | Giấy tờ pháp nhân và MST còn hiệu lực | Pháp lý | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-KYC-01` | Quy trình xác minh danh tính và xử lý sai lệch | Pháp lý/Vận hành | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-KYC-02` | Hợp đồng/provider hoặc biên bản diễn tập xác minh thủ công | Vận hành | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-TAX-01` | Ý kiến thuế cho bản quyền, thưởng, Xu và payout | Thuế | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-TAX-02` | Ma trận khấu trừ, kê khai, hóa đơn/chứng từ | Thuế/Kế toán | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-IP-01` | Điều khoản tác giả và cam kết quyền khai thác | Pháp lý/Nội dung | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-IP-02` | Quy trình notice/takedown/counter-notice và SLA | Pháp lý/Nội dung | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-DP-01` | Bản kiểm kê hoạt động xử lý dữ liệu | Bảo vệ dữ liệu | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-DP-02` | Hồ sơ đánh giá tác động/chuyển dữ liệu nếu áp dụng | Bảo vệ dữ liệu | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-DP-03` | Lịch retention/deletion và biên bản diễn tập quyền chủ thể | Bảo vệ dữ liệu | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-REC-01` | Bản nội dung consent/đổi giá/hủy đã duyệt | Pháp lý/Sản phẩm | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-REC-02` | Provider/UAT: mandate, charge, QueryDr, revoke | Thanh toán | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-REF-01` | Chính sách refund/chargeback và SLA công khai | Pháp lý/CS | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-REF-02` | Diễn tập refund, provider settlement và ledger reconciliation | Tài chính/QA | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-4EYE-01` | Test chứng minh actor tách biệt theo phase payout | Kỹ thuật/QA | `20260817_author_payout_four_eyes.sql`; `AuthorPayoutFourEyesMySqlIntegrationTest`; 3/3 MySQL, gate 24 lớp/62 test | Đạt kỹ thuật trên worktree; chờ gắn RC |
| `E-4EYE-02` | Diễn tập maker-checker và đối soát sao kê | Tài chính/Vận hành | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-RC-01` | CI, release manifest/checksum, image digest, SBOM, scan, attestation và E2E của RC | Release Manager | `[[BẮT BUỘC]]` | Chưa nộp |
| `E-RC-02` | Promotion record gắn biên bản đã ký với tag/commit/digest và workflow run | Release Manager/Đại diện phê duyệt | `[[BẮT BUỘC]]` | Chưa nộp |

## 6. Quyết định chi tiết

### 6.1. KYC và xác minh người nhận tiền

Nền kỹ thuật hiện tại:

- dữ liệu pháp lý/ngân hàng dùng AES-256-GCM, trường dò trùng dùng HMAC và API chỉ trả bốn số cuối;
- mọi lần xem PII và thay đổi trạng thái có audit;
- phiên bản chấp thuận kỹ thuật hiện tại là `KYC-VN-2026-01`;
- admin có thể chuyển hồ sơ sang `VERIFIED`, nhưng validation định dạng không phải xác minh từ nguồn tin cậy.

Điều kiện phê duyệt:

- [ ] Xác định rõ mục đích, căn cứ xử lý và bộ trường tối thiểu cho từng bước.
- [ ] Xác định platform có/không thuộc đối tượng báo cáo AML bằng ý kiến pháp lý.
- [ ] Có provider hoặc checklist thủ công xác minh giấy tờ, liveness/sai lệch và tài khoản nhận tiền.
- [ ] Có quy trình từ chối, khiếu nại, cập nhật, khóa payout và kiểm tra lại định kỳ.
- [ ] KYC `VERIFIED` không do người thực hiện payout tự phê duyệt.
- [ ] Có retention/deletion riêng cho hồ sơ bị từ chối, hết quan hệ và dữ liệu payout phải lưu theo luật.

Quyết định: `[ ] APPROVED` `[ ] APPROVED CÓ ĐIỀU KIỆN` `[ ] REJECTED`

Điều kiện/ngoại lệ: `[[BẮT BUỘC nếu không APPROVED toàn phần]]`

### 6.2. Thuế, hóa đơn và chứng từ

Không lấy các giá trị mặc định `tax-rate`, `share-proportion`, `exchange-proportion` hoặc
`AUTHOR_PAYOUT_VND_PER_XU` làm kết luận thuế. Trường `withheldTaxVnd` hiện chỉ kiểm tra từ 0 đến tổng
tiền; hệ thống chưa tự chứng minh cách phân loại thu nhập hay mức khấu trừ đúng luật.

Điều kiện phê duyệt:

- [ ] `E-TAX-01` phân loại riêng doanh thu bản quyền, thưởng gamification, khuyến mại và khoản khác.
- [ ] Ma trận nêu cá nhân cư trú/không cư trú, hồ sơ MST, thời điểm khấu trừ/kê khai và xử lý điều chỉnh.
- [ ] Tỷ giá Xu → VND, kỳ chốt, mức rút tối thiểu và quy tắc làm tròn được ký bởi Tài chính.
- [ ] Xác định tài liệu PDF hiện có là phiếu nội bộ hay chứng từ hợp pháp; không gọi là hóa đơn điện tử
  nếu chưa có cơ sở pháp lý/provider.
- [ ] `FINANCIAL_VOUCHER_ISSUANCE_ENABLED` chỉ bật sau khi pháp nhân/MST và mẫu chứng từ được duyệt.
- [ ] Có đối soát tổng gross, withheld, net, clearing, sao kê và tờ khai theo kỳ.

Quyết định: `[ ] APPROVED` `[ ] APPROVED CÓ ĐIỀU KIỆN` `[ ] REJECTED`

Phân loại/mức áp dụng đã duyệt: `[[BẮT BUỘC: tham chiếu E-TAX-01/E-TAX-02, không ghi dữ liệu cá nhân]]`

### 6.3. Bản quyền và gỡ bỏ nội dung

Điều kiện phê duyệt:

- [ ] Điều khoản tác giả xác nhận quyền sở hữu/quyền khai thác và trách nhiệm với nội dung tải lên.
- [ ] Mẫu thông báo vi phạm yêu cầu định danh người yêu cầu, tác phẩm, URL, căn cứ quyền và tuyên bố.
- [ ] Có SLA tiếp nhận, bảo toàn bằng chứng, hạn chế/gỡ, thông báo tác giả và counter-notice.
- [ ] Quyền xem evidence tách khỏi quyền ra quyết định; mọi quyết định có lý do và audit.
- [ ] Có quy tắc repeat infringer và khôi phục nội dung khi khiếu nại được chấp nhận.
- [ ] Nội dung crawler mặc định không nhận tiền/Đuốc và không xuất bản nếu chưa chứng minh quyền.
- [ ] Có địa chỉ liên hệ công khai và lịch trực xử lý yêu cầu khẩn cấp.

SLA được duyệt: `[[BẮT BUỘC: tiếp nhận / khóa tạm / quyết định / phản hồi khiếu nại]]`

Quyết định: `[ ] APPROVED` `[ ] APPROVED CÓ ĐIỀU KIỆN` `[ ] REJECTED`

### 6.4. Bảo vệ dữ liệu cá nhân

Điều kiện phê duyệt:

- [ ] Hoàn thành inventory: chủ thể, trường dữ liệu, mục đích, căn cứ, hệ thống, bên nhận và retention.
- [ ] Xác định vai trò bên kiểm soát/xử lý dữ liệu với VNPAY, email, object storage, AI và crawler.
- [ ] Hoàn thành hồ sơ đánh giá tác động và chuyển dữ liệu ra nước ngoài nếu thuộc trường hợp áp dụng.
- [ ] Công bố thông báo riêng tư bằng tiếng Việt trước khi thu thập; consent không gộp mục đích tùy chọn.
- [ ] Có đầu mối/bộ phận bảo vệ dữ liệu và quy trình tiếp nhận quyền của chủ thể.
- [ ] Diễn tập truy cập, chỉnh sửa, rút consent, hạn chế/phản đối và xóa; ghi nhận đúng thời hạn pháp lý.
- [ ] Có DPA với processor, quy trình sự cố, thông báo, backup deletion và rotation khóa PII.
- [ ] Dữ liệu nhạy cảm không xuất hiện trong log, ticket, analytics, SBOM hoặc artifact CI.

Retention đã duyệt:

| Nhóm dữ liệu | Thời hạn/căn cứ | Cách xóa/ẩn danh | Chủ sở hữu |
|---|---|---|---|
| Tài khoản độc giả | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| KYC/tài khoản ngân hàng | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Ledger/thuế/chứng từ | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Consent/mandate/audit | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Bản thảo/nội dung đã xóa | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Log/backup | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |

Quyết định: `[ ] APPROVED` `[ ] APPROVED CÓ ĐIỀU KIỆN` `[ ] REJECTED`

### 6.5. Recurring consent và thay đổi giá

Nội dung consent phải hiển thị trước thao tác xác nhận, không dùng checkbox chọn sẵn và phải lưu được
bằng chứng gắn với user, plan, `planVersion`, giá, nguồn tiền, thời gian, locale và phiên bản điều khoản.

Điều kiện phê duyệt:

- [ ] Nêu rõ pháp nhân thu tiền, số tiền, chu kỳ, ngày dự kiến, quyền lợi, thời điểm bắt đầu và kết thúc.
- [ ] Nêu nguồn chính/fallback, trường hợp provider `PENDING/UNKNOWN` và không trừ nguồn thứ hai sớm.
- [ ] Nêu cách hủy, thời điểm hủy có hiệu lực và quyền đọc tới cuối kỳ hiện tại.
- [ ] Giá mới được thông báo trước tối thiểu 7 ngày và yêu cầu consent snapshot mới; im lặng không phải đồng ý.
- [ ] Có bằng chứng mandate/token do VNPAY cấp; không lưu dữ liệu thẻ.
- [ ] Thu hồi consent dừng auto-renew nội bộ ngay và mandate đi qua queue revoke fail-closed.
- [ ] Email/UI receipt không chứa token, secret hoặc PII vượt mức cần thiết.
- [ ] `E-REC-02` chứng minh create/charge/QueryDr/retry/fallback/revoke/idempotency trên credential được duyệt.

Phiên bản consent được duyệt: `[[BẮT BUỘC: ví dụ REC-VN-2026-01]]`

Quyết định: `[ ] APPROVED` `[ ] APPROVED CÓ ĐIỀU KIỆN` `[ ] REJECTED`

### 6.6. Refund, chargeback và khiếu nại

Nền kỹ thuật hiện chỉ hỗ trợ refund/chargeback **toàn phần**. `APPROVED` mới là giữ Xu vào
`REFUND_CLEARING`; chỉ provider settlement có mã tham chiếu mới là hoàn tất VND. Không mô tả refund
một phần hoặc refund tự động nếu chưa có code và kiểm thử tương ứng.

Điều kiện phê duyệt:

- [ ] Công bố trường hợp đủ điều kiện: trừ trùng, giao dịch trái phép, không cấp quyền, lỗi nội dung và trường hợp khác.
- [ ] Công bố trường hợp không đủ điều kiện nhưng không loại trừ quyền bắt buộc của người tiêu dùng.
- [ ] Chốt cửa sổ gửi yêu cầu, thời gian phản hồi, thời gian provider xử lý và kênh escalation.
- [ ] Người yêu cầu, người duyệt, người xác nhận provider và người đối soát được tách vai trò.
- [ ] Retry không hoàn hai lần; ledger, `order_pay`, provider và projection được đối soát zero-sum.
- [ ] Chargeback chỉ ghi sau bằng chứng ngân hàng; debt của ví và cách bù nợ được thông báo cho người dùng.
- [ ] Khi provider thất bại, Xu giữ được release lại; không sửa trực tiếp balance hay audit.

SLA được duyệt: `[[BẮT BUỘC: tiếp nhận / quyết định / hoàn provider / escalation]]`

Quyết định: `[ ] APPROVED` `[ ] APPROVED CÓ ĐIỀU KIỆN` `[ ] REJECTED`

### 6.7. Payout thủ công bốn mắt

#### Blocker kỹ thuật đã được khắc phục

Migration `20260817_author_payout_four_eyes.sql` lưu riêng `approved_by`/`executed_by`, tách quyền
`novel:authorFinance:payout:approve` và `novel:authorFinance:payout:execute`. Service từ chối sớm actor
trùng; câu `UPDATE` atomic và check constraint tiếp tục chặn khi bypass service hoặc có race. Worker hệ
thống mới tất toán ledger từ `SETTLEMENT_PENDING` sang `PAID` và ghi audit `SYSTEM`; không có API admin
tự sửa settlement ledger.

Ngày 09/08/2026, Flyway migrate/validate 40 migration hai lượt trên MySQL 8.4; 24 lớp integration/
concurrency đạt 62/62, riêng `AuthorPayoutFourEyesMySqlIntegrationTest` đạt 3/3. Đây là bằng chứng kỹ
thuật trên worktree, chưa thay thế diễn tập vận hành, sao kê/provider hoặc bằng chứng gắn với RC.

Điều kiện kỹ thuật bắt buộc:

- [x] Tách permission tối thiểu thành `payout:approve` và `payout:execute`; settlement ledger chỉ do worker hệ thống thực hiện.
- [x] Lưu actor riêng cho approve và execute; không dùng `reviewed_by` legacy làm nguồn kiểm soát bốn mắt.
- [x] Backend, câu update atomic và check constraint từ chối approver tự execute.
- [x] Quyền xem PII tách khỏi quyền approve/execute trên controller và giao diện.
- [x] Auto-payout nội bộ chỉ bắt đầu sau approve của actor khác.
- [ ] Provider payout phải có hợp đồng/kiểm soát maker-checker được duyệt trước khi bật auto-payout.
- [ ] Mọi override khẩn cấp yêu cầu hai approver, lý do, ticket và audit bất biến; không sửa DB trực tiếp.
- [x] Unit, permission, packaging và MySQL concurrency chứng minh actor trùng bị chặn, hai executor chỉ một người claim được.
- [ ] Đối soát sao kê do actor không tham gia approve/execute thực hiện.

Ma trận vai trò mục tiêu:

| Bước | Vai trò | Không được trùng với | Bằng chứng |
|---|---|---|---|
| Duyệt KYC | KYC reviewer | Người payout/reconcile cùng hồ sơ | KYC audit |
| Duyệt số tiền/thuế | Payout approver | Payout executor | Withdrawal audit |
| Tạo lệnh ngân hàng | Payout executor | Payout approver | Provider reference/maker log |
| Xác nhận settlement | Settlement verifier | Executor | IPN/sao kê/query |
| Đối soát | Reconciler | Approver và executor | Báo cáo zero-sum ký số |

Quyết định hiện tại: **`CHỜ PHÊ DUYỆT — NO-GO`**

`E-4EYE-01` đã có bằng chứng kỹ thuật trên worktree. Chỉ được ký sau khi gắn bằng chứng với đúng RC,
hoàn thành `E-4EYE-02` và có chữ ký Tài chính/Vận hành.

## 7. Kịch bản diễn tập bắt buộc

| Mã | Kịch bản | Kết quả đạt | Report |
|---|---|---|---|
| `DR-KYC-01` | Hồ sơ giả/sai lệch và khiếu nại | Không payout, audit đủ, xử lý bởi actor khác | `[[BẮT BUỘC]]` |
| `DR-DP-01` | Yêu cầu truy cập/rút consent/xóa | Đúng SLA, lan tới processor/backup theo policy | `[[BẮT BUỘC]]` |
| `DR-IP-01` | Notice → khóa → counter-notice | Đúng SLA, bảo toàn evidence, không mất audit | `[[BẮT BUỘC]]` |
| `DR-REC-01` | Price consent bị từ chối | Dừng auto-renew cuối kỳ, không charge giá mới | `[[BẮT BUỘC]]` |
| `DR-REC-02` | Provider pending và revoke lỗi | Không fallback sớm, retry/audit đúng | `[[BẮT BUỘC]]` |
| `DR-REF-01` | Refund lặp và provider fail | Không hoàn trùng, Xu được release đúng | `[[BẮT BUỘC]]` |
| `DR-PAY-01` | Cùng actor cố approve và execute | Bị chặn ở backend/database | `[[BẮT BUỘC]]` |
| `DR-PAY-02` | Hai actor payout rồi reconcile | Clearing zero-sum, sao kê và audit khớp | `[[BẮT BUỘC]]` |

## 8. Chữ ký theo miền

Mỗi người ký xác nhận đã đọc bằng chứng thuộc phạm vi của mình, không chỉ đọc checkbox.

| Miền | Người chịu trách nhiệm | Chức danh | Quyết định | Ngày | Chữ ký/tham chiếu |
|---|---|---|---|---|---|
| KYC/AML applicability | `[[BẮT BUỘC]]` | Pháp lý/Compliance | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Thuế/chứng từ | `[[BẮT BUỘC]]` | Thuế/Kế toán trưởng | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Bản quyền | `[[BẮT BUỘC]]` | Pháp lý/Nội dung | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Dữ liệu cá nhân | `[[BẮT BUỘC]]` | DPO/An toàn thông tin | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Recurring/refund | `[[BẮT BUỘC]]` | Pháp lý/Sản phẩm/Tài chính | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |
| Payout bốn mắt | `[[BẮT BUỘC]]` | Tài chính/Vận hành | `CHỜ PHÊ DUYỆT` | 09/08/2026 | `E-4EYE-01` đạt kỹ thuật; chờ RC, `E-4EYE-02` và chữ ký |
| Kỹ thuật/An toàn | `[[BẮT BUỘC]]` | Engineering/Security | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |

## 9. Quyết định phát hành cuối

Chỉ người đại diện theo pháp luật hoặc người được ủy quyền bằng văn bản được ký mục này.

- `[ ] GO` — tất cả miền đã đạt, bằng chứng còn hiệu lực và RC khớp digest.
- `[ ] GO CÓ ĐIỀU KIỆN` — không áp dụng cho blocker payout bốn mắt, secret hoặc dữ liệu cá nhân.
- `[x] NO-GO` — trạng thái mặc định của bản `0.2-draft`.

Lý do hiện tại:

1. Chưa có bằng chứng/phê duyệt bên ngoài cho bảy miền.
2. Chưa diễn tập payout bốn mắt với hai người thật và đối soát sao kê/provider (`E-4EYE-02`).
3. Chưa gắn biên bản với commit/tag và artifact digest cụ thể.
4. Chưa cấu hình required reviewers cho GitHub Environment `production` và chưa chạy workflow promotion.

| Người quyết định | Chức danh/căn cứ ủy quyền | Ngày | Chữ ký/tham chiếu |
|---|---|---|---|
| `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` | `[[BẮT BUỘC]]` |

## 10. Thu hồi và rà soát lại

Phê duyệt tự động hết hiệu lực khi:

- thay pháp nhân, MST, provider thanh toán/KYC/ngân hàng hoặc quốc gia lưu trữ dữ liệu;
- đổi mục đích xử lý dữ liệu, nhóm dữ liệu nhạy cảm, retention hoặc processor;
- đổi giá/cơ chế recurring, refund, tỷ giá Xu, thuế hoặc payout;
- có sự cố dữ liệu, gian lận payout, chargeback nghiêm trọng hoặc yêu cầu của cơ quan nhà nước;
- luật/căn cứ tại mục 4 thay đổi;
- release làm thay đổi contract hoặc invariant được biên bản phê duyệt.

Người phát hiện điều kiện thu hồi phải tắt feature flag liên quan, mở incident/change ticket và cập
nhật trạng thái thành `REVOKED` trước khi tiếp tục xử lý giao dịch mới.

## 11. Lịch sử phiên bản

| Phiên bản | Ngày | Tác giả | Thay đổi | Trạng thái |
|---|---|---|---|---|
| `0.1-draft` | 09/08/2026 | Codex, theo yêu cầu chủ dự án | Tạo mẫu và ghi baseline đã xác minh | `NO-GO` |
| `0.2-draft` | 09/08/2026 | Codex, theo yêu cầu chủ dự án | Ghi nhận maker-checker kỹ thuật đã đạt; giữ NO-GO vì thiếu RC, diễn tập và chữ ký | `NO-GO` |
