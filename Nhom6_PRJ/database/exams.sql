USE AITA_DB;
GO

IF OBJECT_ID('Exams', 'U') IS NULL
BEGIN
    CREATE TABLE Exams (
        exam_id INT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(160) NOT NULL,
        description NVARCHAR(2000) NULL,
        created_by INT NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_Exams_CreatedBy FOREIGN KEY (created_by) REFERENCES Users(user_id)
    );
END
GO

IF OBJECT_ID('Exam_Questions', 'U') IS NULL
BEGIN
    CREATE TABLE Exam_Questions (
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
            REFERENCES Exams(exam_id) ON DELETE CASCADE
    );
END
GO

IF OBJECT_ID('Exam_Options', 'U') IS NULL
BEGIN
    CREATE TABLE Exam_Options (
        option_id INT IDENTITY(1,1) PRIMARY KEY,
        question_id INT NOT NULL,
        option_key CHAR(1) NOT NULL,
        option_text NVARCHAR(1000) NOT NULL,
        CONSTRAINT CK_ExamOptions_Key CHECK (option_key IN ('A', 'B', 'C', 'D')),
        CONSTRAINT UQ_ExamOptions_QuestionKey UNIQUE (question_id, option_key),
        CONSTRAINT FK_ExamOptions_Question FOREIGN KEY (question_id)
            REFERENCES Exam_Questions(question_id) ON DELETE CASCADE
    );
END
GO

IF OBJECT_ID('Exam_Attempts', 'U') IS NULL
BEGIN
    CREATE TABLE Exam_Attempts (
        attempt_id INT IDENTITY(1,1) PRIMARY KEY,
        exam_id INT NOT NULL,
        student_id INT NOT NULL,
        submitted_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        score DECIMAL(8,2) NOT NULL,
        max_score DECIMAL(8,2) NOT NULL,
        CONSTRAINT UQ_ExamAttempts_Student UNIQUE (exam_id, student_id),
        CONSTRAINT CK_ExamAttempts_Score CHECK (score >= 0 AND score <= max_score),
        CONSTRAINT FK_ExamAttempts_Exam FOREIGN KEY (exam_id)
            REFERENCES Exams(exam_id) ON DELETE CASCADE,
        CONSTRAINT FK_ExamAttempts_Student FOREIGN KEY (student_id)
            REFERENCES Users(user_id)
    );
END
GO

IF OBJECT_ID('Exam_Answers', 'U') IS NULL
BEGIN
    CREATE TABLE Exam_Answers (
        answer_id INT IDENTITY(1,1) PRIMARY KEY,
        attempt_id INT NOT NULL,
        question_id INT NOT NULL,
        submitted_answer NVARCHAR(1000) NULL,
        is_correct BIT NOT NULL,
        awarded_points DECIMAL(8,2) NOT NULL,
        CONSTRAINT UQ_ExamAnswers_AttemptQuestion UNIQUE (attempt_id, question_id),
        CONSTRAINT FK_ExamAnswers_Attempt FOREIGN KEY (attempt_id)
            REFERENCES Exam_Attempts(attempt_id) ON DELETE CASCADE,
        CONSTRAINT FK_ExamAnswers_Question FOREIGN KEY (question_id)
            REFERENCES Exam_Questions(question_id)
    );
END
GO