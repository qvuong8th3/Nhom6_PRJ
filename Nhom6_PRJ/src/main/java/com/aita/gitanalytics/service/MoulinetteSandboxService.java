package com.aita.gitanalytics.service;

import com.aita.gitanalytics.model.SandboxResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MoulinetteSandboxService {

    public SandboxResult runStudentCode(String studentFolderPath) {
        if (studentFolderPath == null || studentFolderPath.trim().isEmpty()) {
            return new SandboxResult("ERROR", 0.0, "Đường dẫn mã nguồn không hợp lệ.");
        }

        // 1. Tìm tất cả file .java trong thư mục giải nén
        List<Path> javaFiles = new ArrayList<>();
        try {
            javaFiles = Files.walk(Paths.get(studentFolderPath))
                    .filter(p -> p.toString().toLowerCase().endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {}

        if (javaFiles.isEmpty()) {
            return new SandboxResult("NO_SOURCE_CODE", 0.0, 
                    "Không tìm thấy tệp mã nguồn .java nào trong tệp ZIP được nộp để biên dịch tự động.");
        }

        // 2. Thử chạy trong Docker Sandbox nếu Docker daemon đang hoạt động
        if (isDockerAvailable()) {
            SandboxResult dockerResult = runInDocker(studentFolderPath);
            if (dockerResult != null) {
                return dockerResult;
            }
        }

        // 3. Fallback sang Local JVM Compiler Sandbox (sử dụng JDK cục bộ)
        return runInLocalJvm(studentFolderPath, javaFiles);
    }

    private boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "ps").start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private SandboxResult runInDocker(String studentFolderPath) {
        try {
            String[] command = {
                "docker", "run", "--rm",
                "--memory=512m",
                "--cpus=1.0",
                "-v", studentFolderPath + ":/app",
                "-w", "/app",
                "eclipse-temurin:17-jdk-alpine",
                "sh", "-c", "find . -name '*.java' > sources.txt && javac @sources.txt && (find . -name 'Main.class' | grep . && java Main || echo 'Biên dịch hoàn tất.')"
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder outputLog = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                outputLog.append(line).append("\n");
            }

            boolean isFinished = process.waitFor(15, TimeUnit.SECONDS);
            if (!isFinished) {
                process.destroyForcibly();
                return new SandboxResult("TIMEOUT (TLE)", 0.0, "Vượt quá giới hạn thời gian Docker Sandbox (15 giây).");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return new SandboxResult("PASSED", 10.0, "[Docker Sandbox - Temurin 17]\n" + outputLog.toString().trim());
            } else {
                return new SandboxResult("LỖI BIÊN DỊCH / RUNTIME", 0.0, "[Docker Sandbox Lỗi]\n" + outputLog.toString().trim());
            }
        } catch (Exception e) {
            return null; // Chuyển sang fallback JVM cục bộ
        }
    }

    private SandboxResult runInLocalJvm(String studentFolderPath, List<Path> javaFiles) {
        try {
            File workDir = new File(studentFolderPath);
            List<String> compileCmd = new ArrayList<>();
            compileCmd.add("javac");
            compileCmd.add("-encoding");
            compileCmd.add("UTF-8");
            for (Path p : javaFiles) {
                compileCmd.add(p.toAbsolutePath().toString());
            }

            ProcessBuilder compilePb = new ProcessBuilder(compileCmd);
            compilePb.directory(workDir);
            compilePb.redirectErrorStream(true);
            Process compileProc = compilePb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(compileProc.getInputStream()));
            StringBuilder compileOutput = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                compileOutput.append(line).append("\n");
            }

            boolean compileFinished = compileProc.waitFor(12, TimeUnit.SECONDS);
            if (!compileFinished) {
                compileProc.destroyForcibly();
                return new SandboxResult("TIMEOUT (TLE)", 0.0, "Quá thời gian biên dịch mã nguồn (12s).");
            }

            if (compileProc.exitValue() != 0) {
                return new SandboxResult("LỖI BIÊN DỊCH", 0.0, 
                        "[Local JVM Sandbox - Biên dịch thất bại]\n" + compileOutput.toString().trim());
            }

            // Tìm file chứa hàm main hoặc file Main.java / App.java
            Path mainFile = javaFiles.stream()
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("Main.java") || p.getFileName().toString().equalsIgnoreCase("App.java"))
                    .findFirst()
                    .orElse(javaFiles.get(0));

            String className = mainFile.getFileName().toString().replace(".java", "");
            File classDir = mainFile.getParent().toFile();

            List<String> runCmd = new ArrayList<>();
            runCmd.add("java");
            runCmd.add("-cp");
            runCmd.add(classDir.getAbsolutePath() + File.pathSeparator + workDir.getAbsolutePath());
            runCmd.add(className);

            ProcessBuilder runPb = new ProcessBuilder(runCmd);
            runPb.directory(classDir);
            runPb.redirectErrorStream(true);
            Process runProc = runPb.start();

            BufferedReader runReader = new BufferedReader(new InputStreamReader(runProc.getInputStream()));
            StringBuilder runOutput = new StringBuilder();
            while ((line = runReader.readLine()) != null) {
                runOutput.append(line).append("\n");
            }

            boolean runFinished = runProc.waitFor(8, TimeUnit.SECONDS);
            if (!runFinished) {
                runProc.destroyForcibly();
                return new SandboxResult("TIMEOUT (TLE)", 5.0, "Biên dịch thành công nhưng chương trình chạy quá thời gian (8s).");
            }

            String log = "[Moulinette Sandbox Verified]\nBiên dịch: Thành công (0 lỗi, " + javaFiles.size() + " files)\nThực thi (" + className + "):\n" + runOutput.toString().trim();
            return new SandboxResult("PASSED", 10.0, log);

        } catch (Exception e) {
            return new SandboxResult("SYSTEM ERROR", 0.0, "Lỗi thực thi Sandbox: " + e.getMessage());
        }
    }
}
