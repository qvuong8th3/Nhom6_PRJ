const apiUrl = path => new URL(path, document.baseURI);
let activeCourse = null;
let activeClass = null;
let studentsForClasses = [];
let teachingCoursesPromise;
let savedUser = JSON.parse(localStorage.getItem('aita_user') || 'null');
let isLecturer = savedUser && savedUser.role === 'LECTURER';
let isAdministrator = savedUser && savedUser.role === 'ADMIN';
let isStudent = savedUser && savedUser.role === 'STUDENT';
let passwordChangeRequired = Boolean(savedUser && savedUser.mustChangePassword);
const escapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

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
    if ((!isLecturer && !isAdministrator && viewId === 'studentsView') || (isLecturer && viewId === 'examDetailView')) {
        return;
    }
    document.querySelectorAll('.page-view').forEach(view => view.classList.toggle('hidden', view.id !== viewId));
    document.querySelectorAll('[data-view-target]').forEach(link => {
        link.parentElement.classList.toggle('active', link.dataset.viewTarget === viewId);
    });
    const titles = {
        dashboardView: ['Tổng quan lớp học', 'Theo dõi tiến độ và hoạt động học tập'],
        studentHomeView: ['Học tập của bạn', 'Chủ động theo dõi và hoàn thành nhiệm vụ'],
        studentsView: ['Quản lý sinh viên', 'Danh sách và thông tin tài khoản sinh viên'],
        coursesView: ['Quản lý môn học & phân lớp', 'Cấu hình môn học, phân công giảng viên và tạo lớp'],
        classesView: ['Danh sách lớp học', 'Quản lý lớp và danh sách sinh viên theo lớp'],
        classStudentsView: ['Danh sách sinh viên lớp', 'Xem và cập nhật sinh viên trong lớp'],
        lecturersView: ['Tài khoản giảng viên', 'Cấp và quản lý tài khoản giảng viên'],
        examsView: [isLecturer ? 'Bài tập & đề thi' : 'Bài tập của bạn', isLecturer
            ? 'Giao bài, theo dõi bài nộp và quản lý đề kiểm tra'
            : 'Xem yêu cầu, nộp bài và hoàn thành bài kiểm tra'],
        examDetailView: ['Làm bài kiểm tra', 'Đọc kỹ câu hỏi trước khi nộp bài']
    };
    if (titles[viewId]) {
        document.getElementById('pageTitle').textContent = titles[viewId][0];
        document.getElementById('groupName').textContent = titles[viewId][1];
    }
    document.getElementById('headerEyebrow').textContent = isAdministrator
        ? 'KHÔNG GIAN QUẢN TRỊ VIÊN'
        : (viewId === 'studentHomeView' || viewId === 'examDetailView'
            ? 'KHÔNG GIAN SINH VIÊN' : (isLecturer ? 'KHÔNG GIAN GIẢNG DẠY' : 'KHÔNG GIAN SINH VIÊN'));
    document.querySelector('.repo-info').classList.toggle('hidden', !isLecturer || viewId !== 'dashboardView');
    document.getElementById('syncBtn').classList.toggle('hidden', (!isLecturer && !isAdministrator) || viewId !== 'dashboardView');
    if (viewId === 'studentsView') {
        loadStudents();
    }
    if (viewId === 'examsView') {
        document.dispatchEvent(new Event('exams-view-open'));
    }
    if (viewId === 'coursesView') {
        loadCourses();
    }
    if (viewId === 'classesView' && activeCourse) {
        loadClasses(activeCourse);
    }
    if (viewId === 'lecturersView') {
        loadLecturers();
    }
}

function renderDashboard(data, isLecturer) {
    const contributions = data.contributions || [];
    const colors = ['#5265e8', '#25a887', '#e7aa46', '#8872de', '#df6570', '#50a5c9'];
    const getSuggestedPercentage = item => {
        const value = Number(item.suggestedPercentage);
        return Number.isFinite(value) ? value : (Number(item.percentage) || 0);
    };
    new Chart(document.getElementById('contributionPieChart'), {
        type: 'doughnut',
        data: {
            labels: contributions.map(item => item.studentName),
            datasets: [{
                data: contributions.map(getSuggestedPercentage),
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
    const averageContribution = contributions.length ? 100 / contributions.length : 0;
    const lowContributionThreshold = averageContribution * 0.5;
    const hasGitActivity = contributions.some(item =>
        Number(item.commits) > 0 || Number(item.loc) > 0 || Number(item.regularity) > 0);
    let lowestSuggestedPercentage = null;
    let highestSuggestedPercentage = null;
    let lowContributionCount = 0;
    tableBody.innerHTML = contributions.map(item => {
        const percentage = getSuggestedPercentage(item);
        const savedPercentage = Number(item.percentage) || 0;
        lowestSuggestedPercentage = lowestSuggestedPercentage === null
            ? percentage : Math.min(lowestSuggestedPercentage, percentage);
        highestSuggestedPercentage = highestSuggestedPercentage === null
            ? percentage : Math.max(highestSuggestedPercentage, percentage);
        const needsReview = hasGitActivity && averageContribution > 0 && percentage < lowContributionThreshold;
        const scoreClass = needsReview ? 'score-low' : percentage < averageContribution ? 'score-medium' : 'score-high';
        if (needsReview) {
            lowContributionCount++;
            alerts.push(`<p class="alert alert-warning"><i class="fa-solid fa-triangle-exclamation"></i> Gợi ý xem xét: <strong>${escapeHtml(item.studentName)}</strong> có điểm đề xuất thấp hơn một nửa mức bình quân nhóm (${percentage.toFixed(2)}%).</p>`);
        }
        return `<tr>
            <td><strong>${escapeHtml(item.studentName)}</strong></td>
            <td>${item.commits} commits</td>
            <td>${item.loc} dòng</td>
            <td>${item.regularity}/100</td>
            <td><span class="score-pill score-medium">${savedPercentage.toFixed(2)}%</span></td>
            <td><span class="score-pill ${scoreClass}">${percentage.toFixed(2)}%</span>
                <small class="assessor-status">${needsReview ? 'Cần xem xét' : 'Tham khảo'}</small></td>
            ${isLecturer ? '<td><button class="btn-outline" data-open-students>Quản lý <i class="fa-solid fa-chevron-right"></i></button></td>' : ''}
        </tr>`;
    }).join('');
    document.getElementById('alertContainer').innerHTML = alerts.join('');
    document.getElementById('contributionSpread').textContent = lowestSuggestedPercentage === null
        ? 'Chưa có dữ liệu' : `${(highestSuggestedPercentage - lowestSuggestedPercentage).toFixed(2)} điểm %`;
    document.getElementById('lowContributionCount').textContent = `${lowContributionCount} / ${contributions.length} thành viên`;
    if (!contributions.length) {
        tableBody.innerHTML = `<tr><td colspan="${isLecturer ? 7 : 6}" class="empty-state">Chưa có dữ liệu đóng góp trong database.</td></tr>`;
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

async function loadCourses() {
    const tbody = document.getElementById('coursesTableBody');
    const message = document.getElementById('coursesMessage');
    message.textContent = 'Đang tải dữ liệu...';
    try {
        const courses = await requestJson('api/v1/courses');
        const isAdministrator = JSON.parse(localStorage.getItem('aita_user') || 'null')?.role === 'ADMIN';
        tbody.innerHTML = courses.length ? courses.map(course => `<tr>
            <td><strong>${escapeHtml(course.courseCode)}</strong></td>
            <td>${escapeHtml(course.courseName)}</td>
            <td>${escapeHtml(course.semester || '-')}</td>
            <td>${escapeHtml((course.lecturers || []).map(lecturer => lecturer.fullName).join(', ') || '-')}</td>
            <td>${course.classCount}</td>
            <td><button class="btn-outline" data-open-classes="${course.courseId}"><i class="fa-solid fa-users-rectangle"></i> Quản lý lớp</button></td>
            ${isAdministrator ? `<td class="student-actions">
                <button class="btn-outline" data-edit-course="${course.courseId}" title="Sửa"><i class="fa-solid fa-pen"></i></button>
                <button class="btn-outline btn-danger" data-delete-course="${course.courseId}" title="Xóa"><i class="fa-solid fa-trash"></i></button>
            </td>` : ''}
        </tr>`).join('') : `<tr><td colspan="${isAdministrator ? 7 : 6}" class="empty-state">${isAdministrator ? 'Chưa có môn học nào.' : 'Bạn chưa được phân công môn học nào.'}</td></tr>`;
        document.getElementById('courseActionHeader').classList.toggle('hidden', !isAdministrator);
        message.textContent = `${courses.length} môn học`;
        tbody.dataset.courses = JSON.stringify(courses);
    } catch (error) {
        message.textContent = error.message;
        tbody.innerHTML = '';
    }
}

async function loadClasses(course) {
    activeCourse = course;
    document.getElementById('classesCourseName').textContent = `${course.courseCode} - ${course.courseName}`;
    const tbody = document.getElementById('classesTableBody');
    const message = document.getElementById('classesMessage');
    message.textContent = 'Đang tải dữ liệu...';
    try {
        const classes = await requestJson(`api/v1/courses/classes?courseId=${encodeURIComponent(course.courseId)}`);
        const isAdministrator = JSON.parse(localStorage.getItem('aita_user') || 'null')?.role === 'ADMIN';
        tbody.innerHTML = classes.length ? classes.map(courseClass => `<tr>
            <td><strong>${escapeHtml(courseClass.className)}</strong></td>
            <td>${courseClass.students.length}</td>
            <td class="student-actions">
                <button class="btn-outline" data-open-class-students="${courseClass.classId}">
                    <i class="fa-solid fa-users"></i> Sinh viên lớp
                </button>
                ${isAdministrator ? `<button class="btn-outline" data-edit-class="${courseClass.classId}" title="Sửa danh sách sinh viên"><i class="fa-solid fa-user-pen"></i></button>
                <button class="btn-outline btn-danger" data-delete-class="${courseClass.classId}" title="Xóa lớp"><i class="fa-solid fa-trash"></i></button>` : ''}
            </td>
        </tr>`).join('') : '<tr><td colspan="3" class="empty-state">Môn học này chưa có lớp.</td></tr>';
        document.getElementById('classActionHeader').classList.remove('hidden');
        message.textContent = `${classes.length} lớp`;
        tbody.dataset.classes = JSON.stringify(classes);
    } catch (error) {
        message.textContent = error.message;
        tbody.innerHTML = '';
    }
}

async function loadLecturers() {
    const tbody = document.getElementById('lecturersTableBody');
    const message = document.getElementById('lecturerFormMessage');
    try {
        const lecturers = await requestJson('api/v1/admin/lecturers');
        tbody.innerHTML = lecturers.length ? lecturers.map(lecturer => `<tr>
            <td><strong>${escapeHtml(lecturer.fullName)}</strong></td>
            <td>${escapeHtml(lecturer.username)}</td>
            <td>${escapeHtml(lecturer.email)}</td>
        </tr>`).join('') : '<tr><td colspan="3" class="empty-state">Chưa có tài khoản giảng viên.</td></tr>';
        message.textContent = `${lecturers.length} giảng viên`;
    } catch (error) {
        message.textContent = error.message;
        tbody.innerHTML = '';
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    Chart.defaults.color = '#94a3b8';
    Chart.defaults.font.family = "'Inter', sans-serif";

    if (document.getElementById('dashboardNavItem')) {
        document.getElementById('dashboardNavItem').classList.toggle('hidden', isAdministrator);
    }
    if (document.getElementById('studentsNavItem')) {
        document.getElementById('studentsNavItem').classList.toggle('hidden', !isLecturer && !isAdministrator);
    }
    if (document.getElementById('examsNavItem')) {
        document.getElementById('examsNavItem').classList.toggle('hidden', isAdministrator);
    }
    if (document.getElementById('coursesNavItem')) {
        document.getElementById('coursesNavItem').classList.toggle('hidden', !isLecturer && !isAdministrator);
    }
    if (document.getElementById('lecturersNavItem')) {
        document.getElementById('lecturersNavItem').classList.toggle('hidden', !isAdministrator);
    }
    if (document.getElementById('addCourseBtn')) {
        document.getElementById('addCourseBtn').classList.toggle('hidden', !isAdministrator);
    }
    if (document.getElementById('addClassBtn')) {
        document.getElementById('addClassBtn').classList.toggle('hidden', !isAdministrator);
    }
    if (document.getElementById('addStudentBtn')) {
        document.getElementById('addStudentBtn').classList.toggle('hidden', !isAdministrator);
    }
    if (document.getElementById('dashboardActionHeader')) {
        document.getElementById('dashboardActionHeader').classList.toggle('hidden', !isLecturer);
    }
    if (savedUser && savedUser.fullName) {
        document.getElementById('currentUserName').textContent = savedUser.fullName;
        if (document.getElementById('currentUserRole')) {
            document.getElementById('currentUserRole').textContent = isAdministrator
                ? 'Quản trị viên' : (isLecturer ? 'Giảng viên' : 'Sinh viên');
        }
        if (document.getElementById('studentWelcomeName')) {
            document.getElementById('studentWelcomeName').textContent = savedUser.fullName.split(/\s+/).slice(-1)[0];
        }
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

    if (isAdministrator) {
        setActiveView('coursesView');
        await loadCourses();
    } else if (isStudent) {
        setActiveView('studentHomeView');
    } else {
        try {
            const result = await requestJson('api/v1/git-analytics/dashboard?groupId=1');
            renderDashboard(result.data, isLecturer);
        } catch (error) {
            if (error.message !== 'Phiên đăng nhập đã hết hạn.') {
                document.getElementById('studentTableBody').innerHTML = `<tr><td colspan="${isLecturer ? 6 : 5}" class="empty-state">${escapeHtml(error.message)}</td></tr>`;
            }
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

    const courseDialog = document.getElementById('courseDialog');
    const courseForm = document.getElementById('courseForm');
    const courseFormError = document.getElementById('courseFormError');
    const courseLecturers = document.getElementById('courseLecturers');
    if (isAdministrator && !passwordChangeRequired) {
        try {
            const lecturers = await requestJson('api/v1/courses/lecturers');
            courseLecturers.innerHTML = lecturers.map(lecturer =>
                `<option value="${lecturer.userId}">${escapeHtml(lecturer.fullName)} (${escapeHtml(lecturer.email)})</option>`
            ).join('');
        } catch (error) {
            document.getElementById('coursesMessage').textContent = error.message;
        }
    }
    document.getElementById('addCourseBtn').addEventListener('click', () => {
        courseForm.reset();
        document.getElementById('courseId').value = '';
        document.getElementById('courseDialogTitle').textContent = 'Thêm môn học';
        courseFormError.classList.add('hidden');
        courseDialog.showModal();
    });
    document.getElementById('closeCourseDialog').addEventListener('click', () => courseDialog.close());
    document.getElementById('coursesTableBody').addEventListener('click', async event => {
        const classesButton = event.target.closest('[data-open-classes]');
        const editButton = event.target.closest('[data-edit-course]');
        const deleteButton = event.target.closest('[data-delete-course]');
        const courses = JSON.parse(event.currentTarget.dataset.courses || '[]');
        if (classesButton) {
            activeCourse = courses.find(item => item.courseId === Number(classesButton.dataset.openClasses));
            if (activeCourse) setActiveView('classesView');
        }
        if (editButton) {
            const course = courses.find(item => item.courseId === Number(editButton.dataset.editCourse));
            if (!course) return;
            document.getElementById('courseId').value = course.courseId;
            document.getElementById('courseCode').value = course.courseCode;
            document.getElementById('courseName').value = course.courseName;
            document.getElementById('courseSemester').value = course.semester || '';
            const assignedIds = new Set((course.lecturers || []).map(lecturer => String(lecturer.userId)));
            Array.from(courseLecturers.options).forEach(option => {
                option.selected = assignedIds.has(option.value);
            });
            document.getElementById('courseDialogTitle').textContent = 'Cập nhật môn học';
            courseFormError.classList.add('hidden');
            courseDialog.showModal();
        }
        if (deleteButton && window.confirm('Xóa môn học này và các phân công giảng viên?')) {
            try {
                await requestJson(`api/v1/courses/${deleteButton.dataset.deleteCourse}`, { method: 'DELETE' });
                await loadCourses();
            } catch (error) {
                document.getElementById('coursesMessage').textContent = error.message;
            }
        }
    });
    courseForm.addEventListener('submit', async event => {
        event.preventDefault();
        courseFormError.classList.add('hidden');
        try {
            await requestJson('api/v1/courses', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams(new FormData(courseForm))
            });
            courseDialog.close();
            await loadCourses();
        } catch (error) {
            courseFormError.textContent = error.message;
            courseFormError.classList.remove('hidden');
        }
    });

    const classDialog = document.getElementById('classDialog');
    const classForm = document.getElementById('classForm');
    const classFormError = document.getElementById('classFormError');
    const classStudents = document.getElementById('classStudents');
    document.getElementById('addClassBtn').addEventListener('click', async () => {
        classForm.reset();
        document.getElementById('classId').value = '';
        document.getElementById('className').readOnly = false;
        document.getElementById('classNameLabel').classList.remove('hidden');
        document.getElementById('classDialogTitle').textContent = 'Thêm lớp học';
        classFormError.classList.add('hidden');
        try {
            studentsForClasses = await requestJson('api/v1/courses/students');
            classStudents.innerHTML = studentsForClasses.map(student =>
                `<option value="${student.userId}">${escapeHtml(student.fullName)} (${escapeHtml(student.username)})</option>`
            ).join('');
            classDialog.showModal();
        } catch (error) {
            document.getElementById('classesMessage').textContent = error.message;
        }
    });
    function renderClassRoster(courseClass) {
        activeClass = courseClass;
        document.getElementById('classStudentsCourseName').textContent =
            `${activeCourse.courseCode} - ${activeCourse.courseName} / Lớp ${courseClass.className}`;
        const tbody = document.getElementById('classStudentsTableBody');
        tbody.innerHTML = courseClass.students.length ? courseClass.students.map(student => `<tr>
            <td><strong>${escapeHtml(student.fullName)}</strong></td>
            <td>${escapeHtml(student.username)}</td>
            <td>${escapeHtml(student.email)}</td>
        </tr>`).join('') : '<tr><td colspan="3" class="empty-state">Lớp này chưa có sinh viên.</td></tr>';
        document.getElementById('classStudentsMessage').textContent =
            `${courseClass.students.length} sinh viên thuộc lớp này`;
        document.getElementById('editClassRosterBtn').classList.toggle('hidden', !isAdministrator);
        setActiveView('classStudentsView');
    }

    async function openClassEditor(courseClass) {
        try {
            studentsForClasses = await requestJson('api/v1/courses/students');
            classStudents.innerHTML = studentsForClasses.map(student =>
                `<option value="${student.userId}">${escapeHtml(student.fullName)} (${escapeHtml(student.username)})</option>`
            ).join('');
            document.getElementById('classId').value = courseClass.classId;
            document.getElementById('className').value = courseClass.className;
            document.getElementById('className').readOnly = true;
            document.getElementById('classNameLabel').classList.remove('hidden');
            document.getElementById('classDialogTitle').textContent = 'Cập nhật danh sách sinh viên lớp';
            const enrolledIds = new Set(courseClass.students.map(student => String(student.userId)));
            Array.from(classStudents.options).forEach(option => {
                option.selected = enrolledIds.has(option.value);
            });
            classFormError.classList.add('hidden');
            classDialog.showModal();
        } catch (error) {
            document.getElementById('classesMessage').textContent = error.message;
        }
    }

    document.getElementById('closeClassDialog').addEventListener('click', () => classDialog.close());
    document.getElementById('backToCourses').addEventListener('click', () => setActiveView('coursesView'));
    document.getElementById('backToClasses').addEventListener('click', () => setActiveView('classesView'));
    document.getElementById('editClassRosterBtn').addEventListener('click', async () => {
        if (isAdministrator && activeClass) {
            await openClassEditor(activeClass);
        }
    });
    document.getElementById('classesTableBody').addEventListener('click', async event => {
        const rosterButton = event.target.closest('[data-open-class-students]');
        const editButton = event.target.closest('[data-edit-class]');
        const deleteButton = event.target.closest('[data-delete-class]');
        const classes = JSON.parse(event.currentTarget.dataset.classes || '[]');
        if (rosterButton) {
            const courseClass = classes.find(item => item.classId === Number(rosterButton.dataset.openClassStudents));
            if (courseClass) {
                renderClassRoster(courseClass);
            }
        }
        if (editButton) {
            const courseClass = classes.find(item => item.classId === Number(editButton.dataset.editClass));
            if (!courseClass) return;
            await openClassEditor(courseClass);
        }
        if (deleteButton && window.confirm('Xóa lớp này cùng danh sách sinh viên đã ghi danh?')) {
            try {
                await requestJson(`api/v1/courses/classes/${deleteButton.dataset.deleteClass}`, { method: 'DELETE' });
                await loadClasses(activeCourse);
            } catch (error) {
                document.getElementById('classesMessage').textContent = error.message;
            }
        }
    });
    classForm.addEventListener('submit', async event => {
        event.preventDefault();
        classFormError.classList.add('hidden');
        const formData = new FormData(classForm);
        const classId = document.getElementById('classId').value;
        const refreshRoster = Boolean(classId && !document.getElementById('classStudentsView').classList.contains('hidden'));
        try {
            if (classId) {
                await requestJson(`api/v1/courses/classes/${classId}/students`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams(formData)
                });
            } else {
                formData.append('courseId', String(activeCourse.courseId));
                await requestJson('api/v1/courses/classes', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams(formData)
                });
            }
            classDialog.close();
            await loadClasses(activeCourse);
            await loadCourses();
            if (refreshRoster) {
                const refreshedClass = JSON.parse(document.getElementById('classesTableBody').dataset.classes || '[]')
                    .find(item => item.classId === Number(classId));
                if (refreshedClass) {
                    renderClassRoster(refreshedClass);
                }
            }
        } catch (error) {
            classFormError.textContent = error.message;
            classFormError.classList.remove('hidden');
        }
    });

    const lecturerForm = document.getElementById('lecturerForm');
    const lecturerFormError = document.getElementById('lecturerFormError');
    lecturerForm.addEventListener('submit', async event => {
        event.preventDefault();
        lecturerFormError.classList.add('hidden');
        try {
            await requestJson('api/v1/admin/lecturers', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams(new FormData(lecturerForm))
            });
            lecturerForm.reset();
            await loadLecturers();
            document.getElementById('lecturerFormMessage').textContent =
                'Đã cấp tài khoản. Giảng viên cần đổi mật khẩu khi đăng nhập lần đầu.';
            const lecturers = await requestJson('api/v1/courses/lecturers');
            courseLecturers.innerHTML = lecturers.map(lecturer =>
                `<option value="${lecturer.userId}">${escapeHtml(lecturer.fullName)} (${escapeHtml(lecturer.email)})</option>`
            ).join('');
        } catch (error) {
            lecturerFormError.textContent = error.message;
            lecturerFormError.classList.remove('hidden');
        }
    });

    const passwordDialog = document.getElementById('passwordDialog');
    const passwordForm = document.getElementById('passwordForm');
    const passwordFormError = document.getElementById('passwordFormError');
    const passwordFormSuccess = document.getElementById('passwordFormSuccess');
    document.getElementById('changePasswordBtn').addEventListener('click', () => {
        passwordForm.reset();
        passwordFormError.classList.add('hidden');
        passwordFormSuccess.classList.add('hidden');
        passwordDialog.showModal();
    });
    document.getElementById('closePasswordDialog').addEventListener('click', () => passwordDialog.close());
    passwordForm.addEventListener('submit', async event => {
        event.preventDefault();
        passwordFormError.classList.add('hidden');
        passwordFormSuccess.classList.add('hidden');
        try {
            await requestJson('api/v1/auth/change-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams(new FormData(passwordForm))
            });
            passwordForm.reset();
            passwordChangeRequired = false;
            if (savedUser) {
                savedUser.mustChangePassword = false;
                localStorage.setItem('aita_user', JSON.stringify(savedUser));
            }
            passwordFormSuccess.textContent = 'Đổi mật khẩu thành công.';
            passwordFormSuccess.classList.remove('hidden');
            document.getElementById('closePasswordDialog').classList.remove('hidden');
            document.getElementById('dashboardNavItem').classList.toggle('hidden', isAdministrator);
            document.getElementById('studentsNavItem').classList.toggle('hidden', !isAdministrator);
            document.getElementById('examsNavItem').classList.toggle('hidden', isAdministrator);
            document.getElementById('coursesNavItem').classList.toggle('hidden', !isLecturer && !isAdministrator);
            document.getElementById('lecturersNavItem').classList.toggle('hidden', !isAdministrator);
            if (isAdministrator) {
                setActiveView('coursesView');
                await requestJson('api/v1/courses/lecturers').then(lecturers => {
                    courseLecturers.innerHTML = lecturers.map(lecturer =>
                        `<option value="${lecturer.userId}">${escapeHtml(lecturer.fullName)} (${escapeHtml(lecturer.email)})</option>`
                    ).join('');
                });
                await loadCourses();
            } else if (isLecturer) {
                setActiveView('coursesView');
            } else if (isStudent) {
                setActiveView('examsView');
            } else {
                const result = await requestJson('api/v1/git-analytics/dashboard?groupId=1');
                renderDashboard(result.data, isLecturer);
            }
        } catch (error) {
            passwordFormError.textContent = error.message;
            passwordFormError.classList.remove('hidden');
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
    if (passwordChangeRequired) {
        document.getElementById('closePasswordDialog').classList.add('hidden');
        passwordDialog.showModal();
    }
});
