-- =====================================================================
-- Migration 01: Course Rubric, Chapters, Shared Question Bank, RAG Materials
-- Conforms to FLOW-02-QUESTION-BANK, BR-BANK-001 -> BR-BANK-007
-- =====================================================================

-- 1. Tao bang courses
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

-- 2. Tao bang course_lecturers (Phan cong giang day va dung chung ngan hang de)
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

CREATE INDEX IF NOT EXISTS ix_course_lecturers_course ON course_lecturers (courses_id);
CREATE INDEX IF NOT EXISTS ix_course_lecturers_user ON course_lecturers (users_id);

-- 3. Tao bang chapters (Phan vung kien thuc truc thuoc Course)
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

-- 4. Dieu chinh / tao bang materials (Hoc lieu RAG phuc vu sinh cau hoi theo chapter)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'materials') THEN
        CREATE TABLE materials (
            materials_id VARCHAR(20)  NOT NULL,
            chapters_id  VARCHAR(20)  NOT NULL,
            uploaded_by  VARCHAR(20)  NOT NULL,
            title        VARCHAR(200) NOT NULL,
            file_url     VARCHAR(500) NOT NULL,
            file_type    VARCHAR(20)  NOT NULL,
            file_size    INTEGER      NOT NULL,
            rag_status   VARCHAR(20)  NOT NULL DEFAULT 'INDEXING',
            created_at   TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,
            updated_at   TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP,

            CONSTRAINT pk_materials PRIMARY KEY (materials_id),
            CONSTRAINT fk_materials_chapter FOREIGN KEY (chapters_id) REFERENCES chapters (chapters_id) ON DELETE CASCADE,
            CONSTRAINT fk_materials_uploader FOREIGN KEY (uploaded_by) REFERENCES users (users_id),
            CONSTRAINT ck_materials_rag_status CHECK (rag_status IN ('INDEXING', 'READY', 'FAILED'))
        );
    ELSE
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'materials_id') THEN
            ALTER TABLE materials ADD COLUMN materials_id VARCHAR(20);
            UPDATE materials SET materials_id = SUBSTRING(id, 1, 20) WHERE materials_id IS NULL;
            ALTER TABLE materials ALTER COLUMN materials_id SET NOT NULL;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'chapters_id') THEN
            ALTER TABLE materials ADD COLUMN chapters_id VARCHAR(20);
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'uploaded_by') THEN
            ALTER TABLE materials ADD COLUMN uploaded_by VARCHAR(20);
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'lecturer_id') THEN
                UPDATE materials SET uploaded_by = SUBSTRING(lecturer_id, 1, 20) WHERE uploaded_by IS NULL;
            END IF;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'file_url') THEN
            ALTER TABLE materials ADD COLUMN file_url VARCHAR(500);
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'gcs_uri') THEN
                UPDATE materials SET file_url = gcs_uri WHERE file_url IS NULL;
            END IF;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'materials' AND column_name = 'updated_at') THEN
            ALTER TABLE materials ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT LOCALTIMESTAMP;
        END IF;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_materials_chapter ON materials (chapters_id);

-- 5. Dieu chinh bang rubrics (1:1 voi Course, ho tro is_locked)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'rubrics' AND column_name = 'courses_id') THEN
        ALTER TABLE rubrics ADD COLUMN courses_id VARCHAR(20);
        ALTER TABLE rubrics ADD CONSTRAINT uq_rubrics_course UNIQUE (courses_id);
        ALTER TABLE rubrics ADD CONSTRAINT fk_rubrics_course FOREIGN KEY (courses_id) REFERENCES courses (courses_id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'rubrics' AND column_name = 'is_locked') THEN
        ALTER TABLE rubrics ADD COLUMN is_locked BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- 6. Dieu chinh bang questions (Loai bo rubrics_id va bloom_level, them chapters_id, created_by, difficulty)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'rubrics_id') THEN
        ALTER TABLE questions DROP CONSTRAINT IF EXISTS fk_questions_rubric;
        ALTER TABLE questions DROP CONSTRAINT IF EXISTS ck_questions_approved_rubric;
        ALTER TABLE questions DROP COLUMN IF EXISTS rubrics_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'chapters_id') THEN
        ALTER TABLE questions ADD COLUMN chapters_id VARCHAR(20);
        ALTER TABLE questions ADD CONSTRAINT fk_questions_chapter FOREIGN KEY (chapters_id) REFERENCES chapters (chapters_id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'created_by') THEN
        ALTER TABLE questions ADD COLUMN created_by VARCHAR(20);
        ALTER TABLE questions ADD CONSTRAINT fk_questions_creator FOREIGN KEY (created_by) REFERENCES users (users_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'difficulty') THEN
        ALTER TABLE questions ADD COLUMN difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';
        ALTER TABLE questions ADD CONSTRAINT ck_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'));
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'questions' AND column_name = 'bloom_level') THEN
        ALTER TABLE questions DROP CONSTRAINT IF EXISTS ck_questions_bloom;
        ALTER TABLE questions DROP COLUMN IF EXISTS bloom_level;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_questions_chapter_status ON questions (chapters_id, status);

-- 7. Dieu chinh bang viva_exams (Them khoa ngoai courses_id)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'viva_exams' AND column_name = 'courses_id') THEN
        ALTER TABLE viva_exams ADD COLUMN courses_id VARCHAR(20);
        ALTER TABLE viva_exams ADD CONSTRAINT fk_viva_exams_course FOREIGN KEY (courses_id) REFERENCES courses (courses_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_viva_exams_course ON viva_exams (courses_id);
