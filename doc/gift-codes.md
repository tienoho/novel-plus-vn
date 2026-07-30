# Mã quà tặng

## Phạm vi đã triển khai

Mã quà cấp một trong hai loại quyền lợi:

- `XU`: ghi qua sổ cái kép bằng `WalletLedgerService.creditReaderReward()` và đồng bộ projection
  `user.account_balance`;
- `READING_TICKET`: ghi qua sổ Vé đọc bằng `ReadingTicketService.grant()`, tạo lot có hạn với
  nguồn `GIFT_CODE`.

Ba bảng được tạo bởi `doc/sql/20260802_gift_codes.sql`:

- `gift_campaign`: cấu hình loại/thời lượng reward, cửa sổ hiệu lực, giới hạn toàn chiến dịch và
  giới hạn mỗi người dùng;
- `gift_code`: chỉ lưu HMAC-SHA256, bốn ký tự gợi nhớ và giới hạn lượt dùng từng code;
- `gift_redemption`: biên nhận bất biến liên kết đúng một giao dịch ví hoặc sổ Vé đọc.

Migration `doc/sql/20260806_gift_code_hmac_rotation.sql` bổ sung `hmac_key_id` cho từng code.
Mọi code cũ được gắn `legacy-v1` mà không re-hash; danh tính gồm campaign, key ID, hash, hint và
giới hạn lượt dùng bị trigger chặn sửa.

Redemption, reward ledger và hai bộ đếm code/campaign nằm trong cùng transaction. Nếu biên nhận
hoặc một bộ đếm không ghi được, toàn bộ reward bị rollback. MySQL test đã chứng minh:

- hai người tranh code đơn chỉ có một reward;
- hai code khác nhau tranh slot cuối của campaign chỉ có một reward;
- retry đồng thời cùng user/code trả `ALREADY_REDEEMED` và không ghi lại ledger;
- tái dùng `clientRequestId` cho code khác bị từ chối bằng lỗi miền nghiệp vụ và rollback reward.

## Vòng đời và giới hạn

Campaign bắt đầu ở `DRAFT`, có thể chuyển sang `ACTIVE`, sau đó `CLOSED`. `CLOSED` là trạng thái
kết thúc. Thay đổi trạng thái dùng optimistic version. Code có trạng thái `ACTIVE`, `EXHAUSTED`
hoặc `REVOKED`; hiện hệ thống tự chuyển sang `EXHAUSTED`, chưa có thao tác quản trị thu hồi.

Cửa sổ đổi mã là nửa mở `[start_at, end_at)`. Mỗi lần đổi đồng thời kiểm tra:

- campaign và code đang `ACTIVE`;
- chưa vượt giới hạn campaign;
- chưa vượt giới hạn mỗi user/campaign;
- chưa vượt giới hạn từng code;
- cùng user/code chỉ có một biên nhận.

Code đơn dùng `maxRedemptionsPerCode=1`. Code chiến dịch dùng chung có thể đặt giá trị lớn hơn 1,
nhưng không vượt giới hạn toàn campaign. Mỗi lần phát hành tối đa 1.000 code.

Admin có thể thu hồi code `ACTIVE` chưa phát sinh lượt đổi bằng optimistic version. Revoke và
redeem cùng khóa hàng `gift_code`; MySQL concurrency test chứng minh chỉ một nhánh thắng: hoặc code
thành `REVOKED` và không có reward, hoặc redemption hoàn tất và code thành `EXHAUSTED`.

## API và giao diện

Độc giả đăng nhập đổi mã tại `/user/gift_codes.html`. Trang có bản desktop/mobile trong runtime
base nên được đóng gói cho green, orange, dark và blue. API:

```http
POST /user/gift-codes/redeem
Content-Type: application/json

{
  "code": "ABCD-EFGH-JKLM-NPQR-2345",
  "clientRequestId": "gift_12345678"
}
```

`userId` và thời điểm đổi lấy từ session/server. Code được chuẩn hóa chữ hoa, bỏ khoảng trắng và
dấu gạch nối trước khi băm. Endpoint giới hạn 10 request/phút/user. Mã không tồn tại, hết hạn,
hết lượt và vượt giới hạn đều trả cùng thông báo công khai để không lộ trạng thái code.

Lịch sử phân trang của chính người dùng được đọc tại:

```http
GET /user/gift-codes/redemptions?page=1&limit=20
```

Response không trả `userId`, `clientRequestId`, ID code/campaign hoặc liên kết ledger nội bộ.
Lịch sử vẫn đọc được khi cờ phát hành/đổi mã đang tắt.

Admin dùng trang `/novel/giftCode` với bốn quyền tách biệt:

- `novel:giftCode:view`: xem campaign;
- `novel:giftCode:config`: tạo và đổi trạng thái campaign;
- `novel:giftCode:issue`: phát hành plaintext code.
- `novel:giftCode:revoke`: thu hồi code chưa sử dụng bằng optimistic version.

Trang admin có bảng phân trang code và redemption theo campaign; không hiển thị hash hoặc plaintext
đã phát hành.

Plaintext chỉ xuất hiện trong response của thao tác phát hành và ô hiển thị một lần. Database chỉ
lưu `code_hash`/`code_hint`; logger controller không ghi payload response. Người vận hành phải sao
chép code sang kênh phân phối an toàn ngay lúc phát hành.

## Cấu hình và rollout

Tính năng mặc định tắt:

```dotenv
GIFT_CODE_ENABLED=false
GIFT_CODE_HMAC_KEY_ID=legacy-v1
GIFT_CODE_HMAC_SECRET=replace-with-at-least-32-random-characters
GIFT_CODE_HMAC_VERIFICATION_KEYS=
GIFT_CODE_POLICY_VERSION=v1
```

`GIFT_CODE_HMAC_SECRET` là khóa active, phải lấy từ secret manager, tối thiểu 32 ký tự và giống nhau
giữa mọi instance. `GIFT_CODE_HMAC_KEY_ID` chỉ là định danh công khai 1–32 ký tự gồm chữ, số,
`.`/`_`/`-`; không chứa secret.

`GIFT_CODE_HMAC_VERIFICATION_KEYS` chứa tối đa bảy khóa cũ theo dạng
`keyId:Base64(secret);keyId2:Base64(secret2)`. Các khóa này chỉ dùng tra cứu code cũ; code mới luôn
được phát hành bằng khóa active. Tổng key ring tối đa tám khóa. Cấu hình trùng key ID, Base64 sai,
secret dưới 32 byte hoặc key ID sai định dạng làm tính năng fail closed. Không ghi secret thô hoặc
chuỗi Base64 vào Git, image, artifact, log hay database.

### Quy trình xoay khóa không gián đoạn

1. Chạy migration `20260806` trên mọi database trước khi đổi cấu hình. Xác nhận code hiện hữu có
   `hmac_key_id='legacy-v1'`.
2. Base64-encode chính xác byte UTF-8 của secret cũ trong secret manager; không dùng công cụ có
   thêm newline.
3. Đặt `GIFT_CODE_HMAC_KEY_ID` và `GIFT_CODE_HMAC_SECRET` thành khóa mới; thêm
   `legacy-v1:<Base64-secret-cũ>` vào `GIFT_CODE_HMAC_VERIFICATION_KEYS` trên front và admin.
4. Giữ `GIFT_CODE_ENABLED=false`, restart canary, chạy test redeem một code cũ và phát/redeem một
   code mới. Xác nhận DB ghi key ID mới nhưng không có plaintext.
5. Triển khai cùng key ring tới mọi instance rồi mới bật lại tính năng. Không để các instance dùng
   active key khác nhau.
6. Chỉ loại khóa cũ khỏi danh sách xác minh khi không còn code `ACTIVE`/`EXHAUSTED` cần replay với
   key ID đó và đã hết cửa sổ hỗ trợ/đối soát. Xóa secret cũ khỏi secret manager theo quy trình
   bốn mắt.

Rollback rotation bằng cách khôi phục khóa cũ làm active và giữ khóa mới trong danh sách xác minh;
không sửa `hmac_key_id`, `code_hash` hay biên nhận trong DB.

Trình tự bật:

1. Sao lưu database, chạy `20260802`, `20260803` và `20260806` hai lần trên staging.
2. Cấu hình cùng một secret cho front/admin, giữ `GIFT_CODE_ENABLED=false`.
3. Chạy unit, MySQL integration/concurrency, i18n và build bốn theme.
4. Tạo campaign thử ở `DRAFT`, phát một lô nhỏ và lưu code ngoài hệ thống.
5. Kích hoạt campaign, bật cờ trên canary rồi kiểm tra Xu, Vé đọc, retry và giới hạn.
6. Đối chiếu `gift_redemption` với `ledger_transaction`/`reading_ticket_ledger` trước khi mở rộng.

Rollback bằng cách tắt cờ và đóng campaign. Không xóa biên nhận hoặc sổ cái. Migration có trigger
chặn UPDATE/DELETE `gift_redemption` và có thể chạy lặp mà không nhân đôi menu/permission.

## Kiểm thử có điều kiện

MySQL phải là database cô lập đã chạy đủ migration:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3307/novel_plus?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Ho_Chi_Minh'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-db-test>'
$env:GIFT_CODE_ENABLED = 'true'
$env:GIFT_CODE_HMAC_KEY_ID = 'legacy-v1'
$env:GIFT_CODE_HMAC_SECRET = '<secret-test-toi-thieu-32-ky-tu>'
$env:GIFT_CODE_HMAC_VERIFICATION_KEYS = ''
mvn -pl novel-front -am `
  '-Dtest=GiftCodeMySqlIntegrationTest,GiftCodeConcurrencyIT' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dgift.code.mysql.it=true' '-Dgift.code.concurrency.it=true' test
```

## Phần chưa triển khai

- đóng campaign theo lịch;
- import/export lô phân phối, retention và xác nhận người nhận;
- browser regression thực trên desktop/mobile của cả bốn theme.

Không mở rộng sang chuyển nhượng hoặc quy đổi reward thành tiền pháp định. Xu và Vé đọc tiếp tục
tuân theo ledger/chính sách riêng của từng loại quyền lợi.
