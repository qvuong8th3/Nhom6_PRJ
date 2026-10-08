USE AITA_DB;
GO

DECLARE @lecturerFullName NVARCHAR(100) =
    N'Gi' + NCHAR(7843) + N'ng vi' + NCHAR(234) + N'n Demo';

IF NOT EXISTS (
    SELECT 1
    FROM dbo.Users
    WHERE email = 'lecturer01@gmail.com'
       OR username = 'aita_lecturer'
)
BEGIN
    INSERT INTO dbo.Users
        (username, password_hash, full_name, email, github_username, role)
    VALUES
        ('aita_lecturer', '123456', @lecturerFullName,
         'lecturer01@gmail.com', NULL, 'LECTURER');
END
ELSE
BEGIN
    UPDATE dbo.Users
    SET full_name = @lecturerFullName
    WHERE email = 'lecturer01@gmail.com'
       OR username = 'aita_lecturer';
END
GO