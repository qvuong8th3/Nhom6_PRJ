// Fetch data from real backend Servlet (with Gson support)
async function fetchDashboardData(groupId) {
    try {
        const token = localStorage.getItem('aita_token');
        if (!token) {
            window.location.href = 'login.html';
            return null;
        }

        const response = await fetch(`/api/v1/git-analytics/dashboard?groupId=${groupId}`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.status === 401) {
            window.location.href = 'login.html';
            return null;
        }

        const json = await response.json();
        return json.data; // JSON returned by Gson mapped from Java object
    } catch (error) {
        console.error("Error fetching data:", error);
        return null;
    }
}

// Cấu hình chung cho Chart.js
Chart.defaults.color = "#94a3b8";
Chart.defaults.font.family = "'Inter', sans-serif";

document.addEventListener('DOMContentLoaded', async () => {
    
    // Call the Java Servlet (mocking groupId=1 for now)
    const dashboardData = await fetchDashboardData(1);
    
    // If API is not running, fallback to Mock Data for the prototype to still work visually
    const dataToUse = dashboardData || {
        groupName: "Nhóm 6 - SE1234",
        contributions: [
            { studentName: "Nguyễn Đặng Trường Hải", percentage: 35.63, commits: 45, loc: 2500, regularity: 95.0 },
            { studentName: "Võ Xuân Long", percentage: 22.49, commits: 25, loc: 1200, regularity: 80.0 },
            { studentName: "Nguyễn Tuấn Kiệt", percentage: 20.18, commits: 40, loc: 300, regularity: 85.0 },
            { studentName: "Nguyễn Quốc Vương", percentage: 19.67, commits: 20, loc: 1800, regularity: 40.0 },
            { studentName: "Võ Thảo Nguyên", percentage: 2.03, commits: 2, loc: 50, regularity: 10.0 } // Free rider
        ],
        timeline: {
            labels: ["Week 1", "Week 2", "Week 3", "Week 4", "Week 5"],
            datasets: [
                { label: "Nguyễn Đặng Trường Hải", data: [5, 10, 8, 12, 10], borderColor: "#3b82f6", backgroundColor: "rgba(59, 130, 246, 0.2)", tension: 0.4 },
                { label: "Võ Xuân Long", data: [2, 6, 7, 5, 5], borderColor: "#10b981", backgroundColor: "rgba(16, 185, 129, 0.2)", tension: 0.4 },
                { label: "Nguyễn Tuấn Kiệt", data: [10, 8, 12, 5, 5], borderColor: "#f59e0b", backgroundColor: "rgba(245, 158, 11, 0.2)", tension: 0.4 },
                { label: "Nguyễn Quốc Vương", data: [0, 0, 0, 10, 10], borderColor: "#8b5cf6", backgroundColor: "rgba(139, 92, 246, 0.2)", tension: 0.4 },
                { label: "Võ Thảo Nguyên", data: [0, 0, 0, 0, 2], borderColor: "#ef4444", backgroundColor: "rgba(239, 68, 68, 0.2)", tension: 0.4 }
            ]
        }
    };
    
    // 1. Render Pie Chart (Contribution %)
    const pieCtx = document.getElementById('contributionPieChart').getContext('2d');
    new Chart(pieCtx, {
        type: 'doughnut',
        data: {
            labels: dataToUse.contributions.map(c => c.studentName),
            datasets: [{
                data: dataToUse.contributions.map(c => c.percentage),
                backgroundColor: ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ef4444'],
                borderWidth: 0,
                hoverOffset: 10
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom', labels: { padding: 20, color: '#f8fafc' } }
            },
            cutout: '70%'
        }
    });

    // 2. Render Bar Chart / Line Chart (Timeline)
    const barCtx = document.getElementById('timelineBarChart').getContext('2d');
    new Chart(barCtx, {
        type: 'line',
        data: {
            labels: dataToUse.timeline.labels,
            datasets: dataToUse.timeline.datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'top', labels: { color: '#f8fafc' } }
            },
            scales: {
                y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.05)' } },
                x: { grid: { display: false } }
            }
        }
    });

    // 3. Render Table Data & Detect Free-Riders
    const tableBody = document.getElementById('studentTableBody');
    const alertContainer = document.getElementById('alertContainer');
    let html = '';
    let alertsHtml = '';
    
    dataToUse.contributions.forEach(item => {
        // Xác định class màu sắc cho điểm số
        let scoreClass = 'score-high';
        if (item.percentage < 25 && item.percentage >= 15) scoreClass = 'score-medium';
        else if (item.percentage < 15) {
            scoreClass = 'score-low';
            // Sinh ra cảnh báo free-rider
            alertsHtml += `
            <p class="alert alert-warning">
                <i class="fa-solid fa-triangle-exclamation"></i> 
                Cảnh báo: <strong>${item.studentName}</strong> có tỷ lệ đóng góp quá thấp (${item.percentage}%). Có dấu hiệu "Free-riding".
            </p>`;
        }

        html += `
            <tr>
                <td><strong>${item.studentName}</strong></td>
                <td>${item.commits} commits</td>
                <td>${item.loc} dòng</td>
                <td>${item.regularity}/100</td>
                <td><span class="score-pill ${scoreClass}">${item.percentage}%</span></td>
                <td>
                    <button class="btn-outline" style="padding: 5px 10px; font-size: 0.8rem;">
                        Chi tiết <i class="fa-solid fa-chevron-right"></i>
                    </button>
                </td>
            </tr>
        `;
    });
    tableBody.innerHTML = html;
    if (alertsHtml) {
        alertContainer.innerHTML = alertsHtml;
    }

    // Logout
    document.getElementById('logoutBtn').addEventListener('click', () => {
        localStorage.removeItem('aita_token');
        window.location.href = 'login.html';
    });
    
    // Sync Animation Mock
    document.getElementById('syncBtn').addEventListener('click', function() {
        const icon = this.querySelector('i');
        icon.classList.add('fa-spin');
        setTimeout(() => {
            icon.classList.remove('fa-spin');
            alert('Đã đồng bộ dữ liệu Git thành công!');
        }, 1500);
    });
});
