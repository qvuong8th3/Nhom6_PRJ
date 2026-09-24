package com.aita.gitanalytics.demo;

import com.aita.gitanalytics.dao.UserDAO;
import java.util.Scanner;

public class InsertUserDemo {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        UserDAO userDAO = new UserDAO();

        System.out.println("=== NHẬP THÔNG TIN NGƯỜI DÙNG MỚI ===");

        System.out.print("Nhập Họ và tên: ");
        String fullName = scanner.nextLine();

        System.out.print("Nhập Email: ");
        String email = scanner.nextLine();

        System.out.print("Nhập Github Username: ");
        String githubUsername = scanner.nextLine();

        // Thực hiện thêm vào CSDL
        boolean success = userDAO.createUser(fullName, email, githubUsername);

        if (success) {
            System.out.println("\n✅ Đã thêm user thành công vào cơ sở dữ liệu!");
        } else {
            System.out.println("\n❌ Thêm user thất bại. Kiểm tra lại thông tin hoặc Email bị trùng!");
        }

        scanner.close();
    }
}
