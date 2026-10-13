# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

## Stack
- Java 21, Spring Boot 3.5, Maven
- Spring Web, Validation, Spring Data JPA, Spring Security + JWT
- Swagger (springdoc-openapi)
- Spring Mail (gửi mật khẩu tạm khi admin tạo tài khoản)
- PostgreSQL (profile `postgres`, mặc định) / Microsoft SQL Server (profile `sqlserver`, bản cũ) / H2 in-memory (profile `dev`)

## Cấu trúc
```
src/main/java/com/aives/
├── AivesApplication.java
├── common/
│   ├── config/      # SecurityConfig, CorsConfig, OpenApiConfig, JpaAuditingConfig, DataInitializer
│   ├── security/    # JwtTokenProvider, JwtAuthFilter, UserPrincipal, TokenBlacklist...
│   ├── exception/   # ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── dto/         # ApiResponse<T>
│   ├── entity/      # BaseEntity (id, createdAt, updatedAt)
│   └── controller/  # HealthController
├── integration/
│   └── mail/        # MailService (interface), SmtpMailService (gửi thật), LogOnlyMailService (chỉ in ra console)
└── modules/
    ├── auth/        # users + roles: controller, dto, entity, enums, mapper, repository, service, service/impl, util
    ├── content/     # (chưa làm)
    ├── exam/        # phiên thi (viva_exams) + lượt thi của sinh viên (viva_attempts)
    └── vivaroom/    # (chưa làm)
```

## Chạy local (PostgreSQL)
1. Cài PostgreSQL 14+ (Windows: bản cài của EnterpriseDB, nhớ mật khẩu user `postgres`), rồi tạo database:
   - Nhanh nhất: `psql -U postgres -c "CREATE DATABASE aives ENCODING 'UTF8'"` (hoặc tạo bằng pgAdmin). Bảng do Hibernate tự tạo khi chạy backend (`ddl-auto: update`).
   - Muốn có đủ ràng buộc CHECK / index như bản SQL Server: chạy thêm `psql -U postgres -d aives -f database/postgres/00_init_aives.sql` (chạy lại nhiều lần không sao).
   - Quy ước tên: khoá chính `<tên bảng>_id`; khoá ngoại mang đúng tên khoá chính nó trỏ tới (`users.roles_id`, `viva_attempts.viva_exams_id`), trừ khoá ngoại theo vai trò (`lecturer_id`, `student_id`...).
2. Khai báo kết nối trong file `.env` (xem `.env.example`): `DB_URL=jdbc:postgresql://localhost:5432/aives`, `DB_USERNAME`, `DB_PASSWORD`.
3. Mở project bằng IntelliJ, chờ Maven tải dependency, chạy `AivesApplication`.
4. Lần chạy đầu, `DataInitializer` tự tạo dữ liệu mẫu khi bảng `users` còn trống: 1 admin, 2 lecturer, 7 student
   (email và mật khẩu mẫu xem `DataInitializer` và mục `app.seed` trong `application.yml`).
5. Mở Swagger: http://localhost:8080/swagger-ui.html, gọi `POST /api/auth/login`, copy `accessToken`, bấm **Authorize** rồi dán vào.

Profile khác (đặt `SPRING_PROFILES_ACTIVE` trong `.env`):
- `sqlserver`: SQL Server như bản cũ, script ở `database/*.sql` (chạy trong SSMS).
- `supabase`: PostgreSQL trên Supabase (`SPRING_DATASOURCE_URL`...).
- `dev`: H2 in-memory, không cần cài database (dữ liệu mất khi tắt app).

## Role và tài khoản
- Bảng `roles` tách riêng khỏi `users`: `roles(roles_id, name, description)` với 3 dòng `AD` / `LE` / `ST`. Mỗi user có đúng 1 role qua khoá ngoại `users.roles_id`. `DataInitializer` tự thêm role còn thiếu mỗi lần khởi động.
- Trong code: entity `Role` (bảng `roles`) và enum `RoleName` (`ADMIN`, `LECTURER`, `STUDENT`). API vẫn trả `"role": "ADMIN"` như trước nên FE không phải đổi.
- **Admin tạo tài khoản** (`POST /api/admin/users`): không nhập mật khẩu. Hệ thống sinh mật khẩu tạm 10 ký tự, gửi tới email của người dùng, và đặt `users.must_change_password = 1`. Gửi mail thất bại thì tài khoản **không** được tạo (lỗi `MAIL_SEND_FAILED`).
- Tài khoản đang dùng mật khẩu tạm chỉ gọi được `/api/auth/**`; API khác trả 403 `PASSWORD_CHANGE_REQUIRED` cho tới khi đổi mật khẩu (`PUT /api/auth/me/password`). Mật khẩu mới phải khác mật khẩu hiện tại.
- Sinh viên tự đăng ký (`POST /api/auth/register`) vẫn tự đặt mật khẩu, không gửi mail.

## Cấu hình gửi mail
Khai báo trong file `.env` (xem `.env.example`) hoặc biến môi trường:

| Biến | Ý nghĩa |
| :--- | :--- |
| `MAIL_ENABLED` | `false` (mặc định): **không gửi mail**, nội dung email (có mật khẩu tạm) chỉ in ra console của backend, API trả `emailSent = false`. `true`: gửi thật qua SMTP. |
| `MAIL_HOST`, `MAIL_PORT` | Mặc định `smtp.gmail.com`, `587` (STARTTLS). |
| `MAIL_USERNAME` | Địa chỉ dùng để gửi (vd tài khoản Gmail của nhóm). |
| `MAIL_PASSWORD` | Với Gmail là **App Password** 16 ký tự (bật xác minh 2 bước rồi tạo tại https://myaccount.google.com/apppasswords), không phải mật khẩu đăng nhập Gmail. |
| `MAIL_FROM` | Địa chỉ hiện ở ô người gửi, mặc định bằng `MAIL_USERNAME`. |
| `FRONTEND_URL` | Dùng để chèn link đăng nhập vào email, mặc định `http://localhost:5173`. |

Không commit file `.env` (đã có trong `.gitignore`).

## Quy tắc id của user
`users.users_id` = 2 chữ cái theo role + 6 chữ số ngẫu nhiên, do `UserIdGenerator` sinh:
`ADxxxxxx` (ADMIN), `LExxxxxx` (LECTURER), `STxxxxxx` (STUDENT).

## API hiện có
| Method | Endpoint | Quyền |
| :--- | :--- | :--- |
| GET | `/api/health` | Public |
| POST | `/api/auth/register` | Public (chỉ tạo STUDENT) |
| POST | `/api/auth/login` | Public |
| POST | `/api/auth/logout` | Đã đăng nhập |
| GET | `/api/auth/me` | Đã đăng nhập |
| PUT | `/api/auth/me` | Đã đăng nhập (sửa họ tên) |
| PUT | `/api/auth/me/password` | Đã đăng nhập |
| GET | `/api/admin/users` | ADMIN |
| POST | `/api/admin/users` | ADMIN (tạo ADMIN / LECTURER / STUDENT; body `email`, `fullName`, `role`; trả `{ user, emailSent }`) |
| PUT | `/api/admin/users/{id}/status` | ADMIN (vô hiệu hoá / kích hoạt lại) |
| DELETE | `/api/admin/users/{id}` | ADMIN |
| POST | `/api/exam-sessions` | LECTURER / ADMIN (tạo phiên, trả về mã phiên + mã truy cập; `maxAttempts` 1–10, mặc định 1) |
| GET | `/api/exam-sessions?status=&keyword=&page=&size=` | LECTURER (phiên của mình) / ADMIN (mọi phiên) |
| GET | `/api/exam-sessions/{id}` | Người tạo phiên / ADMIN |
| PUT | `/api/exam-sessions/{id}` | Người tạo phiên / ADMIN |
| DELETE | `/api/exam-sessions/{id}` | Người tạo phiên / ADMIN (đã có sinh viên vào thì chỉ huỷ) |
| POST | `/api/exam-sessions/{id}/regenerate-passcode` | Người tạo phiên / ADMIN |
| POST | `/api/exam-sessions/join` | STUDENT (nhập mã phiên + mã truy cập) |
| GET | `/api/exam-sessions/attempts/{attemptId}` | STUDENT chủ lượt thi |
| POST | `/api/exam-sessions/attempts/{attemptId}/finish` | STUDENT chủ lượt thi (kết thúc bài thi trước khi hết giờ) |

## Phiên thi (module `exam`)
- **Mã phiên** chính là khoá chính `viva_exams_id`: `AIVES_EXAM_yyyy_xxxxxx` (yyyy là năm tạo, 6 số ngẫu nhiên, không trùng); không có cột `exam_code` riêng. Sinh viên gửi mã này ở field `examId` khi vào thi. **Mã truy cập**: 6 chữ số, dùng chung cho cả phiên, người tạo xem lại và tạo lại được.
- **Thời gian**: API nhận ISO-8601 có múi giờ (vd `2026-10-10T08:00:00+07:00`), DB lưu giờ UTC.
- **Trạng thái** tính từ thời gian, không lưu cứng: `UPCOMING` → `ONGOING` → `ENDED`; chỉ `CANCELLED` được lưu (cột `cancelled_at`).
- **Ràng buộc khi tạo**: giờ đóng sau giờ mở; giờ mở không ở quá khứ và không xa quá 365 ngày; mở tối đa 30 ngày; thời lượng 5–300 phút và không dài hơn khoảng mở.
- **Số lượt thi** (`maxAttempts`, cột `viva_exams.max_attempts`): số lượt thi tối đa của mỗi sinh viên trong phiên, từ 1 đến 10, mặc định 1.
- **Sửa**: chưa mở thì sửa tự do; đã mở chỉ sửa tên, mô tả, kéo dài giờ đóng và tăng số lượt thi; đã kết thúc hoặc đã huỷ thì không sửa.
- **Xoá**: chưa có sinh viên vào thì xoá hẳn; đã có thì chuyển sang `CANCELLED`; đã kết thúc và có sinh viên thì không xoá.
- **Vào thi**: sinh viên nhập mã phiên + mã truy cập. Hạn làm bài = `min(lúc vào + thời lượng, giờ đóng phiên)`, chốt ở server.
  - Đang có lượt thi chưa xong: nhận lại đúng lượt đó, đồng hồ không chạy lại.
  - Lượt trước đã kết thúc hoặc hết giờ và còn lượt: tạo lượt thi mới (`viva_attempts.attempt_no` tăng 1), đồng hồ chạy lại từ đầu. Phiên phải còn mở.
  - Đã dùng hết `maxAttempts` lượt: lỗi 409 `EXAM_ATTEMPT_LIMIT_REACHED`.
- **Chống dò mã**: sai 5 lần trong 10 phút thì khoá tài khoản sinh viên đó khỏi việc vào thi cho đến khi lần sai cũ nhất hết hạn (bộ đếm lưu trong RAM).

## Test
`mvn test` chạy `ExamSessionApiTest` (phiên thi, lượt thi) và `AdminUserApiTest` (bảng roles, tạo tài khoản + gửi mail, bắt đổi mật khẩu) trên H2 (profile `dev`), không cần PostgreSQL / SQL Server và không gửi mail thật.
