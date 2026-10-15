# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

---

## Stack
- Java 21, Spring Boot 3.5, Maven
- Spring Web, Validation, Spring Data JPA, Spring Security + JWT
- Swagger (springdoc-openapi)
- Spring Mail (gửi mật khẩu tạm khi admin tạo tài khoản)
- PostgreSQL (profile `postgres`, mặc định) / Microsoft SQL Server (profile `sqlserver`, bản cũ) / H2 in-memory (profile `dev`)

---

## 🛠️ Yêu cầu môi trường (Prerequisites)
* **Java:** JDK 17 hoặc JDK 21
* **Maven:** 3.8+ (hoặc dùng Maven Wrapper kèm theo `./mvnw`)
* **Database:** Microsoft SQL Server (database tên `aives`)

---

## 🚀 Hướng dẫn Cài đặt & Khởi chạy nhanh (Quick Start)

### Bước 1: Chuẩn bị file biến môi trường (`.env`)
> ⚠️ **LƯU Ý QUAN TRỌNG:** Backend có cơ chế kiểm tra môi trường nghiêm ngặt. Nếu **chưa có `.env`** hoặc **thiếu biến quan trọng**, ứng dụng sẽ **dừng ngay từ đầu** và hiển thị cảnh báo chi tiết.

1. Tại thư mục `aives-backend`, copy file mẫu:
   ```bash
   cp .env.example .env
   # Trên Windows PowerShell:
   Copy-Item .env.example .env
   ```
2. Mở file `.env` vừa tạo và điền mật khẩu SQL Server của máy bạn:
   ```env
   DB_PASSWORD=mật_khẩu_sql_server_của_bạn
   ```
   *(Các biến khác như `JWT_SECRET`, `DB_USERNAME=sa`, `DB_URL` đã có giá trị sẵn sàng để chạy).*

### Bước 2: Chuẩn bị Database
Tạo database `aives` trên SQL Server rồi chạy các script trong thư mục `database/`:
- **Máy chưa có database**: chạy `database/00_init_aives.sql` trong SSMS. Script tự tạo database `aives` và đủ 17 bảng (bảng nào đã có thì bỏ qua), kèm 3 dòng của bảng `roles`.
- **Database đã có từ trước** (bảng `users` còn cột `role`): chạy `06_roles.sql` rồi `07_exam_max_attempts.sql` trước khi chạy backend bản mới, sau đó chạy `00_init_aives.sql` để tạo nốt các bảng còn thiếu.

### Bước 3: Khởi chạy ứng dụng
Chạy lệnh:
```bash
mvn spring-boot:run
```
Ứng dụng sẽ khởi động tại: `http://localhost:8080`.

---

## 🐳 Triển khai với Docker & Render

### 1. Build & Chạy Docker tại local:
```bash
docker build -t aives-backend .
docker run -p 8080:8080 --env-file .env aives-backend
```

### 2. Triển khai lên Render:
- Tạo **New Web Service** trên Render kết nối với repo GitHub.
- **Root Directory**: `aives-backend` (hoặc để trống nếu repo riêng).
- **Environment**: `Docker` (Render sẽ tự động dùng `Dockerfile`).
- **Cấu hình Environment Variables trên Render Dashboard**:
  - `SPRING_PROFILES_ACTIVE`: `supabase`
  - `DB_URL`: `jdbc:postgresql://<host-pooler-supabase>:6543/postgres?sslmode=require&prepareThreshold=0`
  - `DB_USERNAME`: `postgres.<project-ref>`
  - `DB_PASSWORD`: `<mat_khau_supabase>`
  - `JWT_SECRET`: chuỗi ký tự bí mật >= 32 ký tự
  - `CORS_ALLOWED_ORIGINS`: `*` hoặc domain của frontend Web/Mobile
  - `ADMIN_EMAIL`: `admin@aives.local`
  - `ADMIN_PASSWORD`: `Admin@123`
  - `SEED_DEFAULT_PASSWORD`: `Aives@123`
  - `MAIL_ENABLED`: `false` (hoặc cấu hình Gmail nếu muốn gửi mail thật)
- *Lưu ý*: Ứng dụng đã tự động map cổng qua biến `${PORT}` mà Render cung cấp.

---

## 📖 Tài liệu API (Swagger UI)
Sau khi chạy backend:
* **Chạy local:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **Tài khoản test nhanh:**
  * **Sinh viên:** `testmobile@fpt.edu.vn` / Mật khẩu: `Password@123`
  * **Giảng viên:** `lamnn@fe.edu.vn` / Mật khẩu: `Aives@123`
  * **Quản trị viên:** `admin@aives.local` / Mật khẩu: `Admin@123`

---

## 🏗️ Cấu trúc Dự án



---

## 🔑 Role và Tài khoản
=======
## Chạy local (PostgreSQL)
1. Cài PostgreSQL 14+ (Windows: bản cài của EnterpriseDB, nhớ mật khẩu user `postgres`). **Không cần tạo database bằng tay**:
   khi backend khởi động, `PostgresDatabaseCreator` tự tạo database trong `DB_URL` (vd `aives`, UTF-8) và chạy
   `src/main/resources/db/postgres/00_init_aives.sql` để tạo 18 bảng kèm ràng buộc CHECK / index, nếu chưa có.
   Database / bảng đã có thì bỏ qua, không xoá dữ liệu. Tắt bằng `DB_AUTO_CREATE=false` trong `.env`.
   - Sửa script thì sửa cả `database/postgres/00_init_aives.sql` và bản sao trong `src/main/resources/db/postgres/`.
   - Quy ước tên: khoá chính `<tên bảng>_id`; khoá ngoại mang đúng tên khoá chính nó trỏ tới (`users.roles_id`, `viva_attempts.viva_exams_id`), trừ khoá ngoại theo vai trò (`lecturer_id`, `student_id`...).
2. Khai báo kết nối trong file `.env` (xem `.env.example`): `SPRING_PROFILES_ACTIVE=postgres`, `DB_URL=jdbc:postgresql://localhost:5432/aives`, `DB_USERNAME`, `DB_PASSWORD`.
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
- **Admin tạo tài khoản** (`POST /api/admin/users`): không nhập mật khẩu. Hệ thống sinh mật khẩu tạm 10 ký tự, gửi tới email của người dùng, và đặt `users.must_change_password = 1`.
- Tài khoản đang dùng mật khẩu tạm chỉ gọi được `/api/auth/**`; API khác trả 403 `PASSWORD_CHANGE_REQUIRED` cho tới khi đổi mật khẩu (`PUT /api/auth/me/password`).
- Sinh viên tự đăng ký (`POST /api/auth/register`) vẫn tự đặt mật khẩu, không gửi mail.

---

## 📧 Cấu hình gửi mail
Khai báo trong file `.env` (xem `.env.example`) hoặc biến môi trường:

| Biến | Ý nghĩa |
| :--- | :--- |
| `MAIL_ENABLED` | `false` (mặc định): **không gửi mail**, nội dung email (có mật khẩu tạm) chỉ in ra console của backend, API trả `emailSent = false`. `true`: gửi thật qua SMTP. |
| `MAIL_HOST`, `MAIL_PORT` | Mặc định `smtp.gmail.com`, `587` (STARTTLS). |
| `MAIL_USERNAME` | Địa chỉ dùng để gửi (vd tài khoản Gmail của nhóm). |
| `MAIL_PASSWORD` | Với Gmail là **App Password** 16 ký tự (bật xác minh 2 bước rồi tạo tại https://myaccount.google.com/apppasswords), không phải mật khẩu đăng nhập Gmail. |
| `MAIL_FROM` | Địa chỉ hiện ở ô người gửi, mặc định bằng `MAIL_USERNAME`. |
| `FRONTEND_URL` | Dùng để chèn link đăng nhập vào email, mặc định `http://localhost:5173`. |




## 🤖 Cấu hình AI Gemini
Khai báo trong file `.env`:
* `GEMINI_API_KEY`: API key tạo từ [Google AI Studio](https://aistudio.google.com/apikey). Để trống thì hệ thống vẫn chạy nhưng không có câu hỏi xoay AI.
* `GEMINI_MODEL`: Mặc định `gemini-3.8-flash`.

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

