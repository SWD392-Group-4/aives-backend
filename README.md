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
    ├── exam/        # phiên thi (viva_exams) + lượt thi của sinh viên (viva_attempts)
    └── vivaroom/    # (chưa làm)
```

## Chạy local
1. Tạo database `aives` trên SQL Server rồi chạy lần lượt `database/01_users.sql` và `database/02_viva_exams.sql` để tạo bảng (khoá chính của 2 bảng phiên thi là `viva_exams_id` và `viva_attempts_id`).
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
| POST | `/api/exam-sessions` | LECTURER / ADMIN (tạo phiên, trả về mã phiên + mã truy cập) |
| GET | `/api/exam-sessions?status=&keyword=&page=&size=` | LECTURER (phiên của mình) / ADMIN (mọi phiên) |
| GET | `/api/exam-sessions/{id}` | Người tạo phiên / ADMIN |
| PUT | `/api/exam-sessions/{id}` | Người tạo phiên / ADMIN |
| DELETE | `/api/exam-sessions/{id}` | Người tạo phiên / ADMIN (đã có sinh viên vào thì chỉ huỷ) |
| POST | `/api/exam-sessions/{id}/regenerate-passcode` | Người tạo phiên / ADMIN |
| POST | `/api/exam-sessions/join` | STUDENT (nhập mã phiên + mã truy cập) |
| GET | `/api/exam-sessions/attempts/{attemptId}` | STUDENT chủ lượt thi |

## Phiên thi (module `exam`)
- **Mã phiên**: `AIVES_EXAM_yyyy_xxxxxx` (yyyy là năm tạo, 6 số ngẫu nhiên, không trùng). **Mã truy cập**: 6 chữ số, dùng chung cho cả phiên, người tạo xem lại và tạo lại được.
- **Thời gian**: API nhận ISO-8601 có múi giờ (vd `2026-10-10T08:00:00+07:00`), DB lưu giờ UTC.
- **Trạng thái** tính từ thời gian, không lưu cứng: `UPCOMING` → `ONGOING` → `ENDED`; chỉ `CANCELLED` được lưu (cột `cancelled_at`).
- **Ràng buộc khi tạo**: giờ đóng sau giờ mở; giờ mở không ở quá khứ và không xa quá 365 ngày; mở tối đa 30 ngày; thời lượng 5–300 phút và không dài hơn khoảng mở.
- **Sửa**: chưa mở thì sửa tự do; đã mở chỉ sửa tên, mô tả và kéo dài giờ đóng; đã kết thúc hoặc đã huỷ thì không sửa.
- **Xoá**: chưa có sinh viên vào thì xoá hẳn; đã có thì chuyển sang `CANCELLED`; đã kết thúc và có sinh viên thì không xoá.
- **Vào thi**: sinh viên nhập mã phiên + mã truy cập. Hạn làm bài = `min(lúc vào + thời lượng, giờ đóng phiên)`, chốt ở server; vào lại nhận đúng lượt thi cũ.
- **Chống dò mã**: sai 5 lần trong 10 phút thì khoá tài khoản sinh viên đó khỏi việc vào thi cho đến khi lần sai cũ nhất hết hạn (bộ đếm lưu trong RAM).

## Test
`mvn test` chạy `ExamSessionApiTest` trên H2 (profile `dev`), không cần SQL Server.
