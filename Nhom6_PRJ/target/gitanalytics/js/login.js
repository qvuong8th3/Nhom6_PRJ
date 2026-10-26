const initializeLoginPage = () => {
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const errorMsg = document.getElementById('error-msg');
    const registerErrorMsg = document.getElementById('register-error-msg');
    const registerSuccessMsg = document.getElementById('register-success-msg');
    const switchButton = document.getElementById('auth-switch-button');
    const switchPrompt = document.getElementById('auth-switch-prompt');

    const requiresServer = () => window.location.protocol === 'file:';

    switchButton.addEventListener('click', () => {
        const showingRegistration = registerForm.classList.contains('hidden');
        loginForm.classList.toggle('hidden', showingRegistration);
        registerForm.classList.toggle('hidden', !showingRegistration);
        switchPrompt.textContent = showingRegistration ? 'Đã có tài khoản?' : 'Chưa có tài khoản?';
        switchButton.textContent = showingRegistration ? 'Đăng nhập' : 'Đăng ký sinh viên';
        errorMsg.classList.add('hidden');
        registerErrorMsg.classList.add('hidden');
        registerSuccessMsg.classList.add('hidden');
    });

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        if (requiresServer()) {
            errorMsg.textContent = 'Bạn đang mở file trực tiếp. Hãy khởi động Tomcat và truy cập http://localhost:8080/gitanalytics/login.jsp.';
            errorMsg.classList.remove('hidden');
            return;
        }

        const identifier = document.getElementById('identifier').value.trim();
        const password = document.getElementById('password').value;
        const rememberMe = document.getElementById('rememberMe').checked;
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
                body: new URLSearchParams({ identifier, password, rememberMe })
            });
            const result = await response.json();
            if (response.ok) {
                localStorage.setItem('aita_user', JSON.stringify(result.user));
                window.location.href = 'index.jsp';
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

    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        if (requiresServer()) {
            registerErrorMsg.textContent = 'Bạn đang mở file trực tiếp. Hãy khởi động Tomcat và truy cập http://localhost:8080/gitanalytics/login.jsp.';
            registerErrorMsg.classList.remove('hidden');
            return;
        }

        const formData = new FormData(registerForm);
        const submitBtn = registerForm.querySelector('button[type="submit"]');
        const originalText = submitBtn.innerHTML;
        registerErrorMsg.classList.add('hidden');
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang tạo tài khoản...';

        try {
            const response = await fetch(new URL('api/v1/auth/register', window.location.href), {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams(formData)
            });
            const result = await response.json();
            if (response.ok) {
                registerForm.reset();
                loginForm.classList.remove('hidden');
                registerForm.classList.add('hidden');
                switchPrompt.textContent = 'Chưa có tài khoản?';
                switchButton.textContent = 'Đăng ký sinh viên';
                registerSuccessMsg.textContent = 'Đăng ký thành công! Hãy đăng nhập bằng tài khoản vừa tạo.';
                registerSuccessMsg.classList.remove('hidden');
            } else {
                registerErrorMsg.textContent = result.error || 'Không thể tạo tài khoản.';
                registerErrorMsg.classList.remove('hidden');
            }
        } catch (error) {
            registerErrorMsg.textContent = 'Không kết nối được máy chủ. Hãy thử lại sau.';
            registerErrorMsg.classList.remove('hidden');
        } finally {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
        }
    });
};

initializeLoginPage();

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
