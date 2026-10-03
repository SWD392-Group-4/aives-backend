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
    CONSTRAINT CK_users_role  CHECK (role IN (N'ADMIN', N'LECTURER', N'STUDENT'))
);
GO

DECLARE @pwd NVARCHAR(255) = N'$2a$10$DWsvDwGzVRGL8WZ9q4BC/OzquZGcqrBddcx96mpXUD3xFEXYOeHCW';

INSERT INTO dbo.users (users_id, email, password_hash, full_name, role, is_active)
VALUES
    -- 1 admin
    (N'ADM001', N'admin@aives.local',       @pwd, N'Quản trị viên',      N'ADMIN', 1),
    -- 2 lecturer
    (N'LEC001', N'lamnn@fe.edu.vn',         @pwd, N'Nguyễn Ngọc Lâm',    N'LECTURER', 1),
    (N'LEC002', N'huongttt@fe.edu.vn',      @pwd, N'Trần Thị Thu Hương', N'LECTURER', 1),
    -- 7 student
    (N'STU001', N'minhtdse180001@fpt.edu.vn',  @pwd, N'Trần Duy Minh',     N'STUDENT', 1),
    (N'STU002', N'khanhhqse180002@fpt.edu.vn', @pwd, N'Hoàng Quốc Khánh',  N'STUDENT', 1),
    (N'STU003', N'anhnvse180003@fpt.edu.vn',   @pwd, N'Nguyễn Việt Anh',   N'STUDENT', 1),
    (N'STU004', N'linhptse180004@fpt.edu.vn',  @pwd, N'Phạm Thuỳ Linh',    N'STUDENT', 1),
    (N'STU005', N'baolgse180005@fpt.edu.vn',   @pwd, N'Lê Gia Bảo',        N'STUDENT', 1),
    (N'STU006', N'ngocvtse180006@fpt.edu.vn',  @pwd, N'Võ Thị Bích Ngọc',  N'STUDENT', 1),
    (N'STU007', N'datdtse180007@fpt.edu.vn',   @pwd, N'Đặng Thành Đạt',    N'STUDENT', 1);
GO

SELECT users_id, email, full_name, role, is_active, created_at
FROM dbo.users
ORDER BY role, users_id;
GO
