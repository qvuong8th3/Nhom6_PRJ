package com.aita.gitanalytics.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dịch vụ Trợ lý Sư phạm AI (Module FE-L-03)
 * 
 * Nhiệm vụ trọng tâm:
 * Chuyển đổi các lỗi biên dịch khô khan và log lỗi từ Docker Sandbox thành các 
 * hướng dẫn sư phạm trực quan, áp dụng phương pháp gợi mở Socratic và phân tích 
 * ngữ cảnh mã nguồn Git theo thời gian thực.
 */
public class PedagogicalGuidanceService {

    public static class PedagogicalFeedback {
        public String errorCategory;
        public String rawErrorSummary;
        public String pedagogicalExplanation;
        public String socraticQuestion;
        public List<String> actionSteps = new ArrayList<>();
        public String gitContextAdvice;
        public String cleanCodeTip;
        public String severity; // INFO, WARNING, CRITICAL

        public PedagogicalFeedback(String errorCategory, String rawErrorSummary,
                                   String pedagogicalExplanation, String socraticQuestion,
                                   String gitContextAdvice, String cleanCodeTip, String severity) {
            this.errorCategory = errorCategory;
            this.rawErrorSummary = rawErrorSummary;
            this.pedagogicalExplanation = pedagogicalExplanation;
            this.socraticQuestion = socraticQuestion;
            this.gitContextAdvice = gitContextAdvice;
            this.cleanCodeTip = cleanCodeTip;
            this.severity = severity;
        }
    }

    /**
     * Chuyển đổi lỗi biên dịch và mã nguồn thành hướng dẫn sư phạm trực quan
     */
    public PedagogicalFeedback transformError(String rawError, String codeSnippet, String studentName) {
        if (rawError == null || rawError.trim().isEmpty()) {
            return new PedagogicalFeedback(
                "MÃ NGUỒN BIÊN DỊCH THÀNH CÔNG",
                "Không có lỗi biên dịch",
                "Mã nguồn của bạn đã vượt qua tầng kiểm tra cú pháp javac và sẵn sàng cho các bài kiểm thử tự động trong Docker Sandbox.",
                "Bạn có chắc chắn rằng các trường hợp biên (Edge Cases) như giá trị null, chuỗi rỗng hay dữ liệu âm đã được bẫy ngoại lệ chu đáo chưa?",
                "Hãy tạo một commit mang thông điệp rõ ràng theo chuẩn Conventional Commits (ví dụ: `feat(dao): add null safety check for user query`) trước khi nộp.",
                "Tuân thủ triệt để mô hình MVC2: Tuyệt đối không nhúng logic truy vấn SQL hay tính toán nghiệp vụ vào trang JSP/View.",
                "INFO"
            );
        }

        String lower = rawError.toLowerCase(Locale.ROOT);
        String name = (studentName != null && !studentName.trim().isEmpty()) ? studentName.trim() : "bạn";

        // Tích hợp LLM API (OpenAI/Gemini 5.1): Nếu có API key, gọi mô hình sinh lời khuyên trực quan
        PedagogicalFeedback llmFeedback = tryGenerateWithLLM(rawError, codeSnippet, name);
        if (llmFeedback != null) {
            return llmFeedback;
        }

        // 1. Lỗi Cannot Find Symbol
        if (lower.contains("cannot find symbol") || lower.contains("symbol not found")) {
            PedagogicalFeedback fb = new PedagogicalFeedback(
                "LỖI ĐỊNH DANH (CANNOT FIND SYMBOL)",
                "Trình biên dịch không tìm thấy biến, phương thức hoặc lớp khai báo.",
                "Chào " + name + ", trình biên dịch Java giống như một người thủ thư nghiêm khắc. Khi gặp một tên gọi mà chưa thấy khai báo trước đó hoặc chưa import thư viện, người thủ thư sẽ dừng lại ngay lập tức.",
                "Biến hoặc phương thức này đã được import đúng package chưa? Tên gọi có bị sai sót chữ hoa/chữ thường (Java phân biệt Case-Sensitive) hay nằm ngoài phạm vi (Scope) không?",
                "Kiểm tra `git diff` của commit gần nhất để xem bạn có lỡ tay đổi tên method hoặc xóa nhầm dòng `import` ở đầu file không.",
                "Đặt tên theo chuẩn CamelCase (`getUserById`, `totalScore`) và tận dụng tính năng Auto-Import của IDE thay vì gõ tay.",
                "WARNING"
            );
            fb.actionSteps.add("Kiểm tra dòng khai báo package và import ở đầu file .java.");
            fb.actionSteps.add("Đối chiếu chính xác từng ký tự chữ hoa/thường giữa nơi khai báo và nơi gọi.");
            fb.actionSteps.add("Xác nhận file chứa class/method đó đã được lưu (Save) và nằm trong cùng Source Directory.");
            return fb;
        }

        // 2. Lỗi Incompatible Types / Type Mismatch
        if (lower.contains("incompatible types") || lower.contains("type mismatch")) {
            PedagogicalFeedback fb = new PedagogicalFeedback(
                "BẤT ĐỒNG KIỂU DỮ LIỆU (INCOMPATIBLE TYPES)",
                "Ép kiểu dữ liệu không hợp lệ giữa hai vế của biểu thức.",
                "Java là ngôn ngữ tĩnh có kiểm tra kiểu dữ liệu nghiêm ngặt (Strongly-Typed). Bạn đang cố gắng đặt một chiếc hộp hình tròn vào một lỗ hình vuông mà không có bộ chuyển đổi thích hợp.",
                "Giá trị bên vế phải trả về kiểu gì (ví dụ: String, int, BigDecimal) và biến ở vế trái được khai báo để nhận kiểu gì?",
                "Khi cập nhật schema Database hoặc Model class, hãy tạo một nhánh Git (feature branch) riêng để tránh làm gãy luồng của đồng đội.",
                "Sử dụng các phương thức chuyển đổi chuẩn như `Integer.parseInt()`, `String.valueOf()` và kiểm tra null trước khi ép kiểu.",
                "WARNING"
            );
            fb.actionSteps.add("Rà soát kiểu trả về của method được gọi xem có khớp với kiểu biến gán không.");
            fb.actionSteps.add("Sử dụng parse an toàn có bẫy NumberFormatException.");
            fb.actionSteps.add("Đối với điểm số hoặc tiền tệ trong PRJ301, ưu tiên `BigDecimal` hoặc `double`.");
            return fb;
        }

        // 3. Lỗi NullPointerException / Unhandled Exception
        if (lower.contains("nullpointerexception") || lower.contains("npe") || lower.contains("unreported exception")) {
            PedagogicalFeedback fb = new PedagogicalFeedback(
                "NGOẠI LỆ CON TRỎ RỖNG (NULLPOINTEREXCEPTION / EXCEPTION)",
                "Truy cập thuộc tính hoặc gọi phương thức trên một đối tượng đang mang giá trị null.",
                "Lỗi này xảy ra khi chương trình cố gắng mở một chiếc phong bì nhưng bên trong hoàn toàn trống rỗng. Đây là nguyên nhân hàng đầu khiến bài tập bị rớt các Hidden Test Cases trong Moulinette Sandbox.",
                "Đối tượng này đã được khởi tạo qua từ khóa `new` hoặc được gán kết quả từ DAO chưa? Liệu database có trả về dòng nào với điều kiện query này không?",
                "Quan sát lịch sử Git: Nếu một thành viên trong nhóm sửa DAO mà không báo trước, các hàm gọi ở tầng Servlet sẽ rất dễ nhận về null.",
                "Thực hiện nguyên tắc Defensive Programming: Luôn kiểm tra `if (obj != null)` hoặc dùng `Optional<T>` trước khi gọi hàm con.",
                "CRITICAL"
            );
            fb.actionSteps.add("Xác định chính xác dòng gây lỗi trong stack trace.");
            fb.actionSteps.add("Đặt câu hỏi: Trong các biến trên dòng đó, biến nào có thể nhận giá trị null từ request/session/database?");
            fb.actionSteps.add("Thêm khối `try-catch` và trả về thông báo thân thiện cho người dùng thay vì để lộ màn hình lỗi 500.");
            return fb;
        }

        // 4. Lỗi JDBC / SQL Syntax / Connection Leak
        if (lower.contains("sqlexception") || lower.contains("connection") || lower.contains("syntax error near") || lower.contains("jdbc")) {
            PedagogicalFeedback fb = new PedagogicalFeedback(
                "LỖI TƯƠNG TÁC CƠ SỞ DỮ LIỆU (JDBC / SQL ACCESS)",
                "Truy vấn SQL sai cú pháp hoặc thất bại khi kết nối tới SQL Server.",
                "Tầng DAO (Data Access Object) của bạn gặp trở ngại khi giao tiếp với hệ quản trị CSDL. Trong barem PRJ301 (CLO1-CLO3), kết nối JDBC phải luôn an toàn, chống rò rỉ bộ nhớ (Connection Leak).",
                "Bạn đã giải phóng Connection, PreparedStatement và ResultSet sau khi dùng xong chưa? Tên bảng hoặc tên cột trong câu lệnh SQL có khớp 100% với file AITA_DB.sql không?",
                "Nếu bạn mới thay đổi bảng hoặc cột trong database, hãy lập tức commit file `.sql` cập nhật lên GitHub kèm mô tả chi tiết để cả nhóm đồng bộ.",
                "Áp dụng cấu trúc `try-with-resources` để Java tự động đóng tài nguyên, ngăn chặn nghẽn pool kết nối khi chạy Sandbox.",
                "CRITICAL"
            );
            fb.actionSteps.add("Copy câu lệnh SQL ra SQL Server Management Studio (SSMS) để chạy thử độc lập.");
            fb.actionSteps.add("Kiểm tra các tham số truyền vào dấu hỏi chấm `ps.setX(1, ...)` có đủ và đúng thứ tự 1-based index không.");
            fb.actionSteps.add("Bọc toàn bộ Connection và PreparedStatement trong `try-with-resources`.");
            return fb;
        }

        // 5. Lỗi Sandbox / Timeout / Resource Limit
        if (lower.contains("timeout") || lower.contains("time limit") || lower.contains("outofmemory") || lower.contains("killed") || lower.contains("sandbox")) {
            PedagogicalFeedback fb = new PedagogicalFeedback(
                "VƯỢT GIỚI HẠN TÀI NGUYÊN DOCKER SANDBOX (RESOURCE EXCEEDED)",
                "Bài nộp bị ngắt do chạy quá thời gian cho phép hoặc tiêu tốn bộ nhớ vượt ngưỡng.",
                "Hệ thống Docker Sandbox (Section 4.5.2) áp dụng cơ chế cô lập nghiêm ngặt: không mạng, giới hạn RAM và CPU. Chương trình của bạn có thể đang vướng vào vòng lặp vô tận (Infinite Loop) hoặc truy vấn N+1 làm cạn kiệt tài nguyên.",
                "Trong các vòng lặp `while`, `for`, điều kiện dừng có chắc chắn đạt được trong mọi trường hợp không? Bạn có đang tải toàn bộ bảng dữ liệu lớn vào bộ nhớ RAM không?",
                "Hãy kiểm tra xem nhóm có commit nào đưa file nhị phân lớn hoặc vòng lặp đệ quy chưa có điểm dừng cơ sở (Base Case) không.",
                "Sử dụng phân trang `OFFSET ... FETCH NEXT` khi truy vấn dữ liệu lớn và tối ưu độ phức tạp thuật toán O(N) thay vì O(N²).",
                "CRITICAL"
            );
            fb.actionSteps.add("Kiểm tra các vòng lặp `while(rs.next())` và các vòng lặp xử lý danh sách.");
            fb.actionSteps.add("Kiểm tra điều kiện thoát đệ quy.");
            fb.actionSteps.add("Đảm bảo các luồng InputStream / OutputStream đều có `flush()` và `close()`.");
            return fb;
        }

        // 6. Lỗi chung / Default Fallback
        PedagogicalFeedback fb = new PedagogicalFeedback(
            "PHÂN TÍCH NGOẠI LỆ BIÊN DỊCH TỔNG QUÁT",
            rawError.length() > 120 ? rawError.substring(0, 120) + "..." : rawError,
            "Hệ thống nhận diện một lỗi trong quá trình biên dịch hoặc chạy thử nghiệm mã nguồn của bạn. Đừng nản lòng, việc gặp lỗi là bước thiết yếu trong học tập dựa trên nghiên cứu (Research-Based Learning).",
            "Nguyên nhân gốc rễ (Root Cause) xuất phát từ tệp mã nguồn nào và dòng bao nhiêu? Dòng mã đó chịu trách nhiệm xử lý nghiệp vụ gì?",
            "Tập thói quen commit thường xuyên các thay đổi nhỏ hoạt động được, giúp bạn dễ dàng so sánh hoặc revert khi gặp lỗi khó.",
            "Tuân thủ triệt để mô hình MVC2: Tầng Controller (Servlet) xử lý điều hướng, Tầng Model (DAO/Service) xử lý nghiệp vụ, Tầng View (JSP) chỉ hiển thị dữ liệu.",
            "WARNING"
        );
        fb.actionSteps.add("Đọc kỹ dòng đầu tiên của thông báo lỗi để xác định file và số dòng.");
        fb.actionSteps.add("Tách nhỏ logic phức tạp thành các phương thức hỗ trợ đơn giản (Modularization).");
        fb.actionSteps.add("Chạy thử nghiệm cục bộ trước khi đẩy code lên repository của nhóm.");
        return fb;
    }

    /**
     * Tích hợp LLM API (Gemini/OpenAI) qua Prompt Engineering theo phương pháp sư phạm Socratic
     */
    private PedagogicalFeedback tryGenerateWithLLM(String rawError, String codeSnippet, String studentName) {
        String geminiApiKey = System.getenv("GEMINI_API_KEY");
        String openAiApiKey = System.getenv("OPENAI_API_KEY");

        if ((geminiApiKey == null || geminiApiKey.trim().isEmpty()) &&
            (openAiApiKey == null || openAiApiKey.trim().isEmpty())) {
            return null; // Không có API key -> chuyển sang fallback pattern matching
        }

        try {
            String prompt = "Bạn là Trợ lý Sư phạm AI của hệ sinh thái AITA (PRJ301 Java Web). "
                    + "Hãy phân tích lỗi sau cho sinh viên " + studentName + " theo phương pháp Socratic (hỏi mở, gợi ý tư duy, không giải hộ):\n"
                    + "Lỗi: " + rawError + "\n"
                    + "Đoạn code: " + (codeSnippet != null ? codeSnippet : "Không kèm đoạn mã") + "\n"
                    + "Trả về JSON duy nhất không kèm markdown format với các trường:\n"
                    + "{\n"
                    + "  \"errorCategory\": \"Tên phân loại lỗi\",\n"
                    + "  \"pedagogicalExplanation\": \"Lời giải thích sư phạm thân thiện\",\n"
                    + "  \"socraticQuestion\": \"Câu hỏi mở kích thích tư duy\",\n"
                    + "  \"actionSteps\": [\"Bước 1\", \"Bước 2\"],\n"
                    + "  \"gitContextAdvice\": \"Lời khuyên về commit/nhánh Git\",\n"
                    + "  \"cleanCodeTip\": \"Mẹo Clean Code / MVC2\",\n"
                    + "  \"severity\": \"WARNING\"\n"
                    + "}";

            String responseJson = null;
            if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey.trim();
                JsonObject reqBody = new JsonObject();
                JsonArray contents = new JsonArray();
                JsonObject part = new JsonObject();
                JsonArray parts = new JsonArray();
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", prompt);
                parts.add(textPart);
                part.add("parts", parts);
                contents.add(part);
                reqBody.add("contents", contents);

                responseJson = postJson(endpoint, reqBody.toString(), null);
                if (responseJson != null) {
                    JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
                    String text = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                            .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                            .get("text").getAsString();
                    text = text.replaceAll("```json", "").replaceAll("```", "").trim();
                    return parseLlmJson(text, rawError);
                }
            } else if (openAiApiKey != null && !openAiApiKey.trim().isEmpty()) {
                String endpoint = "https://api.openai.com/v1/chat/completions";
                JsonObject reqBody = new JsonObject();
                reqBody.addProperty("model", "gpt-3.5-turbo");
                JsonArray messages = new JsonArray();
                JsonObject msg = new JsonObject();
                msg.addProperty("role", "user");
                msg.addProperty("content", prompt);
                messages.add(msg);
                reqBody.add("messages", messages);

                responseJson = postJson(endpoint, reqBody.toString(), "Bearer " + openAiApiKey.trim());
                if (responseJson != null) {
                    JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
                    String text = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                    text = text.replaceAll("```json", "").replaceAll("```", "").trim();
                    return parseLlmJson(text, rawError);
                }
            }
        } catch (Exception ignored) {
            // Lỗi mạng hoặc key không hợp lệ -> fallback tự nhiên
        }
        return null;
    }

    private PedagogicalFeedback parseLlmJson(String jsonStr, String rawError) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            String cat = obj.has("errorCategory") ? obj.get("errorCategory").getAsString() : "PHÂN TÍCH AI (GEMINI/OPENAI)";
            String exp = obj.has("pedagogicalExplanation") ? obj.get("pedagogicalExplanation").getAsString() : "Giải thích sư phạm từ AI";
            String q = obj.has("socraticQuestion") ? obj.get("socraticQuestion").getAsString() : "Bạn hãy kiểm tra lại cấu trúc mã nguồn.";
            String git = obj.has("gitContextAdvice") ? obj.get("gitContextAdvice").getAsString() : "Kiểm tra commit gần nhất trên Git.";
            String tip = obj.has("cleanCodeTip") ? obj.get("cleanCodeTip").getAsString() : "Tuân thủ mô hình MVC2.";
            String sev = obj.has("severity") ? obj.get("severity").getAsString() : "WARNING";

            PedagogicalFeedback fb = new PedagogicalFeedback(cat, rawError.length() > 100 ? rawError.substring(0, 100) + "..." : rawError,
                    exp, q, git, tip, sev);

            if (obj.has("actionSteps") && obj.get("actionSteps").isJsonArray()) {
                for (var s : obj.getAsJsonArray("actionSteps")) {
                    fb.actionSteps.add(s.getAsString());
                }
            }
            return fb;
        } catch (Exception e) {
            return null;
        }
    }

    private String postJson(String endpoint, String body, String authHeader) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }
        return null;
    }
}
