package com.aita.gitanalytics.service;

import com.aita.gitanalytics.model.SandboxResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class MoulinetteSandboxService {

    // Thay đổi kiểu trả về từ void thành SandboxResult
    public SandboxResult runStudentCode(String studentFolderPath) { 
        try {
            String[] command = {
                "docker", "run", "--rm",
                "--memory=512m",
                "--cpus=1.0",
                "-v", studentFolderPath + ":/app", 
                "-w", "/app",                      
                "eclipse-temurin:17-jdk-alpine", 
                "sh", "-c", "javac Main.java && java Main" 
            };

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); 
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder outputLog = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                outputLog.append(line).append("\n");
            }

            boolean isFinished = process.waitFor(10, TimeUnit.SECONDS);

            if (!isFinished) {
                process.destroy(); 
                // Trả về Object khi bị Timeout
                return new SandboxResult("TIMEOUT (TLE)", 0.0, "Vượt quá giới hạn thời gian 10 giây.");
            } else {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    // Trả về Object khi code chạy đúng
                    return new SandboxResult("PASSED", 10.0, outputLog.toString());
                } else {
                    // Trả về Object khi code bị lỗi cú pháp/runtime
                    return new SandboxResult("LỖI BIÊN DỊCH / RUNTIME", 0.0, outputLog.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SandboxResult("SYSTEM ERROR", 0.0, e.getMessage());
        }
    }
}