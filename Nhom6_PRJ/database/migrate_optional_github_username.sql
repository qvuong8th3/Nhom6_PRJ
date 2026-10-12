USE AITA_DB;
GO

DECLARE @githubConstraint sysname;

SELECT TOP (1) @githubConstraint = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic
    ON ic.object_id = kc.parent_object_id
    AND ic.index_id = kc.unique_index_id
    AND ic.key_ordinal = 1
JOIN sys.columns c
    ON c.object_id = ic.object_id
    AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID(N'dbo.Users')
    AND kc.type = 'UQ'
    AND c.name = N'github_username'
    AND NOT EXISTS (
        SELECT 1
        FROM sys.index_columns extra
        WHERE extra.object_id = kc.parent_object_id
            AND extra.index_id = kc.unique_index_id
            AND extra.key_ordinal > 1
    );

IF @githubConstraint IS NOT NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.Users DROP CONSTRAINT ' + QUOTENAME(@githubConstraint));
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.Users')
        AND name = N'UX_Users_GithubUsername_NotNull'
)
BEGIN
    CREATE UNIQUE INDEX UX_Users_GithubUsername_NotNull
        ON dbo.Users(github_username)
        WHERE github_username IS NOT NULL;
END
GO
