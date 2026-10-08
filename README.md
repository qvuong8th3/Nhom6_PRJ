# AITA - Git Analytics

Ứng dụng Java Servlet dùng SQL Server để đăng nhập, xem thống kê đóng góp Git và quản lý tài khoản sinh viên.

## Chuẩn bị

- JDK 11, Maven và Apache Tomcat 9.
- SQL Server đang chạy TCP/IP trên cổng `1433`.

Với database mới, trong SQL Server Management Studio chạy lần lượt `Nhom6_PRJ/database/schema.sql`, `Nhom6_PRJ/database/SQLQuery1.sql` và `Nhom6_PRJ/database/exams.sql`. `SQLQuery1.sql` tạo dữ liệu mẫu và chỉ nên chạy một lần. Với database đã có dữ liệu người dùng, chạy `Nhom6_PRJ/database/remember_me.sql` để tạo bảng token ghi nhớ đăng nhập, sau đó chạy `Nhom6_PRJ/database/exams.sql` để thêm các bảng đề thi; chạy `Nhom6_PRJ/database/setup_demo_lecturer.sql` nếu cần thêm tài khoản demo.

## Đề thi và chấm tự động

Giảng viên tải CSV hoặc XLSX lên từ màn “Đề thi”. File XLSX dùng trang tính đầu tiên; hàng đầu tiên phải có đủ các cột sau:

```text
type,question,points,option_a,option_b,option_c,option_d,correct_answer
```

`type` nhận `MCQ` hoặc `SHORT_TEXT`. Câu `MCQ` cần ít nhất hai lựa chọn và `correct_answer` là chữ cái A-D. Câu `SHORT_TEXT` để trống các cột lựa chọn và ghi đáp án trong `correct_answer`. Câu trả lời ngắn được so khớp sau khi bỏ khoảng trắng đầu/cuối và không phân biệt chữ hoa/thường. Sinh viên làm bài trực tiếp trên web, chỉ được nộp một lần cho mỗi đề; hệ thống lưu điểm vào database và hiển thị ngay sau khi nộp. Có file mẫu tại `Nhom6_PRJ/src/main/webapp/files/exam-template.csv`.

## Cấu hình database

Ứng dụng mặc định kết nối `localhost:1433`, database `AITA_DB`, user `sa`, password `123`. Có thể thay bằng biến môi trường trước khi khởi động Tomcat:

```powershell
$env:AITA_DB_SERVER = "localhost"
$env:AITA_DB_PORT = "1433"
$env:AITA_DB_NAME = "AITA_DB"
$env:AITA_DB_USER = "sa"
$env:AITA_DB_PASSWORD = "your-sql-server-password"
```

## Chạy ứng dụng

Từ thư mục `Nhom6_PRJ`, chạy `mvn clean package`, sau đó chép `target/gitanalytics.war` vào thư mục `webapps` của Tomcat 9. Mở `http://localhost:8080/gitanalytics/login.jsp`.

Tài khoản giảng viên demo:

- Email: `lecturer01@gmail.com`
- Mật khẩu: `123456`

Đổi mật khẩu demo trước khi dùng ngoài môi trường local.

Dashboard đọc điểm từ `Contribution_Scores` và số commit từ `Commits`. Màn “Quản lý sinh viên” cho phép thêm, sửa, xóa tài khoản có role `STUDENT`; database có thể từ chối xóa nếu sinh viên còn bản ghi điểm đang tham chiếu.
