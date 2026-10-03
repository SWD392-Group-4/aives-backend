-- =====================================================================
-- AIVES - Dong bo ten cot theo ERD: khoa ngoai mang dung ten khoa chinh ma no tro toi.
-- Chay tren database: aives, SAU 04_viva_exams_id_as_code.sql. Du lieu duoc giu nguyen.
-- Chay lai nhieu lan khong sao.
--
-- Thay doi:
--   ~ viva_attempts.viva_exam_id -> viva_exams_id
--     (FK_viva_attempts_exam va UQ_viva_attempts_exam_student tu dong di theo ten cot moi)
--   ~ viva_exams.passcode NVARCHAR(10) -> NVARCHAR(6) cho khop ERD (ma truy cap luon dung 6 chu so)
-- =====================================================================
USE aives;
GO

/* ---------- 1. Doi ten khoa ngoai ---------- */
IF COL_LENGTH(N'dbo.viva_attempts', N'viva_exam_id') IS NOT NULL
   AND COL_LENGTH(N'dbo.viva_attempts', N'viva_exams_id') IS NULL
    EXEC sp_rename N'dbo.viva_attempts.viva_exam_id', N'viva_exams_id', N'COLUMN';
GO

/* ---------- 2. Thu gon passcode ve NVARCHAR(6) ---------- */
-- Chi doi khi moi ma dang co deu dai dung 6 ky tu, tranh cat mat du lieu.
IF NOT EXISTS (SELECT 1 FROM dbo.viva_exams WHERE LEN(passcode) > 6)
    ALTER TABLE dbo.viva_exams ALTER COLUMN passcode NVARCHAR(6) NOT NULL;
GO

/* ---------- 3. Kiem tra ket qua ---------- */
SELECT viva_attempts_id, viva_exams_id, student_id, status FROM dbo.viva_attempts;

SELECT c.name AS column_name, t.name AS data_type, c.max_length / 2 AS so_ky_tu
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.viva_exams') AND c.name IN (N'viva_exams_id', N'passcode');
GO
