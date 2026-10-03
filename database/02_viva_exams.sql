-- =====================================================================
-- AIVES - Bang viva_exams (phien thi) va viva_attempts (luot thi cua sinh vien).
-- Chay tren database: aives, SAU khi da chay 01_users.sql.
--
-- CANH BAO: script XOA 2 bang nay (neu da co) roi tao lai, du lieu trong 2 bang se mat.
--
-- Khoa ngoai mang dung ten khoa chinh ma no tro toi (viva_attempts.viva_exams_id), tru khoa ngoai
-- dat theo vai tro (lecturer_id, student_id).
-- Khoa chinh dat ten theo bang de khong trung nhau:
--     viva_exams.viva_exams_id       -> AIVES_EXAM_yyyy_xxxxxx (do code Java sinh, cung la ma vao thi;
--                                       dai 22 ky tu nen cot la NVARCHAR(30), khong co cot exam_code rieng)
--     viva_attempts.viva_attempts_id -> ATxxxxxxxx (AT + 8 chu so, do code Java sinh)
--
-- Thoi gian cua phien thi (start_at, end_at, cancelled_at, started_at, deadline_at,
-- completed_at) luu theo gio UTC. created_at / updated_at luu theo gio may chu nhu bang users.
--
-- So voi ERD:
--   viva_exams    + passcode, cancelled_at ; - status (trang thai tinh tu thoi gian)
--   viva_attempts + deadline_at, updated_at
-- Cac cot ERD ma code chua dung (max_follow_up_per_question, prepare_seconds, answer_seconds,
-- domain_keywords, result_status, total_*_score, full_audio_key, published_at) deu co DEFAULT
-- hoac cho NULL, de INSERT tu code hien tai khong loi.
-- =====================================================================
USE aives;
GO

-- Xoa bang con truoc vi co khoa ngoai tro toi viva_exams
IF OBJECT_ID(N'dbo.viva_attempts', N'U') IS NOT NULL DROP TABLE dbo.viva_attempts;
IF OBJECT_ID(N'dbo.viva_exams', N'U') IS NOT NULL DROP TABLE dbo.viva_exams;
GO

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

CREATE INDEX IX_viva_exams_lecturer ON dbo.viva_exams (lecturer_id, start_at);
GO

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
    CONSTRAINT FK_viva_attempts_student      FOREIGN KEY (student_id)   REFERENCES dbo.users (users_id),
    -- Moi sinh vien chi co 1 luot thi trong 1 phien
    CONSTRAINT UQ_viva_attempts_exam_student UNIQUE (viva_exams_id, student_id),
    CONSTRAINT CK_viva_attempts_status       CHECK (status IN (N'NOT_STARTED', N'IN_PROGRESS', N'COMPLETED', N'ABANDONED')),
    CONSTRAINT CK_viva_attempts_result       CHECK (result_status IN (N'NONE', N'GRADING', N'PENDING_REVIEW', N'GRADING_FAILED', N'PUBLISHED')),
    CONSTRAINT CK_viva_attempts_deadline     CHECK (deadline_at > started_at)
);
GO

CREATE INDEX IX_viva_attempts_student ON dbo.viva_attempts (student_id);
GO
