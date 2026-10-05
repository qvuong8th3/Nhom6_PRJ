USE AITA_DB;
GO

IF OBJECT_ID('dbo.Remember_Tokens', 'U') IS NULL
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