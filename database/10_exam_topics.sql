-- Chon chu de khi tao phien thi (2026-10-07).
-- KHONG bat buoc chay tay: backend dang de spring.jpa.hibernate.ddl-auto = update nen tu tao cot va bang nay
-- khi khoi dong. Script nay de tham khao / dung khi tat ddl-auto. Chay nhieu lan khong sao.

IF COL_LENGTH('viva_exams', 'question_count') IS NULL
    ALTER TABLE viva_exams ADD question_count INT NULL;
GO

IF OBJECT_ID('viva_exam_topics', 'U') IS NULL
    CREATE TABLE viva_exam_topics (
        viva_exams_id NVARCHAR(30) NOT NULL,
        topics_id     NVARCHAR(20) NOT NULL,
        CONSTRAINT PK_viva_exam_topics PRIMARY KEY (viva_exams_id, topics_id),
        CONSTRAINT FK_viva_exam_topics_exam FOREIGN KEY (viva_exams_id) REFERENCES viva_exams (viva_exams_id)
    );
GO
