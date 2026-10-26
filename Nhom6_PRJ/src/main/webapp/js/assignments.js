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
            const countEl = document.getElementById('studentAssignmentCount');
            if (countEl) {
                countEl.textContent = assignments.filter(assignment => !assignment.submittedAt).length;
            }
        }
        body.innerHTML = assignments.length ? assignments.map(assignment => {
            let submissionCell = '';
            if (lecturer) {
                submissionCell = `<strong>${assignment.submissionCount}</strong> bài đã nộp`;
            } else {
                if (assignment.submittedAt) {
                    if (assignment.score != null) {
                        submissionCell = `<div style="line-height:1.4;">
                            <span class="score-pill score-high" style="display:inline-block; font-weight:700; color:#047857; background:#d1fae5; border:1px solid #6ee7b7; padding:3px 10px; border-radius:15px; font-size:0.9rem;">
                                <i class="fa-solid fa-star" style="color:#f59e0b;"></i> ${assignment.score}/10 điểm
                            </span>
                            ${assignment.feedback ? `<br><small class="text-muted" style="display:inline-block; margin-top:3px;"><i class="fa-solid fa-comment-dots"></i> Nhận xét: <em>${assignmentEscapeHtml(assignment.feedback)}</em></small>` : ''}
                            <br><span class="text-muted" style="font-size:0.75rem;">Nộp lúc: ${assignmentDate(assignment.submittedAt)}</span>
                        </div>`;
                    } else {
                        submissionCell = `<div style="line-height:1.4;">
                            <span style="display:inline-block; font-weight:600; color:#d97706; background:#fffbeb; border:1px solid #f59e0b; padding:2px 8px; border-radius:12px; font-size:0.85rem;">
                                <i class="fa-solid fa-clock"></i> Đã nộp (Chờ chấm điểm)
                            </span>
                            <br><span class="text-muted" style="font-size:0.75rem;">${assignmentDate(assignment.submittedAt)}</span>
                        </div>`;
                    }
                } else {
                    submissionCell = `<span class="text-muted" style="font-style:italic;">Chưa nộp bài</span>`;
                }
            }

            return `<tr>
                <td><strong>${assignmentEscapeHtml(assignment.courseCode || '')}</strong>${assignment.courseCode ? '<br>' : ''}<span class="text-muted">${assignmentEscapeHtml(assignment.courseName || 'Chưa gắn môn học')}</span></td>
                <td><strong class="assignment-title">${assignmentEscapeHtml(assignment.title)}</strong>
                    ${assignment.description ? `<br><span class="text-muted">${assignmentEscapeHtml(assignment.description)}</span>` : ''}</td>
                <td>${assignment.promptFileName
                    ? `<a class="assignment-download" href="api/v1/assignments/${assignment.assignmentId}/file"><i class="fa-solid fa-download"></i> ${assignmentEscapeHtml(assignment.promptFileName)}</a>`
                    : 'Không có file đính kèm'}</td>
                <td>${submissionCell}</td>
                <td>${lecturer
                    ? `<form class="assignment-deadline-form" data-deadline-assignment="${assignment.assignmentId}">
                           <input type="datetime-local" name="dueAt" aria-label="Hạn nộp: ${assignmentEscapeHtml(assignment.title)}"
                               value="${assignmentDateTimeInput(assignment.dueAt)}">
                           <button class="btn-outline" type="submit">Lưu hạn</button>
                       </form>`
                    : assignment.dueAt
                        ? `${assignmentDate(assignment.dueAt)}${new Date(assignment.dueAt).getTime() < Date.now()
                            ? '<br><span class="text-muted" style="color:#ef4444 !important; font-weight:600;">Đã hết hạn</span>' : ''}`
                        : 'Không giới hạn'}</td>
                <td>${assignmentDate(assignment.createdAt)}</td>
                <td class="assignment-actions">${lecturer
                    ? `<button class="btn-outline" type="button" data-assignment-submissions="${assignment.assignmentId}">
                           <i class="fa-solid fa-users"></i> Xem & chấm bài
                       </button>`
                    : (assignment.dueAt && new Date(assignment.dueAt).getTime() < Date.now()
                        ? '<span class="text-muted">Đã khóa nộp bài</span>'
                        : `<form class="assignment-submit-form" data-submit-assignment="${assignment.assignmentId}">
                           <label class="assignment-file-label">File bài làm<input type="file" name="file" accept=".pdf,.doc,.docx,.odt,.rtf,.txt" required></label>
                           <button class="btn-outline" type="submit"><i class="fa-solid fa-cloud-arrow-up"></i> ${assignment.submittedAt ? 'Nộp lại' : 'Nộp bài'}</button>
                       </form>`)}</td>
            </tr>`;
        }).join('') : '<tr><td colspan="7" class="empty-state">Chưa có bài tập trong các môn học của bạn.</td></tr>';
        message.textContent = `${assignments.length} bài tập · Chấp nhận PDF, DOC, DOCX, ODT, RTF, TXT tối đa 15 MB`;
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
        document.getElementById('assignmentSubmissionsTitle').textContent = `Bài sinh viên đã nộp: ${title}`;
        document.getElementById('assignmentSubmissionsBody').innerHTML = submissions.length
            ? submissions.map(submission => `<tr>
                <td><strong>${assignmentEscapeHtml(submission.fullName)}</strong><br><span class="text-muted">${assignmentEscapeHtml(submission.username)}</span></td>
                <td>${assignmentEscapeHtml(submission.email)}</td>
                <td>${assignmentEscapeHtml(submission.fileName)}</td>
                <td>${assignmentDate(submission.submittedAt)}</td>
                <td><a class="btn-outline assignment-download" href="api/v1/assignments/${assignmentId}/submissions/${submission.studentId}/file">
                    <i class="fa-solid fa-download"></i> Tải xuống
                </a></td>
                <td>
                    <input type="number" step="0.25" min="0" max="10" class="grade-score-input"
                        value="${submission.score != null ? submission.score : ''}"
                        placeholder="0 - 10"
                        style="width: 75px; padding: 5px 8px; border: 1px solid #cbd5e1; border-radius: 6px; font-weight: 700; font-size: 0.95rem; text-align: center;">
                </td>
                <td>
                    <input type="text" class="grade-feedback-input"
                        value="${assignmentEscapeHtml(submission.feedback || '')}"
                        placeholder="Ghi chú, nhận xét..."
                        style="width: 170px; padding: 5px 8px; border: 1px solid #cbd5e1; border-radius: 6px; font-size: 0.85rem;">
                </td>
                <td style="white-space: nowrap;">
                    <button class="btn-primary btn-save-grade" type="button"
                        data-assignment-id="${assignmentId}"
                        data-student-id="${submission.studentId}"
                        style="padding: 5px 12px; font-size: 0.85rem; border-radius: 6px;">
                        <i class="fa-solid fa-floppy-disk"></i> Lưu
                    </button>
                    <span class="grade-save-msg" style="display: block; font-size: 0.75rem; margin-top: 3px; font-weight: 600;"></span>
                </td>
            </tr>`).join('')
            : '<tr><td colspan="8" class="empty-state">Chưa có sinh viên nào nộp bài.</td></tr>';
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
    if (createToggle) {
        createToggle.classList.toggle('hidden', !lecturer);
    }
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

    if (createToggle) {
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
    }

    const closeBtn = document.getElementById('closeAssignmentSubmissions');
    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            document.getElementById('assignmentSubmissionsPanel').classList.add('hidden');
        });
    }

    if (createForm) {
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
    }

    const assignmentsTableBody = document.getElementById('assignmentsTableBody');
    if (assignmentsTableBody) {
        assignmentsTableBody.addEventListener('click', event => {
            const button = event.target.closest('[data-assignment-submissions]');
            if (!button) {
                return;
            }
            const row = button.closest('tr');
            const titleEl = row.querySelector('.assignment-title') || row.querySelector('td strong');
            const title = titleEl ? titleEl.textContent : '';
            loadAssignmentSubmissions(button.dataset.assignmentSubmissions, title);
        });

        assignmentsTableBody.addEventListener('submit', async event => {
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

        assignmentsTableBody.addEventListener('submit', async event => {
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
    }

    // Xử lý nút Chấm điểm cho từng sinh viên
    const submissionsBody = document.getElementById('assignmentSubmissionsBody');
    if (submissionsBody) {
        submissionsBody.addEventListener('click', async event => {
            const button = event.target.closest('.btn-save-grade');
            if (!button) return;
            const row = button.closest('tr');
            const assignmentId = button.dataset.assignmentId;
            const studentId = button.dataset.studentId;
            const scoreInput = row.querySelector('.grade-score-input');
            const feedbackInput = row.querySelector('.grade-feedback-input');
            const msgEl = row.querySelector('.grade-save-msg');

            const scoreVal = scoreInput ? scoreInput.value.trim() : '';
            const feedbackVal = feedbackInput ? feedbackInput.value.trim() : '';

            if (scoreVal !== '' && (isNaN(scoreVal) || Number(scoreVal) < 0 || Number(scoreVal) > 10)) {
                if (msgEl) {
                    msgEl.textContent = 'Điểm từ 0 - 10!';
                    msgEl.style.color = '#ef4444';
                }
                return;
            }

            button.disabled = true;
            const originalHtml = button.innerHTML;
            button.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
            if (msgEl) msgEl.textContent = '';

            try {
                const payload = new URLSearchParams({
                    studentId: studentId,
                    score: scoreVal,
                    feedback: feedbackVal
                });
                await requestJson(`api/v1/assignments/${assignmentId}/grade`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: payload
                });
                if (msgEl) {
                    msgEl.textContent = '✓ Đã lưu';
                    msgEl.style.color = '#10b981';
                }
                button.innerHTML = '<i class="fa-solid fa-check"></i> Đã lưu';
                setTimeout(() => {
                    button.innerHTML = originalHtml;
                    button.disabled = false;
                }, 1500);
            } catch (err) {
                if (msgEl) {
                    msgEl.textContent = err.message || 'Lỗi lưu điểm!';
                    msgEl.style.color = '#ef4444';
                }
                button.innerHTML = originalHtml;
                button.disabled = false;
            }
        });
    }
});
