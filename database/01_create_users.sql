/* =====================================================================
   AIVES - Tạo bảng users + dữ liệu mẫu
   Chạy trên database: aives (SQL Server)
   Mật khẩu của TẤT CẢ tài khoản mẫu: Aives@123  (đã mã hoá BCrypt)
   Script chạy lại nhiều lần được: xoá bảng cũ rồi tạo lại.
   ===================================================================== */
USE aives;
GO

IF OBJECT_ID(N'dbo.users', N'U') IS NOT NULL
    DROP TABLE dbo.users;
GO

CREATE TABLE dbo.users (
    id            NVARCHAR(20)  NOT NULL,
    email         NVARCHAR(255) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    full_name     NVARCHAR(150) NOT NULL,
    role          NVARCHAR(20)  NOT NULL,
    student_code  NVARCHAR(20)  NULL,
    is_active     BIT           NOT NULL CONSTRAINT DF_users_is_active  DEFAULT (1),
    created_at    DATETIME      NOT NULL CONSTRAINT DF_users_created_at DEFAULT (GETDATE()),
    updated_at    DATETIME      NOT NULL CONSTRAINT DF_users_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_users       PRIMARY KEY (id),
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT CK_users_role  CHECK (role IN (N'ADMIN', N'LECTURER', N'STUDENT'))
);
GO

-- student_code là UNIQUE nhưng cho phép nhiều dòng NULL (admin, lecturer không có mã SV).
-- SQL Server coi các NULL trong UNIQUE là trùng nhau nên phải dùng filtered unique index.
CREATE UNIQUE INDEX UQ_users_student_code
    ON dbo.users (student_code)
    WHERE student_code IS NOT NULL;
GO

DECLARE @pwd NVARCHAR(255) = N'$2a$10$DWsvDwGzVRGL8WZ9q4BC/OzquZGcqrBddcx96mpXUD3xFEXYOeHCW';

INSERT INTO dbo.users (id, email, password_hash, full_name, role, student_code, is_active)
VALUES
    -- 1 admin
    (N'ADM001', N'admin@aives.local',       @pwd, N'Quản trị viên',      N'ADMIN',    NULL,        1),
    -- 2 lecturer
    (N'LEC001', N'lamnn@fe.edu.vn',         @pwd, N'Nguyễn Ngọc Lâm',    N'LECTURER', NULL,        1),
    (N'LEC002', N'huongttt@fe.edu.vn',      @pwd, N'Trần Thị Thu Hương', N'LECTURER', NULL,        1),
    -- 7 student
    (N'STU001', N'minhtdse180001@fpt.edu.vn',  @pwd, N'Trần Duy Minh',     N'STUDENT', N'SE180001', 1),
    (N'STU002', N'khanhhqse180002@fpt.edu.vn', @pwd, N'Hoàng Quốc Khánh',  N'STUDENT', N'SE180002', 1),
    (N'STU003', N'anhnvse180003@fpt.edu.vn',   @pwd, N'Nguyễn Việt Anh',   N'STUDENT', N'SE180003', 1),
    (N'STU004', N'linhptse180004@fpt.edu.vn',  @pwd, N'Phạm Thuỳ Linh',    N'STUDENT', N'SE180004', 1),
    (N'STU005', N'baolgse180005@fpt.edu.vn',   @pwd, N'Lê Gia Bảo',        N'STUDENT', N'SE180005', 1),
    (N'STU006', N'ngocvtse180006@fpt.edu.vn',  @pwd, N'Võ Thị Bích Ngọc',  N'STUDENT', N'SE180006', 1),
    (N'STU007', N'datdtse180007@fpt.edu.vn',   @pwd, N'Đặng Thành Đạt',    N'STUDENT', N'SE180007', 1);
GO

SELECT id, email, full_name, role, student_code, is_active, created_at
FROM dbo.users
ORDER BY role, id;
GO
