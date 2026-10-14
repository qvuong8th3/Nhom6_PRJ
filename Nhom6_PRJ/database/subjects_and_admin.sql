USE AITA_DB;
GO

IF OBJECT_ID('dbo.CK_Users_Role', 'C') IS NOT NULL
BEGIN
    ALTER TABLE dbo.Users DROP CONSTRAINT CK_Users_Role;
END
GO

ALTER TABLE dbo.Users
ADD CONSTRAINT CK_Users_Role CHECK (role IN ('STUDENT', 'LECTURER', 'ADMIN'));
GO

IF COL_LENGTH('dbo.Users', 'must_change_password') IS NULL
BEGIN
    ALTER TABLE dbo.Users
    ADD must_change_password BIT NOT NULL
        CONSTRAINT DF_Users_MustChangePassword DEFAULT (0);
END
GO

IF OBJECT_ID('dbo.Courses', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Courses (
        course_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        course_code VARCHAR(30) NOT NULL UNIQUE,
        course_name NVARCHAR(150) NOT NULL,
        semester NVARCHAR(50) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
GO

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
    CREATE INDEX IX_Lecturer_Courses_LecturerId
        ON dbo.Lecturer_Courses(lecturer_id, course_id);
END
GO

IF OBJECT_ID('dbo.Course_Classes', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Course_Classes (
        class_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        course_id INT NOT NULL,
        class_name NVARCHAR(100) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_CourseClasses_Course_Name UNIQUE (course_id, class_name),
        CONSTRAINT FK_CourseClasses_Course FOREIGN KEY (course_id)
            REFERENCES dbo.Courses(course_id) ON DELETE CASCADE
    );
END
GO

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
    CREATE INDEX IX_Class_Students_StudentId
        ON dbo.Class_Students(student_id, class_id);
END
GO
