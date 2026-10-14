USE AITA_DB;
GO

IF COL_LENGTH('dbo.Exams', 'course_id') IS NULL
BEGIN
    ALTER TABLE dbo.Exams ADD course_id INT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = 'FK_Exams_Course' AND parent_object_id = OBJECT_ID('dbo.Exams')
)
BEGIN
    ALTER TABLE dbo.Exams
    ADD CONSTRAINT FK_Exams_Course FOREIGN KEY (course_id)
        REFERENCES dbo.Courses(course_id);
END
GO

IF COL_LENGTH('dbo.Essay_Assignments', 'course_id') IS NULL
BEGIN
    ALTER TABLE dbo.Essay_Assignments ADD course_id INT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = 'FK_EssayAssignments_Course'
        AND parent_object_id = OBJECT_ID('dbo.Essay_Assignments')
)
BEGIN
    ALTER TABLE dbo.Essay_Assignments
    ADD CONSTRAINT FK_EssayAssignments_Course FOREIGN KEY (course_id)
        REFERENCES dbo.Courses(course_id);
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_Exams_CourseId' AND object_id = OBJECT_ID('dbo.Exams')
)
BEGIN
    CREATE INDEX IX_Exams_CourseId ON dbo.Exams(course_id, created_by);
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_EssayAssignments_CourseId'
        AND object_id = OBJECT_ID('dbo.Essay_Assignments')
)
BEGIN
    CREATE INDEX IX_EssayAssignments_CourseId
        ON dbo.Essay_Assignments(course_id, created_by);
END
GO

UPDATE e
SET course_id = (
    SELECT MIN(lc.course_id)
    FROM dbo.Lecturer_Courses lc
    WHERE lc.lecturer_id = e.created_by
)
FROM dbo.Exams e
WHERE e.course_id IS NULL
    AND (
        SELECT COUNT(*)
        FROM dbo.Lecturer_Courses lc
        WHERE lc.lecturer_id = e.created_by
    ) = 1;
GO

UPDATE a
SET course_id = (
    SELECT MIN(lc.course_id)
    FROM dbo.Lecturer_Courses lc
    WHERE lc.lecturer_id = a.created_by
)
FROM dbo.Essay_Assignments a
WHERE a.course_id IS NULL
    AND (
        SELECT COUNT(*)
        FROM dbo.Lecturer_Courses lc
        WHERE lc.lecturer_id = a.created_by
    ) = 1;
GO
