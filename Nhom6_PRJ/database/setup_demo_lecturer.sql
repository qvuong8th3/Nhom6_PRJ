USE AITA_DB;
GO

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
        ('aita_lecturer', '123456', N'Giảng viên Demo',
         'lecturer01@gmail.com', NULL, 'LECTURER');
END
GO