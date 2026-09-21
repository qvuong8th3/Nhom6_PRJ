-- =========================================================
-- DATABASE: AITA_DB - GIT ANALYTICS MODULE - GROUP 6
-- Hệ quản trị: Microsoft SQL Server
-- =========================================================

-- =========================================================
-- 1. TẠO DATABASE
-- =========================================================

IF NOT EXISTS (
    SELECT name
    FROM sys.databases
    WHERE name = 'AITA_DB'
)
BEGIN
    CREATE DATABASE AITA_DB;
END
GO

USE AITA_DB;
GO


-- =========================================================
-- 2. USERS
-- Lưu thông tin người dùng:
-- Sinh viên và Giảng viên
-- =========================================================

IF OBJECT_ID('Users', 'U') IS NULL
BEGIN
    CREATE TABLE Users (

        -- Khóa chính của người dùng
        user_id INT IDENTITY(1,1) PRIMARY KEY,

        -- Tên đăng nhập
        username VARCHAR(50) NOT NULL UNIQUE,

        -- Mật khẩu đã mã hóa
        password_hash VARCHAR(255) NOT NULL,

        -- Họ và tên
        full_name NVARCHAR(100) NOT NULL,

        -- Email người dùng
        email VARCHAR(100) NOT NULL UNIQUE,

        -- Tên tài khoản GitHub
        -- Dùng để liên kết sinh viên với Git
        github_username VARCHAR(50) NULL,

        -- Vai trò:
        -- STUDENT = Sinh viên
        -- LECTURER = Giảng viên
        role VARCHAR(20) NOT NULL
            DEFAULT 'STUDENT',

        -- Thời gian tạo tài khoản
        created_at DATETIME
            DEFAULT GETDATE(),

        -- Chỉ cho phép 2 loại tài khoản
        CONSTRAINT CK_Users_Role
            CHECK (
                role IN ('STUDENT', 'LECTURER')
            )
    );
END
GO


-- =========================================================
-- 3. PROJECT_GROUPS
-- Lưu thông tin các nhóm sinh viên
-- Ví dụ: Nhóm 1, Nhóm 2, Nhóm 6...
-- =========================================================

IF OBJECT_ID('Project_Groups', 'U') IS NULL
BEGIN
    CREATE TABLE Project_Groups (

        -- Khóa chính của nhóm
        group_id INT IDENTITY(1,1)
            PRIMARY KEY,

        -- Tên nhóm
        group_name NVARCHAR(100)
            NOT NULL,

        -- Tên/chủ đề project
        project_topic NVARCHAR(255)
            NULL,

        -- Ngày tạo nhóm
        created_at DATETIME
            DEFAULT GETDATE()
    );
END
GO


-- =========================================================
-- 4. GROUP_MEMBERS
-- Bảng trung gian lưu sinh viên thuộc nhóm nào
--
-- Quan hệ:
-- Users <--> Project_Groups
-- thông qua Group_Members
-- =========================================================

IF OBJECT_ID('Group_Members', 'U') IS NULL
BEGIN
    CREATE TABLE Group_Members (

        -- ID nhóm
        -- Đồng thời là Foreign Key
        group_id INT NOT NULL,

        -- ID sinh viên
        -- Đồng thời là Foreign Key
        user_id INT NOT NULL,

        -- Vai trò trong nhóm
        -- Leader = Trưởng nhóm
        -- Member = Thành viên
        role_in_group VARCHAR(50)
            DEFAULT 'Member',

        -- Khóa chính gồm 2 cột
        -- Một sinh viên chỉ xuất hiện 1 lần trong 1 nhóm
        CONSTRAINT PK_Group_Members
            PRIMARY KEY (
                group_id,
                user_id
            ),

        -- Liên kết với Project_Groups
        CONSTRAINT FK_GroupMembers_Group
            FOREIGN KEY (group_id)
            REFERENCES Project_Groups(group_id)
            ON DELETE CASCADE,

        -- Liên kết với Users
        CONSTRAINT FK_GroupMembers_User
            FOREIGN KEY (user_id)
            REFERENCES Users(user_id)
            ON DELETE CASCADE
    );
END
GO


-- =========================================================
-- 5. GIT_REPOSITORIES
-- Lưu thông tin Repository Git của từng nhóm
--
-- Ví dụ:
-- Nhóm 6 -> GitHub Repository của project
-- =========================================================

IF OBJECT_ID('Git_Repositories', 'U') IS NULL
BEGIN
    CREATE TABLE Git_Repositories (

        -- ID Repository
        repo_id INT IDENTITY(1,1)
            PRIMARY KEY,

        -- Repository thuộc nhóm nào
        group_id INT NOT NULL,

        -- URL GitHub/GitLab
        repo_url VARCHAR(255)
            NOT NULL,

        -- Nhà cung cấp Git
        -- GITHUB hoặc GITLAB
        provider VARCHAR(20)
            NOT NULL
            DEFAULT 'GITHUB',

        -- Token truy cập Repository private
        -- Lưu ý: dữ liệu thật phải được bảo mật
        access_token VARCHAR(255)
            NULL,

        -- Thời điểm cuối cùng hệ thống đồng bộ Git
        last_synced DATETIME
            NULL,

        -- Chỉ cho phép GitHub hoặc GitLab
        CONSTRAINT CK_GitRepositories_Provider
            CHECK (
                provider IN ('GITHUB', 'GITLAB')
            ),

        -- Liên kết Repository với nhóm
        CONSTRAINT FK_GitRepositories_Group
            FOREIGN KEY (group_id)
            REFERENCES Project_Groups(group_id)
            ON DELETE CASCADE
    );
END
GO


-- =========================================================
-- 6. COMMITS
-- Lưu lịch sử Commit được lấy từ GitHub/GitLab
--
-- Đây là bảng QUAN TRỌNG cho Git Analytics
-- =========================================================

IF OBJECT_ID('Commits', 'U') IS NULL
BEGIN
    CREATE TABLE Commits (

        -- Hash duy nhất của Commit
        -- Có thể là SHA-1 hoặc SHA-256
        commit_id VARCHAR(64)
            PRIMARY KEY,

        -- Commit thuộc Repository nào
        repo_id INT NOT NULL,

        -- Commit do User nào thực hiện
        -- Có thể NULL nếu hệ thống chưa map được
        user_id INT NULL,

        -- Email của người tạo Commit
        author_email VARCHAR(100)
            NOT NULL,

        -- Tên tác giả Commit
        author_name NVARCHAR(100)
            NULL,

        -- Nội dung Commit Message
        message NVARCHAR(MAX)
            NULL,

        -- Số dòng code được thêm
        lines_added INT
            DEFAULT 0,

        -- Số dòng code bị xóa
        lines_deleted INT
            DEFAULT 0,

        -- Thời gian Commit
        commit_date DATETIME
            NOT NULL,

        -- Liên kết Commit với Repository
        CONSTRAINT FK_Commits_Repository
            FOREIGN KEY (repo_id)
            REFERENCES Git_Repositories(repo_id)
            ON DELETE CASCADE,

        -- Liên kết Commit với User
        CONSTRAINT FK_Commits_User
            FOREIGN KEY (user_id)
            REFERENCES Users(user_id)
            ON DELETE SET NULL
    );


    -- Index giúp tìm Commit theo Repository
    -- và theo thời gian nhanh hơn
    CREATE INDEX IX_Commits_Repo_Date
        ON Commits(
            repo_id,
            commit_date
        );


    -- Index giúp tìm Commit theo sinh viên
    CREATE INDEX IX_Commits_User
        ON Commits(user_id);

END
GO


-- =========================================================
-- 7. CONTRIBUTION_SETTINGS
-- Lưu cấu hình trọng số tính điểm đóng góp
--
-- Ví dụ:
-- Commit       = 40%
-- LOC          = 40%
-- Regularity   = 20%
-- =========================================================

IF OBJECT_ID('Contribution_Settings', 'U') IS NULL
BEGIN
    CREATE TABLE Contribution_Settings (

        -- ID cấu hình
        setting_id INT IDENTITY(1,1)
            PRIMARY KEY,

        -- Mỗi nhóm có một bộ cấu hình
        group_id INT NOT NULL UNIQUE,

        -- Trọng số số lượng Commit
        commit_weight FLOAT
            DEFAULT 0.4,

        -- Trọng số số dòng code
        loc_weight FLOAT
            DEFAULT 0.4,

        -- Trọng số độ đều đặn
        regularity_weight FLOAT
            DEFAULT 0.2,

        -- Liên kết cấu hình với nhóm
        CONSTRAINT FK_ContributionSettings_Group
            FOREIGN KEY (group_id)
            REFERENCES Project_Groups(group_id)
            ON DELETE CASCADE
    );
END
GO


-- =========================================================
-- 8. CONTRIBUTION_SCORES
-- Lưu kết quả đánh giá mức độ đóng góp của sinh viên
--
-- Đây là bảng dùng để tạo Dashboard cho Giảng viên
-- =========================================================

IF OBJECT_ID('Contribution_Scores', 'U') IS NULL
BEGIN
    CREATE TABLE Contribution_Scores (

        -- ID kết quả đánh giá
        score_id INT IDENTITY(1,1)
            PRIMARY KEY,

        -- Nhóm được đánh giá
        group_id INT NOT NULL,

        -- Sinh viên được đánh giá
        user_id INT NOT NULL,

        -- Khoảng thời gian đánh giá
        -- Ví dụ: Week 1, Week 2, Sprint 1...
        evaluated_period VARCHAR(50)
            NOT NULL,

        -- Tổng số Commit
        total_commits INT
            DEFAULT 0,

        -- Tổng số dòng code
        total_loc INT
            DEFAULT 0,

        -- Điểm độ đều đặn
        regularity_score FLOAT
            DEFAULT 0.0,

        -- Phần trăm đóng góp cuối cùng
        -- Ví dụ: 35.5%
        final_contribution_percentage FLOAT
            DEFAULT 0.0,

        -- Điểm hệ thống đề xuất
        -- Ví dụ: 8.5 / 10
        system_proposed_score FLOAT
            DEFAULT 0.0,

        -- Điểm do Giảng viên điều chỉnh
        -- Có thể NULL nếu giảng viên chưa chỉnh
        lecturer_adjusted_score FLOAT
            NULL,

        -- Thời gian tạo kết quả đánh giá
        created_at DATETIME
            DEFAULT GETDATE(),

        -- Liên kết với nhóm
        CONSTRAINT FK_ContributionScores_Group
            FOREIGN KEY (group_id)
            REFERENCES Project_Groups(group_id),

        -- Liên kết với sinh viên
        CONSTRAINT FK_ContributionScores_User
            FOREIGN KEY (user_id)
            REFERENCES Users(user_id),

        -- Không cho phép trùng:
        -- Một sinh viên + một nhóm + một kỳ đánh giá
        CONSTRAINT UQ_Group_User_Period
            UNIQUE (
                group_id,
                user_id,
                evaluated_period
            )
    );
END
GO