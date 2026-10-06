-- =====================================================================
-- AIVES - Doi cac cot kieu TEXT / NTEXT sang NVARCHAR(MAX) (database DA TON TAI). Du lieu duoc giu nguyen.
-- Chay tren database: aives. Chay lai nhieu lan khong sao (khong con cot TEXT thi khong lam gi).
--
-- Vi sao can: neu bang do Hibernate tu tao (ddl-auto: update) thay vi tao bang 00_init_aives.sql thi
-- cac cot noi dung dai (cau hoi, dap an, cau tra loi cua sinh vien, nhan xet...) co kieu TEXT.
-- TEXT khong luu duoc tieng Viet co dau: chu se thanh dau "?".
-- Bang tao bang 00_init_aives.sql da la NVARCHAR(MAX) san, khong can chay file nay.
--
-- Nen chay TRUOC khi thi thu phong van AI (cau hoi mau va cau tra loi deu la tieng Viet).
-- =====================================================================
USE aives;
GO

DECLARE @sql NVARCHAR(MAX) = N'';

SELECT @sql = @sql
    + N'ALTER TABLE ' + QUOTENAME(TABLE_SCHEMA) + N'.' + QUOTENAME(TABLE_NAME)
    + N' ALTER COLUMN ' + QUOTENAME(COLUMN_NAME) + N' NVARCHAR(MAX) '
    + CASE WHEN IS_NULLABLE = 'YES' THEN N'NULL' ELSE N'NOT NULL' END
    + N';' + CHAR(13) + CHAR(10)
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND DATA_TYPE IN (N'text', N'ntext');

IF @sql = N''
    PRINT N'Khong co cot TEXT / NTEXT nao, khong can doi.';
ELSE
BEGIN
    PRINT N'Cac lenh se chay:';
    PRINT @sql;
    EXEC sys.sp_executesql @sql;
    PRINT N'Da doi xong sang NVARCHAR(MAX).';
END
GO

-- Kiem tra lai: ket qua phai rong
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo' AND DATA_TYPE IN (N'text', N'ntext');
GO
