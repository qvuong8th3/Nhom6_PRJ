USE AITA_DB;
GO


/* =========================================================
   1. USERS
   ========================================================= */

INSERT INTO Users
(
    username,
    password_hash,
    full_name,
    email,
    role
)
VALUES
(
    'lecturer01',
    '123456',
    N'Nguyễn Văn Giảng',
    'lecturer01@gmail.com',
    'LECTURER'
),
(
    'student01',
    '123456',
    N'Nguyễn Văn An',
    'student01@gmail.com',
    'STUDENT'
),
(
    'student02',
    '123456',
    N'Trần Văn Bình',
    'student02@gmail.com',
    'STUDENT'
),
(
    'student03',
    '123456',
    N'Lê Văn Cường',
    'student03@gmail.com',
    'STUDENT'
),
(
    'student04',
    '123456',
    N'Phạm Văn Dũng',
    'student04@gmail.com',
    'STUDENT'
);
GO


/* =========================================================
   2. PROJECT GROUPS
   ========================================================= */

INSERT INTO Project_Groups
(
    group_name,
    project_topic
)
VALUES
(
    N'Group 6',
    N'Git Analytics - Student Contribution Assessment'
);
GO


/* =========================================================
   3. GROUP MEMBERS
   ========================================================= */

INSERT INTO Group_Members
(
    group_id,
    user_id,
    role_in_group
)
VALUES
(1, 2, 'Member'),
(1, 3, 'Member'),
(1, 4, 'Member'),
(1, 5, 'Member');
GO


/* =========================================================
   4. GIT REPOSITORY
   ========================================================= */

INSERT INTO Git_Repositories
(
    group_id,
    repo_url,
    provider,
    access_token,
    last_synced
)
VALUES
(
    1,
    'https://github.com/group6/git-analytics',
    'GITHUB',
    NULL,
    GETDATE()
);
GO


/* =========================================================
   5. CONTRIBUTION SETTINGS
   ---------------------------------------------------------
   Commit     = 40%
   LOC        = 40%
   Regularity = 20%
   ========================================================= */

INSERT INTO Contribution_Settings
(
    group_id,
    commit_weight,
    loc_weight,
    regularity_weight
)
VALUES
(
    1,
    0.4,
    0.4,
    0.2
);
GO


/* =========================================================
   6. COMMITS
   ========================================================= */

INSERT INTO Commits
(
    commit_id,
    repo_id,
    user_id,
    author_email,
    author_name,
    message,
    lines_added,
    lines_deleted,
    commit_date
)
VALUES

/* ---------- STUDENT 01 ---------- */

(
    'commit001',
    1,
    2,
    'student01@gmail.com',
    N'Nguyễn Văn An',
    N'Create project structure',
    120,
    10,
    '2026-09-10 09:00:00'
),

(
    'commit002',
    1,
    2,
    'student01@gmail.com',
    N'Nguyễn Văn An',
    N'Implement login page',
    180,
    20,
    '2026-09-12 10:00:00'
),

(
    'commit003',
    1,
    2,
    'student01@gmail.com',
    N'Nguyễn Văn An',
    N'Fix login validation',
    70,
    15,
    '2026-09-15 14:00:00'
),

(
    'commit004',
    1,
    2,
    'student01@gmail.com',
    N'Nguyễn Văn An',
    N'Update user interface',
    100,
    25,
    '2026-09-18 16:00:00'
),


/* ---------- STUDENT 02 ---------- */

(
    'commit005',
    1,
    3,
    'student02@gmail.com',
    N'Trần Văn Bình',
    N'Create database connection',
    150,
    20,
    '2026-09-10 08:30:00'
),

(
    'commit006',
    1,
    3,
    'student02@gmail.com',
    N'Trần Văn Bình',
    N'Create DAO classes',
    200,
    30,
    '2026-09-13 11:00:00'
),

(
    'commit007',
    1,
    3,
    'student02@gmail.com',
    N'Trần Văn Bình',
    N'Implement JDBC functions',
    180,
    40,
    '2026-09-16 15:00:00'
),


/* ---------- STUDENT 03 ---------- */

(
    'commit008',
    1,
    4,
    'student03@gmail.com',
    N'Lê Văn Cường',
    N'Create dashboard',
    250,
    30,
    '2026-09-11 09:30:00'
),

(
    'commit009',
    1,
    4,
    'student03@gmail.com',
    N'Lê Văn Cường',
    N'Add contribution chart',
    180,
    20,
    '2026-09-14 13:00:00'
),

(
    'commit010',
    1,
    4,
    'student03@gmail.com',
    N'Lê Văn Cường',
    N'Fix dashboard layout',
    120,
    25,
    '2026-09-19 10:00:00'
),


/* ---------- STUDENT 04 ---------- */

(
    'commit011',
    1,
    5,
    'student04@gmail.com',
    N'Phạm Văn Dũng',
    N'Create GitHub API service',
    220,
    30,
    '2026-09-10 14:00:00'
),

(
    'commit012',
    1,
    5,
    'student04@gmail.com',
    N'Phạm Văn Dũng',
    N'Implement Git commit crawler',
    300,
    50,
    '2026-09-13 15:30:00'
),

(
    'commit013',
    1,
    5,
    'student04@gmail.com',
    N'Phạm Văn Dũng',
    N'Fix GitHub API integration',
    150,
    35,
    '2026-09-17 16:00:00'
),

(
    'commit014',
    1,
    5,
    'student04@gmail.com',
    N'Phạm Văn Dũng',
    N'Improve commit synchronization',
    180,
    40,
    '2026-09-20 09:30:00'
);
GO


/* =========================================================
   7. CONTRIBUTION SCORES
   ========================================================= */

INSERT INTO Contribution_Scores
(
    group_id,
    user_id,
    evaluated_period,
    total_commits,
    total_loc,
    regularity_score,
    final_contribution_percentage,
    system_proposed_score,
    lecturer_adjusted_score
)
VALUES
(
    1,
    2,
    '2026-09',
    4,
    400,
    85.0,
    24.0,
    24.0,
    NULL
),

(
    1,
    3,
    '2026-09',
    3,
    440,
    80.0,
    22.0,
    22.0,
    NULL
),

(
    1,
    4,
    '2026-09',
    3,
    475,
    90.0,
    27.0,
    27.0,
    NULL
),

(
    1,
    5,
    '2026-09',
    4,
    695,
    95.0,
    27.0,
    27.0,
    NULL
);
GO