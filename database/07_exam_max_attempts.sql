-- =====================================================================
-- AIVES - Gioi han so luot thi cua moi sinh vien trong 1 phien (database DA TON TAI). Du lieu duoc giu nguyen.
-- Chay tren database: aives, SAU 05_rename_viva_attempts_fk.sql. Chay lai nhieu lan khong sao.
-- PHAI chay truoc khi khoi dong backend ban moi.
--
-- Thay doi:
--   + viva_exams.max_attempts   so luot thi toi da cua moi sinh vien (1-10). Phien dang co = 1 nhu truoc day.
--   + viva_attempts.attempt_no  luot thi thu may cua sinh vien trong phien. Luot dang co = 1.
--   ~ UNIQUE (viva_exams_id, student_id)  ->  UNIQUE (viva_exams_id, student_id, attempt_no)
--     (truoc day moi sinh vien chi co 1 luot / phien, nay duoc nhieu luot)
-- =====================================================================
USE aives;
GO

/* ---------- 1. viva_exams.max_attempts ---------- */
IF COL_LENGTH(N'dbo.viva_exams', N'max_attempts') IS NULL
    ALTER TABLE dbo.viva_exams ADD max_attempts INT NOT NULL
        CONSTRAINT DF_viva_exams_max_attempts DEFAULT (1);
GO

IF OBJECT_ID(N'dbo.CK_viva_exams_max_attempts', N'C') IS NULL
    ALTER TABLE dbo.viva_exams ADD CONSTRAINT CK_viva_exams_max_attempts CHECK (max_attempts BETWEEN 1 AND 10);
GO

/* ---------- 2. viva_attempts.attempt_no ---------- */
IF COL_LENGTH(N'dbo.viva_attempts', N'attempt_no') IS NULL
    ALTER TABLE dbo.viva_attempts ADD attempt_no INT NOT NULL
        CONSTRAINT DF_viva_attempts_attempt_no DEFAULT (1);
GO

IF OBJECT_ID(N'dbo.CK_viva_attempts_attempt_no', N'C') IS NULL
    ALTER TABLE dbo.viva_attempts ADD CONSTRAINT CK_viva_attempts_attempt_no CHECK (attempt_no >= 1);
GO

/* ---------- 3. Go rang buoc cu "moi sinh vien 1 luot / phien" ---------- */
-- Tim moi UNIQUE constraint / unique index co DUNG 2 cot (viva_exams_id, student_id),
-- du ten la UQ_viva_attempts_exam_student hay ten do Hibernate tu dat.
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql += CASE WHEN i.is_unique_constraint = 1
                    THEN N'ALTER TABLE dbo.viva_attempts DROP CONSTRAINT ' + QUOTENAME(i.name) + N';'
                    ELSE N'DROP INDEX ' + QUOTENAME(i.name) + N' ON dbo.viva_attempts;'
               END
FROM sys.indexes i
WHERE i.object_id = OBJECT_ID(N'dbo.viva_attempts')
  AND i.is_unique = 1 AND i.is_primary_key = 0
  AND (SELECT COUNT(*) FROM sys.index_columns ic
       WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0) = 2
  AND (SELECT COUNT(*) FROM sys.index_columns ic
       JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
       WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
         AND c.name IN (N'viva_exams_id', N'student_id')) = 2;
EXEC sp_executesql @sql;
GO

/* ---------- 4. Rang buoc moi: (phien, sinh vien, luot thu may) khong trung ---------- */
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = N'UQ_viva_attempts_exam_student_no' AND object_id = OBJECT_ID(N'dbo.viva_attempts'))
    ALTER TABLE dbo.viva_attempts ADD CONSTRAINT UQ_viva_attempts_exam_student_no
        UNIQUE (viva_exams_id, student_id, attempt_no);
GO

/* ---------- 5. Kiem tra ket qua ---------- */
SELECT viva_exams_id, title, duration_minutes, max_attempts FROM dbo.viva_exams;
SELECT viva_attempts_id, viva_exams_id, student_id, attempt_no, status FROM dbo.viva_attempts;

SELECT i.name AS unique_name,
       STUFF((SELECT N', ' + c.name
              FROM sys.index_columns ic
              JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
              WHERE ic.object_id = i.object_id AND ic.index_id = i.index_id
              ORDER BY ic.key_ordinal
              FOR XML PATH(N'')), 1, 2, N'') AS cot
FROM sys.indexes i
WHERE i.object_id = OBJECT_ID(N'dbo.viva_attempts') AND i.is_unique = 1 AND i.is_primary_key = 0;
GO
