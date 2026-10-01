const apiUrl = path => new URL(path, document.baseURI);
const escapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

async function requestJson(path, options) {
    const response = await fetch(apiUrl(path), { credentials: 'same-origin', ...options });
    if (response.status === 401) {
        window.location.href = 'login.html';
        throw new Error('Phiên đăng nhập đã hết hạn.');
    }
    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.error || 'Yêu cầu không thành công.');
    }
    return result;
}

function setActiveView(viewId) {
    document.querySelectorAll('.page-view').forEach(view => view.classList.toggle('hidden', view.id !== viewId));
    document.querySelectorAll('[data-view-target]').forEach(link => {
        link.parentElement.classList.toggle('active', link.dataset.viewTarget === viewId);
    });
    if (viewId === 'studentsView') {
        loadStudents();
    }
}

function renderDashboard(data, isLecturer) {
    const contributions = data.contributions || [];
    const colors = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444', '#06b6d4'];
    new Chart(document.getElementById('contributionPieChart'), {
        type: 'doughnut',
        data: {
            labels: contributions.map(item => item.studentName),
            datasets: [{
                data: contributions.map(item => item.percentage),
                backgroundColor: colors,
                borderWidth: 0,
                hoverOffset: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom', labels: { padding: 20, color: '#f8fafc' } } },
            cutout: '70%'
        }
    });

    new Chart(document.getElementById('timelineBarChart'), {
        type: 'line',
        data: data.timeline || { labels: [], datasets: [] },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'top', labels: { color: '#f8fafc' } } },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.05)' } },
                x: { grid: { display: false } }
            }
        }
    });

    const tableBody = document.getElementById('studentTableBody');
    const alerts = [];
    tableBody.innerHTML = contributions.map(item => {
        const percentage = Number(item.percentage) || 0;
        const scoreClass = percentage < 15 ? 'score-low' : percentage < 25 ? 'score-medium' : 'score-high';
        if (percentage < 15) {
            alerts.push(`<p class="alert alert-warning"><i class="fa-solid fa-triangle-exclamation"></i> Cảnh báo: <strong>${escapeHtml(item.studentName)}</strong> có tỷ lệ đóng góp thấp (${percentage}%).</p>`);
        }
        return `<tr>
            <td><strong>${escapeHtml(item.studentName)}</strong></td>
            <td>${item.commits} commits</td>
            <td>${item.loc} dòng</td>
            <td>${item.regularity}/100</td>
            <td><span class="score-pill ${scoreClass}">${percentage}%</span></td>
            ${isLecturer ? '<td><button class="btn-outline" data-open-students>Quản lý <i class="fa-solid fa-chevron-right"></i></button></td>' : ''}
        </tr>`;
    }).join('');
    document.getElementById('alertContainer').innerHTML = alerts.join('');
    if (!contributions.length) {
        tableBody.innerHTML = `<tr><td colspan="${isLecturer ? 6 : 5}" class="empty-state">Chưa có dữ liệu đóng góp trong database.</td></tr>`;
    }
    document.getElementById('groupName').textContent = data.groupName || 'Chưa cấu hình nhóm';
    document.getElementById('repoUrl').textContent = data.repoUrl || 'Chưa cấu hình repository';
}

async function loadStudents() {
    const tbody = document.getElementById('studentsTableBody');
    const message = document.getElementById('studentsMessage');
    message.textContent = 'Đang tải dữ liệu...';
    try {
        const students = await requestJson('api/v1/students');
        tbody.innerHTML = students.length ? students.map(student => `<tr>
            <td><strong>${escapeHtml(student.fullName)}</strong></td>
            <td>${escapeHtml(student.username)}</td>
            <td>${escapeHtml(student.email)}</td>
            <td>${escapeHtml(student.githubUsername || '-')}</td>
            <td class="student-actions">
                <button class="btn-outline" data-edit-student="${student.userId}" title="Sửa"><i class="fa-solid fa-pen"></i></button>
                <button class="btn-outline btn-danger" data-delete-student="${student.userId}" title="Xóa"><i class="fa-solid fa-trash"></i></button>
            </td>
        </tr>`).join('') : '<tr><td colspan="5" class="empty-state">Chưa có sinh viên nào.</td></tr>';
        message.textContent = `${students.length} sinh viên`;
        tbody.dataset.students = JSON.stringify(students);
    } catch (error) {
        message.textContent = error.message;
        tbody.innerHTML = '';
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    Chart.defaults.color = '#94a3b8';
    Chart.defaults.font.family = "'Inter', sans-serif";
    const savedUser = JSON.parse(localStorage.getItem('aita_user') || 'null');
    const isLecturer = savedUser && savedUser.role === 'LECTURER';
    document.getElementById('studentsNavItem').classList.toggle('hidden', !isLecturer);
    document.getElementById('addStudentBtn').classList.toggle('hidden', !isLecturer);
    document.getElementById('dashboardActionHeader').classList.toggle('hidden', !isLecturer);
    if (savedUser && savedUser.fullName) {
        document.getElementById('currentUserName').textContent = savedUser.fullName;
    }

    document.querySelectorAll('[data-view-target]').forEach(link => link.addEventListener('click', event => {
        event.preventDefault();
        setActiveView(link.dataset.viewTarget);
    }));
    document.addEventListener('click', event => {
        if (event.target.closest('[data-open-students]')) setActiveView('studentsView');
    });

    try {
        const result = await requestJson('api/v1/git-analytics/dashboard?groupId=1');
        renderDashboard(result.data, isLecturer);
    } catch (error) {
        if (error.message !== 'Phiên đăng nhập đã hết hạn.') {
            document.getElementById('studentTableBody').innerHTML = `<tr><td colspan="${isLecturer ? 6 : 5}" class="empty-state">${escapeHtml(error.message)}</td></tr>`;
        }
    }

    const dialog = document.getElementById('studentDialog');
    const form = document.getElementById('studentForm');
    const formError = document.getElementById('studentFormError');
    const closeDialog = () => dialog.close();
    document.getElementById('addStudentBtn').addEventListener('click', () => {
        form.reset();
        document.getElementById('studentId').value = '';
        document.getElementById('studentDialogTitle').textContent = 'Thêm sinh viên';
        document.getElementById('passwordHint').textContent = '(bắt buộc)';
        document.getElementById('studentPassword').required = true;
        formError.classList.add('hidden');
        dialog.showModal();
    });
    document.getElementById('closeStudentDialog').addEventListener('click', closeDialog);

    document.getElementById('studentsTableBody').addEventListener('click', async event => {
        const editButton = event.target.closest('[data-edit-student]');
        const deleteButton = event.target.closest('[data-delete-student]');
        const students = JSON.parse(event.currentTarget.dataset.students || '[]');
        if (editButton) {
            const student = students.find(item => item.userId === Number(editButton.dataset.editStudent));
            if (!student) return;
            document.getElementById('studentId').value = student.userId;
            document.getElementById('studentUsername').value = student.username;
            document.getElementById('studentFullName').value = student.fullName;
            document.getElementById('studentEmail').value = student.email;
            document.getElementById('studentGithub').value = student.githubUsername || '';
            document.getElementById('studentPassword').value = '';
            document.getElementById('studentPassword').required = false;
            document.getElementById('passwordHint').textContent = '(để trống nếu không đổi)';
            document.getElementById('studentDialogTitle').textContent = 'Cập nhật sinh viên';
            formError.classList.add('hidden');
            dialog.showModal();
        }
        if (deleteButton && window.confirm('Xóa sinh viên này khỏi database?')) {
            try {
                await requestJson(`api/v1/students?id=${deleteButton.dataset.deleteStudent}`, { method: 'DELETE' });
                await loadStudents();
            } catch (error) {
                document.getElementById('studentsMessage').textContent = error.message;
            }
        }
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        formError.classList.add('hidden');
        const values = new URLSearchParams({
            id: document.getElementById('studentId').value,
            username: document.getElementById('studentUsername').value,
            fullName: document.getElementById('studentFullName').value,
            email: document.getElementById('studentEmail').value,
            githubUsername: document.getElementById('studentGithub').value,
            password: document.getElementById('studentPassword').value
        });
        try {
            await requestJson('api/v1/students', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: values
            });
            closeDialog();
            await loadStudents();
        } catch (error) {
            formError.textContent = error.message;
            formError.classList.remove('hidden');
        }
    });

    document.getElementById('logoutBtn').addEventListener('click', async () => {
        try {
            await requestJson('api/v1/auth/logout', { method: 'POST' });
        } finally {
            localStorage.removeItem('aita_user');
            window.location.href = 'login.html';
        }
    });

    document.getElementById('syncBtn').addEventListener('click', () => {
        setActiveView('dashboardView');
        window.location.reload();
    });
});
