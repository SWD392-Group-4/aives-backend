-- =====================================================================
-- FLOW-02-QUESTION-BANK: Migration Script (TASK-011)
-- Course Hierarchy, Shared Questions, and Audit Log Setup
-- =====================================================================

-- 1. BẢNG MÔN HỌC (COURSES)
CREATE TABLE IF NOT EXISTS courses (
    courses_id  VARCHAR(20)  NOT NULL,
    course_code VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_courses PRIMARY KEY (courses_id),
    CONSTRAINT uq_courses_code UNIQUE (course_code)
);

CREATE INDEX IF NOT EXISTS ix_courses_code ON courses (course_code);

-- 2. BẢNG PHÂN CÔNG GIẢNG VIÊN MÔN HỌC (COURSE_LECTURERS)
CREATE TABLE IF NOT EXISTS course_lecturers (
    course_lecturers_id VARCHAR(20)  NOT NULL,
    courses_id          VARCHAR(20)  NOT NULL,
    users_id            VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_course_lecturers PRIMARY KEY (course_lecturers_id),
    CONSTRAINT fk_course_lecturers_course FOREIGN KEY (courses_id) REFERENCES courses (courses_id) ON DELETE CASCADE,
    CONSTRAINT fk_course_lecturers_user FOREIGN KEY (users_id) REFERENCES users (users_id) ON DELETE CASCADE,
    CONSTRAINT uq_course_lecturers UNIQUE (courses_id, users_id)
);

CREATE INDEX IF NOT EXISTS ix_course_lecturers_user ON course_lecturers (users_id);

-- 3. BẢNG CHƯƠNG KIẾN THỨC (CHAPTERS)
CREATE TABLE IF NOT EXISTS chapters (
    chapters_id VARCHAR(20)  NOT NULL,
    courses_id  VARCHAR(20)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    order_no    INTEGER      NOT NULL DEFAULT 1,
    description TEXT         NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_chapters PRIMARY KEY (chapters_id),
    CONSTRAINT fk_chapters_course FOREIGN KEY (courses_id) REFERENCES courses (courses_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_chapters_course_order ON chapters (courses_id, order_no);

-- 4. BẢNG HỌC LIỆU RAG (MATERIALS)
CREATE TABLE IF NOT EXISTS materials (
    id             VARCHAR(50)  NOT NULL,
    lecturer_id    VARCHAR(50)  NOT NULL,
    lesson_id      VARCHAR(50)  NULL,
    topic_id       VARCHAR(50)  NULL,
    chapters_id    VARCHAR(50)  NULL,
    uploaded_by    VARCHAR(50)  NULL,
    title          VARCHAR(255) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_size      BIGINT       NOT NULL,
    file_type      VARCHAR(20)  NOT NULL,
    gcs_uri        VARCHAR(500) NOT NULL,
    rag_corpus_id  VARCHAR(100) NULL DEFAULT '6917529027641081856',
    rag_status     VARCHAR(30)  NOT NULL DEFAULT 'INDEXING',
    chunk_count    INTEGER      NULL DEFAULT 0,
    error_message  TEXT         NULL,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

    CONSTRAINT pk_materials PRIMARY KEY (id)
);

-- Bổ sung cột cho materials nếu bảng đã có sẵn từ trước
ALTER TABLE materials ADD COLUMN IF NOT EXISTS chapters_id VARCHAR(50) NULL;
ALTER TABLE materials ADD COLUMN IF NOT EXISTS uploaded_by VARCHAR(50) NULL;

-- 5. CẬP NHẬT BẢNG RUBRICS (RUBRIC DUY NHẤT CẤP MÔN HỌC & IMMUTABILITY LOCK)
ALTER TABLE rubrics ADD COLUMN IF NOT EXISTS courses_id VARCHAR(20) NULL;
ALTER TABLE rubrics ADD COLUMN IF NOT EXISTS is_locked BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rubrics_course') THEN
        ALTER TABLE rubrics ADD CONSTRAINT fk_rubrics_course FOREIGN KEY (courses_id) REFERENCES courses (courses_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_rubrics_course') THEN
        ALTER TABLE rubrics ADD CONSTRAINT uq_rubrics_course UNIQUE (courses_id);
    END IF;
END $$;

-- 6. CẬP NHẬT BẢNG QUESTIONS (NGÂN HÀNG DÙNG CHUNG THEO CHƯƠNG & ĐỘ KHÓ TRỰC QUAN)
ALTER TABLE questions ADD COLUMN IF NOT EXISTS chapters_id VARCHAR(20) NULL;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS created_by VARCHAR(20) NULL;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';

-- Cho phép NULL đối với các trường cấu trúc cũ để tương thích ngược
ALTER TABLE questions ALTER COLUMN topics_id DROP NOT NULL;
ALTER TABLE questions ALTER COLUMN rubrics_id DROP NOT NULL;
ALTER TABLE questions ALTER COLUMN bloom_level DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_questions_chapter') THEN
        ALTER TABLE questions ADD CONSTRAINT fk_questions_chapter FOREIGN KEY (chapters_id) REFERENCES chapters (chapters_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_questions_difficulty') THEN
        ALTER TABLE questions ADD CONSTRAINT ck_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_questions_chapter_status ON questions (chapters_id, status);

-- 7. CẬP NHẬT BẢNG VIVA_EXAMS (LIÊN KẾT CA THI VỚI MÔN HỌC)
ALTER TABLE viva_exams ADD COLUMN IF NOT EXISTS courses_id VARCHAR(20) NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_viva_exams_course') THEN
        ALTER TABLE viva_exams ADD CONSTRAINT fk_viva_exams_course FOREIGN KEY (courses_id) REFERENCES courses (courses_id);
    END IF;
END $$;
