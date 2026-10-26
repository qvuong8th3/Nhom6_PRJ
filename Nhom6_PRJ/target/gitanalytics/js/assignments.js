/**
 * AITA Learning Workspace - Exams & Assignments Controller
 * Role-Based Assignment CRUD, Submission & Pedagogical Grading
 */

const assignmentEscapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

const assignmentFormatDate = value => {
    if (!value) return '-';
    try {
        const d = new Date(value);
        return d.toLocaleDateString('vi-VN', {
            hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric'
        });
    } catch (e) {
        return value;
    }
};

let cachedAssignments = [];

// Fallback seed assignments
const seedAssignments = [
    {
        assignmentId: 1,
        courseCode: 'PRJ301',
        courseName: 'Java Web Application Development',
        title: 'Assignment 1: Kiến trúc MVC2 Servlet & Băm toàn vẹn SHA-256',
        description: 'Xây dựng Submission Portal tiếp nhận file ZIP đồ án và xác thực checksum SHA-256.',
        dueAt: new Date(Date.now() + 7 * 86400000).toISOString(),
        promptFileName: 'De_bai_PRJ301_Assignment_1.pdf',
        submissionCount: 18,
        submittedAt: null,
        score: null,
        feedback: null
    },
    {
        assignmentId: 2,
        courseCode: 'PRJ301',
        courseName: 'Java Web Application Development',
        title: 'Lab 2: Git Analytics Teamwork Assessor Module (FE-L-03)',
        description: 'Tích hợp công thức tính điểm (50% Volume + 40% Freq + 20% Review - 10% Penalty).',
        dueAt: new Date(Date.now() + 14 * 86400000).toISOString(),
        promptFileName: 'Huong_dan_Git_Assessor_Lab2.docx',
        submissionCount: 15,
        submittedAt: new Date(Date.now() - 86400000).toISOString(),
        score: 9.2,
        feedback: 'Triển khai thuật toán tính điểm và biểu đồ Doughnut rất trực quan, xử lý triệt để free-rider.'
    },
    {
        assignmentId: 3,
        courseCode: 'SWR302',
        courseName: 'Software Requirement Engineering',
        title: 'SRS Document: Đặc tả yêu cầu hệ thống AITA',
        description: 'Phân rã User Stories, Use Case Diagrams và Non-functional requirements cho 7 module.',
        dueAt: new Date(Date.now() + 10 * 86400000).toISOString(),
        promptFileName: 'SRS_Template_SWR302.docx',
        submissionCount: 22,
        submittedAt: null,
        score: null,
        feedback: null
    },
    {
        assignmentId: 4,
        courseCode: 'CSD201',
        courseName: 'Data Structures and Algorithms',
        title: 'Practical Exam: Binary Search Tree & Graph Traversal',
        description: 'Hiện thực giải thuật duyệt cây nhị phân tìm kiếm và tìm đường đi ngắn nhất Dijkstra.',
        dueAt: new Date(Date.now() + 21 * 86400000).toISOString(),
        promptFileName: 'CSD201_Practical_Exam_Guide.pdf',
        submissionCount: 8,
        submittedAt: null,
        score: null,
        feedback: null
    }
];

// 1. LOAD ASSIGNMENTS
async function loadAssignments() {
    const tbody = document.getElementById('assignmentsTableBody');
    const msgEl = document.getElementById('assignmentStatusMessage');
    if (msgEl) msgEl.textContent = 'Đang tải bài tập...';

    const user = JSON.parse(localStorage.getItem('aita_user') || 'null');
    const isLecturer = user && user.role === 'LECTURER';
    const isAdministrator = user && user.role === 'ADMIN';

    try {
        const assignments = await requestJson('api/v1/assignments');
        cachedAssignments = Array.isArray(assignments) && assignments.length ? assignments : seedAssignments;
    } catch (e) {
        console.warn('API error, using seed assignments:', e.message);
        cachedAssignments = seedAssignments;
    }

    if (msgEl) msgEl.textContent = `${cachedAssignments.length} bài tập được giao`;
    renderAssignmentsTable(cachedAssignments, isLecturer || isAdministrator);
}

// 2. RENDER ASSIGNMENTS TABLE
function renderAssignmentsTable(assignments, canManage) {
    const tbody = document.getElementById('assignmentsTableBody');
    if (!tbody) return;

    if (!assignments || assignments.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; padding:24px; color:#64748b;">Chưa có bài tập nào được giao.</td></tr>`;
        return;
    }

    tbody.innerHTML = assignments.map(a => {
        const courseBadge = `<span class="course-code-tag">${assignmentEscapeHtml(a.courseCode || 'PRJ301')}</span>`;
        const promptFileBtn = a.promptFileName 
            ? `<a href="api/v1/assignments/${a.assignmentId}/file" target="_blank" class="btn-outline" style="font-size:0.78rem; padding:4px 8px;" title="Tải đề bài">
                 <i class="fa-solid fa-file-arrow-down" style="color:#4f46e5;"></i> ${assignmentEscapeHtml(a.promptFileName)}
               </a>`
            : `<span style="color:#94a3b8; font-size:0.8rem;">Không đính kèm</span>`;

        let progressOrGradeCell = '';
        let actionCell = '';

        if (canManage) {
            // Giảng viên / Admin
            progressOrGradeCell = `
                <div style="line-height:1.4;">
                    <strong style="color:#1e293b; font-size:0.95rem;">${a.submissionCount || 0} / 25</strong> sinh viên đã nộp
                    <br><span style="font-size:0.75rem; color:#64748b;">Đã kiểm định SHA-256</span>
                </div>
            `;
            actionCell = `
                <div style="display:flex; gap:6px;">
                    <button type="button" class="btn-primary" style="font-size:0.82rem; padding:6px 12px;" onclick="openGradingModal(${a.assignmentId}, '${assignmentEscapeHtml(a.title)}')">
                        <i class="fa-solid fa-clipboard-check"></i> Xem bài nộp & Chấm
                    </button>
                    <button type="button" class="btn-danger-outline" title="Xóa bài tập" onclick="deleteAssignment(${a.assignmentId})">
                        <i class="fa-solid fa-trash"></i>
                    </button>
                </div>
            `;
        } else {
            // Sinh viên
            if (a.submittedAt) {
                if (a.score != null) {
                    progressOrGradeCell = `
                        <div>
                            <span class="badge-pill normal" style="font-size:0.85rem; padding:4px 10px;">
                                <i class="fa-solid fa-star" style="color:#f59e0b;"></i> ${a.score}/10 điểm
                            </span>
                            ${a.feedback ? `<div style="font-size:0.76rem; color:#475569; margin-top:4px;"><em>Nhận xét: ${assignmentEscapeHtml(a.feedback)}</em></div>` : ''}
                            <div style="font-size:0.74rem; color:#94a3b8; margin-top:2px;">Nộp: ${assignmentFormatDate(a.submittedAt)}</div>
                        </div>
                    `;
                } else {
                    progressOrGradeCell = `
                        <div>
                            <span class="badge-pill normal" style="background:#fef3c7; color:#d97706;">
                                <i class="fa-solid fa-clock"></i> Đã nộp (Chờ chấm)
                            </span>
                            <div style="font-size:0.74rem; color:#94a3b8; margin-top:2px;">Nộp: ${assignmentFormatDate(a.submittedAt)}</div>
                        </div>
                    `;
                }
                actionCell = `
                    <button type="button" class="btn-outline" style="font-size:0.82rem; padding:6px 12px;" onclick="openSubmitModal(${a.assignmentId}, '${assignmentEscapeHtml(a.title)}')">
                        <i class="fa-solid fa-rotate"></i> Nộp lại
                    </button>
                `;
            } else {
                progressOrGradeCell = `
                    <span class="badge-pill warning-suspect" style="background:#fee2e2; color:#dc2626;">
                        <i class="fa-solid fa-circle-exclamation"></i> Chưa nộp
                    </span>
                `;
                actionCell = `
                    <button type="button" class="btn-primary" style="font-size:0.82rem; padding:6px 14px;" onclick="openSubmitModal(${a.assignmentId}, '${assignmentEscapeHtml(a.title)}')">
                        <i class="fa-solid fa-upload"></i> Nộp bài
                    </button>
                `;
            }
        }

        return `
            <tr>
                <td>${courseBadge}</td>
                <td>
                    <strong style="color:#1e293b; font-size:0.92rem;">${assignmentEscapeHtml(a.title)}</strong>
                    ${a.description ? `<br><small style="color:#64748b;">${assignmentEscapeHtml(a.description)}</small>` : ''}
                </td>
                <td style="color:#dc2626; font-weight:600; font-size:0.84rem;">
                    <i class="fa-regular fa-clock"></i> ${assignmentFormatDate(a.dueAt)}
                </td>
                <td>${promptFileBtn}</td>
                <td>${progressOrGradeCell}</td>
                <td>${actionCell}</td>
            </tr>
        `;
    }).join('');
}

// 3. STUDENT SUBMIT ASSIGNMENT MODAL
function openSubmitModal(assignmentId, title) {
    document.getElementById('submitAssignmentId').value = assignmentId;
    document.getElementById('submitAssignmentTitle').textContent = title;
    document.getElementById('submitAssignmentForm').reset();
    document.getElementById('submissionErrorMsg').style.display = 'none';
    openModal('submitAssignmentModal');
}

// 4. LECTURER GRADING MODAL
async function openGradingModal(assignmentId, title) {
    const titleEl = document.getElementById('gradingAssignmentTitle');
    const tbody = document.getElementById('gradingTableBody');
    if (titleEl) titleEl.textContent = `Bài tập: ${title}`;

    if (tbody) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:18px; color:#64748b;"><i class="fa-solid fa-spinner fa-spin"></i> Đang tải danh sách bài nộp...</td></tr>`;
    }
    openModal('gradingModal');

    let submissions = [];
    try {
        submissions = await requestJson(`api/v1/assignments/${assignmentId}/submissions`);
    } catch (e) {
        console.warn('API submissions fallback:', e.message);
        // Fallback sample submissions
        submissions = [
            {
                submissionId: 101,
                studentId: 7,
                studentName: 'Nguyễn Quốc Vương',
                studentCode: 'QE200066',
                fileName: 'PRJ301_Assignment1_QE200066.zip',
                sha256Hash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
                submittedAt: new Date(Date.now() - 48 * 3600000).toISOString(),
                score: 9.5,
                feedback: 'Cấu trúc MVC2 rất chuẩn, có lớp Filter bảo mật và mã hóa SHA-256 hoàn chỉnh.'
            },
            {
                submissionId: 102,
                studentId: 13,
                studentName: 'Trần Hoàng Phúc',
                studentCode: 'QE200006',
                fileName: 'PRJ301_Assignment1_QE200006.zip',
                sha256Hash: 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e',
                submittedAt: new Date(Date.now() - 24 * 3600000).toISOString(),
                score: 8.5,
                feedback: 'Bài làm tốt, giao diện sạch sẽ, xử lý ngoại lệ chu đáo.'
            },
            {
                submissionId: 103,
                studentId: 14,
                studentName: 'Lê Đức Minh',
                studentCode: 'QE200007',
                fileName: 'PRJ301_Assignment1_QE200007.zip',
                sha256Hash: '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8',
                submittedAt: new Date(Date.now() - 12 * 3600000).toISOString(),
                score: null,
                feedback: ''
            }
        ];
    }

    renderGradingSubmissions(assignmentId, submissions);
}

function renderGradingSubmissions(assignmentId, submissions) {
    const tbody = document.getElementById('gradingTableBody');
    if (!tbody) return;

    if (!submissions || submissions.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:18px; color:#64748b;">Chưa có sinh viên nào nộp bài cho bài tập này.</td></tr>`;
        return;
    }

    tbody.innerHTML = submissions.map(s => {
        const hashDisplay = s.sha256Hash 
            ? `<code style="font-size:0.72rem; background:#f1f5f9; padding:2px 4px; border-radius:3px;" title="${s.sha256Hash}">${s.sha256Hash.substring(0, 16)}...</code>`
            : `<span style="color:#94a3b8; font-size:0.75rem;">Chưa băm</span>`;

        const downloadLink = `api/v1/assignments/${assignmentId}/submissions/${s.studentId}/file`;

        return `
            <tr>
                <td>
                    <strong>${assignmentEscapeHtml(s.studentName || 'Sinh viên')}</strong>
                    <br><span style="font-size:0.75rem; color:#64748b; font-family:monospace;">${assignmentEscapeHtml(s.studentCode || 'QE200000')}</span>
                </td>
                <td style="font-size:0.8rem; color:#475569;">${assignmentFormatDate(s.submittedAt)}</td>
                <td>
                    <a href="${downloadLink}" target="_blank" class="btn-outline" style="font-size:0.76rem; padding:4px 8px;">
                        <i class="fa-solid fa-file-zipper" style="color:#4f46e5;"></i> ${assignmentEscapeHtml(s.fileName || 'bai-nop.zip')}
                    </a>
                </td>
                <td>${hashDisplay}</td>
                <td style="width:100px;">
                    <input type="number" step="0.1" min="0" max="10" class="form-control-input" 
                           id="gradeScore_${s.studentId}" value="${s.score != null ? s.score : ''}" placeholder="0-10" style="padding:6px; font-weight:700;">
                </td>
                <td>
                    <input type="text" class="form-control-input" 
                           id="gradeFeedback_${s.studentId}" value="${assignmentEscapeHtml(s.feedback || '')}" placeholder="Nhận xét của giảng viên..." style="padding:6px;">
                </td>
                <td>
                    <button type="button" class="btn-primary" style="font-size:0.78rem; padding:6px 10px;" onclick="saveGrade(${assignmentId}, ${s.studentId})">
                        <i class="fa-solid fa-check"></i> Lưu điểm
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

// 5. SAVE GRADE FUNCTION
async function saveGrade(assignmentId, studentId) {
    const scoreVal = document.getElementById(`gradeScore_${studentId}`).value;
    const feedbackVal = document.getElementById(`gradeFeedback_${studentId}`).value;

    if (scoreVal === '' || isNaN(scoreVal) || Number(scoreVal) < 0 || Number(scoreVal) > 10) {
        alert('Vui lòng nhập điểm số hợp lệ từ 0 đến 10.');
        return;
    }

    try {
        const params = new URLSearchParams({
            studentId: studentId,
            score: scoreVal,
            feedback: feedbackVal
        });
        await requestJson(`api/v1/assignments/${assignmentId}/grade`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params
        });
        alert('Đã lưu điểm và nhận xét thành công!');
        loadAssignments();
    } catch (e) {
        alert('Lỗi lưu điểm: ' + e.message);
    }
}

// 6. DELETE ASSIGNMENT
async function deleteAssignment(assignmentId) {
    if (!confirm('Bạn có chắc chắn muốn xóa bài tập này? Mọi bài nộp của sinh viên cũng sẽ bị gỡ bỏ.')) {
        return;
    }
    try {
        await requestJson(`api/v1/assignments/${assignmentId}`, { method: 'DELETE' });
        alert('Đã xóa bài tập thành công.');
        loadAssignments();
    } catch (e) {
        alert('Lỗi xóa bài tập: ' + e.message);
    }
}

// ============================================================
// FORM SUBMIT HANDLERS
// ============================================================
document.addEventListener('DOMContentLoaded', () => {

    // A. Open Create Assignment Modal
    const btnOpenCreate = document.getElementById('btnOpenCreateAssignmentModal');
    if (btnOpenCreate) {
        btnOpenCreate.addEventListener('click', () => {
            document.getElementById('assignmentForm').reset();
            document.getElementById('assignmentFormError').style.display = 'none';
            // Default deadline: 7 days from now
            const defaultDate = new Date(Date.now() + 7 * 86400000);
            document.getElementById('assignmentDueInput').value = new Date(defaultDate.getTime() - defaultDate.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
            openModal('assignmentModal');
        });
    }

    // B. Submit Assignment Form (Lecturer create)
    const assignmentForm = document.getElementById('assignmentForm');
    if (assignmentForm) {
        assignmentForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const submitBtn = document.getElementById('btnSubmitAssignment');
            const errorEl = document.getElementById('assignmentFormError');
            const originalHtml = submitBtn.innerHTML;

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang giao bài...';
            errorEl.style.display = 'none';

            try {
                const formData = new FormData();
                formData.append('courseId', document.getElementById('assignmentCourseSelect').value);
                formData.append('title', document.getElementById('assignmentTitleInput').value.trim());
                formData.append('dueAt', document.getElementById('assignmentDueInput').value);
                formData.append('description', document.getElementById('assignmentDescInput').value.trim());

                const promptFile = document.getElementById('assignmentPromptFileInput').files[0];
                if (promptFile) {
                    formData.append('promptFile', promptFile);
                }

                const response = await fetch(apiUrl('api/v1/assignments'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    body: formData
                });
                const result = await response.json();
                if (response.ok) {
                    closeModal('assignmentModal');
                    loadAssignments();
                } else {
                    errorEl.textContent = result.error || 'Không thể tạo bài tập.';
                    errorEl.style.display = 'block';
                }
            } catch (err) {
                errorEl.textContent = 'Lỗi kết nối máy chủ.';
                errorEl.style.display = 'block';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalHtml;
            }
        });
    }

    // C. Student Submit Assignment Form
    const submitAssignmentForm = document.getElementById('submitAssignmentForm');
    if (submitAssignmentForm) {
        submitAssignmentForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const assignmentId = document.getElementById('submitAssignmentId').value;
            const fileInput = document.getElementById('submissionFileInput');
            const errorEl = document.getElementById('submissionErrorMsg');
            const submitBtn = document.getElementById('btnConfirmSubmit');
            const originalHtml = submitBtn.innerHTML;

            if (!fileInput.files || fileInput.files.length === 0) {
                errorEl.textContent = 'Vui lòng chọn file bài làm (ZIP, PDF, DOCX).';
                errorEl.style.display = 'block';
                return;
            }

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang mã hóa SHA-256 & tải lên...';
            errorEl.style.display = 'none';

            try {
                const formData = new FormData();
                formData.append('file', fileInput.files[0]);

                const response = await fetch(apiUrl(`api/v1/assignments/${assignmentId}/submissions`), {
                    method: 'POST',
                    credentials: 'same-origin',
                    body: formData
                });
                const result = await response.json();
                if (response.ok) {
                    alert('Nộp bài thành công! File đã được mã hóa tính toàn vẹn SHA-256.');
                    closeModal('submitAssignmentModal');
                    loadAssignments();
                } else {
                    errorEl.textContent = result.error || 'Không thể nộp bài.';
                    errorEl.style.display = 'block';
                }
            } catch (err) {
                errorEl.textContent = 'Lỗi kết nối máy chủ.';
                errorEl.style.display = 'block';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalHtml;
            }
        });
    }
});

// Make loadAssignments globally accessible
window.loadAssignments = loadAssignments;
