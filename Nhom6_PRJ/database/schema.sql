-- ========================================================
-- DATABASE SCHEMA: AITA - GIT ANALYTICS MODULE (NHÓM 5)
-- Hệ quản trị: Microsoft SQL Server (SSMS)
-- ========================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'AITA_DB')
BEGIN
    CREATE DATABASE AITA_DB;
END
GO

USE AITA_DB;
GO

-- 1. Bảng Users: Chứa thông tin Sinh viên, Giảng viên
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Users')
BEGIN
    CREATE TABLE Users (
        user_id INT IDENTITY(1,1) PRIMARY KEY,
        username VARCHAR(50) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        full_name NVARCHAR(100) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        github_username VARCHAR(50) NULL, -- THÊM: Map tài khoản Git với hệ thống
        role VARCHAR(20) NOT NULL DEFAULT 'STUDENT'
            CHECK (role IN ('STUDENT', 'LECTURER')),
        created_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- 2. Bảng Project_Groups: Các nhóm sinh viên
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Project_Groups')
BEGIN
    CREATE TABLE Project_Groups (
        group_id INT IDENTITY(1,1) PRIMARY KEY,
        group_name NVARCHAR(100) NOT NULL,
        project_topic NVARCHAR(255),
        created_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- 3. Bảng Group_Members: Danh sách thành viên từng nhóm
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Group_Members')
BEGIN
    CREATE TABLE Group_Members (
        group_id INT,
        user_id INT,
        role_in_group VARCHAR(50) DEFAULT 'Member', -- Leader, Member
        PRIMARY KEY (group_id, user_id),
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
    );
END
GO

-- 4. Bảng Git_Repositories: Liên kết Nhóm với Repo Git
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Git_Repositories')
BEGIN
    CREATE TABLE Git_Repositories (
        repo_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        repo_url VARCHAR(255) NOT NULL,
        provider VARCHAR(20) NOT NULL DEFAULT 'GITHUB'
            CHECK (provider IN ('GITHUB', 'GITLAB')),
        access_token VARCHAR(255) NULL,
        last_synced DATETIME NULL,
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE
    );
END
GO

-- 5. Bảng Commits: Lịch sử commit cào về từ Git
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Commits')
BEGIN
    CREATE TABLE Commits (
        commit_id VARCHAR(64) PRIMARY KEY, -- Hash commit (SHA-1 hoặc SHA-256)
        repo_id INT NOT NULL,
        user_id INT NULL,                  -- THÊM: Liên kết trực tiếp tới User sau khi map email
        author_email VARCHAR(100) NOT NULL,
        author_name NVARCHAR(100),
        message NVARCHAR(MAX),
        lines_added INT DEFAULT 0,
        lines_deleted INT DEFAULT 0,
        commit_date DATETIME NOT NULL,
        FOREIGN KEY (repo_id) REFERENCES Git_Repositories(repo_id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE SET NULL
    );

    -- Tối ưu Index cho truy vấn phân tích
    CREATE INDEX IX_Commits_Repo_Date ON Commits(repo_id, commit_date);
    CREATE INDEX IX_Commits_User ON Commits(user_id);
END
GO

-- 6. Bảng Contribution_Settings (THÊM): Cấu hình trọng số tính điểm
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Contribution_Settings')
BEGIN
    CREATE TABLE Contribution_Settings (
        setting_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT UNIQUE NOT NULL,
        commit_weight FLOAT DEFAULT 0.4,       -- Trọng số số lượng commit
        loc_weight FLOAT DEFAULT 0.4,          -- Trọng số khối lượng code
        regularity_weight FLOAT DEFAULT 0.2,   -- Trọng số độ đều đặn
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE
    );
END
GO

-- 7. Bảng Contribution_Scores: Lưu điểm đánh giá định kỳ
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Contribution_Scores')
BEGIN
    CREATE TABLE Contribution_Scores (
        score_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        user_id INT NOT NULL,
        evaluated_period VARCHAR(50) NOT NULL, -- VD: "Week 1", "Sprint 1"
        total_commits INT DEFAULT 0,
        total_loc INT DEFAULT 0,               -- Lines of Code (added - deleted)
        regularity_score FLOAT DEFAULT 0.0,    -- Điểm đều đặn
        final_contribution_percentage FLOAT DEFAULT 0.0, -- Tỷ lệ % đóng góp
        system_proposed_score FLOAT DEFAULT 0.0,        -- Hệ số điểm đề xuất (0.0 - 10.0)
        lecturer_adjusted_score FLOAT NULL,             -- Giảng viên điều chỉnh
        created_at DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id),
        FOREIGN KEY (user_id) REFERENCES Users(user_id),
        -- Ràng buộc chống ghi trùng lặp một kỳ đánh giá
        CONSTRAINT UQ_Group_User_Period UNIQUE (group_id, user_id, evaluated_period)
    );
END
GO