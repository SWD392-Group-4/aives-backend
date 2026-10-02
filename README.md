# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

## Stack
- Java 21, Spring Boot 3.5, Maven
- Spring Web, Validation, Spring Data JPA, Spring Security + JWT
- Swagger (springdoc-openapi)
- H2 in-memory (profile `dev`, mặc định) / Microsoft SQL Server (profile `sqlserver`)

## Cấu trúc
```
src/main/java/com/aives/
├── AivesApplication.java
├── common/
│   ├── config/      # SecurityConfig, CorsConfig, OpenApiConfig, JpaAuditingConfig, DataSeeder
│   ├── security/    # JwtTokenProvider, JwtAuthFilter, UserPrincipal, TokenBlacklist...
│   ├── exception/   # ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── dto/         # ApiResponse<T>
│   ├── entity/      # BaseEntity (id, createdAt, updatedAt)
│   └── controller/  # HealthController
└── modules/
    ├── auth/        # controller, dto, entity, enums, mapper, repository, service, service/impl
    ├── content/     # (chưa làm)
    ├── exam/        # (chưa làm)
    └── vivaroom/    # (chưa làm)
```

## Chạy local (chưa cần SQL Server)
1. Mở project bằng IntelliJ, chờ Maven tải dependency.
2. Chạy `AivesApplication` (profile mặc định là `dev`, dùng H2 in-memory).
3. Mở Swagger: http://localhost:8080/swagger-ui.html
4. Gọi `POST /api/auth/login` với tài khoản admin mặc định trong `application.yml` (`app.seed.admin`).
5. Copy `accessToken`, bấm **Authorize** trên Swagger rồi dán vào.

## API hiện có
| Method | Endpoint | Quyền |
| :--- | :--- | :--- |
| GET | `/api/health` | Public |
| POST | `/api/auth/login` | Public |
| POST | `/api/auth/logout` | Đã đăng nhập |
| GET | `/api/auth/me` | Đã đăng nhập |
| GET | `/api/admin/users` | ADMIN |
| POST | `/api/admin/users` | ADMIN |
| DELETE | `/api/admin/users/{id}` | ADMIN |

## Chuyển sang SQL Server
1. Tạo database `aives`.
2. Đặt biến môi trường `DB_USERNAME`, `DB_PASSWORD` (và `DB_URL` nếu khác mặc định).
3. Chạy với profile `sqlserver`: trong IntelliJ, Run Configuration → Active profiles = `sqlserver`
   (hoặc biến môi trường `SPRING_PROFILES_ACTIVE=sqlserver`).
