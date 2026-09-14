-- Khởi tạo Database cho AITA - Git Analytics (Nhóm 6)
CREATE DATABASE IF NOT EXISTS AITA_DB;
USE AITA_DB;

-- Bảng Users: Chứa thông tin Sinh viên, Giảng viên
CREATE TABLE IF NOT EXISTS Users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role ENUM('STUDENT', 'LECTURER') DEFAULT 'STUDENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bảng Groups: Các nhóm sinh viên
CREATE TABLE IF NOT EXISTS Project_Groups (
    group_id INT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    project_topic VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bảng trung gian Group_Members
CREATE TABLE IF NOT EXISTS Group_Members (
    group_id INT,
    user_id INT,
    role_in_group VARCHAR(50), -- VD: Leader, Developer
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);

-- Bảng Git_Repositories: Liên kết Nhóm với Repo Git
CREATE TABLE IF NOT EXISTS Git_Repositories (
    repo_id INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NOT NULL,
    repo_url VARCHAR(255) NOT NULL,
    provider ENUM('GITHUB', 'GITLAB') DEFAULT 'GITHUB',
    access_token VARCHAR(255), -- Mã token để truy cập private repo
    last_synced TIMESTAMP NULL,
    FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id) ON DELETE CASCADE
);

-- Bảng Commits: Lịch sử commit cào về từ Git
CREATE TABLE IF NOT EXISTS Commits (
    commit_id VARCHAR(50) PRIMARY KEY, -- Hash của commit
    repo_id INT NOT NULL,
    author_email VARCHAR(100) NOT NULL,
    author_name VARCHAR(100),
    message TEXT,
    lines_added INT DEFAULT 0,
    lines_deleted INT DEFAULT 0,
    commit_date TIMESTAMP NOT NULL,
    FOREIGN KEY (repo_id) REFERENCES Git_Repositories(repo_id) ON DELETE CASCADE
);

-- Bảng Contribution_Scores: Lưu điểm đánh giá định kỳ
CREATE TABLE IF NOT EXISTS Contribution_Scores (
    score_id INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NOT NULL,
    user_id INT NOT NULL,
    evaluated_period VARCHAR(50), -- Ví dụ: "Week 1", "Sprint 1"
    total_commits INT DEFAULT 0,
    total_loc INT DEFAULT 0, -- Lines of Code (added - deleted)
    regularity_score FLOAT DEFAULT 0.0, -- Điểm đều đặn
    final_contribution_percentage FLOAT DEFAULT 0.0, -- Phần trăm đóng góp tổng
    system_proposed_score FLOAT DEFAULT 0.0, -- Điểm hệ thống đề xuất (hệ số)
    lecturer_adjusted_score FLOAT NULL, -- Giảng viên có quyền sửa đổi
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES Project_Groups(group_id),
    FOREIGN KEY (user_id) REFERENCES Users(user_id)
);
