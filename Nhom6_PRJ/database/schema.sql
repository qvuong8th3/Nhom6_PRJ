-- Khởi tạo Database cho AITA - Git Analytics (Nhóm 6)
-- Phiên bản dành cho SQL Server (SSMS)

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'AITA_DB')
BEGIN
    CREATE DATABASE AITA_DB;
END
GO

USE AITA_DB;
GO

-- Bảng Users: Chứa thông tin Sinh viên, Giảng viên
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Users')
BEGIN
    CREATE TABLE Users (
        user_id INT IDENTITY(1,1) PRIMARY KEY,
        username VARCHAR(50) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        full_name VARCHAR(100) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        role VARCHAR(20) NOT NULL DEFAULT 'STUDENT'
            CHECK (role IN ('STUDENT', 'LECTURER')),
        created_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- Bảng Groups: Các nhóm sinh viên
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Project_Groups')
BEGIN
    CREATE TABLE Project_Groups (
        group_id INT IDENTITY(1,1) PRIMARY KEY,
        group_name VARCHAR(100) NOT NULL,
        project_topic VARCHAR(255),
        created_at DATETIME DEFAULT GETDATE()
    );
END
GO

-- Bảng trung gian Group_Members
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Group_Members')
BEGIN
    CREATE TABLE Group_Members (
        group_id INT,
        user_id INT,
        role_in_group VARCHAR(50), -- VD: Leader, Developer
        PRIMARY KEY (group_id, user_id),
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
    );
END
GO

-- Bảng Git_Repositories: Liên kết Nhóm với Repo Git
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Git_Repositories')
BEGIN
    CREATE TABLE Git_Repositories (
        repo_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        repo_url VARCHAR(255) NOT NULL,
        provider VARCHAR(20) NOT NULL DEFAULT 'GITHUB'
            CHECK (provider IN ('GITHUB', 'GITLAB')),
        access_token VARCHAR(255), -- Mã token để truy cập private repo
        last_synced DATETIME NULL,
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE
    );
END
GO

-- Bảng Commits: Lịch sử commit cào về từ Git
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Commits')
BEGIN
    CREATE TABLE Commits (
        commit_id VARCHAR(50) PRIMARY KEY, -- Hash của commit
        repo_id INT NOT NULL,
        author_email VARCHAR(100) NOT NULL,
        author_name VARCHAR(100),
        message VARCHAR(MAX),
        lines_added INT DEFAULT 0,
        lines_deleted INT DEFAULT 0,
        commit_date DATETIME NOT NULL,
        FOREIGN KEY (repo_id) REFERENCES Git_Repositories(repo_id) ON DELETE CASCADE
    );
END
GO

-- Bảng Contribution_Scores: Lưu điểm đánh giá định kỳ
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Contribution_Scores')
BEGIN
    CREATE TABLE Contribution_Scores (
        score_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        user_id INT NOT NULL,
        evaluated_period VARCHAR(50), -- Ví dụ: "Week 1", "Sprint 1"
        total_commits INT DEFAULT 0,
        total_loc INT DEFAULT 0, -- Lines of Code (added - deleted)
        regularity_score FLOAT DEFAULT 0.0, -- Điểm đều đặn
        final_contribution_percentage FLOAT DEFAULT 0.0, -- Phần trăm đóng góp tổng
        system_proposed_score FLOAT DEFAULT 0.0, -- Điểm hệ thống đề xuất (hệ số)
        lecturer_adjusted_score FLOAT NULL, -- Giảng viên có quyền sửa đổi
        created_at DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id),
        FOREIGN KEY (user_id) REFERENCES Users(user_id)
    );
END
GO