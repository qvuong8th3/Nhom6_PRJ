# AITA - Git Analytics

Ứng dụng Java Servlet dùng SQL Server để đăng nhập, xem thống kê đóng góp Git và quản lý tài khoản sinh viên.

## Không gian giảng viên và sinh viên

Sau khi đăng nhập, giao diện tự hiển thị theo vai trò tài khoản:

- **Giảng viên**: xem tổng quan đóng góp Git, quản lý danh sách sinh viên, giao bài tập có hạn nộp, tải đề kiểm tra và xem bài nộp/kết quả.
- **Sinh viên**: xem trang học tập cá nhân, theo dõi số bài chưa nộp và đề chưa làm, tải tài liệu, nộp hoặc nộp lại bài trước hạn, làm đề trực tuyến và xem điểm.

Đăng ký công khai tạo tài khoản sinh viên. Tài khoản giảng viên được quản trị viên cấp; vai trò không được người đăng ký tự chọn.

## Chuẩn bị

- JDK 11, Maven và Apache Tomcat 9.
- SQL Server đang chạy TCP/IP trên cổng `1433`.

Với database mới, trong SQL Server Management Studio chạy lần lượt `Nhom6_PRJ/database/schema.sql`, `Nhom6_PRJ/database/SQLQuery1.sql`, `Nhom6_PRJ/database/exams.sql`, `Nhom6_PRJ/database/subjects_and_admin.sql` và `Nhom6_PRJ/database/task_course_access.sql`. `SQLQuery1.sql` tạo dữ liệu mẫu và chỉ nên chạy một lần. Với database đã có, chạy `Nhom6_PRJ/database/remember_me.sql`, `Nhom6_PRJ/database/exams.sql`, `Nhom6_PRJ/database/subjects_and_admin.sql` và `Nhom6_PRJ/database/task_course_access.sql`; có thể chạy lại các migration để tạo các bảng/cột còn thiếu. Migration môn học ghép bài thi/bài tập cũ vào môn của giảng viên nếu giảng viên chỉ được phân công đúng một môn; nội dung còn mơ hồ được để chưa gắn môn và không hiển thị cho học sinh đến khi được tạo lại trong môn phù hợp. Chạy `Nhom6_PRJ/database/migrate_optional_github_username.sql` một lần nếu database cũ đang có ràng buộc duy nhất trực tiếp trên `github_username`. Chạy `Nhom6_PRJ/database/setup_demo_lecturer.sql` nếu cần tài khoản giảng viên demo.

Khi khởi động, ứng dụng tạo tài khoản quản trị viên nếu chưa có tài khoản `ADMIN` trong database. Đặt các biến môi trường sau cho tiến trình Tomcat trước khi khởi động; mật khẩu cần có ít nhất 12 ký tự và không được lưu trong source code:

```powershell
$env:AITA_ADMIN_USERNAME = "admin"
$env:AITA_ADMIN_FULL_NAME = "Quan tri vien"
$env:AITA_ADMIN_EMAIL = "admin@example.com"
$env:AITA_ADMIN_PASSWORD = "replace-with-a-unique-12-character-password"
```

Đăng nhập bằng username/email và mật khẩu khởi tạo này; hệ thống bắt buộc đổi mật khẩu trước khi dùng các chức năng khác. Quản trị viên có thể cấp tài khoản giảng viên, quản lý tài khoản sinh viên toàn hệ thống, tạo môn học, phân công giảng viên và tạo lớp kèm danh sách học sinh. Giảng viên chỉ xem môn được phân công và danh sách sinh viên theo từng lớp của các môn đó; khi giao bài hoặc tạo đề thi, phải chọn một môn mình được phân công. Học sinh chỉ xem và làm/nộp bài thi, bài tập của những môn có lớp mà mình đã được ghi danh; các quyền này cũng được kiểm tra ở API, không chỉ ẩn trên giao diện. API quản lý sinh viên toàn hệ thống chỉ dành cho quản trị viên. Học sinh cần có tài khoản trước khi được ghi danh vào lớp; các em có thể đăng ký từ trang đăng nhập. Người dùng đã đăng nhập có thể đổi mật khẩu từ biểu tượng chìa khóa; thao tác yêu cầu mật khẩu hiện tại và thu hồi token “ghi nhớ đăng nhập” cũ.

## Đề thi và chấm tự động

Giảng viên tải CSV hoặc XLSX lên từ màn “Đề thi”. File XLSX dùng trang tính đầu tiên; hàng đầu tiên phải có đủ các cột sau:

```text
type,question,points,option_a,option_b,option_c,option_d,correct_answer
```

`type` nhận `MCQ` hoặc `SHORT_TEXT`. Câu `MCQ` cần ít nhất hai lựa chọn và `correct_answer` là chữ cái A-D. Câu `SHORT_TEXT` để trống các cột lựa chọn và ghi đáp án trong `correct_answer`. Câu trả lời ngắn được so khớp sau khi bỏ khoảng trắng đầu/cuối và không phân biệt chữ hoa/thường. Sinh viên làm bài trực tiếp trên web, chỉ được nộp một lần cho mỗi đề; hệ thống lưu điểm vào database và hiển thị ngay sau khi nộp. Có file mẫu tại `Nhom6_PRJ/src/main/webapp/files/exam-template.csv`.

## Bài tập tự luận nộp file

Trong mục “Đề thi & bài tập”, giảng viên chọn “Giao bài tập”, nhập tên/yêu cầu, có thể đặt hạn nộp và đính kèm file đề bài. Hạn nộp có thể được thay đổi sau khi giao; sinh viên chỉ nộp hoặc nộp lại trước hạn. Để bỏ hạn, xóa giá trị ngày giờ rồi chọn “Lưu hạn”. Sinh viên có thể tải file đề; giảng viên xem danh sách và tải bài từng sinh viên. Tệp đề và bài nộp được lưu trong SQL Server. Hỗ trợ PDF, DOC, DOCX, ODT, RTF và TXT, tối đa 15 MB cho mỗi tệp. Khi cập nhật hệ thống hiện có, chạy lại `Nhom6_PRJ/database/exams.sql` để thêm cột hạn nộp nếu chưa có.

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

Dashboard đọc điểm từ `Contribution_Scores` và số commit từ `Commits`. Mục “Tài khoản sinh viên” dành cho quản trị viên để thêm, sửa, xóa tài khoản có role `STUDENT`; database có thể từ chối xóa nếu sinh viên còn bản ghi điểm đang tham chiếu. Giảng viên vào “Môn học” → “Quản lý lớp” → “Sinh viên lớp” để chỉ xem danh sách sinh viên thuộc lớp đang phụ trách.
