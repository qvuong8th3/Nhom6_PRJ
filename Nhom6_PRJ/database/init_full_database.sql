-- ============================================================================
-- AITA GIT ANALYTICS - GROUP 6
-- TOÀN BỘ CƠ SỞ DỮ LIỆU ĐỒNG BỘ: SCHEMA + MIGRATIONS + DỮ LIỆU MẪU ĐẦY ĐỦ
-- Hệ quản trị: Microsoft SQL Server (Local, Azure SQL, Docker, Cloud DB)
-- Chạy script này 1 lần duy nhất để tạo mới hoặc cập nhật toàn bộ database
-- ============================================================================

-- 1. TẠO DATABASE NẾU CHƯA CÓ
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'AITA_DB')
BEGIN
    CREATE DATABASE AITA_DB;
END
GO

USE AITA_DB;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 2. BẢNG USERS (Người dùng: Sinh viên, Giảng viên, Quản trị viên)
IF OBJECT_ID('dbo.Users', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Users (
        user_id INT IDENTITY(1,1) PRIMARY KEY,
        username VARCHAR(50) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        full_name NVARCHAR(100) NOT NULL,
        email VARCHAR(100) NOT NULL UNIQUE,
        github_username VARCHAR(100) NULL,
        role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
        must_change_password BIT NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT CK_Users_Role CHECK (role IN ('STUDENT', 'LECTURER', 'ADMIN'))
    );
END
GO

-- Đảm bảo các cột và ràng buộc của Users đầy đủ nếu bảng đã tồn tại từ trước
IF COL_LENGTH('dbo.Users', 'must_change_password') IS NULL
BEGIN
    ALTER TABLE dbo.Users ADD must_change_password BIT NOT NULL DEFAULT 0;
END
GO

IF OBJECT_ID('dbo.CK_Users_Role', 'C') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_Role;
END
GO
ALTER TABLE dbo.Users ADD CONSTRAINT CK_Users_Role CHECK (role IN ('STUDENT', 'LECTURER', 'ADMIN'));
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes 
    WHERE object_id = OBJECT_ID(N'dbo.Users') AND name = N'UX_Users_GithubUsername_NotNull'
)
BEGIN
    CREATE UNIQUE INDEX UX_Users_GithubUsername_NotNull
        ON dbo.Users(github_username)
        WHERE github_username IS NOT NULL;
END
GO

-- 3. BẢNG REMEMBER_TOKENS (Ghi nhớ đăng nhập)
IF OBJECT_ID('dbo.Remember_Tokens', 'U') IS NULL AND OBJECT_ID('dbo.remember_tokens', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Remember_Tokens (
        token_hash CHAR(64) NOT NULL PRIMARY KEY,
        user_id INT NOT NULL,
        expires_at DATETIME2 NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_RememberTokens_User FOREIGN KEY (user_id)
            REFERENCES dbo.Users(user_id) ON DELETE CASCADE
    );
    CREATE INDEX IX_RememberTokens_UserId ON dbo.Remember_Tokens(user_id);
END
GO

-- 4. BẢNG COURSES (Môn học)
IF OBJECT_ID('dbo.Courses', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Courses (
        course_id INT IDENTITY(1,1) PRIMARY KEY,
        course_code VARCHAR(30) NOT NULL UNIQUE,
        course_name NVARCHAR(150) NOT NULL,
        semester NVARCHAR(50) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
GO

-- 5. BẢNG LECTURER_COURSES (Phân công giảng viên phụ trách môn)
IF OBJECT_ID('dbo.Lecturer_Courses', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Lecturer_Courses (
        course_id INT NOT NULL,
        lecturer_id INT NOT NULL,
        CONSTRAINT PK_Lecturer_Courses PRIMARY KEY (course_id, lecturer_id),
        CONSTRAINT FK_LecturerCourses_Course FOREIGN KEY (course_id)
            REFERENCES dbo.Courses(course_id) ON DELETE CASCADE,
        CONSTRAINT FK_LecturerCourses_Lecturer FOREIGN KEY (lecturer_id)
            REFERENCES dbo.Users(user_id) ON DELETE CASCADE
    );
    CREATE INDEX IX_Lecturer_Courses_LecturerId ON dbo.Lecturer_Courses(lecturer_id, course_id);
END
GO

-- 6. BẢNG COURSE_CLASSES (Lớp học theo môn)
IF OBJECT_ID('dbo.Course_Classes', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Course_Classes (
        class_id INT IDENTITY(1,1) PRIMARY KEY,
        course_id INT NOT NULL,
        class_name NVARCHAR(100) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_CourseClasses_Course_Name UNIQUE (course_id, class_name),
        CONSTRAINT FK_CourseClasses_Course FOREIGN KEY (course_id)
            REFERENCES dbo.Courses(course_id) ON DELETE CASCADE
    );
END
GO

-- 7. BẢNG CLASS_STUDENTS (Sinh viên thuộc lớp)
IF OBJECT_ID('dbo.Class_Students', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Class_Students (
        class_id INT NOT NULL,
        student_id INT NOT NULL,
        enrolled_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_Class_Students PRIMARY KEY (class_id, student_id),
        CONSTRAINT FK_ClassStudents_Class FOREIGN KEY (class_id)
            REFERENCES dbo.Course_Classes(class_id) ON DELETE CASCADE,
        CONSTRAINT FK_ClassStudents_Student FOREIGN KEY (student_id)
            REFERENCES dbo.Users(user_id) ON DELETE CASCADE
    );
    CREATE INDEX IX_Class_Students_StudentId ON dbo.Class_Students(student_id, class_id);
END
GO

-- 8. BẢNG GROUPS & PROJECT_GROUPS (Nhóm làm đồ án)
IF OBJECT_ID('dbo.groups', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.groups (
        group_id INT IDENTITY(1,1) PRIMARY KEY,
        group_name NVARCHAR(100) NOT NULL,
        created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP
    );
END
GO

IF OBJECT_ID('dbo.Project_Groups', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Project_Groups (
        group_id INT IDENTITY(1,1) PRIMARY KEY,
        group_name VARCHAR(100) NOT NULL,
        project_topic VARCHAR(255) NULL,
        created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP
    );
END
GO

-- 9. BẢNG GROUP_MEMBERS (Thành viên nhóm sinh viên)
IF OBJECT_ID('dbo.Group_Members', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Group_Members (
        group_id INT NOT NULL,
        user_id INT NOT NULL,
        role_in_group VARCHAR(50) NULL DEFAULT 'Member',
        PRIMARY KEY (group_id, user_id),
        CONSTRAINT FK_GroupMembers_User FOREIGN KEY (user_id)
            REFERENCES dbo.Users(user_id) ON DELETE CASCADE
    );
END
GO

-- 10. BẢNG PROJECTS (Dự án theo nhóm)
IF OBJECT_ID('dbo.projects', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.projects (
        project_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        project_name NVARCHAR(150) NOT NULL
    );
END
GO

-- 11. BẢNG GIT_REPOSITORIES (Kho Git liên kết với nhóm/dự án)
IF OBJECT_ID('dbo.git_repositories', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.git_repositories (
        repo_id INT IDENTITY(1,1) PRIMARY KEY,
        project_id INT NOT NULL,
        repo_url VARCHAR(255) NOT NULL,
        access_token VARCHAR(255) NULL,
        last_synced_at DATETIME NULL
    );
END
GO

-- 12. BẢNG COMMIT_LOGS (Lịch sử commits đồng bộ từ Git)
IF OBJECT_ID('dbo.commit_logs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.commit_logs (
        commit_id INT IDENTITY(1,1) PRIMARY KEY,
        repo_id INT NOT NULL,
        user_id INT NULL,
        commit_hash VARCHAR(64) NOT NULL,
        author_email VARCHAR(100) NULL,
        lines_added INT NULL DEFAULT 0,
        lines_deleted INT NULL DEFAULT 0,
        commit_date DATETIME NOT NULL,
        commit_message NVARCHAR(MAX) NULL,
        CONSTRAINT FK_CommitLogs_User FOREIGN KEY (user_id)
            REFERENCES dbo.Users(user_id) ON DELETE SET NULL
    );
END
GO

-- 13. BẢNG PULL_REQUESTS (Lịch sử PRs đồng bộ từ GitHub)
IF OBJECT_ID('dbo.Pull_Requests', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Pull_Requests (
        pr_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        user_id INT NOT NULL,
        pr_number INT NOT NULL,
        title NVARCHAR(255) NULL,
        status VARCHAR(50) NULL,
        created_at DATETIME NULL,
        merged_at DATETIME NULL,
        CONSTRAINT FK_PullRequests_User FOREIGN KEY (user_id)
            REFERENCES dbo.Users(user_id)
    );
END
GO

-- 14. BẢNG CODE_REVIEWS (Lịch sử Review Code)
IF OBJECT_ID('dbo.Code_Reviews', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Code_Reviews (
        review_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        reviewer_id INT NOT NULL,
        pr_id INT NULL,
        state VARCHAR(50) NULL,
        submitted_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT FK_CodeReviews_Reviewer FOREIGN KEY (reviewer_id)
            REFERENCES dbo.Users(user_id)
    );
END
GO

-- 15. BẢNG CONTRIBUTION_SETTINGS (Cấu hình trọng số tính điểm Git)
IF OBJECT_ID('dbo.contribution_settings', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.contribution_settings (
        setting_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        w_volume FLOAT NULL DEFAULT 0.35,
        w_frequency FLOAT NULL DEFAULT 0.25,
        w_review FLOAT NULL DEFAULT 0.25,
        w_penalty FLOAT NULL DEFAULT 0.15,
        updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP
    );
END
GO

-- 16. BẢNG CONTRIBUTION_SCORES (Điểm đóng góp của sinh viên)
IF OBJECT_ID('dbo.Contribution_Scores', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Contribution_Scores (
        score_id INT IDENTITY(1,1) PRIMARY KEY,
        group_id INT NOT NULL,
        user_id INT NOT NULL,
        evaluated_period VARCHAR(50) NULL,
        total_commits INT NULL DEFAULT 0,
        total_loc INT NULL DEFAULT 0,
        regularity_score FLOAT NULL DEFAULT 0,
        final_contribution_percentage FLOAT NULL DEFAULT 0,
        system_proposed_score FLOAT NULL DEFAULT 0,
        lecturer_adjusted_score FLOAT NULL DEFAULT 0,
        created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT FK_ContributionScores_User FOREIGN KEY (user_id)
            REFERENCES dbo.Users(user_id)
    );
END
GO

IF COL_LENGTH('dbo.Contribution_Scores', 'lecturer_adjusted_score') IS NULL
BEGIN
    ALTER TABLE dbo.Contribution_Scores ADD lecturer_adjusted_score FLOAT NULL DEFAULT 0;
END
GO

-- 17. BẢNG MEMBER_CONTRIBUTIONS
IF OBJECT_ID('dbo.member_contributions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.member_contributions (
        contribution_id INT IDENTITY(1,1) PRIMARY KEY,
        user_id INT NOT NULL,
        group_id INT NOT NULL,
        total_commits INT NULL DEFAULT 0,
        lines_added INT NULL DEFAULT 0,
        lines_deleted INT NULL DEFAULT 0,
        contribution_score FLOAT NULL DEFAULT 0,
        last_updated DATETIME NULL DEFAULT CURRENT_TIMESTAMP
    );
END
GO

-- 18. BẢNG EXAMS (Đề thi trực tuyến)
IF OBJECT_ID('dbo.Exams', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Exams (
        exam_id INT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(160) NOT NULL,
        description NVARCHAR(2000) NULL,
        created_by INT NOT NULL,
        course_id INT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_Exams_CreatedBy FOREIGN KEY (created_by) REFERENCES dbo.Users(user_id),
        CONSTRAINT FK_Exams_Course FOREIGN KEY (course_id) REFERENCES dbo.Courses(course_id)
    );
END
GO

-- Đảm bảo cột course_id tồn tại trên Exams
IF COL_LENGTH('dbo.Exams', 'course_id') IS NULL
BEGIN
    ALTER TABLE dbo.Exams ADD course_id INT NULL;
    ALTER TABLE dbo.Exams ADD CONSTRAINT FK_Exams_Course FOREIGN KEY (course_id) REFERENCES dbo.Courses(course_id);
END
GO

-- 19. BẢNG EXAM_QUESTIONS (Câu hỏi trong đề thi)
IF OBJECT_ID('dbo.Exam_Questions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Exam_Questions (
        question_id INT IDENTITY(1,1) PRIMARY KEY,
        exam_id INT NOT NULL,
        question_order INT NOT NULL,
        question_type VARCHAR(20) NOT NULL,
        question_text NVARCHAR(2000) NOT NULL,
        points DECIMAL(8,2) NOT NULL,
        correct_answer NVARCHAR(1000) NOT NULL,
        CONSTRAINT CK_ExamQuestions_Type CHECK (question_type IN ('MCQ', 'SHORT_TEXT')),
        CONSTRAINT CK_ExamQuestions_Points CHECK (points > 0),
        CONSTRAINT UQ_ExamQuestions_Order UNIQUE (exam_id, question_order),
        CONSTRAINT FK_ExamQuestions_Exam FOREIGN KEY (exam_id)
            REFERENCES dbo.Exams(exam_id) ON DELETE CASCADE
    );
END
GO

-- 20. BẢNG EXAM_OPTIONS (Các lựa chọn A, B, C, D cho câu trắc nghiệm)
IF OBJECT_ID('dbo.Exam_Options', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Exam_Options (
        option_id INT IDENTITY(1,1) PRIMARY KEY,
        question_id INT NOT NULL,
        option_key CHAR(1) NOT NULL,
        option_text NVARCHAR(1000) NOT NULL,
        CONSTRAINT CK_ExamOptions_Key CHECK (option_key IN ('A', 'B', 'C', 'D')),
        CONSTRAINT UQ_ExamOptions_QuestionKey UNIQUE (question_id, option_key),
        CONSTRAINT FK_ExamOptions_Question FOREIGN KEY (question_id)
            REFERENCES dbo.Exam_Questions(question_id) ON DELETE CASCADE
    );
END
GO

-- 21. BẢNG EXAM_ATTEMPTS (Lượt làm bài của sinh viên)
IF OBJECT_ID('dbo.Exam_Attempts', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Exam_Attempts (
        attempt_id INT IDENTITY(1,1) PRIMARY KEY,
        exam_id INT NOT NULL,
        student_id INT NOT NULL,
        submitted_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        score DECIMAL(8,2) NOT NULL,
        max_score DECIMAL(8,2) NOT NULL,
        CONSTRAINT UQ_ExamAttempts_Student UNIQUE (exam_id, student_id),
        CONSTRAINT CK_ExamAttempts_Score CHECK (score >= 0 AND score <= max_score),
        CONSTRAINT FK_ExamAttempts_Exam FOREIGN KEY (exam_id)
            REFERENCES dbo.Exams(exam_id) ON DELETE CASCADE,
        CONSTRAINT FK_ExamAttempts_Student FOREIGN KEY (student_id)
            REFERENCES dbo.Users(user_id)
    );
END
GO

-- 22. BẢNG EXAM_ANSWERS (Chi tiết câu trả lời của từng lượt thi)
IF OBJECT_ID('dbo.Exam_Answers', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Exam_Answers (
        answer_id INT IDENTITY(1,1) PRIMARY KEY,
        attempt_id INT NOT NULL,
        question_id INT NOT NULL,
        submitted_answer NVARCHAR(1000) NULL,
        is_correct BIT NOT NULL,
        awarded_points DECIMAL(8,2) NOT NULL,
        CONSTRAINT UQ_ExamAnswers_AttemptQuestion UNIQUE (attempt_id, question_id),
        CONSTRAINT FK_ExamAnswers_Attempt FOREIGN KEY (attempt_id)
            REFERENCES dbo.Exam_Attempts(attempt_id) ON DELETE CASCADE,
        CONSTRAINT FK_ExamAnswers_Question FOREIGN KEY (question_id)
            REFERENCES dbo.Exam_Questions(question_id)
    );
END
GO

-- 23. BẢNG ESSAY_ASSIGNMENTS (Bài tập tự luận / Nộp bài)
IF OBJECT_ID('dbo.Essay_Assignments', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Essay_Assignments (
        assignment_id INT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(160) NOT NULL,
        description NVARCHAR(2000) NULL,
        created_by INT NOT NULL,
        prompt_file_name NVARCHAR(255) NULL,
        prompt_file_data VARBINARY(MAX) NULL,
        due_date DATETIME2 NULL,
        course_id INT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_EssayAssignments_CreatedBy FOREIGN KEY (created_by)
            REFERENCES dbo.Users(user_id),
        CONSTRAINT FK_EssayAssignments_Course FOREIGN KEY (course_id)
            REFERENCES dbo.Courses(course_id)
    );
END
GO

IF COL_LENGTH('dbo.Essay_Assignments', 'course_id') IS NULL
BEGIN
    ALTER TABLE dbo.Essay_Assignments ADD course_id INT NULL;
    ALTER TABLE dbo.Essay_Assignments ADD CONSTRAINT FK_EssayAssignments_Course FOREIGN KEY (course_id) REFERENCES dbo.Courses(course_id);
END
GO

IF COL_LENGTH('dbo.Essay_Assignments', 'due_date') IS NULL
BEGIN
    ALTER TABLE dbo.Essay_Assignments ADD due_date DATETIME2 NULL;
END
GO

-- 24. BẢNG ESSAY_SUBMISSIONS (Bài nộp file của sinh viên)
IF OBJECT_ID('dbo.Essay_Submissions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Essay_Submissions (
        submission_id INT IDENTITY(1,1) PRIMARY KEY,
        assignment_id INT NOT NULL,
        student_id INT NOT NULL,
        file_name NVARCHAR(255) NOT NULL,
        file_data VARBINARY(MAX) NOT NULL,
        file_size_bytes BIGINT NOT NULL,
        submitted_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        grade DECIMAL(8,2) NULL,
        feedback NVARCHAR(2000) NULL,
        graded_by INT NULL,
        graded_at DATETIME2 NULL,
        CONSTRAINT UQ_EssaySubmissions_Assignment_Student UNIQUE (assignment_id, student_id),
        CONSTRAINT CK_EssaySubmissions_Grade CHECK (grade IS NULL OR (grade >= 0 AND grade <= 10)),
        CONSTRAINT FK_EssaySubmissions_Assignment FOREIGN KEY (assignment_id)
            REFERENCES dbo.Essay_Assignments(assignment_id) ON DELETE CASCADE,
        CONSTRAINT FK_EssaySubmissions_Student FOREIGN KEY (student_id)
            REFERENCES dbo.Users(user_id),
        CONSTRAINT FK_EssaySubmissions_GradedBy FOREIGN KEY (graded_by)
            REFERENCES dbo.Users(user_id)
    );
END
GO

-- ============================================================================
-- DỮ LIỆU KHỞI TẠO CHUẨN (TÀI KHOẢN, MÔN HỌC, LỚP, DỮ LIỆU TEST ĐỒNG BỘ)
-- ============================================================================

-- 1. QUẢN TRỊ VIÊN MẶC ĐỊNH (admin / Admin@123456)
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'admin' OR role = 'ADMIN')
BEGIN
    INSERT INTO dbo.Users (username, password_hash, full_name, email, role, must_change_password)
    VALUES ('admin', '123456', N'Quản trị viên Hệ thống', 'admin@example.com', 'ADMIN', 0);
END
GO

-- 2. GIẢNG VIÊN DEMO (lecturer01 / 123456)
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'lecturer01' OR email = 'lecturer01@gmail.com')
BEGIN
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role, must_change_password)
    VALUES ('lecturer01', '123456', N'Giảng viên Demo', 'lecturer01@gmail.com', 'lecturer-demo', 'LECTURER', 0);
END
ELSE
BEGIN
    UPDATE dbo.Users
    SET role = 'LECTURER', password_hash = '123456', full_name = N'Giảng viên Demo'
    WHERE username = 'lecturer01' OR email = 'lecturer01@gmail.com';
END
GO

-- Thêm bí danh aita_lecturer nếu chưa có
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'aita_lecturer')
BEGIN
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role, must_change_password)
    VALUES ('aita_lecturer', '123456', N'Giảng viên Demo', 'aita_lecturer@gmail.com', NULL, 'LECTURER', 0);
END
GO

-- 3. SINH VIÊN DEMO (student01 - student06, password: 123456)
IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'student01')
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role)
    VALUES ('student01', '123456', N'Nguyễn Văn An', 'student01@gmail.com', 'nguyenvanan', 'STUDENT');

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'student02')
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role)
    VALUES ('student02', '123456', N'Trần Văn Bình', 'student02@gmail.com', 'tranvanbinh', 'STUDENT');

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'student03')
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role)
    VALUES ('student03', '123456', N'Lê Văn Cường', 'student03@gmail.com', 'levancuong', 'STUDENT');

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'student04')
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role)
    VALUES ('student04', '123456', N'Phạm Văn Dũng', 'student04@gmail.com', 'phamvandung', 'STUDENT');

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'vothaonguyen222')
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role)
    VALUES ('vothaonguyen222', '123456', N'Võ Thảo Nguyên', 'vothaonguyen222@gmail.com', 'vothaonguyen', 'STUDENT');

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'hainguyen')
    INSERT INTO dbo.Users (username, password_hash, full_name, email, github_username, role)
    VALUES ('hainguyen', '123456', N'Nguyễn Đặng Trường Hải', 'hainguyen@gmail.com', 'truonghai', 'STUDENT');
GO

-- 4. MÔN HỌC DEMO: PRJ301
IF NOT EXISTS (SELECT 1 FROM dbo.Courses WHERE course_code = 'PRJ301')
BEGIN
    INSERT INTO dbo.Courses (course_code, course_name, semester)
    VALUES ('PRJ301', N'Lập trình Web với Java Servlet & JSP', N'Fall 2026');
END
GO

-- 5. PHÂN CÔNG GIẢNG VIÊN VÀO MÔN
DECLARE @lecId INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE role = 'LECTURER' ORDER BY user_id);
DECLARE @crsId INT = (SELECT TOP 1 course_id FROM dbo.Courses WHERE course_code = 'PRJ301');

IF @lecId IS NOT NULL AND @crsId IS NOT NULL
BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.Lecturer_Courses WHERE course_id = @crsId AND lecturer_id = @lecId)
    BEGIN
        INSERT INTO dbo.Lecturer_Courses (course_id, lecturer_id) VALUES (@crsId, @lecId);
    END
END
GO

-- 6. TẠO LỚP HỌC DEMO: SE1801
DECLARE @crsId INT = (SELECT TOP 1 course_id FROM dbo.Courses WHERE course_code = 'PRJ301');
IF @crsId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Course_Classes WHERE course_id = @crsId AND class_name = N'SE1801')
BEGIN
    INSERT INTO dbo.Course_Classes (course_id, class_name) VALUES (@crsId, N'SE1801');
END
GO

-- 7. GHI DANH SINH VIÊN VÀO LỚP
DECLARE @clsId INT = (SELECT TOP 1 class_id FROM dbo.Course_Classes WHERE class_name = N'SE1801');
IF @clsId IS NOT NULL
BEGIN
    INSERT INTO dbo.Class_Students (class_id, student_id)
    SELECT @clsId, u.user_id
    FROM dbo.Users u
    WHERE u.role = 'STUDENT'
      AND NOT EXISTS (
          SELECT 1 FROM dbo.Class_Students cs
          WHERE cs.class_id = @clsId AND cs.student_id = u.user_id
      );
END
GO

-- 8. NHÓM SINH VIÊN (Group 6)
IF NOT EXISTS (SELECT 1 FROM dbo.groups WHERE group_name = N'Group 6')
BEGIN
    SET IDENTITY_INSERT dbo.groups ON;
    INSERT INTO dbo.groups (group_id, group_name) VALUES (1, N'Group 6');
    SET IDENTITY_INSERT dbo.groups OFF;
END

IF NOT EXISTS (SELECT 1 FROM dbo.Project_Groups WHERE group_name = 'Group 6')
BEGIN
    SET IDENTITY_INSERT dbo.Project_Groups ON;
    INSERT INTO dbo.Project_Groups (group_id, group_name, project_topic)
    VALUES (1, 'Group 6', 'Git Analytics - Student Contribution Assessment');
    SET IDENTITY_INSERT dbo.Project_Groups OFF;
END
GO

-- 9. GẮN THÀNH VIÊN VÀO NHÓM GROUP 6
INSERT INTO dbo.Group_Members (group_id, user_id, role_in_group)
SELECT 1, u.user_id, 'Member'
FROM dbo.Users u
WHERE u.role = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1 FROM dbo.Group_Members gm
      WHERE gm.group_id = 1 AND gm.user_id = u.user_id
  );
GO

-- 10. DỰ ÁN VÀ KHO REPO GIT
IF NOT EXISTS (SELECT 1 FROM dbo.projects WHERE group_id = 1)
BEGIN
    SET IDENTITY_INSERT dbo.projects ON;
    INSERT INTO dbo.projects (project_id, group_id, project_name)
    VALUES (1, 1, N'Git Analytics Project');
    SET IDENTITY_INSERT dbo.projects OFF;
END

IF NOT EXISTS (SELECT 1 FROM dbo.git_repositories WHERE project_id = 1)
BEGIN
    SET IDENTITY_INSERT dbo.git_repositories ON;
    INSERT INTO dbo.git_repositories (repo_id, project_id, repo_url, last_synced_at)
    VALUES (1, 1, 'https://github.com/qvuong8th3/Nhom6_PRJ.git', GETDATE());
    SET IDENTITY_INSERT dbo.git_repositories OFF;
END
GO

-- 11. CẤU HÌNH TRỌNG SỐ CHO NHÓM (contribution_settings)
IF NOT EXISTS (SELECT 1 FROM dbo.contribution_settings WHERE group_id = 1)
BEGIN
    INSERT INTO dbo.contribution_settings (group_id, w_volume, w_frequency, w_review, w_penalty)
    VALUES (1, 0.35, 0.25, 0.25, 0.15);
END
GO

-- 12. DỮ LIỆU COMMITS MẪU (commit_logs) CHO DASHBOARD
IF NOT EXISTS (SELECT 1 FROM dbo.commit_logs WHERE repo_id = 1)
BEGIN
    DECLARE @u1 INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE username = 'student01');
    DECLARE @u2 INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE username = 'student02');
    DECLARE @u3 INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE username = 'student03');
    DECLARE @u4 INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE username = 'student04');

    INSERT INTO dbo.commit_logs (repo_id, user_id, commit_hash, author_email, lines_added, lines_deleted, commit_date, commit_message)
    VALUES 
    (1, @u1, 'c1a1001', 'student01@gmail.com', 250, 10, DATEADD(day, -10, GETDATE()), N'feat: initial project structure and servlet config'),
    (1, @u1, 'c1a1002', 'student01@gmail.com', 180, 25, DATEADD(day, -8, GETDATE()), N'feat: implement authentication filter and session handling'),
    (1, @u1, 'c1a1003', 'student01@gmail.com', 120, 15, DATEADD(day, -4, GETDATE()), N'fix: resolve remember-me cookie expiration'),
    
    (1, @u2, 'c1b2001', 'student02@gmail.com', 320, 40, DATEADD(day, -9, GETDATE()), N'feat: build exam online quiz taking UI and timer'),
    (1, @u2, 'c1b2002', 'student02@gmail.com', 140, 20, DATEADD(day, -6, GETDATE()), N'feat: auto grading MCQ answers and instant score feedback'),
    (1, @u2, 'c1b2003', 'student02@gmail.com', 95, 12, DATEADD(day, -2, GETDATE()), N'test: add unit tests for grading logic'),

    (1, @u3, 'c1c3001', 'student03@gmail.com', 290, 30, DATEADD(day, -7, GETDATE()), N'feat: assignment file upload and teacher review interface'),
    (1, @u3, 'c1c3002', 'student03@gmail.com', 85, 15, DATEADD(day, -3, GETDATE()), N'fix: file validation limit 15MB'),

    (1, @u4, 'c1d4001', 'student04@gmail.com', 210, 15, DATEADD(day, -5, GETDATE()), N'feat: git analytics chart dashboard with Chart.js'),
    (1, @u4, 'c1d4002', 'student04@gmail.com', 130, 10, DATEADD(day, -1, GETDATE()), N'style: responsive navigation and dark theme enhancement');
END
GO

-- 13. DỮ LIỆU PULL REQUESTS MẪU
IF NOT EXISTS (SELECT 1 FROM dbo.Pull_Requests WHERE group_id = 1)
BEGIN
    DECLARE @u1 INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE username = 'student01');
    DECLARE @u2 INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE username = 'student02');

    INSERT INTO dbo.Pull_Requests (group_id, user_id, pr_number, title, status, created_at, merged_at)
    VALUES 
    (1, @u1, 1, N'Feature: Core authentication and DBContext pooling', 'MERGED', DATEADD(day, -9, GETDATE()), DATEADD(day, -8, GETDATE())),
    (1, @u2, 2, N'Feature: Online exam submission module', 'MERGED', DATEADD(day, -6, GETDATE()), DATEADD(day, -5, GETDATE())),
    (1, @u1, 3, N'Fix: Clean git spam commit filter', 'OPEN', DATEADD(day, -1, GETDATE()), NULL);
END
GO

-- 14. ĐỀ THI MẪU (Exams)
DECLARE @lecId INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE role = 'LECTURER' ORDER BY user_id);
DECLARE @crsId INT = (SELECT TOP 1 course_id FROM dbo.Courses WHERE course_code = 'PRJ301');

IF @lecId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Exams WHERE title = N'Đề thi trắc nghiệm Java Web cơ bản')
BEGIN
    INSERT INTO dbo.Exams (title, description, created_by, course_id)
    VALUES (N'Đề thi trắc nghiệm Java Web cơ bản', N'Kiểm tra kiến thức Servlet, JSP và kiến trúc MVC', @lecId, @crsId);

    DECLARE @newExamId INT = SCOPE_IDENTITY();

    -- Câu 1
    INSERT INTO dbo.Exam_Questions (exam_id, question_order, question_type, question_text, points, correct_answer)
    VALUES (@newExamId, 1, 'MCQ', N'Phương thức nào của HttpServlet được gọi để xử lý yêu cầu HTTP GET?', 5.0, 'B');
    DECLARE @q1Id INT = SCOPE_IDENTITY();
    INSERT INTO dbo.Exam_Options (question_id, option_key, option_text) VALUES
    (@q1Id, 'A', N'doPost()'),
    (@q1Id, 'B', N'doGet()'),
    (@q1Id, 'C', N'service()'),
    (@q1Id, 'D', N'init()');

    -- Câu 2
    INSERT INTO dbo.Exam_Questions (exam_id, question_order, question_type, question_text, points, correct_answer)
    VALUES (@newExamId, 2, 'SHORT_TEXT', N'Trong mô hình MVC của ứng dụng Java Web, Servlet đóng vai trò là tầng nào? (Model / View / Controller)', 5.0, N'Controller');
END
GO

-- 15. BÀI TẬP TỰ LUẬN MẪU (Essay_Assignments)
DECLARE @lecId INT = (SELECT TOP 1 user_id FROM dbo.Users WHERE role = 'LECTURER' ORDER BY user_id);
DECLARE @crsId INT = (SELECT TOP 1 course_id FROM dbo.Courses WHERE course_code = 'PRJ301');

IF @lecId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.Essay_Assignments WHERE title = N'Bài tập lớn: Xây dựng Git Analytics Web')
BEGIN
    INSERT INTO dbo.Essay_Assignments (title, description, created_by, course_id, due_date)
    VALUES (
        N'Bài tập lớn: Xây dựng Git Analytics Web',
        N'Nộp báo cáo đồ án kèm liên kết repository và video demo ứng dụng.',
        @lecId,
        @crsId,
        DATEADD(day, 14, GETDATE())
    );
END
GO

PRINT N'====================================================================';
PRINT N'✅ HOÀN TẤT ĐỒNG BỘ CƠ SỞ DỮ LIỆU AITA_DB THÀNH CÔNG!';
PRINT N'Các tài khoản kiểm thử sẵn sàng:';
PRINT N'1. ADMIN:      admin       / Admin@123456';
PRINT N'2. GIẢNG VIÊN: lecturer01  / 123456';
PRINT N'3. SINH VIÊN:  student01   / 123456 (student01 - student06)';
PRINT N'====================================================================';
GO
