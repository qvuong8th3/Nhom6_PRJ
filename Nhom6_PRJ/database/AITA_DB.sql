-- ============================================================
-- AITA - AI-POWERED TEACHING ASSISTANT SYSTEM (PRJ301 / PRJ30X)
-- TẬP LỆNH DATABASE TOÀN DIỆN VÀ THỐNG NHẤT (UNIFIED AITA_DB)
-- 1 Admin + 5 Giảng viên + 25 Sinh viên + 5 Môn học:
-- PRJ301, SWR302, JPD123, CSD201, IOT102
-- Mật khẩu mặc định toàn hệ thống: 123456
-- ============================================================

IF DB_ID('AITA_DB') IS NULL
BEGIN
    CREATE DATABASE AITA_DB;
END
GO

USE AITA_DB;
GO

-- 1. XÓA BẢNG VÀ VIEW CŨ
IF OBJECT_ID('Tasks', 'V') IS NOT NULL DROP VIEW Tasks;
IF OBJECT_ID('TaskSubmissions', 'V') IS NOT NULL DROP VIEW TaskSubmissions;
IF OBJECT_ID('projects', 'V') IS NOT NULL DROP VIEW projects;
IF OBJECT_ID('git_repositories', 'V') IS NOT NULL DROP VIEW git_repositories;
IF OBJECT_ID('commit_logs', 'V') IS NOT NULL DROP VIEW commit_logs;
IF OBJECT_ID('Group_Members', 'V') IS NOT NULL DROP VIEW Group_Members;

IF OBJECT_ID('ExamAnswers', 'U') IS NOT NULL DROP TABLE ExamAnswers;
IF OBJECT_ID('ExamSubmissions', 'U') IS NOT NULL DROP TABLE ExamSubmissions;
IF OBJECT_ID('ExamQuestions', 'U') IS NOT NULL DROP TABLE ExamQuestions;
IF OBJECT_ID('Exams', 'U') IS NOT NULL DROP TABLE Exams;

IF OBJECT_ID('Essay_Submissions', 'U') IS NOT NULL DROP TABLE Essay_Submissions;
IF OBJECT_ID('Essay_Assignments', 'U') IS NOT NULL DROP TABLE Essay_Assignments;
IF OBJECT_ID('Class_Students', 'U') IS NOT NULL DROP TABLE Class_Students;
IF OBJECT_ID('Course_Classes', 'U') IS NOT NULL DROP TABLE Course_Classes;
IF OBJECT_ID('Lecturer_Courses', 'U') IS NOT NULL DROP TABLE Lecturer_Courses;

IF OBJECT_ID('CorrectionPointsTransactions', 'U') IS NOT NULL DROP TABLE CorrectionPointsTransactions;
IF OBJECT_ID('CorrectionPointsWallet', 'U') IS NOT NULL DROP TABLE CorrectionPointsWallet;
IF OBJECT_ID('CircularReviews', 'U') IS NOT NULL DROP TABLE CircularReviews;
IF OBJECT_ID('PedagogicalFeedbacks', 'U') IS NOT NULL DROP TABLE PedagogicalFeedbacks;
IF OBJECT_ID('Code_Reviews', 'U') IS NOT NULL DROP TABLE Code_Reviews;
IF OBJECT_ID('Pull_Requests', 'U') IS NOT NULL DROP TABLE Pull_Requests;
IF OBJECT_ID('contribution_settings', 'U') IS NOT NULL DROP TABLE contribution_settings;
IF OBJECT_ID('ContributionScores', 'U') IS NOT NULL DROP TABLE ContributionScores;
IF OBJECT_ID('GitCommits', 'U') IS NOT NULL DROP TABLE GitCommits;
IF OBJECT_ID('Repositories', 'U') IS NOT NULL DROP TABLE Repositories;
IF OBJECT_ID('GroupMembers', 'U') IS NOT NULL DROP TABLE GroupMembers;
IF OBJECT_ID('StudentGroups', 'U') IS NOT NULL DROP TABLE StudentGroups;

IF OBJECT_ID('CourseEnrollments', 'U') IS NOT NULL DROP TABLE CourseEnrollments;
IF OBJECT_ID('Courses', 'U') IS NOT NULL DROP TABLE Courses;
IF OBJECT_ID('RememberMeTokens', 'U') IS NOT NULL DROP TABLE RememberMeTokens;
IF OBJECT_ID('Users', 'U') IS NOT NULL DROP TABLE Users;
GO

-- 2. TẠO CÁC BẢNG CỐT LÕI
CREATE TABLE Users (
    user_id              INT IDENTITY(1,1) PRIMARY KEY,
    student_code         VARCHAR(20) NULL UNIQUE,
    username             VARCHAR(50) NOT NULL UNIQUE,
    full_name            NVARCHAR(100) NOT NULL,
    email                VARCHAR(100) NOT NULL UNIQUE,
    github_username      VARCHAR(60) NULL,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'LECTURER', 'ADMIN')),
    is_admin             BIT NOT NULL DEFAULT 0,
    must_change_password BIT NOT NULL DEFAULT 0,
    created_at           DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
GO

CREATE TABLE Courses (
    course_id      INT IDENTITY(1,1) PRIMARY KEY,
    course_code    VARCHAR(30) NOT NULL UNIQUE,
    course_name    NVARCHAR(150) NOT NULL,
    semester       VARCHAR(50) NULL DEFAULT 'Fall 2026',
    created_by     INT NULL,
    created_at     DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Courses_CreatedBy FOREIGN KEY (created_by) REFERENCES Users(user_id)
);
GO

CREATE TABLE Lecturer_Courses (
    course_id   INT NOT NULL,
    lecturer_id INT NOT NULL,
    assigned_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    PRIMARY KEY (course_id, lecturer_id),
    CONSTRAINT FK_LC_Course FOREIGN KEY (course_id) REFERENCES Courses(course_id) ON DELETE CASCADE,
    CONSTRAINT FK_LC_Lecturer FOREIGN KEY (lecturer_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE Course_Classes (
    class_id   INT IDENTITY(1,1) PRIMARY KEY,
    course_id  INT NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Classes_Courses FOREIGN KEY (course_id) REFERENCES Courses(course_id) ON DELETE CASCADE,
    CONSTRAINT UQ_CourseClass UNIQUE (course_id, class_name)
);
GO

CREATE TABLE Class_Students (
    class_id    INT NOT NULL,
    student_id  INT NOT NULL,
    enrolled_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    PRIMARY KEY (class_id, student_id),
    CONSTRAINT FK_CS_Class FOREIGN KEY (class_id) REFERENCES Course_Classes(class_id) ON DELETE CASCADE,
    CONSTRAINT FK_CS_Student FOREIGN KEY (student_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE CourseEnrollments (
    enrollment_id INT IDENTITY(1,1) PRIMARY KEY,
    course_id     INT NOT NULL,
    student_id    INT NOT NULL,
    class_name    VARCHAR(50) NULL DEFAULT 'BIT_SE_K20C',
    enrolled_at   DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_CourseEnrollments_Courses FOREIGN KEY (course_id) REFERENCES Courses(course_id) ON DELETE CASCADE,
    CONSTRAINT FK_CourseEnrollments_Users FOREIGN KEY (student_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT UQ_CourseStudent UNIQUE (course_id, student_id)
);
GO

-- 3. BÀI TẬP TỰ LUẬN & SUBMISSION PORTAL
CREATE TABLE Essay_Assignments (
    assignment_id    INT IDENTITY(1,1) PRIMARY KEY,
    title            NVARCHAR(200) NOT NULL,
    description      NVARCHAR(MAX) NULL,
    created_by       INT NOT NULL,
    course_id        INT NULL,
    prompt_file_name NVARCHAR(255) NULL,
    prompt_file_data VARBINARY(MAX) NULL,
    due_at           DATETIME2 NULL,
    created_at       DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Essay_Users FOREIGN KEY (created_by) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT FK_Essay_Courses FOREIGN KEY (course_id) REFERENCES Courses(course_id) ON DELETE SET NULL
);
GO

CREATE TABLE Essay_Submissions (
    submission_id   INT IDENTITY(1,1) PRIMARY KEY,
    assignment_id   INT NOT NULL,
    student_id      INT NOT NULL,
    file_name       NVARCHAR(255) NOT NULL,
    file_data       VARBINARY(MAX) NOT NULL,
    sha256_hash     VARCHAR(64) NULL,
    submitted_at    DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    score           DECIMAL(5,2) NULL,
    feedback        NVARCHAR(1000) NULL,
    graded_by       INT NULL,
    graded_at       DATETIME2 NULL,
    CONSTRAINT FK_Submissions_Assignments FOREIGN KEY (assignment_id) REFERENCES Essay_Assignments(assignment_id) ON DELETE CASCADE,
    CONSTRAINT FK_Submissions_Student FOREIGN KEY (student_id) REFERENCES Users(user_id),
    CONSTRAINT FK_Submissions_Grader FOREIGN KEY (graded_by) REFERENCES Users(user_id),
    CONSTRAINT UQ_Assignment_Student UNIQUE (assignment_id, student_id)
);
GO

-- 4. MODULE FE-L-03: GIT ANALYTICS ASSESSOR
CREATE TABLE StudentGroups (
    group_id    INT IDENTITY(1,1) PRIMARY KEY,
    group_name  NVARCHAR(100) NOT NULL,
    course_code VARCHAR(30) NOT NULL,
    semester    VARCHAR(20) NOT NULL DEFAULT 'Fall 2026',
    lecturer_id INT NULL,
    created_at  DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_StudentGroups_Lecturer FOREIGN KEY (lecturer_id) REFERENCES Users(user_id)
);
GO

CREATE TABLE GroupMembers (
    group_member_id INT IDENTITY(1,1) PRIMARY KEY,
    group_id        INT NOT NULL,
    student_id      INT NOT NULL,
    role_in_group   VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at       DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_GroupMembers_Group FOREIGN KEY (group_id) REFERENCES StudentGroups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_GroupMembers_Student FOREIGN KEY (student_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT UQ_Group_Student UNIQUE (group_id, student_id)
);
GO

CREATE TABLE Repositories (
    repo_id        INT IDENTITY(1,1) PRIMARY KEY,
    group_id       INT NOT NULL UNIQUE,
    repo_name      VARCHAR(100) NOT NULL,
    repo_url       VARCHAR(255) NOT NULL,
    default_branch VARCHAR(40) NOT NULL DEFAULT 'main',
    last_synced_at DATETIME2 NULL,
    created_at     DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Repositories_Group FOREIGN KEY (group_id) REFERENCES StudentGroups(group_id) ON DELETE CASCADE
);
GO

CREATE TABLE GitCommits (
    commit_id        BIGINT IDENTITY(1,1) PRIMARY KEY,
    repo_id          INT NOT NULL,
    author_user_id   INT NULL,
    commit_hash      CHAR(40) NOT NULL,
    author_email     VARCHAR(100) NULL,
    commit_message   NVARCHAR(500) NOT NULL,
    commit_date      DATETIME2 NOT NULL,
    lines_added      INT NOT NULL DEFAULT 0 CHECK (lines_added >= 0),
    lines_deleted    INT NOT NULL DEFAULT 0 CHECK (lines_deleted >= 0),
    files_changed    INT NOT NULL DEFAULT 0 CHECK (files_changed >= 0),
    is_merge_commit  BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_GitCommits_Repo FOREIGN KEY (repo_id) REFERENCES Repositories(repo_id) ON DELETE CASCADE,
    CONSTRAINT FK_GitCommits_Author FOREIGN KEY (author_user_id) REFERENCES Users(user_id) ON DELETE SET NULL,
    CONSTRAINT UQ_Repo_CommitHash UNIQUE (repo_id, commit_hash)
);
GO

CREATE TABLE Pull_Requests (
    pr_id      INT IDENTITY(1,1) PRIMARY KEY,
    group_id   INT NOT NULL,
    user_id    INT NOT NULL,
    pr_number  INT NOT NULL,
    title      NVARCHAR(255) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    merged_at  DATETIME2 NULL,
    CONSTRAINT FK_PR_Group FOREIGN KEY (group_id) REFERENCES StudentGroups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_PR_User FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE Code_Reviews (
    review_id    INT IDENTITY(1,1) PRIMARY KEY,
    group_id     INT NOT NULL,
    reviewer_id  INT NOT NULL,
    pr_id        INT NULL,
    comments_num INT NOT NULL DEFAULT 1,
    reviewed_at  DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Review_Group FOREIGN KEY (group_id) REFERENCES StudentGroups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_Review_User FOREIGN KEY (reviewer_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE contribution_settings (
    setting_id        INT IDENTITY(1,1) PRIMARY KEY,
    project_id        INT NOT NULL DEFAULT 1,
    group_id          INT NOT NULL,
    commit_weight     DECIMAL(4,2) NOT NULL DEFAULT 0.20,
    loc_weight        DECIMAL(4,2) NOT NULL DEFAULT 0.50,
    regularity_weight DECIMAL(4,2) NOT NULL DEFAULT 0.40,
    review_weight     DECIMAL(4,2) NOT NULL DEFAULT 0.20,
    penalty_weight    DECIMAL(4,2) NOT NULL DEFAULT 0.10,
    updated_at        DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
GO

CREATE TABLE ContributionScores (
    score_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    group_id            INT NOT NULL,
    student_id          INT NOT NULL,
    total_commits       INT NOT NULL DEFAULT 0,
    total_lines_added   INT NOT NULL DEFAULT 0,
    total_lines_deleted INT NOT NULL DEFAULT 0,
    active_days         INT NOT NULL DEFAULT 0,
    score_percentage    DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    classification      VARCHAR(20) NOT NULL CHECK (classification IN ('HIGH', 'MEDIUM', 'LOW', 'FREERIDER')),
    calculated_at       DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_ContributionScores_Group FOREIGN KEY (group_id) REFERENCES StudentGroups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_ContributionScores_Student FOREIGN KEY (student_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT UQ_Group_Student_Score UNIQUE (group_id, student_id)
);
GO

-- 5. THỊ TRƯỜNG HỌC THUẬT: CIRCULAR PEER-REVIEW & CORRECTION POINTS
CREATE TABLE CircularReviews (
    review_id             INT IDENTITY(1,1) PRIMARY KEY,
    reviewer_group_id     INT NOT NULL,
    reviewee_group_id     INT NOT NULL,
    sandbox_score         DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (sandbox_score >= 0 AND sandbox_score <= 30),
    mvc2_clean_code_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (mvc2_clean_code_score >= 0 AND mvc2_clean_code_score <= 40),
    peer_defense_score    DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (peer_defense_score >= 0 AND peer_defense_score <= 30),
    total_score           DECIMAL(5,2) NOT NULL DEFAULT 0.00 CHECK (total_score >= 0 AND total_score <= 100),
    artifact_sha256       VARCHAR(64) NULL,
    feedback              NVARCHAR(MAX) NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED')),
    reviewed_at           DATETIME2 NULL,
    CONSTRAINT FK_CircularReviews_Reviewer FOREIGN KEY (reviewer_group_id) REFERENCES StudentGroups(group_id),
    CONSTRAINT FK_CircularReviews_Reviewee FOREIGN KEY (reviewee_group_id) REFERENCES StudentGroups(group_id),
    CONSTRAINT UQ_CircularPair UNIQUE (reviewer_group_id, reviewee_group_id)
);
GO

CREATE TABLE CorrectionPointsWallet (
    wallet_id    INT IDENTITY(1,1) PRIMARY KEY,
    user_id      INT NOT NULL UNIQUE,
    balance_cp   INT NOT NULL DEFAULT 150 CHECK (balance_cp >= 0),
    updated_at   DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Wallet_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE CorrectionPointsTransactions (
    tx_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT NOT NULL,
    amount      INT NOT NULL,
    tx_type     VARCHAR(30) NOT NULL CHECK (tx_type IN ('EARNED_REVIEW', 'SPENT_SUBMISSION', 'BONUS', 'PENALTY')),
    description NVARCHAR(500) NOT NULL,
    created_at  DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Tx_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

CREATE TABLE PedagogicalFeedbacks (
    feedback_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id               INT NOT NULL,
    commit_hash           VARCHAR(40) NULL,
    error_category        VARCHAR(50) NOT NULL,
    raw_error_text        NVARCHAR(MAX) NOT NULL,
    pedagogical_guidance  NVARCHAR(MAX) NOT NULL,
    socratic_question     NVARCHAR(MAX) NULL,
    created_at            DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Pedagogical_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

-- 6. ĐỀ THI TRẮC NGHIỆM
CREATE TABLE Exams (
    exam_id          INT IDENTITY(1,1) PRIMARY KEY,
    title            NVARCHAR(200) NOT NULL,
    description      NVARCHAR(1000) NULL,
    course_id        INT NOT NULL,
    created_by       INT NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    source_file_name NVARCHAR(255) NULL,
    question_count   INT NOT NULL DEFAULT 0,
    is_open          BIT NOT NULL DEFAULT 1,
    created_at       DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Exams_Courses FOREIGN KEY (course_id) REFERENCES Courses(course_id) ON DELETE CASCADE,
    CONSTRAINT FK_Exams_CreatedBy FOREIGN KEY (created_by) REFERENCES Users(user_id)
);
GO

CREATE TABLE ExamQuestions (
    question_id    INT IDENTITY(1,1) PRIMARY KEY,
    exam_id        INT NOT NULL,
    question_no    INT NOT NULL,
    question_text  NVARCHAR(MAX) NOT NULL,
    option_a       NVARCHAR(1000) NOT NULL,
    option_b       NVARCHAR(1000) NOT NULL,
    option_c       NVARCHAR(1000) NOT NULL,
    option_d       NVARCHAR(1000) NOT NULL,
    correct_option CHAR(1) NOT NULL CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    CONSTRAINT FK_ExamQuestions_Exams FOREIGN KEY (exam_id) REFERENCES Exams(exam_id) ON DELETE CASCADE,
    CONSTRAINT UQ_ExamQuestions_Order UNIQUE (exam_id, question_no)
);
GO

CREATE TABLE ExamSubmissions (
    submission_id   INT IDENTITY(1,1) PRIMARY KEY,
    exam_id         INT NOT NULL,
    student_id      INT NOT NULL,
    total_questions INT NOT NULL DEFAULT 0,
    correct_answers INT NOT NULL DEFAULT 0,
    score           DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    submitted_at    DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_ExamSubmissions_Exams FOREIGN KEY (exam_id) REFERENCES Exams(exam_id) ON DELETE CASCADE,
    CONSTRAINT FK_ExamSubmissions_Students FOREIGN KEY (student_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT UQ_Exam_Student UNIQUE (exam_id, student_id)
);
GO

CREATE TABLE ExamAnswers (
    answer_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    submission_id   INT NOT NULL,
    question_id     INT NOT NULL,
    selected_option CHAR(1) NULL CHECK (selected_option IN ('A', 'B', 'C', 'D')),
    is_correct      BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_ExamAnswers_Submissions FOREIGN KEY (submission_id) REFERENCES ExamSubmissions(submission_id) ON DELETE CASCADE,
    CONSTRAINT FK_ExamAnswers_Questions FOREIGN KEY (question_id) REFERENCES ExamQuestions(question_id),
    CONSTRAINT UQ_Submission_Question UNIQUE (submission_id, question_id)
);
GO

CREATE TABLE RememberMeTokens (
    series     VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id    INT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME2 NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_RememberMeTokens_Users FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
);
GO

-- 7. CÁC COMPATIBILITY VIEWS ĐẢM BẢO TƯƠNG THÍCH MỌI DAO
CREATE VIEW projects AS
SELECT group_id AS project_id, group_id, group_name AS project_name, created_at FROM StudentGroups;
GO

CREATE VIEW git_repositories AS
SELECT repo_id, group_id AS project_id, repo_name, repo_url, default_branch, last_synced_at, created_at FROM Repositories;
GO

CREATE VIEW commit_logs AS
SELECT commit_id, repo_id, author_user_id AS user_id, commit_hash, author_email, lines_added, lines_deleted, commit_date, commit_message FROM GitCommits;
GO

CREATE VIEW Group_Members AS
SELECT group_member_id, group_id, student_id AS user_id, role_in_group, joined_at FROM GroupMembers;
GO

CREATE VIEW Tasks AS
SELECT assignment_id AS task_id, title, description, created_by, course_id, due_at AS deadline, prompt_file_name AS attachment_name, prompt_file_data AS attachment_data, created_at FROM Essay_Assignments;
GO

CREATE VIEW TaskSubmissions AS
SELECT submission_id, assignment_id AS task_id, student_id, file_name, file_data, sha256_hash, submitted_at, score, feedback, graded_by, graded_at FROM Essay_Submissions;
GO

-- ============================================================
-- 8. NẠP DỮ LIỆU ĐẦY ĐỦ: 1 ADMIN + 5 GIẢNG VIÊN + 25 SINH VIÊN
-- Mật khẩu mặc định: 123456
-- ============================================================
INSERT INTO Users (student_code, username, full_name, email, github_username, password_hash, role, is_admin, must_change_password)
VALUES 
    -- 1 Quản trị viên
    ('ADMIN001', 'admin', N'System Administrator', 'admin@aita.edu.vn', 'admin-aita', '123456', 'ADMIN', 1, 0),

    -- 4 Giảng viên (Username: [Tên][chữ đầu họ đệm][MSGV], MSGV: GV00001 - GV00099)
    ('GV00001', 'KhoanaGV00001', N'TS. Nguyễn Anh Khoa (CSD201)', 'khoana@aita.edu.vn', 'khoa-csd201', '123456', 'LECTURER', 0, 0),
    ('GV00002', 'VinhnvGV00002', N'ThS. Nguyễn Văn Vịnh (PRJ301, SWR302)', 'vinhnv@aita.edu.vn', 'vinh-swr302', '123456', 'LECTURER', 0, 0),
    ('GV00003', 'TinnlGV00003', N'ThS. Nguyễn Lê Tín (JPD123)', 'tinnl@aita.edu.vn', 'tin-jpd123', '123456', 'LECTURER', 0, 0),
    ('GV00004', 'HungnvGV00004', N'ThS. Nguyễn Văn Hưng (IOT102)', 'hungnv@aita.edu.vn', 'hung-iot102', '123456', 'LECTURER', 0, 0),

    -- 25 Sinh viên (Khóa 20 - BIT_SE_K20C, Username: [Tên][chữ đầu họ đệm][MSSV])
    -- 5 Sinh viên trọng tâm bắt buộc:
    ('QE200066', 'VuongnqQE200066', N'Nguyễn Quốc Vương', 'vuong.nq@fpt.edu.vn', 'qvuong8th3', '123456', 'STUDENT', 0, 0), -- user_id 6
    ('QE200001', 'AnnvQE200001', N'Nguyễn Văn An', 'an.nguyen@fpt.edu.vn', 'an-nguyen', '123456', 'STUDENT', 0, 0),
    ('QE200002', 'BinhttQE200002', N'Trần Thái Bình', 'binh.tran@fpt.edu.vn', 'binh-tran', '123456', 'STUDENT', 0, 0),
    ('QE200003', 'ChilkQE200003', N'Lê Kim Chi', 'chi.le@fpt.edu.vn', 'chi-le', '123456', 'STUDENT', 0, 0),
    ('QE200004', 'DunghtQE200004', N'Hoàng Tiến Dũng', 'dung.hoang@fpt.edu.vn', 'dung-hoang', '123456', 'STUDENT', 0, 0),
    ('QE200005', 'EmptQE200005', N'Phạm Thị Em', 'em.pham@fpt.edu.vn', 'em-pham', '123456', 'STUDENT', 0, 0),
    ('QE200006', 'PhucthQE200006', N'Trần Hoàng Phúc', 'phuc.th@fpt.edu.vn', 'phuc-tran', '123456', 'STUDENT', 0, 0), -- user_id 12
    ('QE200007', 'MinhldQE200007', N'Lê Đức Minh', 'minh.ld@fpt.edu.vn', 'minh-le', '123456', 'STUDENT', 0, 0), -- user_id 13
    ('QE200008', 'VyhyQE200008', N'Hoàng Yến Vy', 'vy.hy@fpt.edu.vn', 'vy-hoang', '123456', 'STUDENT', 0, 0), -- user_id 14
    ('QE200064', 'NguyenvtQE200064', N'Võ Thảo Nguyên', 'nguyenvt@fpt.edu.vn', 'Vo-Thao-Nguyen', '123456', 'STUDENT', 0, 0), -- user_id 15
    ('QE200140', 'LongvxQE200140', N'Võ Xuân Long', 'longvx@fpt.edu.vn', 'long-vx', '123456', 'STUDENT', 0, 0), -- user_id 16
    ('QE200011', 'KiendtQE200011', N'Đặng Trung Kiên', 'kien.dt@fpt.edu.vn', 'kien-dang', '123456', 'STUDENT', 0, 0),
    ('QE200012', 'LannnQE200012', N'Ngô Ngọc Lan', 'lan.nt@fpt.edu.vn', 'lan-ngo', '123456', 'STUDENT', 0, 0),
    ('QE200013', 'MaidtQE200013', N'Dương Tuyết Mai', 'mai.dtt@fpt.edu.vn', 'mai-duong', '123456', 'STUDENT', 0, 0),
    ('QE200014', 'NambhQE200014', N'Bùi Hải Nam', 'nam.bh@fpt.edu.vn', 'nam-bui', '123456', 'STUDENT', 0, 0),
    ('QE200015', 'OanhdkQE200015', N'Đinh Kim Oanh', 'oanh.dtk@fpt.edu.vn', 'oanh-dinh', '123456', 'STUDENT', 0, 0),
    ('QE200016', 'QuandaQE200016', N'Đỗ Anh Quân', 'quan.da@fpt.edu.vn', 'quan-do', '123456', 'STUDENT', 0, 0),
    ('QE200017', 'SonlhQE200017', N'Lý Hồng Sơn', 'son.lh@fpt.edu.vn', 'son-ly', '123456', 'STUDENT', 0, 0),
    ('QE200018', 'ThaotpQE200018', N'Trịnh Phương Thảo', 'thao.tpt@fpt.edu.vn', 'thao-trinh', '123456', 'STUDENT', 0, 0),
    ('QE200019', 'UytqQE200019', N'Trương Quốc Uy', 'uy.tq@fpt.edu.vn', 'uy-truong', '123456', 'STUDENT', 0, 0),
    ('QE200020', 'VietphQE200020', N'Phan Hoàng Việt', 'viet.ph@fpt.edu.vn', 'viet-phan', '123456', 'STUDENT', 0, 0),
    ('QE200021', 'XuanltQE200021', N'Lương Thanh Xuân', 'xuan.lth@fpt.edu.vn', 'xuan-luong', '123456', 'STUDENT', 0, 0),
    ('QE200022', 'YenhbQE200022', N'Hà Bích Yến', 'yen.hb@fpt.edu.vn', 'yen-ha', '123456', 'STUDENT', 0, 0),
    ('QE190093', 'KietntQE190093', N'Nguyễn Tuấn Kiệt', 'kietnt@fpt.edu.vn', 'kiet-nt', '123456', 'STUDENT', 0, 0), -- user_id 29
    ('QE200042', 'HaindtQE200042', N'Nguyễn Đặng Trường Hải', 'haindt@fpt.edu.vn', 'hai-ndt', '123456', 'STUDENT', 0, 0); -- user_id 30
GO

-- 9. KHỞI TẠO 5 MÔN HỌC BẮT BUỘC
INSERT INTO Courses (course_code, course_name, semester, created_by)
VALUES 
    ('PRJ301', N'Java Web Application Development (PRJ301)', 'Fall 2026', 1),
    ('SWR302', N'Software Requirement Engineering (SWR302)', 'Fall 2026', 1),
    ('JPD123', N'Core Java Lab & Practice (JPD123)', 'Fall 2026', 1),
    ('CSD201', N'Data Structures and Algorithms (CSD201)', 'Fall 2026', 1),
    ('IOT102', N'Internet of Things Fundamentals (IOT102)', 'Fall 2026', 1);
GO

-- Phân công 4 giảng viên phụ trách các môn (ThS. Nguyễn Văn Vịnh phụ trách cả PRJ301 và SWR302)
INSERT INTO Lecturer_Courses (course_id, lecturer_id)
VALUES 
    (1, 3), -- PRJ301 -> ThS. Nguyễn Văn Vịnh (VinhnvGV00002)
    (2, 3), -- SWR302 -> ThS. Nguyễn Văn Vịnh (VinhnvGV00002)
    (3, 4), -- JPD123 -> ThS. Nguyễn Lê Tín (TinnlGV00003)
    (4, 2), -- CSD201 -> TS. Nguyễn Anh Khoa (KhoanaGV00001)
    (5, 5); -- IOT102 -> ThS. Nguyễn Văn Hưng (HungnvGV00004)
GO

-- Tạo Lớp học cho mỗi môn
INSERT INTO Course_Classes (course_id, class_name)
VALUES 
    (1, 'SE20C_PRJ301'),
    (2, 'SE20C_SWR302'),
    (3, 'SE20C_JPD123'),
    (4, 'SE20C_CSD201'),
    (5, 'SE20C_IOT102');
GO

-- Ghi danh 25 sinh viên (từ user_id 6 đến 30) vào lớp và môn học
DECLARE @uid INT = 6;
WHILE @uid <= 30
BEGIN
    INSERT INTO Class_Students (class_id, student_id) VALUES (1, @uid);
    INSERT INTO Class_Students (class_id, student_id) VALUES (2, @uid);
    INSERT INTO Class_Students (class_id, student_id) VALUES (3, @uid);
    INSERT INTO Class_Students (class_id, student_id) VALUES (4, @uid);
    INSERT INTO Class_Students (class_id, student_id) VALUES (5, @uid);

    INSERT INTO CourseEnrollments (course_id, student_id, class_name) VALUES (1, @uid, 'BIT_SE_K20C');
    INSERT INTO CourseEnrollments (course_id, student_id, class_name) VALUES (2, @uid, 'BIT_SE_K20C');
    INSERT INTO CourseEnrollments (course_id, student_id, class_name) VALUES (3, @uid, 'BIT_SE_K20C');
    INSERT INTO CourseEnrollments (course_id, student_id, class_name) VALUES (4, @uid, 'BIT_SE_K20C');
    INSERT INTO CourseEnrollments (course_id, student_id, class_name) VALUES (5, @uid, 'BIT_SE_K20C');

    -- Khởi tạo ví điểm sửa lỗi cho sinh viên (150 CP)
    INSERT INTO CorrectionPointsWallet (user_id, balance_cp, updated_at) VALUES (@uid, 150, GETDATE());

    SET @uid = @uid + 1;
END;
GO

-- 10. NHÓM VÀ LỊCH SỬ GIT CHO MODULE FE-L-03 (NHÓM 6)
INSERT INTO StudentGroups (group_name, course_code, semester, lecturer_id)
VALUES 
    (N'Nhóm 1 - AST Parser Engine (FE-L-01)', 'PRJ301', 'Fall 2026', 3),
    (N'Nhóm 2 - Moulinette Docker Sandbox (FE-L-02)', 'PRJ301', 'Fall 2026', 3),
    (N'Nhóm 3 - Circular Peer Auditor (FE-L-04)', 'PRJ301', 'Fall 2026', 3),
    (N'Nhóm 4 - Submission Integrity SHA256 (FE-L-05)', 'PRJ301', 'Fall 2026', 3),
    (N'Nhóm 5 - Pedagogical AI Teacher (FE-L-06)', 'PRJ301', 'Fall 2026', 3),
    (N'Nhóm 6 - Git Analytics Platform', 'PRJ301', 'Fall 2026', 3),
    (N'Nhóm 7 - Academic Marketplace (FE-L-07)', 'PRJ301', 'Fall 2026', 3);
GO

-- Gán thành viên Nhóm 6: Nguyễn Quốc Vương, Trần Hoàng Phúc, Lê Đức Minh, Hoàng Yến Vy, Võ Thảo Nguyên
INSERT INTO GroupMembers (group_id, student_id, role_in_group)
VALUES 
    (6, 6,  'LEADER'), -- Nguyễn Quốc Vương (user_id 6)
    (6, 12, 'MEMBER'), -- Trần Hoàng Phúc (user_id 12)
    (6, 13, 'MEMBER'), -- Lê Đức Minh (user_id 13)
    (6, 14, 'MEMBER'), -- Hoàng Yến Vy (user_id 14)
    (6, 15, 'MEMBER'); -- Võ Thảo Nguyên (user_id 15)
GO

-- Gán Repository Nhóm 6 (Khớp chính xác với hình ảnh: https://github.com/lx426352-art/PRJ301-Nh-m-6)
INSERT INTO Repositories (group_id, repo_name, repo_url, default_branch, last_synced_at)
VALUES (6, 'PRJ301-Nhom-6', 'https://github.com/lx426352-art/PRJ301-Nh-m-6', 'main', GETDATE());
GO

-- Trọng số Assessor cho Nhóm 6: 50% Volume + 40% Freq + 20% Review - 10% Penalty (Khớp chính xác với hình ảnh!)
INSERT INTO contribution_settings (project_id, group_id, loc_weight, regularity_weight, review_weight, penalty_weight)
VALUES (1, 6, 0.50, 0.40, 0.20, 0.10);
GO

-- Commits mẫu cho Nhóm 6
INSERT INTO GitCommits (repo_id, author_user_id, commit_hash, author_email, commit_message, commit_date, lines_added, lines_deleted, files_changed)
VALUES 
    (1, 15, 'e1f2a3b4c5d67890123456789abcdef012345671', 'nguyen.vt@fpt.edu.vn', 'feat(core): setup teamwork assessor base formula and engine', DATEADD(day, -14, GETDATE()), 450, 30, 6),
    (1, 15, 'e1f2a3b4c5d67890123456789abcdef012345672', 'nguyen.vt@fpt.edu.vn', 'feat(ui): build dashboard charts and weekly timeline', DATEADD(day, -10, GETDATE()), 320, 20, 5),
    (1, 6,  'a1b2c3d4e5f67890123456789abcdef012345673', 'vuong.nq@fpt.edu.vn', 'feat(assessor): implement anti-freerider penalty heuristic', DATEADD(day, -7, GETDATE()), 380, 25, 4),
    (1, 6,  'a1b2c3d4e5f67890123456789abcdef012345674', 'vuong.nq@fpt.edu.vn', 'refactor(dao): optimize commit querying and connection pool', DATEADD(day, -3, GETDATE()), 290, 15, 3),
    (1, 12, 'b1c2d3e4f5a67890123456789abcdef012345675', 'phuc.th@fpt.edu.vn', 'feat(service): add GitHub sync service endpoint', DATEADD(day, -5, GETDATE()), 210, 18, 3),
    (1, 13, 'c1d2e3f4a5b67890123456789abcdef012345676', 'minh.ld@fpt.edu.vn', 'docs: update ERD and system specification', DATEADD(day, -4, GETDATE()), 130, 10, 2),
    (1, 14, 'd1e2f3a4b5c67890123456789abcdef012345677', 'vy.hy@fpt.edu.vn', 'fix', DATEADD(hour, -2, GETDATE()), 25, 2, 1),
    (1, 14, 'd1e2f3a4b5c67890123456789abcdef012345678', 'vy.hy@fpt.edu.vn', 'update', DATEADD(hour, -1, GETDATE()), 18, 1, 1);
GO

-- Pull Requests & Code Reviews
INSERT INTO Pull_Requests (group_id, user_id, pr_number, title, status, created_at, merged_at)
VALUES 
    (6, 15, 1, 'feat: Setup Core Assessor UI', 'MERGED', DATEADD(day, -12, GETDATE()), DATEADD(day, -11, GETDATE())),
    (6, 6,  2, 'feat: Anti-freerider Algorithm', 'MERGED', DATEADD(day, -6, GETDATE()), DATEADD(day, -5, GETDATE())),
    (6, 12, 3, 'feat: GitHub REST Sync', 'MERGED', DATEADD(day, -4, GETDATE()), DATEADD(day, -3, GETDATE()));
GO

INSERT INTO Code_Reviews (group_id, reviewer_id, pr_id, comments_num, reviewed_at)
VALUES 
    (6, 6,  1, 3, DATEADD(day, -11, GETDATE())),
    (6, 15, 2, 2, DATEADD(day, -5, GETDATE()));
    (6, 16, 2, 2, DATEADD(day, -5, GETDATE()));
GO

-- 11. BÀI TẬP MẪU VÀ BÀI NỘP
INSERT INTO Essay_Assignments (title, description, created_by, course_id, due_at, prompt_file_name)
VALUES 
    (N'Assignment 1: Xây dựng Submission Portal MVC2 chuẩn Filter SHA-256', 
     N'Yêu cầu hoàn thành cổng nộp bài theo mô hình MVC2, sử dụng Filter để kiểm tra mã băm SHA-256 của file ZIP trước khi lưu vào CSDL.', 
     2, 1, DATEADD(day, 10, GETDATE()), 'PRJ301_Assignment1_Requirement.pdf'),
    (N'Lab 2: Phân tích yêu cầu hệ thống theo chuẩn IEEE 830', 
     N'Viết tài liệu đặc tả yêu cầu phần mềm SRS cho module quản lý sinh viên.', 
     3, 2, DATEADD(day, 7, GETDATE()), 'SWR302_Lab2_SRS_Template.docx');
GO

INSERT INTO Essay_Submissions (assignment_id, student_id, file_name, file_data, sha256_hash, score, feedback, graded_by, graded_at)
VALUES 
    (1, 7, 'PRJ301_Assignment1_NguyenQuocVuong.zip', 0x504B0304, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 9.5, N'Bài làm rất tốt, cấu trúc Package MVC2 chuẩn mực, Filter SHA-256 hoạt động chuẩn.', 2, GETDATE());
GO
