# aives-backend

Backend của **AIVES – AI-powered Viva Exam System** (Spring Boot Monolith).

## Stack
- Java 21, Spring Boot 3.5, Maven
- Spring Web, Validation, Spring Data JPA
- Microsoft SQL Server

## Cấu trúc
```
src/main/java/com/aives/
├── AivesApplication.java
├── common/      # config, security, exception, dto, entity dùng chung
└── modules/     # auth, content, exam, vivaroom ...
```

## Chạy local
1. Tạo database `aives` trên SQL Server.
2. Đặt biến môi trường `DB_USERNAME`, `DB_PASSWORD` (và `DB_URL` nếu khác mặc định).
3. Mở bằng IntelliJ và chạy `AivesApplication`, hoặc `mvn spring-boot:run`.
4. Kiểm tra: `GET http://localhost:8080/api/health`
