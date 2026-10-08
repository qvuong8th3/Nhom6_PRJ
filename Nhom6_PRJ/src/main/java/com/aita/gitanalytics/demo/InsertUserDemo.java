package com.aita.gitanalytics.demo;

import com.aita.gitanalytics.dao.UserDAO;
import java.util.Scanner;

public class InsertUserDemo {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        UserDAO userDAO = new UserDAO();

        int choice;

        do {

            System.out.println();
            System.out.println("====================================");
            System.out.println("          USER MANAGEMENT");
            System.out.println("====================================");
            System.out.println("1. Thêm user");
            System.out.println("2. Xem danh sách user");
            System.out.println("3. Sửa user");
            System.out.println("4. Xóa user");
            System.out.println("5. Đăng nhập");
            System.out.println("0. Thoát");
            System.out.println("====================================");

            System.out.print("Chọn chức năng: ");

            try {

                choice =
                        Integer.parseInt(
                                scanner.nextLine()
                        );

            } catch (NumberFormatException e) {

                System.out.println(
                        "❌ Vui lòng nhập số!"
                );

                choice = -1;
            }

            switch (choice) {

                case 1:
                    addUser(scanner, userDAO);
                    break;

                case 2:
                    viewUsers(userDAO);
                    break;

                case 3:
                    updateUser(scanner, userDAO);
                    break;

                case 4:
                    deleteUser(scanner, userDAO);
                    break;

                case 5:
                    login(scanner, userDAO);
                    break;

                case 0:
                    System.out.println(
                            "👋 Đã thoát chương trình."
                    );
                    break;

                default:

                    System.out.println(
                            "❌ Lựa chọn không hợp lệ!"
                    );
            }

        } while (choice != 0);

        scanner.close();
    }


    // =====================================
    // THÊM USER
    // =====================================

    private static void addUser(
            Scanner scanner,
            UserDAO userDAO) {

        System.out.println();
        System.out.println(
                "========== THÊM USER =========="
        );

        System.out.print("Username: ");
        String username =
                scanner.nextLine();

        System.out.print("Họ và tên: ");
        String fullName =
                scanner.nextLine();

        System.out.print("Email: ");
        String email =
                scanner.nextLine();

        System.out.print("Github Username: ");
        String githubUsername =
                scanner.nextLine();

        System.out.print("Mật khẩu: ");
        String password =
                scanner.nextLine();

        System.out.print(
                "Role (STUDENT/LECTURER): "
        );

        String role =
                scanner.nextLine();

        boolean success =
                userDAO.createUser(
                        username,
                        fullName,
                        email,
                        githubUsername,
                        password,
                        role
                );

        if (success) {

            System.out.println(
                    "✅ Thêm user thành công!"
            );

        } else {

            System.out.println(
                    "❌ Thêm user thất bại!"
            );
        }
    }


    // =====================================
    // XEM USER
    // =====================================

    private static void viewUsers(
            UserDAO userDAO) {

        System.out.println();
        System.out.println(
                "========== DANH SÁCH USER =========="
        );

        userDAO.getAllUsers();
    }


    // =====================================
    // SỬA USER
    // =====================================

    private static void updateUser(
            Scanner scanner,
            UserDAO userDAO) {

        System.out.println();
        System.out.println(
                "========== SỬA USER =========="
        );

        System.out.print(
                "Nhập User ID cần sửa: "
        );

        int userId;

        try {

            userId =
                    Integer.parseInt(
                            scanner.nextLine()
                    );

        } catch (NumberFormatException e) {

            System.out.println(
                    "❌ User ID phải là số!"
            );

            return;
        }

        System.out.print(
                "Username mới: "
        );

        String username =
                scanner.nextLine();

        System.out.print(
                "Họ và tên mới: "
        );

        String fullName =
                scanner.nextLine();

        System.out.print(
                "Email mới: "
        );

        String email =
                scanner.nextLine();

        System.out.print(
                "Github Username mới: "
        );

        String githubUsername =
                scanner.nextLine();

        System.out.print(
                "Mật khẩu mới "
                + "(Enter nếu giữ nguyên): "
        );

        String password =
                scanner.nextLine();

        System.out.print(
                "Role mới (STUDENT/LECTURER): "
        );

        String role =
                scanner.nextLine();

        boolean success =
                userDAO.updateUser(
                        userId,
                        username,
                        fullName,
                        email,
                        githubUsername,
                        password,
                        role
                );

        if (success) {

            System.out.println(
                    "✅ Sửa user thành công!"
            );

        } else {

            System.out.println(
                    "❌ Không tìm thấy user "
                    + "hoặc sửa thất bại!"
            );
        }
    }


    // =====================================
    // XÓA USER
    // =====================================

    private static void deleteUser(
            Scanner scanner,
            UserDAO userDAO) {

        System.out.println();
        System.out.println(
                "========== XÓA USER =========="
        );

        System.out.print(
                "Nhập User ID cần xóa: "
        );

        int userId;

        try {

            userId =
                    Integer.parseInt(
                            scanner.nextLine()
                    );

        } catch (NumberFormatException e) {

            System.out.println(
                    "❌ User ID phải là số!"
            );

            return;
        }

        System.out.print(
                "Bạn có chắc muốn xóa? (Y/N): "
        );

        String confirm =
                scanner.nextLine();

        if (!confirm.equalsIgnoreCase("Y")) {

            System.out.println(
                    "❌ Đã hủy xóa."
            );

            return;
        }

        boolean success =
                userDAO.deleteUser(userId);

        if (success) {

            System.out.println(
                    "✅ Xóa user thành công!"
            );

        } else {

            System.out.println(
                    "❌ Không tìm thấy user!"
            );
        }
    }


    // =====================================
    // ĐĂNG NHẬP BẰNG EMAIL
    // =====================================

    private static void login(
            Scanner scanner,
            UserDAO userDAO) {

        System.out.println();
        System.out.println(
                "========== ĐĂNG NHẬP =========="
        );

        System.out.print("Email: ");

        String email =
                scanner.nextLine();

        System.out.print("Mật khẩu: ");

        String password =
                scanner.nextLine();

        boolean success =
                userDAO.login(
                        email,
                        password
                );

        if (success) {

            System.out.println(
                    "✅ Đăng nhập thành công!"
            );

        } else {

            System.out.println(
                    "❌ Email hoặc mật khẩu không đúng!"
            );
        }
    }
}