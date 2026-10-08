const examEscapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

const examDate = value => value ? new Date(value).toLocaleString('vi-VN') : '-';

async function loadExams() {
    const body = document.getElementById('examsTableBody');
    const message = document.getElementById('examMessage');
    message.textContent = 'Đang tải đề thi...';
    try {
        const exams = await requestJson('api/v1/exams');
        const user = JSON.parse(localStorage.getItem('aita_user') || 'null');
        const lecturer = user && user.role === 'LECTURER';
        body.innerHTML = exams.length ? exams.map(exam => `<tr>
            <td><strong>${examEscapeHtml(exam.title)}</strong>${exam.description ? `<br><span class="text-muted">${examEscapeHtml(exam.description)}</span>` : ''}</td>
            <td>${exam.questionCount}</td>
            <td>${lecturer ? `${exam.attemptCount} bài nộp` : (exam.submitted ? `${exam.score}/${exam.maxScore}` : 'Chưa làm')}</td>
            <td>${examDate(exam.createdAt)}</td>
            <td class="exam-actions">
                ${lecturer
                    ? `<button class="btn-outline" data-exam-results="${exam.examId}" title="Xem kết quả"><i class="fa-solid fa-chart-column"></i></button>`
                    : `<button class="btn-outline" data-open-exam="${exam.examId}" ${exam.submitted ? 'disabled title="Đã nộp bài"' : 'title="Làm bài"'}><i class="fa-solid fa-arrow-up-right-from-square"></i></button>`}
            </td>
        </tr>`).join('') : `<tr><td colspan="5" class="empty-state">Chưa có đề thi trong database.</td></tr>`;
        message.textContent = `${exams.length} đề thi`;
    } catch (error) {
        message.textContent = error.message;
        body.innerHTML = '';
    }
}

async function openExam(examId) {
    try {
        const exam = await requestJson(`api/v1/exams/${examId}`);
        document.getElementById('examDetailTitle').textContent = exam.title;
        document.getElementById('examDetailDescription').textContent = exam.description || '';
        const form = document.getElementById('examAttemptForm');
        const result = document.getElementById('examAttemptResult');
        result.classList.toggle('hidden', !exam.submitted);
        result.textContent = exam.submitted
            ? `Bạn đã nộp bài. Điểm: ${exam.score}/${exam.maxScore}.`
            : '';
        form.innerHTML = exam.questions.map((question, index) => {
            const answerControl = question.type === 'MCQ'
                ? question.options.map(option => `<label class="exam-option">
                    <input type="radio" name="answer-${question.questionId}" value="${examEscapeHtml(option.key)}" required>
                    <span><strong>${examEscapeHtml(option.key)}.</strong> ${examEscapeHtml(option.text)}</span>
                </label>`).join('')
                : `<input class="exam-short-answer" type="text" name="answer-${question.questionId}" maxlength="1000" required aria-label="Câu trả lời">`;
            return `<section class="exam-question">
                <h4><span class="exam-question-type">Câu ${index + 1} · ${question.points} điểm</span><br>${examEscapeHtml(question.text)}</h4>
                ${answerControl}
            </section>`;
        }).join('');
        if (!exam.submitted) {
            form.insertAdjacentHTML('beforeend', '<div class="exam-submit-row"><button class="btn-primary" type="submit"><i class="fa-solid fa-paper-plane"></i> Nộp bài</button></div>');
            form.onsubmit = async event => {
                event.preventDefault();
                const answers = {};
                exam.questions.forEach(question => {
                    const controls = form.elements[`answer-${question.questionId}`];
                    answers[question.questionId] = question.type === 'MCQ'
                        ? (Array.from(controls).find(control => control.checked) || {}).value || null
                        : controls.value;
                });
                const submitButton = form.querySelector('button[type="submit"]');
                submitButton.disabled = true;
                try {
                    const score = await requestJson(`api/v1/exams/${examId}/submit`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ answers })
                    });
                    result.textContent = `Đã chấm tự động. Điểm của bạn: ${score.score}/${score.maxScore}.`;
                    result.classList.remove('hidden');
                    form.querySelectorAll('input').forEach(input => { input.disabled = true; });
                    submitButton.remove();
                    loadExams();
                } catch (error) {
                    window.alert(error.message);
                    submitButton.disabled = false;
                }
            };
        } else {
            form.querySelectorAll('input').forEach(input => { input.disabled = true; });
        }
        setActiveView('examDetailView');
    } catch (error) {
        window.alert(error.message);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const user = JSON.parse(localStorage.getItem('aita_user') || 'null');
    const lecturer = user && user.role === 'LECTURER';
    const uploadForm = document.getElementById('examUploadForm');
    const uploadError = document.getElementById('examUploadError');
    const uploadToggle = document.getElementById('examUploadToggle');
    uploadToggle.classList.toggle('hidden', !lecturer);

    document.addEventListener('exams-view-open', loadExams);
    uploadToggle.addEventListener('click', () => uploadForm.classList.toggle('hidden'));
    document.getElementById('closeExamResults').addEventListener('click', () => {
        document.getElementById('examResultsPanel').classList.add('hidden');
    });
    document.getElementById('backToExams').addEventListener('click', () => setActiveView('examsView'));

    document.getElementById('examsTableBody').addEventListener('click', async event => {
        const openButton = event.target.closest('[data-open-exam]');
        const resultsButton = event.target.closest('[data-exam-results]');
        if (openButton) {
            openExam(openButton.dataset.openExam);
        }
        if (resultsButton) {
            try {
                const data = await requestJson(`api/v1/exams/${resultsButton.dataset.examResults}/results`);
                document.getElementById('examResultsTitle').textContent = `Kết quả: ${data.title}`;
                const resultsBody = document.getElementById('examResultsBody');
                resultsBody.innerHTML = data.results.length ? data.results.map(row => `<tr>
                    <td>${examEscapeHtml(row.fullName)}</td><td>${examEscapeHtml(row.username)}</td>
                    <td><strong>${row.score}/${row.maxScore}</strong></td><td>${examDate(row.submittedAt)}</td>
                </tr>`).join('') : '<tr><td colspan="4" class="empty-state">Chưa có sinh viên nộp bài.</td></tr>';
                document.getElementById('examResultsPanel').classList.remove('hidden');
            } catch (error) {
                window.alert(error.message);
            }
        }
    });

    uploadForm.addEventListener('submit', async event => {
        event.preventDefault();
        uploadError.classList.add('hidden');
        const button = uploadForm.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            await requestJson('api/v1/exams', { method: 'POST', body: new FormData(uploadForm) });
            uploadForm.reset();
            uploadForm.classList.add('hidden');
            await loadExams();
        } catch (error) {
            uploadError.textContent = error.message;
            uploadError.classList.remove('hidden');
        } finally {
            button.disabled = false;
        }
    });
});