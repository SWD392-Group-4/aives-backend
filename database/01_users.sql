-- =====================================================================
-- AIVES - Bang users (SQL Server). Chay 1 lan tren database: aives
-- Database da co bang users kieu cu (khoa chinh ten id, co cot student_code)
-- thi KHONG chay file nay ma chay 03_alter_users.sql.
-- Du lieu mau KHONG nam o day: DataInitializer (common/config) se tu
-- insert 1 admin, 2 lecturer, 7 student khi app chay lan dau va bang con trong.
--
-- users_id: 2 chu cai theo role + 6 chu so ngau nhien, do code Java sinh ra
--     ADMIN    -> ADxxxxxx
--     LECTURER -> LExxxxxx
--     STUDENT  -> STxxxxxx
-- =====================================================================
USE aives;
GO

CREATE TABLE dbo.users (
    users_id      NVARCHAR(20)  NOT NULL,
    email         NVARCHAR(255) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    full_name     NVARCHAR(150) NOT NULL,
    role          NVARCHAR(20)  NOT NULL,
    is_active     BIT           NOT NULL CONSTRAINT DF_users_is_active  DEFAULT (1),
    created_at    DATETIME      NOT NULL CONSTRAINT DF_users_created_at DEFAULT (GETDATE()),
    updated_at    DATETIME      NOT NULL CONSTRAINT DF_users_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_users       PRIMARY KEY (users_id),
    CONSTRAINT UQ_users_email UNIQUE (email),
    -- "enum" role: SQL Server khong co kieu ENUM nen dung CHECK, khop voi enum Role trong Java
    CONSTRAINT CK_users_role  CHECK (role IN (N'ADMIN', N'LECTURER', N'STUDENT'))
);
GO
