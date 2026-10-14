const assignmentEscapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

const assignmentDate = value => value ? new Date(value).toLocaleString('vi-VN') : '-';
const assignmentDateTimeInput = value => {
    if (!value) return '';
    const date = new Date(value);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};
const assignmentDueAtPayload = value => value ? new Date(value).toISOString() : '';

async function loadAssignments() {
    const body = document.getElementById('assignmentsTableBody');
    const message = document.getElementById('assignmentMessage');
    const error = document.getElementById('assignmentError');
    const user = JSON.parse(localStorage.getItem('aita_user') || 'null');
    const lecturer = user && user.role === 'LECTURER';
    const student = user && user.role === 'STUDENT' && !user.mustChangePassword;
    message.textContent = 'Đang tải bài tập...';
    error.classList.add('hidden');
    try {
        const assignments = await requestJson('api/v1/assignments');
        if (!lecturer) {
            document.getElementById('studentAssignmentCount').textContent =
                assignments.filter(assignment => !assignment.submittedAt).length;
        }
        body.innerHTML = assignments.length ? assignments.map(assignment => `<tr>
            <td><strong>${assignmentEscapeHtml(assignment.courseCode || '')}</strong>${assignment.courseCode ? '<br>' : ''}<span class="text-muted">${assignmentEscapeHtml(assignment.courseName || 'Chưa gắn môn học')}</span></td>
            <td><strong class="assignment-title">${assignmentEscapeHtml(assignment.title)}</strong>
                ${assignment.description ? `<br><span class="text-muted">${assignmentEscapeHtml(assignment.description)}</span>` : ''}</td>
            <td>${assignment.promptFileName
                ? `<a class="assignment-download" href="api/v1/assignments/${assignment.assignmentId}/file"><i class="fa-solid fa-download"></i> ${assignmentEscapeHtml(assignment.promptFileName)}</a>`
                : 'Không có file đính kèm'}</td>
            <td>${lecturer ? `${assignment.submissionCount} bài` : (assignment.submittedAt
                ? `Đã nộp<br><span class="text-muted">${assignmentDate(assignment.submittedAt)}</span>`
                : 'Chưa nộp')}</td>
            <td>${lecturer
                ? `<form class="assignment-deadline-form" data-deadline-assignment="${assignment.assignmentId}">
                       <input type="datetime-local" name="dueAt" aria-label="Hạn nộp: ${assignmentEscapeHtml(assignment.title)}"
                           value="${assignmentDateTimeInput(assignment.dueAt)}">
                       <button class="btn-outline" type="submit">Lưu hạn</button>
                   </form>`
                : assignment.dueAt
                    ? `${assignmentDate(assignment.dueAt)}${new Date(assignment.dueAt).getTime() < Date.now()
                        ? '<br><span class="text-muted">Đã hết hạn</span>' : ''}`
                    : 'Không giới hạn'}</td>
            <td>${assignmentDate(assignment.createdAt)}</td>
            <td class="assignment-actions">${lecturer
                ? `<button class="btn-outline" type="button" data-assignment-submissions="${assignment.assignmentId}">
                       <i class="fa-solid fa-users"></i> Xem bài nộp
                   </button>`
                : (assignment.dueAt && new Date(assignment.dueAt).getTime() < Date.now()
                    ? '<span class="text-muted">Đã khóa nộp bài</span>'
                    : `<form class="assignment-submit-form" data-submit-assignment="${assignment.assignmentId}">
                       <label class="assignment-file-label">File bài làm<input type="file" name="file" accept=".pdf,.doc,.docx,.odt,.rtf,.txt" required></label>
                       <button class="btn-outline" type="submit"><i class="fa-solid fa-cloud-arrow-up"></i> ${assignment.submittedAt ? 'Nộp lại' : 'Nộp bài'}</button>
                   </form>`)}</td>
        </tr>`).join('') : '<tr><td colspan="7" class="empty-state">Chưa có bài tập trong các môn học của bạn.</td></tr>';
        message.textContent = `${assignments.length} bài tập · chấp nhận PDF, DOC, DOCX, ODT, RTF, TXT tối đa 15 MB`;
    } catch (requestError) {
        message.textContent = '';
        error.textContent = requestError.message;
        error.classList.remove('hidden');
        body.innerHTML = '';
    }
}

async function loadAssignmentSubmissions(assignmentId, title) {
    try {
        const submissions = await requestJson(`api/v1/assignments/${assignmentId}/submissions`);
        document.getElementById('assignmentSubmissionsTitle').textContent = `Bài đã nộp: ${title}`;
        document.getElementById('assignmentSubmissionsBody').innerHTML = submissions.length
            ? submissions.map(submission => `<tr>
                <td><strong>${assignmentEscapeHtml(submission.fullName)}</strong><br><span class="text-muted">${assignmentEscapeHtml(submission.username)}</span></td>
                <td>${assignmentEscapeHtml(submission.email)}</td>
                <td>${assignmentEscapeHtml(submission.fileName)}</td>
                <td>${assignmentDate(submission.submittedAt)}</td>
                <td><a class="btn-outline assignment-download" href="api/v1/assignments/${assignmentId}/submissions/${submission.studentId}/file">
                    <i class="fa-solid fa-download"></i> Tải xuống
                </a></td>
            </tr>`).join('')
            : '<tr><td colspan="5" class="empty-state">Chưa có sinh viên nộp bài.</td></tr>';
        document.getElementById('assignmentSubmissionsPanel').classList.remove('hidden');
    } catch (error) {
        window.alert(error.message);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const user = JSON.parse(localStorage.getItem('aita_user') || 'null');
    const lecturer = user && user.role === 'LECTURER';
    const student = user && user.role === 'STUDENT' && !user.mustChangePassword;
    const createToggle = document.getElementById('assignmentCreateToggle');
    const createForm = document.getElementById('assignmentCreateForm');
    const createError = document.getElementById('assignmentCreateError');
    createToggle.classList.toggle('hidden', !lecturer);
    document.addEventListener('exams-view-open', loadAssignments);

    if (lecturer) {
        populateTeachingCourseSelect('assignmentCourseId').catch(error => {
            if (createError) {
                createError.textContent = error.message;
                createError.classList.remove('hidden');
            }
        });
    } else if (student) {
        loadAssignments();
    }

    createToggle.addEventListener('click', async () => {
        createForm.classList.toggle('hidden');
        if (!createForm.classList.contains('hidden') && lecturer) {
            try {
                const courses = await populateTeachingCourseSelect('assignmentCourseId');
                const submitButton = createForm.querySelector('button[type="submit"]');
                if (!courses || courses.length === 0) {
                    createError.textContent = 'Bạn chưa được phân công môn học nào. Vui lòng liên hệ Quản trị viên để được phân môn trước khi giao bài tập.';
                    createError.classList.remove('hidden');
                    if (submitButton) submitButton.disabled = true;
                } else {
                    createError.classList.add('hidden');
                    if (submitButton) submitButton.disabled = false;
                }
            } catch (error) {
                createError.textContent = error.message;
                createError.classList.remove('hidden');
            }
        }
    });

    document.getElementById('closeAssignmentSubmissions').addEventListener('click', () => {
        document.getElementById('assignmentSubmissionsPanel').classList.add('hidden');
    });

    createForm.addEventListener('submit', async event => {
        event.preventDefault();
        createError.classList.add('hidden');
        const courseSelect = document.getElementById('assignmentCourseId');
        if (!courseSelect || !courseSelect.value) {
            createError.textContent = 'Vui lòng chọn môn học bạn được phân công để giao bài tập.';
            createError.classList.remove('hidden');
            return;
        }
        const button = createForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const formData = new FormData(createForm);
            formData.set('dueAt', assignmentDueAtPayload(formData.get('dueAt')));
            await requestJson('api/v1/assignments', { method: 'POST', body: formData });
            createForm.reset();
            createForm.classList.add('hidden');
            await loadAssignments();
        } catch (error) {
            createError.textContent = error.message;
            createError.classList.remove('hidden');
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('assignmentsTableBody').addEventListener('click', event => {
        const button = event.target.closest('[data-assignment-submissions]');
        if (!button) {
            return;
        }
        const row = button.closest('tr');
        const titleEl = row.querySelector('.assignment-title') || row.querySelector('td strong');
        const title = titleEl ? titleEl.textContent : '';
        loadAssignmentSubmissions(button.dataset.assignmentSubmissions, title);
    });

    document.getElementById('assignmentsTableBody').addEventListener('submit', async event => {
        const form = event.target.closest('[data-deadline-assignment]');
        if (!form) return;
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const message = document.getElementById('assignmentError');
        message.classList.add('hidden');
        button.disabled = true;
        try {
            const dueAt = assignmentDueAtPayload(form.elements.dueAt.value);
            await requestJson(`api/v1/assignments/${form.dataset.deadlineAssignment}/deadline`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ dueAt })
            });
            await loadAssignments();
        } catch (error) {
            message.textContent = error.message;
            message.classList.remove('hidden');
            button.disabled = false;
        }
    });

    document.getElementById('assignmentsTableBody').addEventListener('submit', async event => {
        const form = event.target.closest('[data-submit-assignment]');
        if (!form) {
            return;
        }
        event.preventDefault();
        const button = form.querySelector('button[type="submit"]');
        const message = document.getElementById('assignmentError');
        message.classList.add('hidden');
        button.disabled = true;
        try {
            await requestJson(`api/v1/assignments/${form.dataset.submitAssignment}/submissions`, {
                method: 'POST',
                body: new FormData(form)
            });
            await loadAssignments();
        } catch (error) {
            message.textContent = error.message;
            message.classList.remove('hidden');
            button.disabled = false;
        }
    });
});
