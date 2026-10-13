-- =====================================================================
-- AIVES - Script khoi tao database cho PostgreSQL (14+): 18 bang
--
--   Tai khoan : roles, users
--   Noi dung  : lessons, topics, rubrics, rubric_criteria, questions
--   Phien thi : viva_exams, viva_exam_topics, viva_exam_questions, viva_attempts, interview_exchanges
--   Cham diem : question_grades, criteria_grades, grade_appeals
--   He thong  : background_jobs, audit_logs, system_settings
--
-- Ban PostgreSQL cua database/00_init_aives.sql (SQL Server) + 10_exam_topics.sql.
--
-- Cach chay (database phai tao truoc, script nay khong tu tao database):
--   psql -U postgres -c "CREATE DATABASE aives ENCODING 'UTF8'"
--   psql -U postgres -d aives -f database/postgres/00_init_aives.sql
-- (pgAdmin: chuot phai database aives > Query Tool > mo file nay > Execute)
--
-- KHONG bat buoc: backend de ddl-auto = update nen tu tao bang khi khoi dong. Chay script nay de co them
-- rang buoc CHECK, index va gia tri mac dinh nhu ban SQL Server.
-- Chay lai nhieu lan khong sao: bang / index / rang buoc nao da co thi bo qua, khong xoa du lieu.
--
-- Khac voi ban SQL Server:
--   - NVARCHAR(n) -> VARCHAR(n), NVARCHAR(MAX) -> TEXT (PostgreSQL luu UTF-8, khong can kieu N...).
--   - DATETIME -> TIMESTAMP(6), BIT -> BOOLEAN, GETDATE() -> LOCALTIMESTAMP.
--   - UNIQUE cho phep nhieu NULL nen khong can filtered index; van giu partial index cho don phuc khao PENDING.
--   - Ten bang / cot / rang buoc viet thuong (PostgreSQL tu doi chu hoa khong dat trong "..." thanh chu thuong).
-- =====================================================================

/* =====================================================================
   1. TAI KHOAN
   ===================================================================== */

CREATE TABLE IF NOT EXISTS roles (
    roles_id    VARCHAR(20)  NOT NULL,   -- AD / LE / ST
    name        VARCHAR(20)  NOT NULL,   -- khop voi enum RoleName trong Java
    description VARCHAR(255) NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_roles      PRIMARY KEY (roles_id),
    CONSTRAINT uq_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('ADMIN', 'LECTURER', 'STUDENT'))
);

-- 3 role co dinh cua he thong. Thieu role nao thi them role do.
INSERT INTO roles (roles_id, name, description)
SELECT v.roles_id, v.name, v.description
FROM (VALUES
    ('AD', 'ADMIN',    'Quản trị viên'),
    ('LE', 'LECTURER', 'Giảng viên'),
    ('ST', 'STUDENT',  'Sinh viên')
) AS v (roles_id, name, description)
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = v.name);

CREATE TABLE IF NOT EXISTS users (
    users_id             VARCHAR(20)  NOT NULL,
    roles_id             VARCHAR(20)  NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,   -- BCrypt
    full_name            VARCHAR(150) NOT NULL,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    -- TRUE = tai khoan do admin tao, dang dung mat khau tam gui qua email -> phai doi mat khau truoc
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at           TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_users       PRIMARY KEY (users_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_role  FOREIGN KEY (roles_id) REFERENCES roles (roles_id)
);

CREATE INDEX IF NOT EXISTS ix_users_role ON users (roles_id);

/* =====================================================================
   2. NOI DUNG: LESSON -> TOPIC -> QUESTION, RUBRIC -> RUBRIC_CRITERIA
   ===================================================================== */

CREATE TABLE IF NOT EXISTS lessons (
    lessons_id  VARCHAR(20)  NOT NULL,
    lecturer_id VARCHAR(20)  NOT NULL,   -- giang vien quan ly bai hoc
    title       VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_lessons          PRIMARY KEY (lessons_id),
    CONSTRAINT fk_lessons_lecturer FOREIGN KEY (lecturer_id) REFERENCES users (users_id)
);

CREATE INDEX IF NOT EXISTS ix_lessons_lecturer ON lessons (lecturer_id);

CREATE TABLE IF NOT EXISTS topics (
    topics_id   VARCHAR(20)  NOT NULL,
    lessons_id  VARCHAR(20)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_topics        PRIMARY KEY (topics_id),
    CONSTRAINT fk_topics_lesson FOREIGN KEY (lessons_id) REFERENCES lessons (lessons_id)
);

CREATE INDEX IF NOT EXISTS ix_topics_lesson ON topics (lessons_id);

CREATE TABLE IF NOT EXISTS rubrics (
    rubrics_id  VARCHAR(20)   NOT NULL,
    created_by  VARCHAR(20)   NOT NULL,   -- nguoi tao rubric
    name        VARCHAR(200)  NOT NULL,
    description TEXT          NULL,
    max_score   NUMERIC(5, 2) NOT NULL DEFAULT 10,
    created_at  TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at  TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_rubrics           PRIMARY KEY (rubrics_id),
    CONSTRAINT fk_rubrics_creator   FOREIGN KEY (created_by) REFERENCES users (users_id),
    CONSTRAINT ck_rubrics_max_score CHECK (max_score > 0)
);

CREATE TABLE IF NOT EXISTS rubric_criteria (
    rubric_criteria_id VARCHAR(20)   NOT NULL,
    rubrics_id         VARCHAR(20)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    description        TEXT          NOT NULL,   -- BR-BANK-003: bat buoc mo ta yeu cau dat diem
    weight_percent     NUMERIC(5, 2) NOT NULL,   -- tong weight cua 1 rubric = 100 (kiem tra o service)
    order_no           INTEGER       NOT NULL DEFAULT 1,
    created_at         TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at         TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_rubric_criteria        PRIMARY KEY (rubric_criteria_id),
    CONSTRAINT fk_rubric_criteria_rubric FOREIGN KEY (rubrics_id) REFERENCES rubrics (rubrics_id),
    CONSTRAINT ck_rubric_criteria_weight CHECK (weight_percent > 0 AND weight_percent <= 100)
);

CREATE INDEX IF NOT EXISTS ix_rubric_criteria_rubric ON rubric_criteria (rubrics_id, order_no);

CREATE TABLE IF NOT EXISTS questions (
    questions_id       VARCHAR(20)  NOT NULL,
    topics_id          VARCHAR(20)  NOT NULL,
    rubrics_id         VARCHAR(20)  NULL,       -- NULL khi cau hoi con DRAFT
    content            TEXT         NOT NULL,
    bloom_level        VARCHAR(20)  NOT NULL,
    model_answer       TEXT         NULL,
    expected_keywords  TEXT         NULL,       -- JSON array
    source             VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    question_audio_key VARCHAR(500) NULL,       -- cache audio TTS cua cau goc
    created_at         TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_questions        PRIMARY KEY (questions_id),
    CONSTRAINT fk_questions_topic  FOREIGN KEY (topics_id)  REFERENCES topics (topics_id),
    CONSTRAINT fk_questions_rubric FOREIGN KEY (rubrics_id) REFERENCES rubrics (rubrics_id),
    CONSTRAINT ck_questions_bloom  CHECK (bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE')),
    CONSTRAINT ck_questions_source CHECK (source IN ('MANUAL', 'AI_GENERATED')),
    CONSTRAINT ck_questions_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'ARCHIVED')),
    -- BR-BANK-001: cau hoi APPROVED bat buoc da gan rubric
    CONSTRAINT ck_questions_approved_rubric CHECK (status <> 'APPROVED' OR rubrics_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS ix_questions_topic_status ON questions (topics_id, status);

/* =====================================================================
   3. PHIEN THI VA LUOT THI
   ===================================================================== */

CREATE TABLE IF NOT EXISTS viva_exams (
    viva_exams_id              VARCHAR(30)   NOT NULL,   -- AIVES_EXAM_yyyy_xxxxxx, cung la ma vao thi
    lecturer_id                VARCHAR(20)   NOT NULL,   -- nguoi tao phien (LECTURER hoac ADMIN)
    title                      VARCHAR(200)  NOT NULL,
    description                VARCHAR(2000) NULL,
    passcode                   VARCHAR(6)    NOT NULL,   -- ma truy cap 6 chu so
    duration_minutes           INTEGER       NOT NULL,   -- thoi luong lam bai cua MOI luot thi
    max_attempts               INTEGER       NOT NULL DEFAULT 1,
    max_follow_up_per_question INTEGER       NOT NULL DEFAULT 2,
    prepare_seconds            INTEGER       NOT NULL DEFAULT 30,
    answer_seconds             INTEGER       NOT NULL DEFAULT 120,
    domain_keywords            TEXT          NULL,       -- BR-VIVA-004: goi y tu khoa cho STT (JSON array)
    question_count             INTEGER       NULL,       -- so cau boc tu cac chu de da chon
    start_at                   TIMESTAMP(6)  NOT NULL,   -- gio mo phien (UTC)
    end_at                     TIMESTAMP(6)  NOT NULL,   -- gio dong phien (UTC)
    cancelled_at               TIMESTAMP(6)  NULL,       -- khac NULL = phien da bi huy
    created_at                 TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at                 TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_viva_exams              PRIMARY KEY (viva_exams_id),
    CONSTRAINT fk_viva_exams_lecturer     FOREIGN KEY (lecturer_id) REFERENCES users (users_id),
    CONSTRAINT ck_viva_exams_duration     CHECK (duration_minutes BETWEEN 5 AND 300),
    CONSTRAINT ck_viva_exams_max_attempts CHECK (max_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_viva_exams_follow_up    CHECK (max_follow_up_per_question BETWEEN 0 AND 5),
    CONSTRAINT ck_viva_exams_time         CHECK (end_at > start_at)
);

CREATE INDEX IF NOT EXISTS ix_viva_exams_lecturer ON viva_exams (lecturer_id, start_at);

-- Chu de duoc chon khi tao phien thi (@ElementCollection VivaExam.topicIds)
CREATE TABLE IF NOT EXISTS viva_exam_topics (
    viva_exams_id VARCHAR(30) NOT NULL,
    topics_id     VARCHAR(20) NOT NULL,

    CONSTRAINT pk_viva_exam_topics      PRIMARY KEY (viva_exams_id, topics_id),
    CONSTRAINT fk_viva_exam_topics_exam FOREIGN KEY (viva_exams_id) REFERENCES viva_exams (viva_exams_id)
);

CREATE TABLE IF NOT EXISTS viva_exam_questions (
    viva_exam_questions_id VARCHAR(20)   NOT NULL,
    viva_exams_id          VARCHAR(30)   NOT NULL,
    questions_id           VARCHAR(20)   NOT NULL,
    order_no               INTEGER       NOT NULL,
    weight                 NUMERIC(5, 2) NOT NULL DEFAULT 1,
    created_at             TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at             TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_viva_exam_questions               PRIMARY KEY (viva_exam_questions_id),
    CONSTRAINT fk_viva_exam_questions_exam          FOREIGN KEY (viva_exams_id) REFERENCES viva_exams (viva_exams_id),
    CONSTRAINT fk_viva_exam_questions_question      FOREIGN KEY (questions_id)  REFERENCES questions (questions_id),
    CONSTRAINT uq_viva_exam_questions_exam_question UNIQUE (viva_exams_id, questions_id),
    CONSTRAINT uq_viva_exam_questions_exam_order    UNIQUE (viva_exams_id, order_no),
    CONSTRAINT ck_viva_exam_questions_weight        CHECK (weight > 0)
);

CREATE TABLE IF NOT EXISTS viva_attempts (
    viva_attempts_id  VARCHAR(20)   NOT NULL,
    viva_exams_id     VARCHAR(30)   NOT NULL,
    student_id        VARCHAR(20)   NOT NULL,
    attempt_no        INTEGER       NOT NULL DEFAULT 1,   -- luot thu may cua sinh vien trong phien
    status            VARCHAR(20)   NOT NULL,
    result_status     VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    total_ai_score    NUMERIC(6, 2) NULL,
    total_final_score NUMERIC(6, 2) NULL,
    full_audio_key    VARCHAR(500)  NULL,
    started_at        TIMESTAMP(6)  NOT NULL,   -- luc sinh vien bat dau luot thi (UTC)
    deadline_at       TIMESTAMP(6)  NOT NULL,   -- han lam bai = min(started_at + duration, end_at) (UTC)
    completed_at      TIMESTAMP(6)  NULL,
    published_at      TIMESTAMP(6)  NULL,
    created_at        TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at        TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_viva_attempts                 PRIMARY KEY (viva_attempts_id),
    CONSTRAINT fk_viva_attempts_exam            FOREIGN KEY (viva_exams_id) REFERENCES viva_exams (viva_exams_id),
    CONSTRAINT fk_viva_attempts_student         FOREIGN KEY (student_id)    REFERENCES users (users_id),
    CONSTRAINT uq_viva_attempts_exam_student_no UNIQUE (viva_exams_id, student_id, attempt_no),
    CONSTRAINT ck_viva_attempts_attempt_no      CHECK (attempt_no >= 1),
    CONSTRAINT ck_viva_attempts_status          CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_viva_attempts_result          CHECK (result_status IN ('NONE', 'GRADING', 'PENDING_REVIEW', 'GRADING_FAILED', 'PUBLISHED')),
    CONSTRAINT ck_viva_attempts_deadline        CHECK (deadline_at > started_at)
);

CREATE INDEX IF NOT EXISTS ix_viva_attempts_student ON viva_attempts (student_id);
-- Bo cham ngam quet theo (status, result_status) moi 5 giay
CREATE INDEX IF NOT EXISTS ix_viva_attempts_status ON viva_attempts (status, result_status);

CREATE TABLE IF NOT EXISTS interview_exchanges (
    interview_exchanges_id VARCHAR(20)  NOT NULL,
    viva_attempts_id       VARCHAR(20)  NOT NULL,
    viva_exam_questions_id VARCHAR(20)  NOT NULL,
    parent_exchange_id     VARCHAR(20)  NULL,       -- NULL = cau goc; khac NULL = cau hoi xoay cua luot cha
    depth                  INTEGER      NOT NULL DEFAULT 0,   -- 0 = cau goc, 1.. = hoi xoay
    question_text          TEXT         NOT NULL,
    question_audio_key     VARCHAR(500) NULL,
    transcript             TEXT         NULL,
    answer_audio_key       VARCHAR(500) NULL,
    follow_up_reason       VARCHAR(20)  NULL,
    ai_decision            VARCHAR(20)  NULL,
    ai_decision_reason     TEXT         NULL,
    answer_started_at      TIMESTAMP(6) NULL,
    answer_ended_at        TIMESTAMP(6) NULL,
    latency_ms             INTEGER      NULL,       -- tu luc sinh vien ngung noi den cau hoi tiep theo (BR-VIVA-003)
    created_at             TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at             TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_interview_exchanges          PRIMARY KEY (interview_exchanges_id),
    CONSTRAINT fk_interview_exchanges_attempt  FOREIGN KEY (viva_attempts_id)       REFERENCES viva_attempts (viva_attempts_id),
    CONSTRAINT fk_interview_exchanges_question FOREIGN KEY (viva_exam_questions_id) REFERENCES viva_exam_questions (viva_exam_questions_id),
    CONSTRAINT fk_interview_exchanges_parent   FOREIGN KEY (parent_exchange_id)     REFERENCES interview_exchanges (interview_exchanges_id),
    -- Moi luot hoi - dap co toi da 1 cau hoi xoay con (PostgreSQL: UNIQUE bo qua NULL, khong can filtered index)
    CONSTRAINT uq_interview_exchanges_parent   UNIQUE (parent_exchange_id),
    CONSTRAINT ck_interview_exchanges_reason   CHECK (follow_up_reason IN ('INCOMPLETE', 'AMBIGUOUS', 'CONTRADICTORY')),
    CONSTRAINT ck_interview_exchanges_decision CHECK (ai_decision IN ('FOLLOW_UP', 'NEXT', 'SKIPPED')),
    CONSTRAINT ck_interview_exchanges_depth    CHECK ((parent_exchange_id IS NULL AND depth = 0) OR (parent_exchange_id IS NOT NULL AND depth > 0))
);

CREATE INDEX IF NOT EXISTS ix_interview_exchanges_attempt_question ON interview_exchanges (viva_attempts_id, viva_exam_questions_id);

/* =====================================================================
   4. CHAM DIEM VA PHUC KHAO
   ===================================================================== */

CREATE TABLE IF NOT EXISTS question_grades (
    question_grades_id     VARCHAR(20)   NOT NULL,
    viva_attempts_id       VARCHAR(20)   NOT NULL,
    viva_exam_questions_id VARCHAR(20)   NOT NULL,
    ai_score               NUMERIC(5, 2) NULL,
    final_score            NUMERIC(5, 2) NULL,
    ai_strengths           TEXT          NULL,
    ai_weaknesses          TEXT          NULL,
    ai_feedback            TEXT          NULL,
    lecturer_note          TEXT          NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'AI_DRAFT',
    graded_by              VARCHAR(20)   NULL,       -- NULL khi giang vien chua chot
    graded_at              TIMESTAMP(6)  NULL,
    created_at             TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at             TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_question_grades                  PRIMARY KEY (question_grades_id),
    CONSTRAINT fk_question_grades_attempt          FOREIGN KEY (viva_attempts_id)       REFERENCES viva_attempts (viva_attempts_id),
    CONSTRAINT fk_question_grades_question         FOREIGN KEY (viva_exam_questions_id) REFERENCES viva_exam_questions (viva_exam_questions_id),
    CONSTRAINT fk_question_grades_grader           FOREIGN KEY (graded_by)              REFERENCES users (users_id),
    CONSTRAINT uq_question_grades_attempt_question UNIQUE (viva_attempts_id, viva_exam_questions_id),
    CONSTRAINT ck_question_grades_status           CHECK (status IN ('AI_DRAFT', 'APPROVED', 'FAILED')),
    CONSTRAINT ck_question_grades_approved         CHECK (status <> 'APPROVED' OR graded_by IS NOT NULL),
    -- BR-GRADE-002: diem chot lech diem AI qua 2.0 thi bat buoc co ghi chu cua giang vien
    CONSTRAINT ck_question_grades_note             CHECK (
        final_score IS NULL OR ai_score IS NULL
        OR ABS(final_score - ai_score) <= 2.0
        OR lecturer_note IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS criteria_grades (
    criteria_grades_id VARCHAR(20)   NOT NULL,
    question_grades_id VARCHAR(20)   NOT NULL,
    rubric_criteria_id VARCHAR(20)   NOT NULL,
    ai_score           NUMERIC(5, 2) NULL,
    final_score        NUMERIC(5, 2) NULL,
    evidence_quote     TEXT          NULL,       -- BR-GRADE-001: trich dan tu transcript
    rationale          TEXT          NULL,
    created_at         TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at         TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_criteria_grades                   PRIMARY KEY (criteria_grades_id),
    CONSTRAINT fk_criteria_grades_question_grade    FOREIGN KEY (question_grades_id) REFERENCES question_grades (question_grades_id),
    CONSTRAINT fk_criteria_grades_criteria          FOREIGN KEY (rubric_criteria_id) REFERENCES rubric_criteria (rubric_criteria_id),
    CONSTRAINT uq_criteria_grades_question_criteria UNIQUE (question_grades_id, rubric_criteria_id)
);

CREATE TABLE IF NOT EXISTS grade_appeals (
    grade_appeals_id   VARCHAR(20)   NOT NULL,
    question_grades_id VARCHAR(20)   NOT NULL,
    reason             TEXT          NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    score_before       NUMERIC(5, 2) NOT NULL,
    score_after        NUMERIC(5, 2) NULL,
    response           TEXT          NULL,
    resolved_by        VARCHAR(20)   NULL,       -- NULL khi don chua duoc xu ly
    resolved_at        TIMESTAMP(6)  NULL,
    created_at         TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at         TIMESTAMP(6)  NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_grade_appeals                PRIMARY KEY (grade_appeals_id),
    CONSTRAINT fk_grade_appeals_question_grade FOREIGN KEY (question_grades_id) REFERENCES question_grades (question_grades_id),
    CONSTRAINT fk_grade_appeals_resolver       FOREIGN KEY (resolved_by)        REFERENCES users (users_id),
    CONSTRAINT ck_grade_appeals_status         CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_grade_appeals_resolved       CHECK (status = 'PENDING' OR resolved_by IS NOT NULL)
);

-- Moi diem cau chi co toi da 1 don phuc khao dang PENDING
CREATE UNIQUE INDEX IF NOT EXISTS ux_grade_appeals_pending ON grade_appeals (question_grades_id) WHERE status = 'PENDING';

/* =====================================================================
   5. BANG HE THONG
   ===================================================================== */

CREATE TABLE IF NOT EXISTS background_jobs (
    background_jobs_id VARCHAR(20)  NOT NULL,
    job_type           VARCHAR(50)  NOT NULL,   -- EVALUATE_ATTEMPT, AUDIO_UPLOAD
    payload            TEXT         NULL,       -- JSON
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts           INTEGER      NOT NULL DEFAULT 0,
    next_run_at        TIMESTAMP(6) NULL     DEFAULT LOCALTIMESTAMP,
    last_error         TEXT         NULL,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_background_jobs        PRIMARY KEY (background_jobs_id),
    CONSTRAINT ck_background_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_background_jobs_poll ON background_jobs (status, next_run_at);

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_logs_id VARCHAR(20)  NOT NULL,
    actor_id      VARCHAR(20)  NOT NULL,   -- nguoi thuc hien
    action        VARCHAR(50)  NOT NULL,   -- OVERRIDE_SCORE, PUBLISH_RESULT, APPROVE_QUESTION, RESOLVE_APPEAL...
    entity_type   VARCHAR(50)  NOT NULL,
    entity_id     VARCHAR(50)  NOT NULL,
    old_value     TEXT         NULL,
    new_value     TEXT         NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_audit_logs       PRIMARY KEY (audit_logs_id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users (users_id)
);

CREATE INDEX IF NOT EXISTS ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key   VARCHAR(100) NOT NULL,
    setting_value TEXT         NOT NULL,
    updated_by    VARCHAR(20)  NULL,
    updated_at    TIMESTAMP(6) NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_system_settings      PRIMARY KEY (setting_key),
    CONSTRAINT fk_system_settings_user FOREIGN KEY (updated_by) REFERENCES users (users_id)
);

/* =====================================================================
   6. KIEM TRA KET QUA: phai du 18 bang
   ===================================================================== */
SELECT t.table_name,
       (SELECT COUNT(*) FROM information_schema.columns c
         WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name) AS so_cot
FROM information_schema.tables t
WHERE t.table_schema = current_schema()
  AND t.table_name IN ('roles', 'users', 'lessons', 'topics', 'rubrics', 'rubric_criteria', 'questions',
                       'viva_exams', 'viva_exam_topics', 'viva_exam_questions', 'viva_attempts', 'interview_exchanges',
                       'question_grades', 'criteria_grades', 'grade_appeals',
                       'background_jobs', 'audit_logs', 'system_settings')
ORDER BY t.table_name;

SELECT roles_id, name, description FROM roles ORDER BY roles_id;
