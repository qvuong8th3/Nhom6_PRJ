document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('loginForm');
    const errorMsg = document.getElementById('error-msg');
    
    // JWT Authentication Mock
    loginForm.addEventListener('submit', (e) => {
        e.preventDefault();
        
        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;
        const submitBtn = loginForm.querySelector('button');
        
        // Disable button & show loading state
        submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Đang xác thực...';
        submitBtn.style.opacity = '0.7';

        // Giả lập API delay
        setTimeout(() => {
            if (email === 'lecturer@university.edu.vn' && password === '123456') {
                // Đăng nhập thành công -> Lưu mock JWT token
                localStorage.setItem('aita_token', 'mock_jwt_token_header.payload.signature');
                window.location.href = 'index.html';
            } else {
                // Đăng nhập thất bại
                errorMsg.classList.remove('hidden');
                submitBtn.innerHTML = 'Đăng Nhập <i class="fa-solid fa-arrow-right"></i>';
                submitBtn.style.opacity = '1';
                
                // Add shake animation
                loginForm.classList.add('shake');
                setTimeout(() => loginForm.classList.remove('shake'), 500);
            }
        }, 1000);
    });
});

// Thêm keyframe cho animation shake vào style
const style = document.createElement('style');
style.innerHTML = `
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
