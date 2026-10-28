-- =====================================================================
-- Tạo tài khoản và database cho máy dev. Chạy MỘT LẦN bằng tài khoản postgres:
--
--   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U postgres -f "D:\kien nest\backend\db\setup-local.sql"
--
-- psql sẽ hỏi mật khẩu của tài khoản postgres (đặt lúc cài PostgreSQL).
-- Chạy lại nhiều lần không sao: script bỏ qua những gì đã tồn tại.
-- Bảng KHÔNG tạo ở đây — Flyway tự tạo khi ứng dụng khởi động.
-- Mật khẩu 'cowork' chỉ dùng cho máy dev, khớp với application.yml.
-- =====================================================================

SELECT 'CREATE ROLE cowork WITH LOGIN PASSWORD ''cowork'''
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cowork')
\gexec

SELECT 'CREATE DATABASE cowork OWNER cowork'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cowork')
\gexec

\echo 'Xong: database cowork, user cowork / mat khau cowork'
