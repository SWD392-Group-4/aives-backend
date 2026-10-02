# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

## Stack
- Java 21, Spring Boot 3.5, Maven
- Spring Web, Validation, Spring Data JPA, Spring Security + JWT
- Swagger (springdoc-openapi)
- Microsoft SQL Server (profile `sqlserver`, mặc định) / H2 in-memory (profile `dev`)

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
└── modules/
    ├── auth/        # controller, dto, entity, enums, mapper, repository, service, service/impl, util
    ├── content/     # (chưa làm)
    ├── exam/        # (chưa làm)
    └── vivaroom/    # (chưa làm)
```

## Chạy local
1. Tạo database `aives` trên SQL Server rồi chạy script `database/01_users.sql` để tạo bảng.
2. Khai báo tài khoản DB trong `application-sqlserver.yml` (hoặc biến môi trường `DB_USERNAME`, `DB_PASSWORD`, `DB_URL`).
3. Mở project bằng IntelliJ, chờ Maven tải dependency, chạy `AivesApplication`.
4. Lần chạy đầu, `DataInitializer` tự tạo dữ liệu mẫu khi bảng `users` còn trống: 1 admin, 2 lecturer, 7 student
   (email và mật khẩu mẫu xem `DataInitializer` và mục `app.seed` trong `application.yml`).
5. Mở Swagger: http://localhost:8080/swagger-ui.html, gọi `POST /api/auth/login`, copy `accessToken`, bấm **Authorize** rồi dán vào.

Máy chưa cài SQL Server: chạy với profile `dev` (H2 in-memory) bằng biến môi trường `SPRING_PROFILES_ACTIVE=dev`.

## Quy tắc id của user
`users.id` = 2 chữ cái theo role + 6 chữ số ngẫu nhiên, do `UserIdGenerator` sinh:
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
| POST | `/api/admin/users` | ADMIN (tạo ADMIN / LECTURER / STUDENT) |
| DELETE | `/api/admin/users/{id}` | ADMIN |
