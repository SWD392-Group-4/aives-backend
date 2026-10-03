-- =====================================================================
-- AIVES - Sua bang users DA TON TAI cho khop ERD moi. Du lieu duoc giu nguyen.
-- Chay tren database: aives. Chay lai nhieu lan khong sao.
--
-- Thay doi:
--   ~ id -> users_id (khoa chinh). PK_users va cac khoa ngoai dang tro toi cot nay
--     (viva_exams.lecturer_id, viva_attempts.student_id) tu dong di theo, khong can tao lai.
--   - student_code (bo cot, kem unique index tren cot do)
-- =====================================================================
USE aives;
GO

/* ---------- 1. Doi ten khoa chinh: id -> users_id ---------- */
IF COL_LENGTH(N'dbo.users', N'id') IS NOT NULL AND COL_LENGTH(N'dbo.users', N'users_id') IS NULL
    EXEC sp_rename N'dbo.users.id', N'users_id', N'COLUMN';
GO

/* ---------- 2. Bo cot student_code ---------- */
IF COL_LENGTH(N'dbo.users', N'student_code') IS NOT NULL
BEGIN
    DECLARE @sql NVARCHAR(MAX) = N'';

    -- Rang buoc UNIQUE (neu cot duoc khai bao UNIQUE dang constraint)
    SELECT @sql += N'ALTER TABLE dbo.users DROP CONSTRAINT ' + QUOTENAME(kc.name) + N';'
    FROM sys.key_constraints kc
    JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE kc.parent_object_id = OBJECT_ID(N'dbo.users') AND c.name = N'student_code';

    -- Index tren cot (UX_users_student_code hoac UQ_users_student_code, tuy script da chay truoc day)
    SELECT @sql += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON dbo.users;'
    FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID(N'dbo.users') AND c.name = N'student_code'
      AND i.is_primary_key = 0 AND i.is_unique_constraint = 0;

    -- DEFAULT gan vao cot (neu co)
    SELECT @sql += N'ALTER TABLE dbo.users DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
    FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID(N'dbo.users') AND c.name = N'student_code';

    SET @sql += N'ALTER TABLE dbo.users DROP COLUMN student_code;';
    EXEC sp_executesql @sql;
END
GO

/* ---------- 3. Kiem tra ket qua ---------- */
SELECT c.name AS column_name, t.name AS data_type, c.max_length, c.is_nullable
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.users')
ORDER BY c.column_id;

SELECT users_id, email, full_name, role, is_active FROM dbo.users ORDER BY role, users_id;
GO
