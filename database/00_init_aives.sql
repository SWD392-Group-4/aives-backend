-- =====================================================================
-- AIVES - Script khoi tao TOAN BO database: tao database "aives" va 17 bang
--
--   Tai khoan : roles, users
--   Noi dung  : lessons, topics, rubrics, rubric_criteria, questions
--   Phien thi : viva_exams, viva_exam_questions, viva_attempts, interview_exchanges
--   Cham diem : question_grades, criteria_grades, grade_appeals
--   He thong  : background_jobs, audit_logs, system_settings
--
-- Dung cho may CHUA co database (vd: thanh vien moi clone project).
-- Chay lai nhieu lan khong sao: database / bang / index nao da ton tai thi bo qua, khong xoa du lieu.
--
-- Database DA CO tu truoc (bang users con cot "role"): chay 06_roles.sql va
-- 07_exam_max_attempts.sql truoc, sau do chay file nay de tao not cac bang con thieu.
--
-- Du lieu mau: script chi them 3 dong cua bang roles (bat buoc phai co).
-- Lan dau chay backend, DataInitializer tu tao 1 admin, 2 lecturer, 7 student khi bang users con trong.
--
-- Quy uoc:
--   - Khoa chinh = <ten bang>_id, kieu NVARCHAR(20), gia tri do code Java sinh:
--       roles_id         -> AD / LE / ST (cung la tien to cua users_id)
--       users_id         -> ADxxxxxx / LExxxxxx / STxxxxxx (2 chu cai theo role + 6 chu so)
--       viva_exams_id    -> AIVES_EXAM_yyyy_xxxxxx (cung la ma vao thi, dai 22 ky tu -> NVARCHAR(30))
--       viva_attempts_id -> ATxxxxxxxx (AT + 8 chu so)
--   - Khoa ngoai mang dung ten khoa chinh ma no tro toi (users.roles_id, viva_attempts.viva_exams_id...),
--     tru khoa ngoai dat theo vai tro (lecturer_id, student_id, created_by, graded_by, resolved_by,
--     actor_id, updated_by, parent_exchange_id).
--   - Khong dung ON DELETE CASCADE (SQL Server bao loi "multiple cascade paths"); xoa xu ly o service.
--   - Thoi gian cua phien thi va luot thi (start_at, end_at, cancelled_at, started_at, deadline_at,
--     completed_at) luu theo gio UTC. created_at / updated_at luu theo gio may chu.
--   - Ten cot, kieu va ten rang buoc UNIQUE khop voi cac @Entity trong code (Hibernate dang de ddl-auto: update).
-- =====================================================================

/* ---------- 0. Database ---------- */
IF DB_ID(N'aives') IS NULL
    CREATE DATABASE aives;
GO

USE aives;
GO

-- Database dang o schema cu thi dung lai, khong tao gi them (tranh de database o trang thai nua cu nua moi).
IF COL_LENGTH(N'dbo.users', N'role') IS NOT NULL
BEGIN
    RAISERROR(N'Database dang o schema cu (bang users con cot role). Chay 06_roles.sql va 07_exam_max_attempts.sql truoc, roi chay lai file nay.', 16, 1);
    SET NOEXEC ON;
END
GO

/* =====================================================================
   1. TAI KHOAN
   ===================================================================== */

/* ---------- 1.1 roles: danh muc role, tach rieng khoi users ---------- */
IF OBJECT_ID(N'dbo.roles', N'U') IS NULL
CREATE TABLE dbo.roles (
    roles_id    NVARCHAR(20)  NOT NULL,   -- AD / LE / ST
    name        NVARCHAR(20)  NOT NULL,   -- khop voi enum RoleName trong Java
    description NVARCHAR(255) NULL,
    created_at  DATETIME      NOT NULL CONSTRAINT DF_roles_created_at DEFAULT (GETDATE()),
    updated_at  DATETIME      NOT NULL CONSTRAINT DF_roles_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_roles      PRIMARY KEY (roles_id),
    CONSTRAINT UQ_roles_name UNIQUE (name),
    CONSTRAINT CK_roles_name CHECK (name IN (N'ADMIN', N'LECTURER', N'STUDENT'))
);
GO

-- 3 role co dinh cua he thong. Thieu role nao thi them role do.
INSERT INTO dbo.roles (roles_id, name, description)
SELECT v.roles_id, v.name, v.description
FROM (VALUES
    (N'AD', N'ADMIN',    N'Quản trị viên'),
    (N'LE', N'LECTURER', N'Giảng viên'),
    (N'ST', N'STUDENT',  N'Sinh viên')
) AS v (roles_id, name, description)
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles r WHERE r.name = v.name);
GO

/* ---------- 1.2 users: moi user co dung 1 role (users.roles_id) ---------- */
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
CREATE TABLE dbo.users (
    users_id             NVARCHAR(20)  NOT NULL,
    roles_id             NVARCHAR(20)  NOT NULL,
    email                NVARCHAR(255) NOT NULL,
    password_hash        NVARCHAR(255) NOT NULL,   -- BCrypt
    full_name            NVARCHAR(150) NOT NULL,
    is_active            BIT           NOT NULL CONSTRAINT DF_users_is_active DEFAULT (1),
    -- 1 = tai khoan do admin tao, dang dung mat khau tam gui qua email -> phai doi mat khau truoc
    must_change_password BIT           NOT NULL CONSTRAINT DF_users_must_change_password DEFAULT (0),
    created_at           DATETIME      NOT NULL CONSTRAINT DF_users_created_at DEFAULT (GETDATE()),
    updated_at           DATETIME      NOT NULL CONSTRAINT DF_users_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_users       PRIMARY KEY (users_id),
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT FK_users_role  FOREIGN KEY (roles_id) REFERENCES dbo.roles (roles_id)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_users_role' AND object_id = OBJECT_ID(N'dbo.users'))
    CREATE INDEX IX_users_role ON dbo.users (roles_id);
GO

/* =====================================================================
   2. NOI DUNG: LESSON -> TOPIC -> QUESTION, RUBRIC -> RUBRIC_CRITERIA
   ===================================================================== */

/* ---------- 2.1 lessons ---------- */
IF OBJECT_ID(N'dbo.lessons', N'U') IS NULL
CREATE TABLE dbo.lessons (
    lessons_id  NVARCHAR(20)  NOT NULL,
    lecturer_id NVARCHAR(20)  NOT NULL,   -- giang vien quan ly bai hoc
    title       NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    created_at  DATETIME      NOT NULL CONSTRAINT DF_lessons_created_at DEFAULT (GETDATE()),
    updated_at  DATETIME      NOT NULL CONSTRAINT DF_lessons_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_lessons          PRIMARY KEY (lessons_id),
    CONSTRAINT FK_lessons_lecturer FOREIGN KEY (lecturer_id) REFERENCES dbo.users (users_id)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_lessons_lecturer' AND object_id = OBJECT_ID(N'dbo.lessons'))
    CREATE INDEX IX_lessons_lecturer ON dbo.lessons (lecturer_id);
GO

/* ---------- 2.2 topics ---------- */
IF OBJECT_ID(N'dbo.topics', N'U') IS NULL
CREATE TABLE dbo.topics (
    topics_id   NVARCHAR(20)  NOT NULL,
    lessons_id  NVARCHAR(20)  NOT NULL,
    name        NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    created_at  DATETIME      NOT NULL CONSTRAINT DF_topics_created_at DEFAULT (GETDATE()),
    updated_at  DATETIME      NOT NULL CONSTRAINT DF_topics_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_topics        PRIMARY KEY (topics_id),
    CONSTRAINT FK_topics_lesson FOREIGN KEY (lessons_id) REFERENCES dbo.lessons (lessons_id)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_topics_lesson' AND object_id = OBJECT_ID(N'dbo.topics'))
    CREATE INDEX IX_topics_lesson ON dbo.topics (lessons_id);
GO

/* ---------- 2.3 rubrics ---------- */
IF OBJECT_ID(N'dbo.rubrics', N'U') IS NULL
CREATE TABLE dbo.rubrics (
    rubrics_id  NVARCHAR(20)  NOT NULL,
    created_by  NVARCHAR(20)  NOT NULL,   -- nguoi tao rubric
    name        NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    max_score   DECIMAL(5,2)  NOT NULL CONSTRAINT DF_rubrics_max_score DEFAULT (10),
    created_at  DATETIME      NOT NULL CONSTRAINT DF_rubrics_created_at DEFAULT (GETDATE()),
    updated_at  DATETIME      NOT NULL CONSTRAINT DF_rubrics_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_rubrics           PRIMARY KEY (rubrics_id),
    CONSTRAINT FK_rubrics_creator   FOREIGN KEY (created_by) REFERENCES dbo.users (users_id),
    CONSTRAINT CK_rubrics_max_score CHECK (max_score > 0)
);
GO

/* ---------- 2.4 rubric_criteria ---------- */
IF OBJECT_ID(N'dbo.rubric_criteria', N'U') IS NULL
CREATE TABLE dbo.rubric_criteria (
    rubric_criteria_id NVARCHAR(20)  NOT NULL,
    rubrics_id         NVARCHAR(20)  NOT NULL,
    name               NVARCHAR(200) NOT NULL,
    description        NVARCHAR(MAX) NOT NULL,   -- BR-BANK-003: bat buoc mo ta yeu cau dat diem
    weight_percent     DECIMAL(5,2)  NOT NULL,   -- tong weight cua 1 rubric = 100 (kiem tra o service)
    order_no           INT           NOT NULL CONSTRAINT DF_rubric_criteria_order_no DEFAULT (1),
    created_at         DATETIME      NOT NULL CONSTRAINT DF_rubric_criteria_created_at DEFAULT (GETDATE()),
    updated_at         DATETIME      NOT NULL CONSTRAINT DF_rubric_criteria_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_rubric_criteria        PRIMARY KEY (rubric_criteria_id),
    CONSTRAINT FK_rubric_criteria_rubric FOREIGN KEY (rubrics_id) REFERENCES dbo.rubrics (rubrics_id),
    CONSTRAINT CK_rubric_criteria_weight CHECK (weight_percent > 0 AND weight_percent <= 100)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_rubric_criteria_rubric' AND object_id = OBJECT_ID(N'dbo.rubric_criteria'))
    CREATE INDEX IX_rubric_criteria_rubric ON dbo.rubric_criteria (rubrics_id, order_no);
GO

/* ---------- 2.5 questions ---------- */
IF OBJECT_ID(N'dbo.questions', N'U') IS NULL
CREATE TABLE dbo.questions (
    questions_id       NVARCHAR(20)  NOT NULL,
    topics_id          NVARCHAR(20)  NOT NULL,
    rubrics_id         NVARCHAR(20)  NULL,       -- NULL khi cau hoi con DRAFT
    content            NVARCHAR(MAX) NOT NULL,
    bloom_level        NVARCHAR(20)  NOT NULL,
    model_answer       NVARCHAR(MAX) NULL,
    expected_keywords  NVARCHAR(MAX) NULL,       -- JSON array
    source             NVARCHAR(20)  NOT NULL CONSTRAINT DF_questions_source DEFAULT (N'MANUAL'),
    status             NVARCHAR(20)  NOT NULL CONSTRAINT DF_questions_status DEFAULT (N'DRAFT'),
    question_audio_key NVARCHAR(500) NULL,       -- cache audio TTS cua cau goc
    created_at         DATETIME      NOT NULL CONSTRAINT DF_questions_created_at DEFAULT (GETDATE()),
    updated_at         DATETIME      NOT NULL CONSTRAINT DF_questions_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_questions        PRIMARY KEY (questions_id),
    CONSTRAINT FK_questions_topic  FOREIGN KEY (topics_id)  REFERENCES dbo.topics (topics_id),
    CONSTRAINT FK_questions_rubric FOREIGN KEY (rubrics_id) REFERENCES dbo.rubrics (rubrics_id),
    CONSTRAINT CK_questions_bloom  CHECK (bloom_level IN (N'REMEMBER', N'UNDERSTAND', N'APPLY', N'ANALYZE')),
    CONSTRAINT CK_questions_source CHECK (source IN (N'MANUAL', N'AI_GENERATED')),
    CONSTRAINT CK_questions_status CHECK (status IN (N'DRAFT', N'APPROVED', N'REJECTED', N'ARCHIVED')),
    -- BR-BANK-001: cau hoi APPROVED bat buoc da gan rubric
    CONSTRAINT CK_questions_approved_rubric CHECK (status <> N'APPROVED' OR rubrics_id IS NOT NULL)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_questions_topic_status' AND object_id = OBJECT_ID(N'dbo.questions'))
    CREATE INDEX IX_questions_topic_status ON dbo.questions (topics_id, status);
GO

/* =====================================================================
   3. PHIEN THI VA LUOT THI
   ===================================================================== */

/* ---------- 3.1 viva_exams (phien thi) ---------- */
IF OBJECT_ID(N'dbo.viva_exams', N'U') IS NULL
CREATE TABLE dbo.viva_exams (
    viva_exams_id              NVARCHAR(30)  NOT NULL,   -- AIVES_EXAM_yyyy_xxxxxx, cung la ma vao thi
    lecturer_id                NVARCHAR(20)  NOT NULL,   -- nguoi tao phien (LECTURER hoac ADMIN)
    title                      NVARCHAR(200) NOT NULL,
    description                NVARCHAR(MAX) NULL,
    passcode                   NVARCHAR(6)   NOT NULL,   -- ma truy cap 6 chu so
    duration_minutes           INT           NOT NULL,   -- thoi luong lam bai cua MOI luot thi
    max_attempts               INT           NOT NULL CONSTRAINT DF_viva_exams_max_attempts    DEFAULT (1),   -- so luot thi toi da cua moi sinh vien
    max_follow_up_per_question INT           NOT NULL CONSTRAINT DF_viva_exams_max_follow_up   DEFAULT (2),
    prepare_seconds            INT           NOT NULL CONSTRAINT DF_viva_exams_prepare_seconds DEFAULT (30),
    answer_seconds             INT           NOT NULL CONSTRAINT DF_viva_exams_answer_seconds  DEFAULT (120),
    domain_keywords            NVARCHAR(MAX) NULL,       -- BR-VIVA-004: goi y tu khoa cho STT (JSON array)
    start_at                   DATETIME      NOT NULL,   -- gio mo phien (UTC)
    end_at                     DATETIME      NOT NULL,   -- gio dong phien (UTC)
    cancelled_at               DATETIME      NULL,       -- khac NULL = phien da bi huy (khong co cot status)
    created_at                 DATETIME      NOT NULL CONSTRAINT DF_viva_exams_created_at DEFAULT (GETDATE()),
    updated_at                 DATETIME      NOT NULL CONSTRAINT DF_viva_exams_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_viva_exams              PRIMARY KEY (viva_exams_id),
    CONSTRAINT FK_viva_exams_lecturer     FOREIGN KEY (lecturer_id) REFERENCES dbo.users (users_id),
    CONSTRAINT CK_viva_exams_duration     CHECK (duration_minutes BETWEEN 5 AND 300),
    CONSTRAINT CK_viva_exams_max_attempts CHECK (max_attempts BETWEEN 1 AND 10),
    CONSTRAINT CK_viva_exams_follow_up    CHECK (max_follow_up_per_question BETWEEN 0 AND 5),
    CONSTRAINT CK_viva_exams_time         CHECK (end_at > start_at)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_viva_exams_lecturer' AND object_id = OBJECT_ID(N'dbo.viva_exams'))
    CREATE INDEX IX_viva_exams_lecturer ON dbo.viva_exams (lecturer_id, start_at);
GO

/* ---------- 3.2 viva_exam_questions (cau hoi duoc chon vao phien) ---------- */
IF OBJECT_ID(N'dbo.viva_exam_questions', N'U') IS NULL
CREATE TABLE dbo.viva_exam_questions (
    viva_exam_questions_id NVARCHAR(20) NOT NULL,
    viva_exams_id          NVARCHAR(30) NOT NULL,
    questions_id           NVARCHAR(20) NOT NULL,
    order_no               INT          NOT NULL,
    weight                 DECIMAL(5,2) NOT NULL CONSTRAINT DF_viva_exam_questions_weight DEFAULT (1),
    created_at             DATETIME     NOT NULL CONSTRAINT DF_viva_exam_questions_created_at DEFAULT (GETDATE()),
    updated_at             DATETIME     NOT NULL CONSTRAINT DF_viva_exam_questions_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_viva_exam_questions               PRIMARY KEY (viva_exam_questions_id),
    CONSTRAINT FK_viva_exam_questions_exam          FOREIGN KEY (viva_exams_id) REFERENCES dbo.viva_exams (viva_exams_id),
    CONSTRAINT FK_viva_exam_questions_question      FOREIGN KEY (questions_id)  REFERENCES dbo.questions (questions_id),
    CONSTRAINT uq_viva_exam_questions_exam_question UNIQUE (viva_exams_id, questions_id),
    CONSTRAINT uq_viva_exam_questions_exam_order    UNIQUE (viva_exams_id, order_no),
    CONSTRAINT CK_viva_exam_questions_weight        CHECK (weight > 0)
);
GO

/* ---------- 3.3 viva_attempts (luot thi cua sinh vien) ---------- */
IF OBJECT_ID(N'dbo.viva_attempts', N'U') IS NULL
CREATE TABLE dbo.viva_attempts (
    viva_attempts_id  NVARCHAR(20)  NOT NULL,
    viva_exams_id     NVARCHAR(30)  NOT NULL,
    student_id        NVARCHAR(20)  NOT NULL,
    attempt_no        INT           NOT NULL CONSTRAINT DF_viva_attempts_attempt_no DEFAULT (1),   -- luot thu may cua sinh vien trong phien
    status            NVARCHAR(20)  NOT NULL,
    result_status     NVARCHAR(20)  NOT NULL CONSTRAINT DF_viva_attempts_result_status DEFAULT (N'NONE'),
    total_ai_score    DECIMAL(6,2)  NULL,
    total_final_score DECIMAL(6,2)  NULL,
    full_audio_key    NVARCHAR(500) NULL,
    started_at        DATETIME      NOT NULL,   -- luc sinh vien bat dau luot thi (UTC)
    deadline_at       DATETIME      NOT NULL,   -- han lam bai = min(started_at + duration, end_at) (UTC)
    completed_at      DATETIME      NULL,
    published_at      DATETIME      NULL,
    created_at        DATETIME      NOT NULL CONSTRAINT DF_viva_attempts_created_at DEFAULT (GETDATE()),
    updated_at        DATETIME      NOT NULL CONSTRAINT DF_viva_attempts_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_viva_attempts                 PRIMARY KEY (viva_attempts_id),
    CONSTRAINT FK_viva_attempts_exam            FOREIGN KEY (viva_exams_id) REFERENCES dbo.viva_exams (viva_exams_id),
    CONSTRAINT FK_viva_attempts_student         FOREIGN KEY (student_id)    REFERENCES dbo.users (users_id),
    -- Moi sinh vien co nhieu luot thi trong 1 phien (toi da viva_exams.max_attempts), danh so 1, 2, 3...
    CONSTRAINT UQ_viva_attempts_exam_student_no UNIQUE (viva_exams_id, student_id, attempt_no),
    CONSTRAINT CK_viva_attempts_attempt_no      CHECK (attempt_no >= 1),
    CONSTRAINT CK_viva_attempts_status          CHECK (status IN (N'NOT_STARTED', N'IN_PROGRESS', N'COMPLETED', N'ABANDONED')),
    CONSTRAINT CK_viva_attempts_result          CHECK (result_status IN (N'NONE', N'GRADING', N'PENDING_REVIEW', N'GRADING_FAILED', N'PUBLISHED')),
    CONSTRAINT CK_viva_attempts_deadline        CHECK (deadline_at > started_at)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_viva_attempts_student' AND object_id = OBJECT_ID(N'dbo.viva_attempts'))
    CREATE INDEX IX_viva_attempts_student ON dbo.viva_attempts (student_id);
GO

/* ---------- 3.4 interview_exchanges (tung luot hoi - dap, ke ca cau hoi xoay) ---------- */
IF OBJECT_ID(N'dbo.interview_exchanges', N'U') IS NULL
CREATE TABLE dbo.interview_exchanges (
    interview_exchanges_id NVARCHAR(20)  NOT NULL,
    viva_attempts_id       NVARCHAR(20)  NOT NULL,
    viva_exam_questions_id NVARCHAR(20)  NOT NULL,
    parent_exchange_id     NVARCHAR(20)  NULL,       -- NULL = cau goc; khac NULL = cau hoi xoay cua luot cha
    depth                  INT           NOT NULL CONSTRAINT DF_interview_exchanges_depth DEFAULT (0),   -- 0 = cau goc, 1.. = hoi xoay
    question_text          NVARCHAR(MAX) NOT NULL,
    question_audio_key     NVARCHAR(500) NULL,
    transcript             NVARCHAR(MAX) NULL,
    answer_audio_key       NVARCHAR(500) NULL,
    follow_up_reason       NVARCHAR(20)  NULL,
    ai_decision            NVARCHAR(20)  NULL,
    ai_decision_reason     NVARCHAR(MAX) NULL,
    answer_started_at      DATETIME      NULL,
    answer_ended_at        DATETIME      NULL,
    latency_ms             INT           NULL,       -- tu luc sinh vien ngung noi den cau hoi tiep theo (BR-VIVA-003)
    created_at             DATETIME      NOT NULL CONSTRAINT DF_interview_exchanges_created_at DEFAULT (GETDATE()),
    updated_at             DATETIME      NOT NULL CONSTRAINT DF_interview_exchanges_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_interview_exchanges          PRIMARY KEY (interview_exchanges_id),
    CONSTRAINT FK_interview_exchanges_attempt  FOREIGN KEY (viva_attempts_id)       REFERENCES dbo.viva_attempts (viva_attempts_id),
    CONSTRAINT FK_interview_exchanges_question FOREIGN KEY (viva_exam_questions_id) REFERENCES dbo.viva_exam_questions (viva_exam_questions_id),
    CONSTRAINT FK_interview_exchanges_parent   FOREIGN KEY (parent_exchange_id)     REFERENCES dbo.interview_exchanges (interview_exchanges_id),
    CONSTRAINT CK_interview_exchanges_reason   CHECK (follow_up_reason IN (N'INCOMPLETE', N'AMBIGUOUS', N'CONTRADICTORY')),
    CONSTRAINT CK_interview_exchanges_decision CHECK (ai_decision IN (N'FOLLOW_UP', N'NEXT', N'SKIPPED')),
    CONSTRAINT CK_interview_exchanges_depth    CHECK ((parent_exchange_id IS NULL AND depth = 0) OR (parent_exchange_id IS NOT NULL AND depth > 0))
);
GO

-- Moi luot hoi - dap co toi da 1 cau hoi xoay con. Dung filtered index vi SQL Server coi cac NULL trong UNIQUE la trung nhau.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_interview_exchanges_parent' AND object_id = OBJECT_ID(N'dbo.interview_exchanges'))
    CREATE UNIQUE INDEX UX_interview_exchanges_parent ON dbo.interview_exchanges (parent_exchange_id) WHERE parent_exchange_id IS NOT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_interview_exchanges_attempt_question' AND object_id = OBJECT_ID(N'dbo.interview_exchanges'))
    CREATE INDEX IX_interview_exchanges_attempt_question ON dbo.interview_exchanges (viva_attempts_id, viva_exam_questions_id);
GO

/* =====================================================================
   4. CHAM DIEM VA PHUC KHAO
   ===================================================================== */

/* ---------- 4.1 question_grades (diem theo cau: AI goi y, giang vien chot) ---------- */
IF OBJECT_ID(N'dbo.question_grades', N'U') IS NULL
CREATE TABLE dbo.question_grades (
    question_grades_id     NVARCHAR(20)  NOT NULL,
    viva_attempts_id       NVARCHAR(20)  NOT NULL,
    viva_exam_questions_id NVARCHAR(20)  NOT NULL,
    ai_score               DECIMAL(5,2)  NULL,
    final_score            DECIMAL(5,2)  NULL,
    ai_strengths           NVARCHAR(MAX) NULL,
    ai_weaknesses          NVARCHAR(MAX) NULL,
    ai_feedback            NVARCHAR(MAX) NULL,
    lecturer_note          NVARCHAR(MAX) NULL,
    status                 NVARCHAR(20)  NOT NULL CONSTRAINT DF_question_grades_status DEFAULT (N'AI_DRAFT'),
    graded_by              NVARCHAR(20)  NULL,       -- NULL khi giang vien chua chot
    graded_at              DATETIME      NULL,
    created_at             DATETIME      NOT NULL CONSTRAINT DF_question_grades_created_at DEFAULT (GETDATE()),
    updated_at             DATETIME      NOT NULL CONSTRAINT DF_question_grades_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_question_grades                  PRIMARY KEY (question_grades_id),
    CONSTRAINT FK_question_grades_attempt          FOREIGN KEY (viva_attempts_id)       REFERENCES dbo.viva_attempts (viva_attempts_id),
    CONSTRAINT FK_question_grades_question         FOREIGN KEY (viva_exam_questions_id) REFERENCES dbo.viva_exam_questions (viva_exam_questions_id),
    CONSTRAINT FK_question_grades_grader           FOREIGN KEY (graded_by)              REFERENCES dbo.users (users_id),
    CONSTRAINT uq_question_grades_attempt_question UNIQUE (viva_attempts_id, viva_exam_questions_id),
    CONSTRAINT CK_question_grades_status           CHECK (status IN (N'AI_DRAFT', N'APPROVED', N'FAILED')),
    CONSTRAINT CK_question_grades_approved         CHECK (status <> N'APPROVED' OR graded_by IS NOT NULL),
    -- BR-GRADE-002: diem chot lech diem AI qua 2.0 thi bat buoc co ghi chu cua giang vien
    CONSTRAINT CK_question_grades_note             CHECK (
        final_score IS NULL OR ai_score IS NULL
        OR ABS(final_score - ai_score) <= 2.0
        OR lecturer_note IS NOT NULL)
);
GO

/* ---------- 4.2 criteria_grades (diem theo tung tieu chi cua rubric) ---------- */
IF OBJECT_ID(N'dbo.criteria_grades', N'U') IS NULL
CREATE TABLE dbo.criteria_grades (
    criteria_grades_id NVARCHAR(20)  NOT NULL,
    question_grades_id NVARCHAR(20)  NOT NULL,
    rubric_criteria_id NVARCHAR(20)  NOT NULL,
    ai_score           DECIMAL(5,2)  NULL,
    final_score        DECIMAL(5,2)  NULL,
    evidence_quote     NVARCHAR(MAX) NULL,       -- BR-GRADE-001: trich dan tu transcript
    rationale          NVARCHAR(MAX) NULL,
    created_at         DATETIME      NOT NULL CONSTRAINT DF_criteria_grades_created_at DEFAULT (GETDATE()),
    updated_at         DATETIME      NOT NULL CONSTRAINT DF_criteria_grades_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_criteria_grades                   PRIMARY KEY (criteria_grades_id),
    CONSTRAINT FK_criteria_grades_question_grade    FOREIGN KEY (question_grades_id) REFERENCES dbo.question_grades (question_grades_id),
    CONSTRAINT FK_criteria_grades_criteria          FOREIGN KEY (rubric_criteria_id) REFERENCES dbo.rubric_criteria (rubric_criteria_id),
    CONSTRAINT uq_criteria_grades_question_criteria UNIQUE (question_grades_id, rubric_criteria_id)
);
GO

/* ---------- 4.3 grade_appeals (phuc khao diem 1 cau) ---------- */
IF OBJECT_ID(N'dbo.grade_appeals', N'U') IS NULL
CREATE TABLE dbo.grade_appeals (
    grade_appeals_id   NVARCHAR(20)  NOT NULL,
    question_grades_id NVARCHAR(20)  NOT NULL,
    reason             NVARCHAR(MAX) NOT NULL,
    status             NVARCHAR(20)  NOT NULL CONSTRAINT DF_grade_appeals_status DEFAULT (N'PENDING'),
    score_before       DECIMAL(5,2)  NOT NULL,
    score_after        DECIMAL(5,2)  NULL,
    response           NVARCHAR(MAX) NULL,
    resolved_by        NVARCHAR(20)  NULL,       -- NULL khi don chua duoc xu ly
    resolved_at        DATETIME      NULL,
    created_at         DATETIME      NOT NULL CONSTRAINT DF_grade_appeals_created_at DEFAULT (GETDATE()),
    updated_at         DATETIME      NOT NULL CONSTRAINT DF_grade_appeals_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_grade_appeals                PRIMARY KEY (grade_appeals_id),
    CONSTRAINT FK_grade_appeals_question_grade FOREIGN KEY (question_grades_id) REFERENCES dbo.question_grades (question_grades_id),
    CONSTRAINT FK_grade_appeals_resolver       FOREIGN KEY (resolved_by)        REFERENCES dbo.users (users_id),
    CONSTRAINT CK_grade_appeals_status         CHECK (status IN (N'PENDING', N'ACCEPTED', N'REJECTED')),
    CONSTRAINT CK_grade_appeals_resolved       CHECK (status = N'PENDING' OR resolved_by IS NOT NULL)
);
GO

-- Moi diem cau chi co toi da 1 don phuc khao dang PENDING
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_grade_appeals_pending' AND object_id = OBJECT_ID(N'dbo.grade_appeals'))
    CREATE UNIQUE INDEX UX_grade_appeals_pending ON dbo.grade_appeals (question_grades_id) WHERE status = N'PENDING';
GO

/* =====================================================================
   5. BANG HE THONG (khong co trong ERD)
   ===================================================================== */

/* ---------- 5.1 background_jobs (hang doi job chay nen: cham diem AI, upload audio) ---------- */
IF OBJECT_ID(N'dbo.background_jobs', N'U') IS NULL
CREATE TABLE dbo.background_jobs (
    background_jobs_id NVARCHAR(20)  NOT NULL,
    job_type           NVARCHAR(50)  NOT NULL,   -- EVALUATE_ATTEMPT, AUDIO_UPLOAD
    payload            NVARCHAR(MAX) NULL,       -- JSON
    status             NVARCHAR(20)  NOT NULL CONSTRAINT DF_background_jobs_status   DEFAULT (N'PENDING'),
    attempts           INT           NOT NULL CONSTRAINT DF_background_jobs_attempts DEFAULT (0),
    next_run_at        DATETIME      NULL     CONSTRAINT DF_background_jobs_next_run DEFAULT (GETDATE()),
    last_error         NVARCHAR(MAX) NULL,
    created_at         DATETIME      NOT NULL CONSTRAINT DF_background_jobs_created_at DEFAULT (GETDATE()),
    updated_at         DATETIME      NOT NULL CONSTRAINT DF_background_jobs_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_background_jobs        PRIMARY KEY (background_jobs_id),
    CONSTRAINT CK_background_jobs_status CHECK (status IN (N'PENDING', N'RUNNING', N'DONE', N'FAILED'))
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_background_jobs_poll' AND object_id = OBJECT_ID(N'dbo.background_jobs'))
    CREATE INDEX IX_background_jobs_poll ON dbo.background_jobs (status, next_run_at);
GO

/* ---------- 5.2 audit_logs (dau vet thao tac: sua diem, cong bo, duyet cau hoi...) ---------- */
IF OBJECT_ID(N'dbo.audit_logs', N'U') IS NULL
CREATE TABLE dbo.audit_logs (
    audit_logs_id NVARCHAR(20)  NOT NULL,
    actor_id      NVARCHAR(20)  NOT NULL,   -- nguoi thuc hien
    action        NVARCHAR(50)  NOT NULL,   -- OVERRIDE_SCORE, PUBLISH_RESULT, APPROVE_QUESTION, RESOLVE_APPEAL...
    entity_type   NVARCHAR(50)  NOT NULL,
    entity_id     NVARCHAR(50)  NOT NULL,
    old_value     NVARCHAR(MAX) NULL,
    new_value     NVARCHAR(MAX) NULL,
    created_at    DATETIME      NOT NULL CONSTRAINT DF_audit_logs_created_at DEFAULT (GETDATE()),
    updated_at    DATETIME      NOT NULL CONSTRAINT DF_audit_logs_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_audit_logs       PRIMARY KEY (audit_logs_id),
    CONSTRAINT FK_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES dbo.users (users_id)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_audit_logs_entity' AND object_id = OBJECT_ID(N'dbo.audit_logs'))
    CREATE INDEX IX_audit_logs_entity ON dbo.audit_logs (entity_type, entity_id);
GO

/* ---------- 5.3 system_settings (cau hinh AI: ai.llm.model, stt.language, tts.voice...) ---------- */
IF OBJECT_ID(N'dbo.system_settings', N'U') IS NULL
CREATE TABLE dbo.system_settings (
    setting_key   NVARCHAR(100) NOT NULL,
    setting_value NVARCHAR(MAX) NOT NULL,
    updated_by    NVARCHAR(20)  NULL,
    updated_at    DATETIME      NULL CONSTRAINT DF_system_settings_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_system_settings      PRIMARY KEY (setting_key),
    CONSTRAINT FK_system_settings_user FOREIGN KEY (updated_by) REFERENCES dbo.users (users_id)
);
GO

/* =====================================================================
   6. KIEM TRA KET QUA: phai du 17 bang
   ===================================================================== */
SELECT t.name AS table_name,
       (SELECT COUNT(*) FROM sys.columns c WHERE c.object_id = t.object_id)                 AS so_cot,
       (SELECT COUNT(*) FROM sys.foreign_keys fk WHERE fk.parent_object_id = t.object_id)   AS so_khoa_ngoai
FROM sys.tables t
WHERE t.name IN (N'roles', N'users', N'lessons', N'topics', N'rubrics', N'rubric_criteria', N'questions',
                 N'viva_exams', N'viva_exam_questions', N'viva_attempts', N'interview_exchanges',
                 N'question_grades', N'criteria_grades', N'grade_appeals',
                 N'background_jobs', N'audit_logs', N'system_settings')
ORDER BY t.name;

SELECT roles_id, name, description FROM dbo.roles ORDER BY roles_id;
GO

SET NOEXEC OFF;
GO
