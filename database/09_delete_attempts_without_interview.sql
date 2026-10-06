-- =====================================================================
-- AIVES - Xoa cac luot thi "rac" (database DA TON TAI). Chay tren database: aives.
--
-- Luot thi "rac" o day la luot thi KHONG co luot hoi - dap nao va KHONG co diem nao:
--   - luot thi tao tu truoc khi co phong van AI (vao phong la xong, khong co cau hoi)
--   - sinh vien vao phong roi thoat, khong bam bat dau
-- Tu ban nay web da tu an cac luot thi do khoi trang "Ket qua" cua sinh vien, nen file nay
-- KHONG bat buoc phai chay. Chi chay khi muon don han du lieu trong database.
--
-- Chay lai nhieu lan khong sao. XOA LA MAT HAN: xem ket qua BUOC 1 truoc khi chay BUOC 2.
-- =====================================================================
USE aives;
GO

/* ---------- BUOC 1: XEM TRUOC cac luot thi se bi xoa (chi doc, khong sua gi) ---------- */
SELECT a.viva_attempts_id, e.title AS phien_thi, u.full_name AS sinh_vien,
       a.status, a.result_status, a.started_at, a.completed_at
FROM dbo.viva_attempts a
JOIN dbo.viva_exams e ON e.viva_exams_id = a.viva_exams_id
JOIN dbo.users u ON u.users_id = a.student_id
WHERE a.status <> N'IN_PROGRESS'
  AND NOT EXISTS (SELECT 1 FROM dbo.interview_exchanges x WHERE x.viva_attempts_id = a.viva_attempts_id)
  AND NOT EXISTS (SELECT 1 FROM dbo.question_grades g WHERE g.viva_attempts_id = a.viva_attempts_id)
ORDER BY a.started_at;
GO

/* ---------- BUOC 2: XOA cac luot thi o buoc 1 ---------- */
-- Khong dung toi luot thi dang thi (IN_PROGRESS), luot thi da co hoi - dap hoac da co diem.
DELETE a
FROM dbo.viva_attempts a
WHERE a.status <> N'IN_PROGRESS'
  AND NOT EXISTS (SELECT 1 FROM dbo.interview_exchanges x WHERE x.viva_attempts_id = a.viva_attempts_id)
  AND NOT EXISTS (SELECT 1 FROM dbo.question_grades g WHERE g.viva_attempts_id = a.viva_attempts_id);

PRINT CONCAT(N'Da xoa ', @@ROWCOUNT, N' luot thi khong co hoi - dap.');
GO

/* ---------- BUOC 3 (TUY CHON): xoa HAN 1 luot thi da co hoi - dap / da co diem ---------- */
-- Dung khi muon bo 1 luot thi thu (vd luot thi bi loi luc AI chua chay duoc).
-- Cach dung: lay viva_attempts_id bang cau SELECT ben duoi, dien vao @attemptId, bo dau "--" o dau cac dong roi chay.
--
-- SELECT a.viva_attempts_id, e.title, u.full_name, a.result_status, a.completed_at
-- FROM dbo.viva_attempts a
-- JOIN dbo.viva_exams e ON e.viva_exams_id = a.viva_exams_id
-- JOIN dbo.users u ON u.users_id = a.student_id
-- ORDER BY a.started_at DESC;
--
-- DECLARE @attemptId NVARCHAR(20) = N'DIEN_MA_LUOT_THI_VAO_DAY';
-- BEGIN TRANSACTION;
--     DELETE FROM dbo.grade_appeals   WHERE question_grades_id IN (SELECT question_grades_id FROM dbo.question_grades WHERE viva_attempts_id = @attemptId);
--     DELETE FROM dbo.criteria_grades WHERE question_grades_id IN (SELECT question_grades_id FROM dbo.question_grades WHERE viva_attempts_id = @attemptId);
--     DELETE FROM dbo.question_grades WHERE viva_attempts_id = @attemptId;
--     -- Cau hoi xoay tro toi cau hoi goc (parent_exchange_id) trong CUNG luot thi: xoa trong 1 lenh la duoc
--     DELETE FROM dbo.interview_exchanges WHERE viva_attempts_id = @attemptId;
--     DELETE FROM dbo.viva_attempts WHERE viva_attempts_id = @attemptId;
-- COMMIT TRANSACTION;
