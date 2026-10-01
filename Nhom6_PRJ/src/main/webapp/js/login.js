document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const errorMsg = document.getElementById('error-msg');

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        const identifier = document.getElementById('identifier').value.trim();
        const password = document.getElementById('password').value;
        const submitBtn = loginForm.querySelector('button');
        const originalText = submitBtn.innerHTML;
        errorMsg.classList.add('hidden');
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang xác thực...';

        try {
            const response = await fetch(new URL('api/v1/auth/login', window.location.href), {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ identifier, password })
            });
            const result = await response.json();
            if (response.ok) {
                localStorage.setItem('aita_user', JSON.stringify(result.user));
                window.location.href = 'index.html';
            } else {
                errorMsg.textContent = result.error || 'Không thể đăng nhập.';
                errorMsg.classList.remove('hidden');
            }
        } catch (error) {
            errorMsg.textContent = 'Không kết nối được máy chủ. Hãy chạy ứng dụng trên Tomcat và kiểm tra database.';
            errorMsg.classList.remove('hidden');
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
        }
    });
});

// Thêm keyframe cho animation shake vào style
const style = document.createElement('style');
style.textContent = `
    .shake {
        animation: shake 0.5s cubic-bezier(.36,.07,.19,.97) both;
    }
    @keyframes shake {
        10%, 90% { transform: translate3d(-1px, 0, 0); }
        20%, 80% { transform: translate3d(2px, 0, 0); }
        30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
        40%, 60% { transform: translate3d(4px, 0, 0); }
    }
`;
document.head.appendChild(style);
