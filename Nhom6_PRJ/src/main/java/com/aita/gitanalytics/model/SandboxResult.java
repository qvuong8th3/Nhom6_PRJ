package com.aita.gitanalytics.model;

public class SandboxResult {
    private String status;   // Trạng thái: PASSED, TIMEOUT, ERROR
    private double score;    // Điểm số: 10.0, 0.0...
    private String logOutput;// Chi tiết terminal log để hiện lên giao diện

    public SandboxResult(String status, double score, String logOutput) {
        this.status = status;
        this.score = score;
        this.logOutput = logOutput;
    }

    // Các hàm Getters để JSP có thể lấy dữ liệu (Sử dụng Expression Language ${result.status})
    public String getStatus() { return status; }
    public double getScore() { return score; }
    public String getLogOutput() { return logOutput; }
}
