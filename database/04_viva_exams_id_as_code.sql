-- =====================================================================
-- AIVES - Bo cot exam_code: khoa chinh viva_exams_id chinh la ma vao thi AIVES_EXAM_yyyy_xxxxxx.
-- Chay tren database: aives, khi bang viva_exams DA TON TAI va con cot exam_code.
-- Du lieu duoc giu: moi phien cu doi id tu EXxxxxxx sang gia tri exam_code cua no,
-- luot thi trong viva_attempts doi theo.
-- Chay TRUOC 05_rename_viva_attempts_fk.sql (file do doi ten cot viva_exam_id ma script nay dang dung).
--
-- Ma dai 22 ky tu nen viva_exams.viva_exams_id va viva_attempts.viva_exam_id
-- doi tu NVARCHAR(20) sang NVARCHAR(30).
-- =====================================================================
USE aives;
GO

/* ---------- 1. Go cac rang buoc dang giu 2 cot can doi kieu ---------- */
IF OBJECT_ID(N'dbo.FK_viva_attempts_exam', N'F') IS NOT NULL
    ALTER TABLE dbo.viva_attempts DROP CONSTRAINT FK_viva_attempts_exam;
IF OBJECT_ID(N'dbo.UQ_viva_attempts_exam_student', N'UQ') IS NOT NULL
    ALTER TABLE dbo.viva_attempts DROP CONSTRAINT UQ_viva_attempts_exam_student;
IF OBJECT_ID(N'dbo.PK_viva_exams', N'PK') IS NOT NULL
    ALTER TABLE dbo.viva_exams DROP CONSTRAINT PK_viva_exams;
IF OBJECT_ID(N'dbo.UQ_viva_exams_exam_code', N'UQ') IS NOT NULL
    ALTER TABLE dbo.viva_exams DROP CONSTRAINT UQ_viva_exams_exam_code;
GO

/* ---------- 2. Noi rong 2 cot len NVARCHAR(30) ---------- */
ALTER TABLE dbo.viva_exams    ALTER COLUMN viva_exams_id NVARCHAR(30) NOT NULL;
ALTER TABLE dbo.viva_attempts ALTER COLUMN viva_exam_id  NVARCHAR(30) NOT NULL;
GO

/* ---------- 3. Doi id cua phien cu sang gia tri exam_code, roi bo cot exam_code ---------- */
-- Dung EXEC vi lan chay lai cot exam_code khong con, cau lenh nhac toi no se khong bien dich duoc.
IF COL_LENGTH(N'dbo.viva_exams', N'exam_code') IS NOT NULL
BEGIN
    EXEC (N'UPDATE a SET a.viva_exam_id = e.exam_code
            FROM dbo.viva_attempts a
            JOIN dbo.viva_exams e ON e.viva_exams_id = a.viva_exam_id;');
    EXEC (N'UPDATE dbo.viva_exams SET viva_exams_id = exam_code;');
    EXEC (N'ALTER TABLE dbo.viva_exams DROP COLUMN exam_code;');
END
GO

/* ---------- 4. Tao lai cac rang buoc ---------- */
IF OBJECT_ID(N'dbo.PK_viva_exams', N'PK') IS NULL
    ALTER TABLE dbo.viva_exams ADD CONSTRAINT PK_viva_exams PRIMARY KEY (viva_exams_id);
IF OBJECT_ID(N'dbo.UQ_viva_attempts_exam_student', N'UQ') IS NULL
    ALTER TABLE dbo.viva_attempts ADD CONSTRAINT UQ_viva_attempts_exam_student UNIQUE (viva_exam_id, student_id);
IF OBJECT_ID(N'dbo.FK_viva_attempts_exam', N'F') IS NULL
    ALTER TABLE dbo.viva_attempts ADD CONSTRAINT FK_viva_attempts_exam
        FOREIGN KEY (viva_exam_id) REFERENCES dbo.viva_exams (viva_exams_id);
GO

/* ---------- 5. Kiem tra ket qua ---------- */
SELECT viva_exams_id, lecturer_id, title, passcode, duration_minutes, start_at, end_at FROM dbo.viva_exams;
SELECT viva_attempts_id, viva_exam_id, student_id, status FROM dbo.viva_attempts;
GO
