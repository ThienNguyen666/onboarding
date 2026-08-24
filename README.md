# 🏦 VietBank eKYC Onboarding — Prototype

> Mô phỏng luồng **mở tài khoản NTB qua SDK vendor** (chính chủ bank), dựa trên workflow gốc Orkes Conductor
> `vendor_sdk_ekyc_account_opening_2.json`. Backend Spring Boot đóng vai trò **state machine thay Conductor**,
> đi kèm FE demo dạng "điện thoại giả lập" để test trực quan cả luồng mà không cần Postman.

`Spring Boot 4.1` · `Java 21` · `PostgreSQL` · `Redis` · `React (demo FE)`

---

## 📱 Xem trước

FE demo là 1 khung điện thoại giả lập + **QA Console** (drawer bên phải) để:
- Ép FAIL từng bước OCR/Liveness/NFC → test retry & termination
- Ép kết quả compliance (SUCCESS / NEED_REVIEW / FAILED) không cần đoán SĐT
- Xem OTP trực tiếp (debug endpoint) thay vì cắm SMS gateway thật
- Giả lập dropoff (thoát app giữa chừng) rồi resume lại đúng bước cũ
- Xoá sạch dữ liệu demo để test lại từ đầu

## 🧭 Tại sao có project này?

Đội ngũ đang build workflow thật trên **Orkes Cloud (Apache Conductor)** cho luồng mở tài khoản eKYC.
Trước khi cắm vendor thật (OCR/Liveness/NFC/C06) và Conductor engine thật, project này giúp:

1. **Hiểu đúng nghiệp vụ** — mọi Phase/nhánh rẽ/điều kiện dừng trong workflow JSON gốc đều được giữ nguyên tinh thần.
2. **Demo cho stakeholder** xem trước UI/luồng mà không cần chờ tích hợp vendor.
3. **Có sẵn state machine rõ ràng** để khi porting sang Conductor worker thật, chỉ cần thay lớp thực thi từng Phase, logic nghiệp vụ giữ nguyên.

## 🏗️ Kiến trúc

```
┌─────────────┐      REST/JSON       ┌──────────────────────────┐
│   FE Demo   │ ───────────────────▶ │   OnboardingController     │
│  (React)    │ ◀─────────────────── │   DebugController           │
└─────────────┘                      └───────────┬──────────────┘
                                                   │
                                      ┌────────────▼─────────────┐
                                      │ OnboardingOrchestrationService │
                                      │  (state machine thay Conductor) │
                                      └──┬───────┬───────┬───────┘
                                         │       │       │
                        ┌────────────────┘  ┌────▼───┐  └──────────────┐
                        ▼                    ▼        ▼                 ▼
              CustomerDirectoryService  MockEkycService  OtpService  ComplianceMockService
                        │                    │             │              │
                        ▼                    │             ▼              │
                  PostgreSQL ◀────────────────┴──────  Redis  ────────────┘
             (session, customer, audit log)      (OTP TTL, dropoff cache)
```

- **PostgreSQL**: state chính thức, lâu dài (`onboarding_session`, `customer_record`, `audit_log_entry`).
- **Redis**: chỉ dữ liệu sống ngắn — OTP tự hết hạn theo TTL, cache dropoff-by-phone.
- **Không có Orkes/Conductor thật** — `OnboardingOrchestrationService` đóng vai trò orchestrator: mỗi method = 1 Phase, chuyển Phase được guard bằng `require(sessionId, expectedPhase)`.

## 🔀 Từ workflow Orkes gốc → prototype: rút gọn những gì?

| Phase gốc | Rút gọn trong bản này | Vì sao |
|---|---|---|
| Phase 0 — OAuth client-credential lấy AccessToken từ vendor | Sinh `accessToken` nội bộ (UUID) | SDK là **chính chủ bank**, không có OAuth ngoài để tích hợp thật |
| Phase 2 — nhánh ETB `handle_etb_customer` | Trả `customerType=ETB` rồi dừng | Không mô phỏng app ETB trong scope |
| Phase 3/4/5 — `DO_WHILE` + `SET_VARIABLE` (đặc thù Orkes) | Counter field (`ocrRetryCount`...) ngay trên entity | Cơ chế riêng của Conductor engine; tự code thì retry-loop chỉ là 1 field tăng dần |
| Phase 3/4/5 — gọi vendor OCR/Liveness/NFC thật | Mock: luôn PASS trừ khi `forceFail=true` | Hiểu luồng nghiệp vụ, không tích hợp vendor thật |
| Phase 6c — SMS gateway | Random 6 số, lưu Redis TTL, có endpoint debug xem lại | Dễ debug, không cắm SMS gateway |
| Phase 7 — `FORK_JOIN` song song | Gộp tuần tự trong 1 transaction | Không có 2 hệ thống độc lập thật để chạy song song |
| Phase 7 — `process_account_in_conductor` | `ComplianceMockService`: rule theo số cuối SĐT hoặc ép qua `forceComplianceResult` | Demo đủ 3 nhánh SUCCESS/NEED_REVIEW/FAILED |
| `notify_vendor_*`, `send_ott_*` | Chỉ log console | Không có webhook/SMS/email gateway thật |

**Giữ nguyên tinh thần gốc**: thứ tự phase, mọi điều kiện dừng (device not eligible, hết retry OCR/Liveness/NFC, OTP sai/hết hạn, dưới 18 tuổi, ETB), dropoff resume theo SĐT, NEED_REVIEW không polling trực tiếp lên session.

## 🚀 Chạy thử

```bash
docker compose up -d postgres redis   # chỉ 2 service này, service "app" cần Dockerfile riêng nếu muốn container hoá
./mvnw clean compile                  # build thử trước (sandbox review có thể bị chặn mạng tới Maven Central)
./mvnw spring-boot:run
```

Backend chạy ở `http://localhost:8080`. Mở FE demo (`ekyc-onboarding-demo.jsx`) và trỏ `baseUrl` về địa chỉ trên — nhớ có `CorsConfig` đã bật sẵn nên gọi cross-origin không bị chặn.

## 📡 Thứ tự gọi API (App Vendor)

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
GET  /api/onboarding/debug/sessions                     # (dev) 20 session gần nhất
POST /api/onboarding/debug/reset                        # (dev) xoá sạch dữ liệu demo
```

## 🧪 Cheat-sheet test nhanh

| Muốn test gì | Làm sao |
|---|---|
| Nhánh khách hàng hiện hữu (ETB) | Nhập SĐT `0901111111` hoặc `0902222222` ở Phase 2 |
| Retry & hết lượt thử (OCR/Liveness/NFC) | Bật "Ép FAIL" trong QA Console, thử 4 lần liên tiếp (mặc định max 3) |
| Dropoff / resume đúng bước cũ | Bấm "Giả lập thoát app", mở phiên mới **cùng SĐT** |
| Compliance NEED_REVIEW | SĐT kết thúc bằng `8`/`9`, hoặc ép trực tiếp trong QA Console |
| Compliance FAILED | SĐT kết thúc bằng `0`, hoặc ép trực tiếp trong QA Console |
| Dưới 18 tuổi | Chỉnh `dob` trong `mockPayload` khi gọi OCR (hoặc sửa mock trong `MockEkycService`) |
| Reset toàn bộ demo | Nút "Xoá toàn bộ dữ liệu demo" trong QA Console |

## 🗺️ Roadmap lên Orkes Cloud

- [ ] Thay `OnboardingOrchestrationService` bằng Conductor worker tasks thật (mỗi method public hiện tại ↔ 1 `@WorkerTask`)
- [ ] Áp dụng idempotency pattern (`idempotencyKey`) đúng chuẩn cho các task ghi dữ liệu non-idempotent (`create_ebank_user`, `create_link_id`)
- [ ] Cắm vendor thật cho OCR/Liveness/NFC, gateway SMS thật cho OTP
- [ ] Bật security cho `DebugController` hoặc gỡ hẳn trước khi lên môi trường có dữ liệu thật

## ⚠️ Lưu ý trước khi lên môi trường thật

- `app.onboarding.otp.debug-endpoint-enabled=true` **chỉ bật ở dev/local** — nhớ tắt trước khi deploy.
- `CorsConfig` đang mở `allowedOriginPatterns("*")` cho tiện demo — cần siết lại domain cụ thể của app vendor.