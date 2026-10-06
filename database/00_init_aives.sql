-- =====================================================================
-- AIVES - Script khoi tao tu dau: tao database "aives" va 3 bang dang dung
--   users, viva_exams, viva_attempts
-- Dung cho may CHUA co database (vd: thanh vien moi clone project).
-- Chay lai nhieu lan khong sao: database / bang nao da ton tai thi bo qua, khong xoa du lieu.
--
-- Du lieu mau: KHONG nam trong script nay. Lan dau chay backend, DataInitializer tu tao
-- 1 admin, 2 lecturer, 7 student khi bang users con trong (xem README).
--
-- Quy uoc:
--   - Khoa chinh = <ten bang>_id, gia tri do code Java sinh:
--       users_id         -> ADxxxxxx / LExxxxxx / STxxxxxx (2 chu cai theo role + 6 chu so)
--       viva_exams_id    -> AIVES_EXAM_yyyy_xxxxxx (cung la ma vao thi, dai 22 ky tu -> NVARCHAR(30))
--       viva_attempts_id -> ATxxxxxxxx (AT + 8 chu so)
--   - Khoa ngoai mang dung ten khoa chinh ma no tro toi (viva_attempts.viva_exams_id),
--     tru khoa ngoai dat theo vai tro (lecturer_id, student_id).
--   - Thoi gian cua phien thi va luot thi (start_at, end_at, cancelled_at, started_at,
--     deadline_at, completed_at) luu theo gio UTC. created_at / updated_at luu theo gio may chu.
-- =====================================================================

/* ---------- 1. Database ---------- */
IF DB_ID(N'aives') IS NULL
    CREATE DATABASE aives;
GO

USE aives;
GO

/* ---------- 2. users ---------- */
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
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
    -- SQL Server khong co kieu ENUM nen dung CHECK, khop voi enum Role trong Java
    CONSTRAINT CK_users_role  CHECK (role IN (N'ADMIN', N'LECTURER', N'STUDENT'))
);
GO

/* ---------- 3. viva_exams (phien thi) ---------- */
IF OBJECT_ID(N'dbo.viva_exams', N'U') IS NULL
CREATE TABLE dbo.viva_exams (
    viva_exams_id              NVARCHAR(30)  NOT NULL,   -- AIVES_EXAM_yyyy_xxxxxx, cung la ma vao thi
    lecturer_id                NVARCHAR(20)  NOT NULL,   -- nguoi tao phien (LECTURER hoac ADMIN)
    title                      NVARCHAR(200) NOT NULL,
    description                NVARCHAR(MAX) NULL,
    passcode                   NVARCHAR(6)   NOT NULL,   -- ma truy cap 6 chu so
    duration_minutes           INT           NOT NULL,   -- thoi luong lam bai cua moi sinh vien
    max_follow_up_per_question INT           NOT NULL CONSTRAINT DF_viva_exams_max_follow_up   DEFAULT (2),
    prepare_seconds            INT           NOT NULL CONSTRAINT DF_viva_exams_prepare_seconds DEFAULT (30),
    answer_seconds             INT           NOT NULL CONSTRAINT DF_viva_exams_answer_seconds  DEFAULT (120),
    domain_keywords            NVARCHAR(MAX) NULL,
    start_at                   DATETIME      NOT NULL,   -- gio mo phien (UTC)
    end_at                     DATETIME      NOT NULL,   -- gio dong phien (UTC)
    cancelled_at               DATETIME      NULL,       -- khac NULL = phien da bi huy
    created_at                 DATETIME      NOT NULL CONSTRAINT DF_viva_exams_created_at DEFAULT (GETDATE()),
    updated_at                 DATETIME      NOT NULL CONSTRAINT DF_viva_exams_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_viva_exams           PRIMARY KEY (viva_exams_id),
    CONSTRAINT FK_viva_exams_lecturer  FOREIGN KEY (lecturer_id) REFERENCES dbo.users (users_id),
    CONSTRAINT CK_viva_exams_duration  CHECK (duration_minutes BETWEEN 5 AND 300),
    CONSTRAINT CK_viva_exams_follow_up CHECK (max_follow_up_per_question BETWEEN 0 AND 5),
    CONSTRAINT CK_viva_exams_time      CHECK (end_at > start_at)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_viva_exams_lecturer' AND object_id = OBJECT_ID(N'dbo.viva_exams'))
    CREATE INDEX IX_viva_exams_lecturer ON dbo.viva_exams (lecturer_id, start_at);
GO

/* ---------- 4. viva_attempts (luot thi cua sinh vien) ---------- */
IF OBJECT_ID(N'dbo.viva_attempts', N'U') IS NULL
CREATE TABLE dbo.viva_attempts (
    viva_attempts_id  NVARCHAR(20)  NOT NULL,
    viva_exams_id     NVARCHAR(30)  NOT NULL,
    student_id        NVARCHAR(20)  NOT NULL,
    status            NVARCHAR(20)  NOT NULL,
    result_status     NVARCHAR(20)  NOT NULL CONSTRAINT DF_viva_attempts_result_status DEFAULT (N'NONE'),
    total_ai_score    DECIMAL(6,2)  NULL,
    total_final_score DECIMAL(6,2)  NULL,
    full_audio_key    NVARCHAR(500) NULL,
    started_at        DATETIME      NOT NULL,   -- luc sinh vien vao phien (UTC)
    deadline_at       DATETIME      NOT NULL,   -- han lam bai = min(started_at + duration, end_at) (UTC)
    completed_at      DATETIME      NULL,
    published_at      DATETIME      NULL,
    created_at        DATETIME      NOT NULL CONSTRAINT DF_viva_attempts_created_at DEFAULT (GETDATE()),
    updated_at        DATETIME      NOT NULL CONSTRAINT DF_viva_attempts_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_viva_attempts              PRIMARY KEY (viva_attempts_id),
    CONSTRAINT FK_viva_attempts_exam         FOREIGN KEY (viva_exams_id) REFERENCES dbo.viva_exams (viva_exams_id),
    CONSTRAINT FK_viva_attempts_student      FOREIGN KEY (student_id)    REFERENCES dbo.users (users_id),
    -- Moi sinh vien chi co 1 luot thi trong 1 phien
    CONSTRAINT UQ_viva_attempts_exam_student UNIQUE (viva_exams_id, student_id),
    CONSTRAINT CK_viva_attempts_status       CHECK (status IN (N'NOT_STARTED', N'IN_PROGRESS', N'COMPLETED', N'ABANDONED')),
    CONSTRAINT CK_viva_attempts_result       CHECK (result_status IN (N'NONE', N'GRADING', N'PENDING_REVIEW', N'GRADING_FAILED', N'PUBLISHED')),
    CONSTRAINT CK_viva_attempts_deadline     CHECK (deadline_at > started_at)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_viva_attempts_student' AND object_id = OBJECT_ID(N'dbo.viva_attempts'))
    CREATE INDEX IX_viva_attempts_student ON dbo.viva_attempts (student_id);
GO

/* ---------- 5. Kiem tra ket qua ---------- */
SELECT t.name AS table_name, c.name AS column_name, ty.name AS data_type,
       CASE WHEN ty.name IN (N'nvarchar', N'nchar') AND c.max_length > 0 THEN c.max_length / 2 ELSE c.max_length END AS length,
       c.is_nullable
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE t.name IN (N'users', N'viva_exams', N'viva_attempts')
ORDER BY t.name, c.column_id;
GO
