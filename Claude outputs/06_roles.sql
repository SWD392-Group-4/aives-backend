-- =====================================================================
-- AIVES - Tach bang roles ra khoi users (database DA TON TAI). Du lieu duoc giu nguyen.
-- Chay tren database: aives. Chay lai nhieu lan khong sao.
-- PHAI chay truoc khi khoi dong backend ban moi (entity User khong con cot role).
--
-- Thay doi:
--   + bang roles (roles_id, name, description) va 3 dong AD / LE / ST
--   + users.roles_id  -> khoa ngoai toi roles.roles_id (moi user dung 1 role)
--   - users.role      (bo cot, kem CHECK constraint CK_users_role)
--   + users.must_change_password (1 = dang dung mat khau tam do admin cap, phai doi mat khau truoc)
-- =====================================================================
USE aives;
GO

/* ---------- 1. Bang roles + 3 role co dinh ---------- */
IF OBJECT_ID(N'dbo.roles', N'U') IS NULL
CREATE TABLE dbo.roles (
    roles_id    NVARCHAR(20)  NOT NULL,   -- AD / LE / ST, cung la tien to cua users_id
    name        NVARCHAR(20)  NOT NULL,   -- khop voi enum RoleName trong Java
    description NVARCHAR(255) NULL,
    created_at  DATETIME      NOT NULL CONSTRAINT DF_roles_created_at DEFAULT (GETDATE()),
    updated_at  DATETIME      NOT NULL CONSTRAINT DF_roles_updated_at DEFAULT (GETDATE()),

    CONSTRAINT PK_roles      PRIMARY KEY (roles_id),
    CONSTRAINT UQ_roles_name UNIQUE (name),
    CONSTRAINT CK_roles_name CHECK (name IN (N'ADMIN', N'LECTURER', N'STUDENT'))
);
GO

INSERT INTO dbo.roles (roles_id, name, description)
SELECT v.roles_id, v.name, v.description
FROM (VALUES
    (N'AD', N'ADMIN',    N'Quản trị viên'),
    (N'LE', N'LECTURER', N'Giảng viên'),
    (N'ST', N'STUDENT',  N'Sinh viên')
) AS v (roles_id, name, description)
WHERE NOT EXISTS (SELECT 1 FROM dbo.roles r WHERE r.name = v.name);
GO

/* ---------- 2. Them users.roles_id (tam thoi cho NULL de dien du lieu) ---------- */
IF COL_LENGTH(N'dbo.users', N'roles_id') IS NULL
    ALTER TABLE dbo.users ADD roles_id NVARCHAR(20) NULL;
GO

/* ---------- 3. Dien roles_id tu cot role cu ---------- */
-- Dung EXEC vi lan chay lai cot role khong con, cau lenh nhac toi no se khong bien dich duoc.
IF COL_LENGTH(N'dbo.users', N'role') IS NOT NULL
    EXEC (N'UPDATE u SET u.roles_id = r.roles_id
            FROM dbo.users u
            JOIN dbo.roles r ON r.name = u.role
            WHERE u.roles_id IS NULL;');
GO

-- Con dong nao chua co role (vd backend ban moi da tu them cot roles_id) thi suy ra tu 2 chu cai dau cua users_id
UPDATE u SET u.roles_id = r.roles_id
FROM dbo.users u
JOIN dbo.roles r ON r.roles_id = UPPER(LEFT(u.users_id, 2))
WHERE u.roles_id IS NULL;
GO

IF EXISTS (SELECT 1 FROM dbo.users WHERE roles_id IS NULL)
BEGIN
    SELECT users_id, email FROM dbo.users WHERE roles_id IS NULL;
    RAISERROR(N'Con user chua xac dinh duoc role (xem danh sach o tren). Tu UPDATE users.roles_id cho cac dong nay roi chay lai script.', 16, 1);
    SET NOEXEC ON;
END
GO

/* ---------- 4. roles_id bat buoc + khoa ngoai ---------- */
-- Backend ban moi co the da tu tao khoa ngoai / index tren cot nay (ddl-auto: update): go ra truoc khi doi kieu cot.
DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql += N'ALTER TABLE dbo.users DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';'
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns c ON c.object_id = fkc.parent_object_id AND c.column_id = fkc.parent_column_id
WHERE fk.parent_object_id = OBJECT_ID(N'dbo.users') AND c.name = N'roles_id';
SELECT @sql += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON dbo.users;'
FROM sys.indexes i
JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE i.object_id = OBJECT_ID(N'dbo.users') AND c.name = N'roles_id'
  AND i.is_primary_key = 0 AND i.is_unique_constraint = 0;
EXEC sp_executesql @sql;
GO

ALTER TABLE dbo.users ALTER COLUMN roles_id NVARCHAR(20) NOT NULL;
GO

ALTER TABLE dbo.users ADD CONSTRAINT FK_users_role FOREIGN KEY (roles_id) REFERENCES dbo.roles (roles_id);
GO

CREATE INDEX IX_users_role ON dbo.users (roles_id);
GO

/* ---------- 5. Bo cot role cu ---------- */
IF COL_LENGTH(N'dbo.users', N'role') IS NOT NULL
BEGIN
    DECLARE @sql NVARCHAR(MAX) = N'';

    -- CHECK constraint nhac toi cot role (CK_users_role, hoac ten do Hibernate tu dat)
    SELECT @sql += N'ALTER TABLE dbo.users DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(N'dbo.users') AND cc.definition LIKE N'%[[]role]%';

    -- DEFAULT gan vao cot (neu co)
    SELECT @sql += N'ALTER TABLE dbo.users DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
    FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID(N'dbo.users') AND c.name = N'role';

    -- Index tren cot (neu co)
    SELECT @sql += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON dbo.users;'
    FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID(N'dbo.users') AND c.name = N'role'
      AND i.is_primary_key = 0 AND i.is_unique_constraint = 0;

    SET @sql += N'ALTER TABLE dbo.users DROP COLUMN role;';
    EXEC sp_executesql @sql;
END
GO

/* ---------- 6. users.must_change_password ---------- */
-- Tai khoan dang co deu da tu dat mat khau nen mac dinh = 0
IF COL_LENGTH(N'dbo.users', N'must_change_password') IS NULL
    ALTER TABLE dbo.users ADD must_change_password BIT NOT NULL
        CONSTRAINT DF_users_must_change_password DEFAULT (0);
GO

/* ---------- 7. Kiem tra ket qua ---------- */
SELECT roles_id, name, description FROM dbo.roles ORDER BY roles_id;

SELECT u.users_id, u.email, u.full_name, u.roles_id, r.name AS role_name, u.is_active, u.must_change_password
FROM dbo.users u
JOIN dbo.roles r ON r.roles_id = u.roles_id
ORDER BY r.name, u.users_id;
GO

SET NOEXEC OFF;
GO
