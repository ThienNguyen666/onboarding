# Onboarding eKYC (prototype) — Spring Boot

Backend mô phỏng luồng mở tài khoản NTB qua SDK vendor (`vendor_sdk_ekyc_account_opening_2.json`,
xem thêm `SDK_NHNT_drawio.png`). Không chạy Orkes Conductor thật — mỗi "Phase" trong workflow gốc
được ánh xạ thành 1 REST endpoint + 1 state machine field trên bảng `onboarding_session`.

## 1. Review workflow gốc & những gì đã tinh gọn cho prototype

| Phase gốc | Rút gọn trong bản này | Vì sao |
|---|---|---|
| Phase 0 — OAuth client-credential lấy AccessToken từ vendor | Sinh `accessToken` nội bộ (UUID), không gọi OAuth thật | SDK là **chính chủ bank**, không phải vendor thứ 3, nên không có hệ thống OAuth ngoài để tích hợp thật trong prototype |
| Phase 2 — nhánh ETB gọi `handle_etb_customer` (điều hướng sang app/luồng ETB riêng) | Chỉ trả `customerType=ETB` rồi dừng, để FE tự điều hướng | Không mô phỏng app ETB trong scope này |
| Phase 3/4/5 — `DO_WHILE` + `SET_VARIABLE` (pattern đặc thù Orkes để đọc kết quả loop) | Thay bằng counter field (`ocrRetryCount`, `livenessRetryCount`, `nfcRetryCount`) ngay trên entity, không cần `workflow.variables` | Đây là cơ chế riêng của Conductor engine — khi tự code, retry-loop là 1 field tăng dần, đơn giản hơn mà hành vi y hệt |
| Phase 3/4/5 — gọi vendor OCR/Liveness/NFC thật | Mock: luôn PASS trừ khi request truyền `forceFail=true` | Theo đúng yêu cầu — mô phỏng để hiểu luồng nghiệp vụ, không tích hợp vendor thật |
| Phase 5 — note "AI engine tự quyết định gọi C06 hay không" | Field `c06Called` cố định `false` trong mock NFC data | Bank không cần biết chi tiết (đúng như note gốc), và không có gateway C06 thật để gọi |
| Phase 6a — `check_customer_type_and_age` tự derive lại ETB từ GTTT | Chỉ giữ check tuổi ≥ 18 (dữ liệu CCCD mock có DOB) | Việc re-detect ETB từ GTTT cần đối chiếu core banking thật — không có ý nghĩa để mock ở phase này khi ETB/NTB đã chốt ở Phase 2 |
| Phase 6c — gửi OTP qua SMS gateway | Random 6 số, lưu Redis với TTL, có endpoint riêng `/debug/sessions/{id}/otp` để xem lại (dev only, tắt qua config) | Đúng yêu cầu: dễ debug, không cắm SMS gateway |
| Phase 7 — `FORK_JOIN` chạy song song `show_result_to_customer` // `process_account_in_conductor` | Gộp tuần tự trong 1 transaction | Không có 2 hệ thống độc lập thật để chạy song song trong prototype; kết quả logic giữ nguyên |
| Phase 7 — `process_account_in_conductor` (đẩy data vào core banking, trả SUCCESS/NEED_REVIEW/FAILED) | `ComplianceMockService`: rule theo số cuối SĐT (`8,9`→NEED_REVIEW, `0`→FAILED, còn lại→SUCCESS), hoặc ép qua `forceComplianceResult` | Giả lập "như đã đẩy data vào core banking là xong" đúng yêu cầu, nhưng vẫn cho demo đủ 3 nhánh |
| `notify_vendor_*`, `send_ott_*` | Chỉ log ra console (`NotificationMockService`) | Không có webhook/SMS/email gateway thật |
| Idempotency key trên `create_ebank_user`, `create_link_id` | Giữ nguyên tinh thần: mỗi `OnboardingSession.id` là idempotency key tự nhiên vì mỗi phase chỉ chạy được đúng 1 lần (guard bằng state machine `phase`/`status`) | Không cần truyền `idempotencyKey` riêng vì không có nhiều instance workflow trùng nhau cho 1 phiên |

**Giữ nguyên đúng tinh thần gốc** (không rút gọn): thứ tự phase, điều kiện dừng (device not eligible,
OCR/Liveness/NFC hết retry, OTP sai/hết hạn, dưới 18 tuổi, ETB), dropoff resume theo SĐT, tách biệt
NEED_REVIEW không polling trực tiếp lên session (chỉ trả trạng thái, không có vòng lặp chờ).

## 2. Kiến trúc

- **Postgres** (`onboarding_session`, `customer_record`, `audit_log_entry`): state chính thức, lâu dài.
- **Redis**: chỉ dữ liệu sống ngắn — OTP (tự hết hạn theo TTL) và cache dropoff-by-phone.
- Không có Orkes/Conductor — `OnboardingOrchestrationService` đóng vai trò orchestrator, mỗi method =
  1 phase, guard chuyển phase bằng `require(sessionId, expectedPhase)`.

## 3. Chạy thử

```bash
docker compose up -d          # Postgres + Redis
./mvnw spring-boot:run
```

> Sandbox này không compile được vì mạng bị chặn tới Maven Central — hãy chạy
> `./mvnw clean compile` ở máy bạn để build thử trước khi chạy.

## 4. Thứ tự gọi API (App Vendor)

```
POST /api/onboarding/sessions                          # Phase 0 -> sessionId, accessToken
POST /api/onboarding/sessions/{id}/device-check         # Phase 1
POST /api/onboarding/sessions/{id}/customer-lookup      # Phase 2 (ETB dừng ở đây)
POST /api/onboarding/sessions/{id}/ocr                  # Phase 3 (gọi lại nếu passed=false & retryAllowed=true)
POST /api/onboarding/sessions/{id}/liveness             # Phase 4
POST /api/onboarding/sessions/{id}/nfc                  # Phase 5
POST /api/onboarding/sessions/{id}/identity-confirm     # Phase 6a
POST /api/onboarding/sessions/{id}/tnc                  # Phase 6b
POST /api/onboarding/sessions/{id}/otp/send             # Phase 6c
GET  /api/onboarding/debug/sessions/{id}/otp            # (dev) xem OTP vừa gửi
POST /api/onboarding/sessions/{id}/otp/verify
POST /api/onboarding/sessions/{id}/account              # Phase 7 -> SUCCESS/NEED_REVIEW/FAILED
GET  /api/onboarding/sessions/{id}                      # trạng thái hiện tại (FE polling)
POST /api/onboarding/sessions/{id}/dropoff              # KH thoát app giữa chừng
```

Test nhanh case NTB xuyên suốt bằng số điện thoại bất kỳ khác `0901111111` / `0902222222` (2 số này
đã seed sẵn là ETB). Số cuối SĐT `8`/`9` → demo NEED_REVIEW, số cuối `0` → demo FAILED ở Phase 7.
