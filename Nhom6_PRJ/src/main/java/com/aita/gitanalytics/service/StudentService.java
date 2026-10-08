package com.aita.gitanalytics.service;

import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.model.Student;
import java.util.List;

public class StudentService {

    private final UserDAO userDAO;

    public StudentService() {
        this(new UserDAO());
    }

    StudentService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public List<Student> getAllStudents() {
        return userDAO.getAllStudents();
    }

    public boolean saveStudent(String idValue, String username, String fullName, String email,
                               String githubUsername, String password) {
        if (isBlank(username) || isBlank(fullName) || isBlank(email)
                || (isBlank(idValue) && isBlank(password))) {
            throw new IllegalArgumentException("Vui lòng nhập đủ thông tin bắt buộc.");
        }

        if (isBlank(idValue)) {
            return userDAO.createUser(username, fullName, email, githubUsername, password, "STUDENT");
        }

        try {
            return userDAO.updateStudent(Integer.parseInt(idValue), username, fullName,
                    email, githubUsername, password);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã sinh viên không hợp lệ.", e);
        }
    }

    public boolean deleteStudent(String idValue) {
        if (isBlank(idValue)) {
            throw new IllegalArgumentException("Mã sinh viên không hợp lệ.");
        }
        try {
            return userDAO.deleteStudent(Integer.parseInt(idValue));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã sinh viên không hợp lệ.", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}