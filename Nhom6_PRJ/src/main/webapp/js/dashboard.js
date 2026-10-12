const apiUrl = path => new URL(path, document.baseURI);
const escapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);
const currentUser = (() => {
    try {
        return JSON.parse(localStorage.getItem('aita_user') || 'null');
    } catch (error) {
        return null;
    }
})();
const isLecturer = Boolean(currentUser && currentUser.role === 'LECTURER');

async function requestJson(path, options) {
    const response = await fetch(apiUrl(path), { credentials: 'same-origin', ...options });
    if (response.status === 401) {
        window.location.href = 'login.jsp';
        throw new Error('Phiên đăng nhập đã hết hạn.');
    }
    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.error || 'Yêu cầu không thành công.');
    }
    return result;
}

function setActiveView(viewId) {
    if ((!isLecturer && viewId === 'studentsView') || (isLecturer && viewId === 'examDetailView')) {
        return;
    }
    document.querySelectorAll('.page-view').forEach(view => view.classList.toggle('hidden', view.id !== viewId));
    document.querySelectorAll('[data-view-target]').forEach(link => {
        link.parentElement.classList.toggle('active', link.dataset.viewTarget === viewId);
    });
    const titles = {
        dashboardView: ['Tổng quan lớp học', 'Theo dõi tiến độ và hoạt động học tập'],
        studentHomeView: ['Học tập của bạn', 'Chủ động theo dõi và hoàn thành nhiệm vụ'],
        studentsView: ['Quản lý sinh viên', 'Danh sách và thông tin sinh viên trong lớp'],
        examsView: [isLecturer ? 'Bài tập & đề thi' : 'Bài tập của bạn', isLecturer
            ? 'Giao bài, theo dõi bài nộp và quản lý đề kiểm tra'
            : 'Xem yêu cầu, nộp bài và hoàn thành bài kiểm tra'],
        examDetailView: ['Làm bài kiểm tra', 'Đọc kỹ câu hỏi trước khi nộp bài']
    };
    if (titles[viewId]) {
        document.getElementById('pageTitle').textContent = titles[viewId][0];
        document.getElementById('groupName').textContent = titles[viewId][1];
    }
    document.getElementById('headerEyebrow').textContent = viewId === 'studentHomeView' || viewId === 'examDetailView'
        ? 'KHÔNG GIAN SINH VIÊN' : (isLecturer ? 'KHÔNG GIAN GIẢNG DẠY' : 'KHÔNG GIAN SINH VIÊN');
    document.querySelector('.repo-info').classList.toggle('hidden', !isLecturer || viewId !== 'dashboardView');
    document.getElementById('syncBtn').classList.toggle('hidden', !isLecturer || viewId !== 'dashboardView');
    if (viewId === 'studentsView') {
        loadStudents();
    }
    if (viewId === 'examsView') {
        document.dispatchEvent(new Event('exams-view-open'));
    }
}

function renderDashboard(data, isLecturer) {
    const contributions = data.contributions || [];
    const colors = ['#5265e8', '#25a887', '#e7aa46', '#8872de', '#df6570', '#50a5c9'];
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
            plugins: { legend: { position: 'bottom', labels: { padding: 20, color: '#59637b' } } },
            cutout: '70%'
        }
    });

    new Chart(document.getElementById('timelineBarChart'), {
        type: 'line',
        data: data.timeline || { labels: [], datasets: [] },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'top', labels: { color: '#59637b' } } },
            scales: {
                y: { beginAtZero: true, grid: { color: '#edf0f5' } },
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
    Chart.defaults.color = '#7d879d';
    Chart.defaults.font.family = "'Inter', sans-serif";
    const savedUser = currentUser;
    document.getElementById('studentsNavItem').classList.toggle('hidden', !isLecturer);
    document.getElementById('addStudentBtn').classList.toggle('hidden', !isLecturer);
    document.getElementById('dashboardActionHeader').classList.toggle('hidden', !isLecturer);
    document.querySelector('[data-view-target="dashboardView"]').dataset.viewTarget = isLecturer
        ? 'dashboardView' : 'studentHomeView';
    document.querySelector('[data-view-target="dashboardView"]').setAttribute('href', isLecturer
        ? '#dashboardView' : '#studentHomeView');
    document.getElementById('dashboardNavLabel').textContent = isLecturer ? 'Tổng quan lớp' : 'Trang chủ';
    document.getElementById('examsNavLabel').textContent = 'Bài tập & đề thi';
    document.getElementById('currentUserRole').textContent = isLecturer ? 'Giảng viên' : 'Sinh viên';
    if (savedUser && savedUser.fullName) {
        document.getElementById('currentUserName').textContent = savedUser.fullName;
        document.getElementById('studentWelcomeName').textContent = savedUser.fullName.split(/\s+/).slice(-1)[0];
    }

    document.querySelectorAll('[data-view-target]').forEach(link => link.addEventListener('click', event => {
        event.preventDefault();
        setActiveView(link.dataset.viewTarget);
    }));
    document.querySelectorAll('[data-open-view]').forEach(button => button.addEventListener('click', () => {
        setActiveView(button.dataset.openView);
    }));
    document.addEventListener('click', event => {
        if (event.target.closest('[data-open-students]')) setActiveView('studentsView');
    });

    setActiveView(isLecturer ? 'dashboardView' : 'studentHomeView');
    try {
        const result = await requestJson('api/v1/git-analytics/dashboard?groupId=1');
        if (isLecturer) {
            renderDashboard(result.data, isLecturer);
        } else {
            document.getElementById('studentGroupName').textContent = result.data.groupName || 'Chưa phân nhóm';
        }
    } catch (error) {
        if (error.message !== 'Phiên đăng nhập đã hết hạn.' && isLecturer) {
            document.getElementById('studentTableBody').innerHTML = `<tr><td colspan="${isLecturer ? 6 : 5}" class="empty-state">${escapeHtml(error.message)}</td></tr>`;
        } else if (error.message !== 'Phiên đăng nhập đã hết hạn.') {
            document.getElementById('studentGroupName').textContent = 'Không thể tải';
        }
    }
    document.dispatchEvent(new Event('exams-view-open'));

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
            window.location.href = 'login.jsp';
        }
    });

    document.getElementById('syncBtn').addEventListener('click', () => {
        setActiveView('dashboardView');
        window.location.reload();
    });
});
