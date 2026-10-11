# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

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
   *(Các biến khác như `JWT_SECRET`, `DB_USERNAME=sa`, `DB_URL` đã có giá trị mặc định sẵn sàng để chạy).*

### Bước 2: Chuẩn bị Database
Tạo database `aives` trên SQL Server rồi chạy các script trong thư mục `database/`:
1. `database/01_create_users.sql` (Tạo bảng users và dữ liệu mẫu)
2. `database/02_create_exam_sessions.sql` (Tạo bảng ca thi)

### Bước 3: Khởi chạy ứng dụng
Chạy lệnh:
```bash
mvn spring-boot:run
```
Ứng dụng sẽ khởi động tại: `http://localhost:8080`.

---

## 🌐 Chia sẻ API Backend cho Frontend & Mobile qua Tunnel

Khi test Mobile (hoặc cho bạn bè trong team test từ xa), máy điện thoại **không thể gọi `localhost:8080`**. Bạn chọn **1 trong 2 giải pháp** sau:

### ⭐ Lựa chọn 1: Dùng Port Forwarding của VS Code (Dễ nhất - Không cần cài đặt gì thêm)
> Thích hợp cho tất cả mọi người có cài VS Code. Không cần đăng ký tài khoản ngoài.

1. Mở thư mục dự án bằng **VS Code**.
2. Nhìn xuống bảng điều khiển bên dưới (cạnh tab Terminal), chọn tab **Ports**.
3. Bấm **Forward a Port** (hoặc dấu `+`) ➔ Nhập `8080`.
4. Chuột phải vào dòng port `8080` vừa tạo ➔ **Port Visibility** ➔ Chọn **Public**.
5. Copy đường dẫn trong cột **Forwarded Address** (VD: `https://xxxx-8080.asse.devtunnels.ms`).
6. Cung cấp URL này cho Mobile hoặc Frontend:
   * URL API sẽ là: `https://xxxx-8080.asse.devtunnels.ms/api`
   * Swagger UI: `https://xxxx-8080.asse.devtunnels.ms/swagger-ui/index.html`

---

### ⭐ Lựa chọn 2: Dùng Ngrok (Cực kỳ ổn định - Không cần mở VS Code)
> Thích hợp khi bạn dùng IntelliJ IDEA, Eclipse hoặc chạy thuần Terminal.

1. Tải và cài đặt Ngrok tại: [ngrok.com](https://ngrok.com) (Đăng ký tài khoản free để lấy authtoken).
2. Thêm authtoken vào máy (chỉ làm 1 lần duy nhất):
   ```bash
   ngrok config add-authtoken <TOKEN_CỦA_BẠN>
   ```
3. Mở một cửa sổ Terminal mới và chạy lệnh mở tunnel cho port 8080:
   ```bash
   ngrok http 8080
   ```
4. Copy đường dẫn `Forwarding` HTTPS sinh ra (VD: `https://xxxx.ngrok-free.app`).
5. Cung cấp URL này cho Mobile hoặc Frontend:
   * URL API: `https://xxxx.ngrok-free.app/api`
   * Swagger UI: `https://xxxx.ngrok-free.app/swagger-ui/index.html`

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
│   ├── config/           # SecurityConfig, CorsConfig, OpenApiConfig, DataInitializer
│   ├── security/         # JwtTokenProvider, JwtAuthFilter
│   └── exception/        # GlobalExceptionHandler, BusinessException
└── modules/
    ├── auth/             # Đăng ký, đăng nhập, hồ sơ cá nhân
    ├── exam/             # Quản lý ca thi và lượt thi vấn đáp
    └── content/          # Ngân hàng câu hỏi & rubric
```
