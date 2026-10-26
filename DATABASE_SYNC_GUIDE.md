# HƯỚNG DẪN ĐỒNG BỘ CƠ SỞ DỮ LIỆU & CHẠY DỰ ÁN CHO TOÀN BỘ THÀNH VIÊN (NHÓM 6)

Tài liệu này hướng dẫn cách đồng bộ 100% mã nguồn và cơ sở dữ liệu để **tất cả thành viên trong nhóm** và **môi trường web** chạy hoàn toàn giống nhau, không bị lỗi thiếu bảng, lỗi sai cổng, hay lỗi mật khẩu.

---

## 🎯 CÁCH 1: ĐỒNG BỘ QUA DATABASE ONLINE TRÊN WEB (DÙNG CHUNG CẢ NHÓM)
> **Khuyên dùng nếu nhóm muốn làm việc nhóm thời gian thực:** Tất cả thành viên (và ứng dụng khi deploy lên web) đều kết nối vào một database duy nhất trên mạng. Bất kỳ ai nộp bài, chấm điểm hay commit Git thì toàn bộ mọi người đều thấy ngay lập tức.

### Bước 1: Khởi tạo database trên Cloud
Bạn có thể dùng bất kỳ dịch vụ SQL Server đám mây miễn phí nào (ví dụ: **Azure SQL Database for Students**, **Railway.app**, **Aiven**, **Render**, hoặc 1 máy chủ SQL Server mở port công khai):
1. Mở công cụ quản trị (SSMS, DBeaver hoặc Azure Query Editor).
2. Kết nối đến Cloud SQL Server.
3. Mở file `Nhom6_PRJ/database/init_full_database.sql` và nhấn **Execute (F5)** để tạo toàn bộ bảng và dữ liệu mẫu.

### Bước 2: Cấu hình trên máy từng thành viên
Mở file `Nhom6_PRJ/src/main/resources/db.properties` (hoặc tạo file `db.properties` ngay cạnh file chạy):
Bỏ dấu `#` ở dòng `db.url` và điền chuỗi kết nối:
```properties
db.url=jdbc:sqlserver://<host-cloud>:1433;databaseName=AITA_DB;user=<tai-khoan>;password=<mat-khau>;encrypt=true;trustServerCertificate=true;loginTimeout=15;
```
*(Chỉ cần cấu hình dòng này, tất cả thành viên trong nhóm sẽ tự động kết nối chung CSDL trên web mà không cần cài đặt SQL Server trên máy cá nhân).*

---

## 💻 CÁCH 2: ĐỒNG BỘ DATABASE LOCAL TRÊN MÁY TỪNG THÀNH VIÊN (1-CLICK)
> **Dành cho làm việc offline hoặc chạy kiểm thử độc lập trên máy cá nhân.**

### Cách làm tự động (Khuyên dùng):
Chỉ cần nhấp đúp chuột vào file:
👉 **`setup_database.bat`** (ở thư mục gốc dự án)

Script sẽ tự động:
1. Dò tìm phiên bản SQL Server đang chạy trên máy bạn (tự động nhận diện cổng 1433, 1434, `SQLEXPRESS`...).
2. Chạy file `init_full_database.sql` để tạo đầy đủ 24 bảng, khóa ngoại, ràng buộc và tài khoản mẫu.
3. Kích hoạt Mixed Mode (SQL Server and Windows Authentication) và cấp quyền cho user `sa` / `123`.
4. Tự động sinh file cấu hình `src/main/resources/db.properties` tương thích với máy bạn.

### Cách làm thủ công bằng SQL Server Management Studio (SSMS):
Nếu bạn không muốn chạy script tự động:
1. Mở SSMS, đăng nhập vào SQL Server trên máy bạn.
2. Mở file: `Nhom6_PRJ/Nhom6_PRJ/database/init_full_database.sql`.
3. Nhấn **Execute (F5)**.
4. Mở file `Nhom6_PRJ/Nhom6_PRJ/src/main/resources/db.properties`, kiểm tra:
   - `db.port`: Điền đúng cổng SQL Server của bạn (thường là `1433` hoặc `1434`).
   - `db.user`: `sa`
   - `db.password`: Mật khẩu `sa` trên máy bạn.

---

## 🚀 CHẠY ỨNG DỤNG (1-CLICK)

Sau khi đồng bộ database:
1. Nhấp đúp chuột vào file:
   👉 **`run_app.bat`**
   Script sẽ tự động biên dịch WAR, đưa vào Tomcat 9 và mở trình duyệt tại `http://localhost:8080/gitanalytics/login.jsp`.

2. Hoặc build thủ công:
   ```powershell
   cd Nhom6_PRJ
   mvn clean package -DskipTests
   ```
   Sau đó chép `target/gitanalytics.war` vào thư mục `webapps` của Apache Tomcat 9 và khởi động Tomcat.

---

## 🔑 DANH SÁCH TÀI KHOẢN KIỂM THỬ ĐÃ ĐỒNG BỘ

Toàn bộ tài khoản dưới đây đã được nạp sẵn vào CSDL qua `init_full_database.sql`:

| Vai trò | Tên đăng nhập | Mật khẩu | Email | Quyền hạn |
| :--- | :--- | :--- | :--- | :--- |
| **QUẢN TRỊ VIÊN (ADMIN)** | `admin` | `Admin@123456` | `admin@example.com` | Quản lý toàn bộ sinh viên, môn học, phân công giảng viên, cấu hình hệ thống |
| **GIẢNG VIÊN (LECTURER)** | `lecturer01` | `123456` | `lecturer01@gmail.com` | Quản lý lớp SE1801 môn PRJ301, giao bài tập, tạo đề thi, xem analytics Git |
| **GIẢNG VIÊN (ALIAS)** | `aita_lecturer` | `123456` | `aita_lecturer@gmail.com` | Tài khoản giảng viên demo dự phòng |
| **SINH VIÊN (STUDENT)** | `student01` | `123456` | `student01@gmail.com` | Thành viên Group 6, lớp SE1801. Làm đề thi, nộp bài tập, theo dõi đóng góp Git |
| **SINH VIÊN (STUDENT)** | `student02` | `123456` | `student02@gmail.com` | Thành viên Group 6 |
| **SINH VIÊN (STUDENT)** | `student03` | `123456` | `student03@gmail.com` | Thành viên Group 6 |
| **SINH VIÊN (STUDENT)** | `student04` | `123456` | `student04@gmail.com` | Thành viên Group 6 |
| **SINH VIÊN (STUDENT)** | `vothaonguyen222` | `123456` | `vothaonguyen222@gmail.com` | Sinh viên thành viên |
| **SINH VIÊN (STUDENT)** | `hainguyen` | `123456` | `hainguyen@gmail.com` | Sinh viên thành viên |

---

## 🛠️ XỬ LÝ SỰ CỐ THƯỜNG GẶP

### 1. Lỗi: "Login failed for user 'sa'"
- **Nguyên nhân:** SQL Server trên máy bạn đang bật chế độ chỉ dùng Windows Authentication, chưa bật Mixed Mode.
- **Cách khắc phục:**
  1. Mở SSMS -> Chuột phải vào tên Server ở cột bên trái -> Chọn **Properties**.
  2. Chọn thẻ **Security** -> Tại mục *Server authentication*, tích chọn **SQL Server and Windows Authentication mode** -> Nhấn **OK**.
  3. Chuột phải lại vào tên Server -> Chọn **Restart** (hoặc mở Services -> chuột phải `SQL Server (MSSQLSERVER)` -> `Restart`).

### 2. Lỗi: "The TCP/IP connection to the host localhost, port 1433 has failed"
- **Nguyên nhân:** SQL Server đang chạy ở cổng khác (ví dụ cổng `1434` hoặc Dynamic Port của `SQLEXPRESS`), hoặc giao thức TCP/IP chưa được Enable.
- **Cách khắc phục:**
  - `DBContext.java` trong dự án đã được tích hợp cơ chế tự động dò tìm cổng `1434` nếu `1433` bị từ chối.
  - Bạn chỉ cần chạy script `setup_database.bat` để script tự dò cổng và ghi vào `db.properties`.
  - Hoặc mở **Sql Server Configuration Manager** -> *SQL Server Network Configuration* -> *Protocols for MSSQLSERVER* -> Bật **TCP/IP** thành **Enabled**.
