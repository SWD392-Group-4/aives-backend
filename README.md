# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

---

## Stack
- Java 21, Spring Boot 3.5, Maven
- Spring Web, Validation, Spring Data JPA, Spring Security + JWT
- Swagger (springdoc-openapi)
- Spring Mail (gửi mật khẩu tạm khi admin tạo tài khoản)
- Microsoft SQL Server (profile `sqlserver`, mặc định) / H2 in-memory (profile `dev`)

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

## 🌐 Chia sẻ API Backend cho Mobile & Thiết bị ngoài qua Cloudflare Tunnel

Khi test Mobile (hoặc cho bạn bè trong team test từ xa), máy điện thoại **không thể gọi `localhost:8080`**. Backend hiện đã tích hợp **Tự động mở Cloudflare Tunnel**:

### ⭐ Lựa chọn 1: Cloudflare Tunnel (Tự động 100% - Khuyên dùng)
> Không cần đăng ký tài khoản, không cần authtoken, băng thông không giới hạn, không có trang cảnh báo trình duyệt.

1. Đảm bảo máy đã cài đặt Cloudflare Tunnel CLI (chỉ cài 1 lần):
   ```powershell
   winget install --id Cloudflare.cloudflared
   ```
2. Khởi chạy Backend như bình thường:
   ```bash
   mvn spring-boot:run
   ```
3. **Cơ chế tự động:**
   * Backend tự động phát hiện và khởi chạy `cloudflared` cho port `8080`.
   * Cloudflare cấp phát URL công khai ngẫu nhiên dạng: `https://<random>.trycloudflare.com`.
   * Backend **tự động cập nhật `EXPO_PUBLIC_API_URL` vào file `aives-mobile/.env`**! Bạn **không cần copy-paste thủ công**.
   * Khi bạn dừng Backend (Ctrl+C), tiến trình tunnel cũng sẽ tự động được dọn dẹp sạch sẽ.

---

### ⭐ Lựa chọn 2: Port Forwarding của VS Code hoặc Ngrok (Thủ công)
* **VS Code:** Tab **Ports** -> Forward port `8080` -> Public -> Copy link `asse.devtunnels.ms`.
* **Ngrok:** Chạy `ngrok http 8080` -> Copy link `ngrok-free.app` dán vào `.env` của mobile.

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
```text
src/main/java/com/aives/
├── AivesApplication.java # Chứa cơ chế tự động nạp & thẩm định file .env khi khởi động
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
    ├── content/     # Quản lý bài học, rubric, câu hỏi, import tài liệu
    ├── exam/        # Phiên thi (viva_exams) + lượt thi của sinh viên (viva_attempts)
    ├── grading/     # Chấm điểm tự động qua AI, duyệt điểm, phúc khảo
    └── vivaroom/    # Phòng thi vấn đáp AI thời gian thực
```

---

## 🔑 Role và Tài khoản
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

---

## 🤖 Cấu hình AI Gemini
Khai báo trong file `.env`:
* `GEMINI_API_KEY`: API key tạo từ [Google AI Studio](https://aistudio.google.com/apikey). Để trống thì hệ thống vẫn chạy nhưng không có câu hỏi xoay AI.
* `GEMINI_MODEL`: Mặc định `gemini-3.8-flash`.
