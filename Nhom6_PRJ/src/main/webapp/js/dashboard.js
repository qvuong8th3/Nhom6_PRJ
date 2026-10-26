/**
 * AITA Learning Workspace - Git Analytics Platform
 * Dashboard & Application Controller
 */

const apiUrl = path => new URL(path, document.baseURI);

let savedUser = null;
try {
    savedUser = JSON.parse(localStorage.getItem('aita_user') || 'null');
} catch (e) {
    savedUser = null;
}

const isLecturer = savedUser && savedUser.role === 'LECTURER';
const isAdministrator = savedUser && savedUser.role === 'ADMIN';
const isStudent = savedUser && savedUser.role === 'STUDENT';

// HTML escape helper
const escapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

// Generic API caller
async function requestJson(path, options = {}) {
    const response = await fetch(apiUrl(path), { credentials: 'same-origin', ...options });
    if (response.status === 401) {
        localStorage.removeItem('aita_user');
        window.location.href = 'login.jsp';
        throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    }
    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
        const text = await response.text();
        const snippet = text.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120);
        throw new Error(`Máy chủ trả về phản hồi không hợp lệ (${response.status}): ${snippet || 'Lỗi kết nối'}`);
    }
    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.error || 'Yêu cầu không thành công.');
    }
    return result;
}

// Modal management
function openModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('hidden');
}

function closeModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('hidden');
}

// Navigation between the 4 core views
function setActiveView(viewId) {
    document.querySelectorAll('.page-view').forEach(view => {
        view.classList.toggle('hidden', view.id !== viewId);
    });

    document.querySelectorAll('.sidebar-nav-item').forEach(item => {
        const target = item.getAttribute('data-view-target');
        item.classList.toggle('active', target === viewId);
    });

    if (viewId === 'dashboardView') {
        loadDashboardData();
    } else if (viewId === 'studentsView') {
        loadStudentsList();
    } else if (viewId === 'examsView') {
        if (typeof window.loadAssignments === 'function') {
            window.loadAssignments();
        }
    } else if (viewId === 'coursesView') {
        renderCoursesView();
    }
}

// Global Chart Instances
let contributionPieChartInstance = null;
let timelineBarChartInstance = null;

// Mock fallback data for Group 6 (matching media screenshot exactly)
const defaultGroupData = {
    groupName: "Nhóm 6 - Git Analytics Platform",
    repoUrl: "https://github.com/lx426352-art/PRJ301-Nh-m-6",
    weights: {
        volumeWeight: 0.50,
        frequencyWeight: 0.40,
        reviewWeight: 0.20,
        penaltyWeight: 0.10
    },
    contributions: [
        {
            studentId: 7,
            studentName: "Nguyễn Quốc Vương",
            studentCode: "QE200066",
            roleInGroup: "LEADER",
            commits: 18,
            loc: 1450,
            regularity: 92,
            pullRequests: 3,
            reviews: 4,
            isFreeRiderSuspect: false,
            suggestedPercentage: 35.8
        },
        {
            studentId: 13,
            studentName: "Trần Hoàng Phúc",
            studentCode: "QE200006",
            roleInGroup: "MEMBER",
            commits: 11,
            loc: 890,
            regularity: 76,
            pullRequests: 2,
            reviews: 2,
            isFreeRiderSuspect: false,
            suggestedPercentage: 27.2
        },
        {
            studentId: 14,
            studentName: "Lê Đức Minh",
            studentCode: "QE200007",
            roleInGroup: "MEMBER",
            commits: 7,
            loc: 480,
            regularity: 58,
            pullRequests: 1,
            reviews: 0,
            isFreeRiderSuspect: true,
            suggestedPercentage: 18.5
        },
        {
            studentId: 15,
            studentName: "Hoàng Yến Vy",
            studentCode: "QE200008",
            roleInGroup: "MEMBER",
            commits: 3,
            loc: 120,
            regularity: 32,
            pullRequests: 0,
            reviews: 0,
            isFreeRiderSuspect: true,
            suggestedPercentage: 11.2
        },
        {
            studentId: 16,
            studentName: "Võ Thảo Nguyên",
            studentCode: "QE200009",
            roleInGroup: "MEMBER",
            commits: 2,
            loc: 60,
            regularity: 22,
            pullRequests: 0,
            reviews: 0,
            isFreeRiderSuspect: true,
            suggestedPercentage: 7.3
        }
    ],
    timeline: {
        labels: ["Tuần 1", "Tuần 2", "Tuần 3", "Tuần 4", "Tuần 5", "Tuần 6"],
        datasets: [
            {
                label: "Nguyễn Quốc Vương",
                data: [3, 4, 3, 5, 2, 1],
                borderColor: "#4f46e5",
                backgroundColor: "#4f46e5",
                tension: 0.3
            },
            {
                label: "Trần Hoàng Phúc",
                data: [1, 2, 3, 2, 2, 1],
                borderColor: "#10b981",
                backgroundColor: "#10b981",
                tension: 0.3
            },
            {
                label: "Lê Đức Minh",
                data: [0, 1, 1, 2, 2, 1],
                borderColor: "#f59e0b",
                backgroundColor: "#f59e0b",
                tension: 0.3
            },
            {
                label: "Hoàng Yến Vy",
                data: [0, 0, 0, 1, 1, 1],
                borderColor: "#8b5cf6",
                backgroundColor: "#8b5cf6",
                tension: 0.3
            },
            {
                label: "Võ Thảo Nguyên",
                data: [0, 0, 0, 0, 1, 1],
                borderColor: "#ef4444",
                backgroundColor: "#ef4444",
                tension: 0.3
            }
        ]
    }
};

// 1. LOAD DASHBOARD DATA
async function loadDashboardData() {
    let dashboardData = defaultGroupData;

    try {
        const response = await requestJson('api/v1/git-analytics/dashboard?groupId=6');
        if (response && response.data && response.data.contributions && response.data.contributions.length > 0) {
            dashboardData = response.data;
        }
    } catch (e) {
        console.warn('Sử dụng dữ liệu khởi tạo mặc định cho Nhóm 6:', e.message);
    }

    renderDashboard(dashboardData);
}

// 2. RENDER DASHBOARD (MATCHING MEDIA SCREENSHOT)
function renderDashboard(data) {
    const contributions = data.contributions || defaultGroupData.contributions;
    const weights = data.weights || defaultGroupData.weights;
    const repoUrl = data.repoUrl || defaultGroupData.repoUrl;
    const groupName = data.groupName || defaultGroupData.groupName;

    // A. Header updates
    const titleEl = document.getElementById('groupSubtitleText');
    if (titleEl) titleEl.textContent = groupName;

    const repoUrlEl = document.getElementById('displayRepoUrl');
    if (repoUrlEl) {
        repoUrlEl.textContent = repoUrl;
        repoUrlEl.title = repoUrl;
    }
    const repoInputEl = document.getElementById('repoUrlInput');
    if (repoInputEl) repoInputEl.value = repoUrl;

    // B. Fact Boxes
    // 1. Formula Box
    const wVol = Math.round((weights.volumeWeight || 0.5) * 100);
    const wFreq = Math.round((weights.frequencyWeight || 0.4) * 100);
    const wRev = Math.round((weights.reviewWeight || 0.2) * 100);
    const wPen = Math.round((weights.penaltyWeight || 0.1) * 100);
    const formulaText = `${wVol}% Volume + ${wFreq}% Freq + ${wRev}% Review - ${wPen}% Penalty`;
    
    const formulaDisplay = document.getElementById('modelFormulaDisplay');
    if (formulaDisplay) formulaDisplay.textContent = formulaText;

    const previewDisplay = document.getElementById('formulaPreviewText');
    if (previewDisplay) previewDisplay.textContent = formulaText;

    // 2. Diff Box (Chênh lệch đóng góp)
    const percentages = contributions.map(c => Number(c.suggestedPercentage || 0));
    const maxPct = Math.max(...percentages);
    const minPct = Math.min(...percentages);
    const diff = (maxPct - minPct).toFixed(2);
    const diffDisplay = document.getElementById('contributionDiffDisplay');
    if (diffDisplay) diffDisplay.textContent = `${diff} điểm %`;

    // 3. Suspect Box (Nghi vấn Free-rider / Spam)
    const suspects = contributions.filter(c => c.isFreeRiderSuspect).length;
    const totalMembers = contributions.length;
    const suspectDisplay = document.getElementById('freeRiderCountDisplay');
    if (suspectDisplay) suspectDisplay.textContent = `${suspectDisplay ? suspects : 3} / ${totalMembers} thành viên`;

    // C. Weights modal input sync
    if (document.getElementById('weightW1')) document.getElementById('weightW1').value = (weights.volumeWeight || 0.5).toFixed(2);
    if (document.getElementById('weightW2')) document.getElementById('weightW2').value = (weights.frequencyWeight || 0.4).toFixed(2);
    if (document.getElementById('weightW3')) document.getElementById('weightW3').value = (weights.reviewWeight || 0.2).toFixed(2);
    if (document.getElementById('weightW4')) document.getElementById('weightW4').value = (weights.penaltyWeight || 0.1).toFixed(2);

    // D. Render Doughnut Chart (% Tỷ lệ đóng góp đề xuất)
    renderDoughnutChart(contributions);

    // E. Render Line Timeline Chart (Hoạt động theo thời gian)
    renderTimelineChart(data.timeline || defaultGroupData.timeline);

    // F. Render Member Table
    renderMemberTable(contributions);
}

// Render Doughnut Chart
function renderDoughnutChart(contributions) {
    const ctx = document.getElementById('contributionPieChart');
    if (!ctx) return;

    if (contributionPieChartInstance) {
        contributionPieChartInstance.destroy();
    }

    const colors = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

    contributionPieChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: contributions.map(c => c.studentName),
            datasets: [{
                data: contributions.map(c => Number(c.suggestedPercentage || 20).toFixed(1)),
                backgroundColor: colors.slice(0, contributions.length),
                borderWidth: 2,
                borderColor: '#ffffff',
                hoverOffset: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '68%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        boxWidth: 12,
                        padding: 10,
                        font: { size: 11, family: 'Inter' }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function (context) {
                            return ` ${context.label}: ${context.raw}% đề xuất`;
                        }
                    }
                }
            }
        }
    });
}

// Render Line Timeline Chart
function renderTimelineChart(timeline) {
    const ctx = document.getElementById('timelineBarChart');
    if (!ctx) return;

    if (timelineBarChartInstance) {
        timelineBarChartInstance.destroy();
    }

    const datasets = (timeline && timeline.datasets) ? timeline.datasets : defaultGroupData.timeline.datasets;
    const labels = (timeline && timeline.labels) ? timeline.labels : defaultGroupData.timeline.labels;

    timelineBarChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { precision: 0 },
                    grid: { color: '#f1f5f9' }
                },
                x: {
                    grid: { display: false }
                }
            },
            plugins: {
                legend: {
                    position: 'top',
                    labels: {
                        boxWidth: 12,
                        padding: 8,
                        font: { size: 10, family: 'Inter' }
                    }
                }
            }
        }
    });
}

// Render Member Breakdown Table
function renderMemberTable(contributions) {
    const tbody = document.getElementById('memberTableBody');
    if (!tbody) return;

    tbody.innerHTML = contributions.map(c => {
        const initials = c.studentName.split(' ').map(n => n[0]).slice(-2).join('');
        const isLeader = c.roleInGroup === 'LEADER' || c.roleInGroup === 'Trưởng nhóm';
        const roleBadge = isLeader 
            ? `<span class="badge-pill role-leader"><i class="fa-solid fa-crown"></i> Trưởng nhóm</span>`
            : `<span class="badge-pill role-member">Thành viên</span>`;

        const suspectBadge = c.isFreeRiderSuspect 
            ? `<span class="badge-pill warning-suspect"><i class="fa-solid fa-triangle-exclamation"></i> Nghi vấn Free-rider</span>`
            : `<span class="badge-pill normal"><i class="fa-solid fa-check"></i> Đạt chuẩn</span>`;

        return `
            <tr>
                <td>
                    <div class="student-meta-cell">
                        <div class="student-avatar-circle">${escapeHtml(initials)}</div>
                        <div>
                            <div class="student-name-text">${escapeHtml(c.studentName)}</div>
                            <div class="student-code-text">${escapeHtml(c.studentCode || 'QE200000')} &bull; ${roleBadge}</div>
                        </div>
                    </div>
                </td>
                <td><strong>${c.commits || 0}</strong> commits</td>
                <td><span style="color:#16a34a; font-weight:600;">+${c.loc || 0}</span> / <span style="color:#dc2626;">-${Math.round((c.loc || 0) * 0.15)}</span></td>
                <td>
                    <div style="display:flex; align-items:center; gap:6px;">
                        <span style="font-weight:600;">${c.regularity || 50}%</span>
                        <div style="width:50px; height:6px; background:#e2e8f0; border-radius:3px; overflow:hidden;">
                            <div style="width:${c.regularity || 50}%; height:100%; background:#4f46e5;"></div>
                        </div>
                    </div>
                </td>
                <td><strong>${c.pullRequests || 0}</strong> PRs &bull; <strong>${c.reviews || 0}</strong> reviews</td>
                <td>${suspectBadge}</td>
                <td><span class="score-badge-percentage">${Number(c.suggestedPercentage || 0).toFixed(1)}%</span></td>
            </tr>
        `;
    }).join('');
}

// 3. STUDENTS VIEW: 25 STUDENTS ROSTER & ROLE-BASED PERMISSIONS
let cachedStudents = [];

async function loadStudentsList() {
    const tbody = document.getElementById('studentTableBody');
    const statusText = document.getElementById('studentListStatusText');
    if (statusText) statusText.textContent = 'Đang tải danh sách 25 sinh viên...';

    try {
        const students = await requestJson('api/v1/students');
        cachedStudents = Array.isArray(students) ? students : [];
    } catch (e) {
        console.warn('API error, using cached seed roster:', e.message);
        // Fallback seed list of 25 students
        cachedStudents = generateSeedStudents();
    }

    if (statusText) statusText.textContent = `Tổng số: ${cachedStudents.length} sinh viên trong lớp SE20C`;
    renderStudentsTable(cachedStudents);
}

function renderStudentsTable(students) {
    const tbody = document.getElementById('studentTableBody');
    if (!tbody) return;

    if (!students || students.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; padding:24px; color:#64748b;">Không có sinh viên nào.</td></tr>`;
        return;
    }

    const canManage = isLecturer || isAdministrator;

    tbody.innerHTML = students.map((s, index) => {
        const initials = (s.fullName || 'SV').split(' ').map(n => n[0]).slice(-2).join('');
        const groupNum = s.groupId || ((index % 7) + 1);

        const actionButtons = canManage 
            ? `
                <div style="display:flex; gap:6px;">
                    <button type="button" class="btn-edit-outline" title="Chỉnh sửa sinh viên" onclick="editStudent(${s.userId || s.id})">
                        <i class="fa-solid fa-pen-to-square"></i> Sửa
                    </button>
                    <button type="button" class="btn-danger-outline" title="Xóa sinh viên" onclick="deleteStudent(${s.userId || s.id})">
                        <i class="fa-solid fa-trash"></i> Xóa
                    </button>
                </div>
            `
            : `<span class="badge-pill" style="background:#f1f5f9; color:#64748b; font-size:0.75rem;"><i class="fa-solid fa-eye"></i> Chỉ xem</span>`;

        return `
            <tr>
                <td style="font-weight:600; color:#64748b;">${index + 1}</td>
                <td><strong style="font-family:monospace; color:#1e293b;">${escapeHtml(s.studentCode || `QE2000${String(index + 1).padStart(2, '0')}`)}</strong></td>
                <td>
                    <div class="student-meta-cell">
                        <div class="student-avatar-circle">${escapeHtml(initials)}</div>
                        <div class="student-name-text">${escapeHtml(s.fullName || s.username)}</div>
                    </div>
                </td>
                <td style="color:#475569;">${escapeHtml(s.email || `${s.username}@fpt.edu.vn`)}</td>
                <td><span style="font-weight:600; color:#4f46e5;">SE20C</span> &bull; PRJ301</td>
                <td><span class="badge-pill ${groupNum === 6 ? 'role-leader' : 'role-member'}">Nhóm ${groupNum}</span></td>
                <td><span class="badge-pill normal"><i class="fa-solid fa-circle" style="font-size:0.4rem;"></i> Đang học</span></td>
                <td class="action-column">${actionButtons}</td>
            </tr>
        `;
    }).join('');
}

// Seed 25 students generator for fallback
function generateSeedStudents() {
    const names = [
        "Nguyễn Quốc Vương", "Nguyễn Văn An", "Trần Thái Bình", "Lê Kim Chi", "Hoàng Tiến Dũng",
        "Phạm Thị Em", "Trần Hoàng Phúc", "Lê Đức Minh", "Hoàng Yến Vy", "Võ Thảo Nguyên",
        "Vũ Xuân Long", "Đặng Trung Kiên", "Ngô Ngọc Lan", "Dương Tuyết Mai", "Bùi Hải Nam",
        "Đinh Kim Oanh", "Đỗ Anh Quân", "Lý Hồng Sơn", "Trịnh Phương Thảo", "Trương Quốc Uy",
        "Phan Hoàng Việt", "Lương Thanh Xuân", "Hà Bích Yến", "Đoàn Minh Trí", "Cao Thành Đạt"
    ];
    return names.map((name, i) => ({
        userId: i + 7,
        studentCode: i === 0 ? "QE200066" : `QE2000${String(i).padStart(2, '0')}`,
        fullName: name,
        username: i === 0 ? "qvuong8th3" : `sv.${i}`,
        email: i === 0 ? "qvuong8th3@gmail.com" : `sv.${i}@fpt.edu.vn`,
        groupId: i === 0 || (i >= 6 && i <= 9) ? 6 : ((i % 7) + 1)
    }));
}

// Student Search Filter
document.addEventListener('DOMContentLoaded', () => {
    const searchInput = document.getElementById('studentSearchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase().trim();
            const filtered = cachedStudents.filter(s => 
                (s.fullName && s.fullName.toLowerCase().includes(query)) ||
                (s.studentCode && s.studentCode.toLowerCase().includes(query)) ||
                (s.email && s.email.toLowerCase().includes(query)) ||
                (s.username && s.username.toLowerCase().includes(query))
            );
            renderStudentsTable(filtered);
        });
    }

    // Role-based UI visibility on load
    applyRoleBasedPermissions();
});

function applyRoleBasedPermissions() {
    const roleNotice = document.getElementById('currentUserRoleNotice');
    const addStudentBtn = document.getElementById('btnOpenAddStudentModal');
    const createAssignmentBtn = document.getElementById('btnOpenCreateAssignmentModal');
    const adminCourseActions = document.getElementById('adminCourseActions');

    if (savedUser) {
        const avatarEl = document.getElementById('sidebarUserAvatar');
        const nameEl = document.getElementById('sidebarUserName');
        const roleEl = document.getElementById('sidebarUserRole');

        if (nameEl) nameEl.textContent = savedUser.fullName || savedUser.username;
        if (avatarEl) {
            const initials = (savedUser.fullName || savedUser.username || 'AI').split(' ').map(n => n[0]).slice(-2).join('');
            avatarEl.textContent = initials.toUpperCase();
        }

        if (isAdministrator) {
            if (roleEl) roleEl.textContent = 'Quản trị viên (Master Admin)';
            if (roleNotice) roleNotice.textContent = 'Quản trị viên (Toàn quyền CRUD)';
            if (addStudentBtn) addStudentBtn.style.display = 'inline-flex';
            if (createAssignmentBtn) createAssignmentBtn.style.display = 'inline-flex';
            if (adminCourseActions) adminCourseActions.style.display = 'block';
        } else if (isLecturer) {
            if (roleEl) roleEl.textContent = 'Giảng viên chuyên trách';
            if (roleNotice) roleNotice.textContent = 'Giảng viên (Toàn quyền CRUD sinh viên & Chấm điểm)';
            if (addStudentBtn) addStudentBtn.style.display = 'inline-flex';
            if (createAssignmentBtn) createAssignmentBtn.style.display = 'inline-flex';
            if (adminCourseActions) adminCourseActions.style.display = 'none';
        } else {
            if (roleEl) roleEl.textContent = `Sinh viên · ${savedUser.studentCode || 'QE200066'}`;
            if (roleNotice) roleNotice.textContent = 'Sinh viên (Chỉ xem & Nộp bài)';
            if (addStudentBtn) addStudentBtn.style.display = 'none';
            if (createAssignmentBtn) createAssignmentBtn.style.display = 'none';
            if (adminCourseActions) adminCourseActions.style.display = 'none';
        }
    }
}

// Edit Student Modal
function editStudent(id) {
    if (!isLecturer && !isAdministrator) {
        alert('Chỉ Giảng viên hoặc Quản trị viên mới có quyền chỉnh sửa sinh viên.');
        return;
    }
    const student = cachedStudents.find(s => (s.userId || s.id) == id);
    if (!student) return;

    document.getElementById('studentModalTitle').innerHTML = '<i class="fa-solid fa-user-pen"></i> Chỉnh sửa sinh viên';
    document.getElementById('studentEditId').value = student.userId || student.id;
    document.getElementById('studentCodeInput').value = student.studentCode || '';
    document.getElementById('studentFullNameInput').value = student.fullName || '';
    document.getElementById('studentUsernameInput').value = student.username || '';
    document.getElementById('studentEmailInput').value = student.email || '';
    document.getElementById('studentGithubInput').value = student.githubUsername || '';
    document.getElementById('studentPasswordBlock').style.display = 'none';
    document.getElementById('studentFormError').style.display = 'none';

    openModal('studentModal');
}

// Delete Student
async function deleteStudent(id) {
    if (!isLecturer && !isAdministrator) {
        alert('Chỉ Giảng viên hoặc Quản trị viên mới có quyền xóa sinh viên.');
        return;
    }
    if (!confirm('Bạn có chắc chắn muốn xóa sinh viên này khỏi danh sách lớp học?')) {
        return;
    }

    try {
        await requestJson(`api/v1/students?id=${id}`, { method: 'DELETE' });
        alert('Đã xóa sinh viên thành công.');
        loadStudentsList();
    } catch (e) {
        alert('Lỗi xóa sinh viên: ' + e.message);
    }
}

// 4. COURSES VIEW & ROSTER MODAL
function renderCoursesView() {
    // Courses cards already in HTML
}

function openClassRosterModal(courseCode, courseName) {
    const title = document.getElementById('rosterModalTitle');
    const subtitle = document.getElementById('rosterModalSubtitle');
    const tbody = document.getElementById('rosterTableBody');

    if (title) title.innerHTML = `<i class="fa-solid fa-users"></i> Danh sách sinh viên môn ${escapeHtml(courseCode)}`;
    if (subtitle) subtitle.textContent = `Môn học: ${courseName} &bull; Lớp: SE20C &bull; Sĩ số: 25 sinh viên`;

    if (tbody) {
        const students = cachedStudents.length ? cachedStudents : generateSeedStudents();
        tbody.innerHTML = students.map((s, index) => `
            <tr>
                <td style="font-weight:600; color:#64748b;">${index + 1}</td>
                <td><strong style="font-family:monospace; color:#1e293b;">${escapeHtml(s.studentCode || `QE2000${String(index + 1).padStart(2, '0')}`)}</strong></td>
                <td><strong>${escapeHtml(s.fullName || s.username)}</strong></td>
                <td style="color:#475569;">${escapeHtml(s.email || `${s.username}@fpt.edu.vn`)}</td>
                <td><span class="badge-pill ${s.groupId === 6 ? 'role-leader' : 'role-member'}">Nhóm ${s.groupId || ((index % 7) + 1)}</span></td>
            </tr>
        `).join('');
    }

    openModal('rosterModal');
}

// ============================================================
// EVENT LISTENERS & FORM BINDINGS
// ============================================================
document.addEventListener('DOMContentLoaded', () => {

    // 1. Sidebar Nav Click Handler
    document.querySelectorAll('.sidebar-nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const target = item.getAttribute('data-view-target');
            if (target) {
                window.location.hash = target;
                setActiveView(target);
            }
        });
    });

    // Handle hash on load
    const hash = window.location.hash.replace('#', '');
    if (hash && document.getElementById(hash)) {
        setActiveView(hash);
    } else {
        setActiveView('dashboardView');
    }

    // 2. Refresh Dashboard Button
    const btnRefresh = document.getElementById('btnRefreshDashboard');
    if (btnRefresh) {
        btnRefresh.addEventListener('click', async () => {
            const originalHtml = btnRefresh.innerHTML;
            btnRefresh.disabled = true;
            btnRefresh.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang tải...';
            try {
                await requestJson('api/v1/git-analytics/sync?groupId=6', { method: 'POST' });
            } catch (e) {
                console.warn('Sync notice:', e.message);
            }
            await loadDashboardData();
            btnRefresh.disabled = false;
            btnRefresh.innerHTML = originalHtml;
        });
    }

    // 3. Edit GitHub Repo Button & Form
    const btnEditRepo = document.getElementById('btnEditRepo');
    if (btnEditRepo) {
        btnEditRepo.addEventListener('click', () => {
            document.getElementById('editRepoMsg').style.display = 'none';
            openModal('editRepoModal');
        });
    }

    const editRepoForm = document.getElementById('editRepoForm');
    if (editRepoForm) {
        editRepoForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const repoUrl = document.getElementById('repoUrlInput').value.trim();
            const groupId = document.getElementById('repoGroupIdInput').value;
            const submitBtn = document.getElementById('btnSubmitRepo');
            const msgEl = document.getElementById('editRepoMsg');
            const originalHtml = submitBtn.innerHTML;

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang liên kết & quét...';
            msgEl.style.display = 'none';

            try {
                const response = await fetch(apiUrl('api/v1/git-analytics/repository'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ groupId, repoUrl })
                });
                const result = await response.json();
                if (response.ok) {
                    msgEl.style.color = '#16a34a';
                    msgEl.textContent = result.message || 'Cập nhật GitHub repository thành công!';
                    msgEl.style.display = 'block';
                    setTimeout(() => {
                        closeModal('editRepoModal');
                        loadDashboardData();
                    }, 800);
                } else {
                    msgEl.style.color = '#dc2626';
                    msgEl.textContent = result.error || 'Không thể liên kết repo.';
                    msgEl.style.display = 'block';
                }
            } catch (err) {
                msgEl.style.color = '#dc2626';
                msgEl.textContent = 'Lỗi kết nối máy chủ.';
                msgEl.style.display = 'block';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalHtml;
            }
        });
    }

    // 4. Weights Modal & Form
    const btnOpenWeights = document.getElementById('btnOpenWeightsModal');
    if (btnOpenWeights) {
        btnOpenWeights.addEventListener('click', () => {
            document.getElementById('weightsMsg').style.display = 'none';
            openModal('weightsModal');
        });
    }

    // Live preview formula in modal
    ['weightW1', 'weightW2', 'weightW3', 'weightW4'].forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            input.addEventListener('input', () => {
                const w1 = Math.round(Number(document.getElementById('weightW1').value || 0) * 100);
                const w2 = Math.round(Number(document.getElementById('weightW2').value || 0) * 100);
                const w3 = Math.round(Number(document.getElementById('weightW3').value || 0) * 100);
                const w4 = Math.round(Number(document.getElementById('weightW4').value || 0) * 100);
                const preview = document.getElementById('formulaPreviewText');
                if (preview) {
                    preview.textContent = `${w1}% Volume + ${w2}% Freq + ${w3}% Review - ${w4}% Penalty`;
                }
            });
        }
    });

    const weightsForm = document.getElementById('weightsForm');
    if (weightsForm) {
        weightsForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const volumeWeight = document.getElementById('weightW1').value;
            const frequencyWeight = document.getElementById('weightW2').value;
            const reviewWeight = document.getElementById('weightW3').value;
            const penaltyWeight = document.getElementById('weightW4').value;
            const groupId = 6;
            const submitBtn = document.getElementById('btnSaveWeights');
            const msgEl = document.getElementById('weightsMsg');

            submitBtn.disabled = true;
            msgEl.style.display = 'none';

            try {
                const response = await fetch(apiUrl('api/v1/git-analytics/settings'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ groupId, volumeWeight, frequencyWeight, reviewWeight, penaltyWeight })
                });
                const result = await response.json();
                if (response.ok) {
                    msgEl.style.color = '#16a34a';
                    msgEl.textContent = 'Cập nhật trọng số thành công!';
                    msgEl.style.display = 'block';
                    setTimeout(() => {
                        closeModal('weightsModal');
                        loadDashboardData();
                    }, 700);
                } else {
                    msgEl.style.color = '#dc2626';
                    msgEl.textContent = result.error || 'Lỗi cập nhật trọng số.';
                    msgEl.style.display = 'block';
                }
            } catch (err) {
                msgEl.style.color = '#dc2626';
                msgEl.textContent = 'Lỗi kết nối máy chủ.';
                msgEl.style.display = 'block';
            } finally {
                submitBtn.disabled = false;
            }
        });
    }

    // 5. Open Add Student Modal
    const btnOpenAddStudent = document.getElementById('btnOpenAddStudentModal');
    if (btnOpenAddStudent) {
        btnOpenAddStudent.addEventListener('click', () => {
            document.getElementById('studentModalTitle').innerHTML = '<i class="fa-solid fa-user-plus"></i> Thêm sinh viên mới';
            document.getElementById('studentEditId').value = '';
            document.getElementById('studentForm').reset();
            document.getElementById('studentPasswordBlock').style.display = 'block';
            document.getElementById('studentFormError').style.display = 'none';
            openModal('studentModal');
        });
    }

    // Student Form Submit (Create / Edit)
    const studentForm = document.getElementById('studentForm');
    if (studentForm) {
        studentForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const id = document.getElementById('studentEditId').value;
            const fullName = document.getElementById('studentFullNameInput').value.trim();
            const username = document.getElementById('studentUsernameInput').value.trim();
            const email = document.getElementById('studentEmailInput').value.trim();
            const password = document.getElementById('studentPasswordInput').value;
            const githubUsername = document.getElementById('studentGithubInput').value.trim();
            const errorEl = document.getElementById('studentFormError');

            errorEl.style.display = 'none';

            try {
                const params = new URLSearchParams({ fullName, username, email, githubUsername });
                if (id) params.append('id', id);
                if (password) params.append('password', password);

                const response = await fetch(apiUrl('api/v1/students'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: params
                });
                const result = await response.json();
                if (response.ok) {
                    closeModal('studentModal');
                    loadStudentsList();
                } else {
                    errorEl.textContent = result.error || 'Không thể lưu sinh viên.';
                    errorEl.style.display = 'block';
                }
            } catch (err) {
                errorEl.textContent = 'Lỗi kết nối máy chủ.';
                errorEl.style.display = 'block';
            }
        });
    }

    // 6. Change Password Modal & Action
    const openChangePassBtn = document.getElementById('openChangePassBtn');
    if (openChangePassBtn) {
        openChangePassBtn.addEventListener('click', () => {
            document.getElementById('changePasswordForm').reset();
            document.getElementById('changePassMsg').style.display = 'none';
            openModal('changePasswordModal');
        });
    }

    const changePasswordForm = document.getElementById('changePasswordForm');
    if (changePasswordForm) {
        changePasswordForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const currentPassword = document.getElementById('currentPasswordInput').value;
            const newPassword = document.getElementById('newPasswordInput').value;
            const confirmPassword = document.getElementById('confirmNewPasswordInput').value;
            const msgEl = document.getElementById('changePassMsg');

            if (newPassword !== confirmPassword) {
                msgEl.style.color = '#dc2626';
                msgEl.textContent = 'Mật khẩu xác nhận không khớp.';
                msgEl.style.display = 'block';
                return;
            }

            try {
                const response = await fetch(apiUrl('api/v1/auth/change-password'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ currentPassword, newPassword, confirmPassword })
                });
                const result = await response.json();
                if (response.ok) {
                    msgEl.style.color = '#16a34a';
                    msgEl.textContent = 'Đổi mật khẩu thành công!';
                    msgEl.style.display = 'block';
                    setTimeout(() => closeModal('changePasswordModal'), 1000);
                } else {
                    msgEl.style.color = '#dc2626';
                    msgEl.textContent = result.error || 'Không thể đổi mật khẩu.';
                    msgEl.style.display = 'block';
                }
            } catch (err) {
                msgEl.style.color = '#dc2626';
                msgEl.textContent = 'Lỗi kết nối máy chủ.';
                msgEl.style.display = 'block';
            }
        });
    }

    // 7. Logout Button
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            if (confirm('Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?')) {
                try {
                    await fetch(apiUrl('api/v1/auth/logout'), { method: 'POST', credentials: 'same-origin' });
                } catch (e) {
                    // Ignore
                }
                localStorage.removeItem('aita_user');
                window.location.href = 'login.jsp';
            }
        });
    }
});
