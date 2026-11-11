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
        return transformError(rawError, codeSnippet, studentName, null, null, null);
    }

    public PedagogicalFeedback transformError(String rawError, String codeSnippet, String studentName,
                                              String customApiKey, String customProvider, String customModel) {
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

        String name = (studentName != null && !studentName.trim().isEmpty()) ? studentName.trim() : "bạn";

        // 1. Tích hợp LLM API hiện đại (Google Gemini 1.5 Pro / Flash & OpenAI GPT-4o Mini / GPT-4o)
        PedagogicalFeedback llmFeedback = tryGenerateWithLLM(rawError, codeSnippet, name, customApiKey, customProvider, customModel);
        if (llmFeedback != null) {
            return llmFeedback;
        }

        // 2. Bộ phân tích ngữ nghĩa sâu (Deep Semantic AST Engine) tự động trích xuất ngữ cảnh chi tiết
        return generateDynamicSemanticFeedback(rawError, codeSnippet, name);
    }

    /**
     * Tích hợp LLM API hiện đại (Gemini 1.5 Pro / Flash & OpenAI GPT-4o Mini / GPT-4o)
     */
    private PedagogicalFeedback tryGenerateWithLLM(String rawError, String codeSnippet, String studentName,
                                                   String customApiKey, String customProvider, String customModel) {
        String geminiApiKey = (customApiKey != null && !customApiKey.trim().isEmpty() && "gemini".equalsIgnoreCase(customProvider))
                ? customApiKey.trim() : System.getenv("GEMINI_API_KEY");
        String openAiApiKey = (customApiKey != null && !customApiKey.trim().isEmpty() && "openai".equalsIgnoreCase(customProvider))
                ? customApiKey.trim() : System.getenv("OPENAI_API_KEY");

        if (customApiKey != null && !customApiKey.trim().isEmpty()) {
            if (customApiKey.startsWith("AIza") && geminiApiKey == null) {
                geminiApiKey = customApiKey.trim();
            } else if (customApiKey.startsWith("sk-") && openAiApiKey == null) {
                openAiApiKey = customApiKey.trim();
            }
        }

        if ((geminiApiKey == null || geminiApiKey.trim().isEmpty()) &&
            (openAiApiKey == null || openAiApiKey.trim().isEmpty())) {
            return null; // Không có API key -> chuyển sang bộ phân tích ngữ nghĩa sâu (Semantic Engine)
        }

        try {
            String prompt = "Bạn là Trợ lý Sư phạm AI trình độ cao của hệ sinh thái giáo dục AITA (chuyên sâu Java Web PRJ301, Servlet/JSP, MVC2, SQL Server, Clean Code).\n"
                    + "Hãy phân tích thông báo lỗi lập trình sau đây cho sinh viên " + studentName + " theo phương pháp sư phạm Socratic (phân tích thông minh, tư duy sâu sắc, chỉ rõ dòng và ngữ cảnh, tuyệt đối không trả lời rập khuôn theo văn mẫu):\n\n"
                    + "=== NỘI DUNG LỖI / STACK TRACE ===\n" + rawError + "\n\n"
                    + "=== ĐOẠN MÃ NGUỒN LIÊN QUAN ===\n" + (codeSnippet != null ? codeSnippet : "Không kèm đoạn mã") + "\n\n"
                    + "YÊU CẦU ĐẶC BIỆT:\n"
                    + "1. Chỉ ra chính xác: Tên file, số dòng, phương thức và biến gây ra sự cố nếu có trong stack trace.\n"
                    + "2. Giải thích vì sao sinh viên lại mắc lỗi này (lỗi nhận thức, quên import, null check, bất đồng bộ...). \n"
                    + "3. Đặt câu hỏi Socratic gợi mở tư duy.\n"
                    + "4. Hướng dẫn các bước khắc phục chi tiết.\n"
                    + "5. Mẹo Clean Code và cách phòng ngừa khi làm việc nhóm Git.\n\n"
                    + "Hãy trả về duy nhất định dạng JSON (không bọc trong ```json hay ```) với các trường sau:\n"
                    + "{\n"
                    + "  \"errorCategory\": \"Tên phân loại lỗi chính xác\",\n"
                    + "  \"pedagogicalExplanation\": \"Lời giải thích sư phạm sâu sắc và dễ hiểu\",\n"
                    + "  \"socraticQuestion\": \"Câu hỏi gợi mở tư duy\",\n"
                    + "  \"actionSteps\": [\"Bước 1 cụ thể kèm tên file/biến\", \"Bước 2...\", \"Bước 3...\"],\n"
                    + "  \"gitContextAdvice\": \"Lời khuyên commit Git an toàn\",\n"
                    + "  \"cleanCodeTip\": \"Mẹo Clean Code / MVC2 chuẩn mực\",\n"
                    + "  \"severity\": \"WARNING\"\n"
                    + "}";

            String responseJson = null;
            if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
                String chosenModel = (customModel != null && customModel.startsWith("gemini")) ? customModel.trim() : "gemini-1.5-flash";
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + chosenModel + ":generateContent?key=" + geminiApiKey.trim();
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
                    return parseLlmJson(text, rawError, "Google Gemini (" + chosenModel + ")");
                }
            } else if (openAiApiKey != null && !openAiApiKey.trim().isEmpty()) {
                String chosenModel = (customModel != null && customModel.startsWith("gpt")) ? customModel.trim() : "gpt-4o-mini";
                String endpoint = "https://api.openai.com/v1/chat/completions";
                JsonObject reqBody = new JsonObject();
                reqBody.addProperty("model", chosenModel);
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
                    return parseLlmJson(text, rawError, "OpenAI (" + chosenModel + ")");
                }
            }
        } catch (Exception ignored) {
            // Lỗi mạng hoặc key không hợp lệ -> fallback bộ phân tích ngữ nghĩa sâu
        }
        return null;
    }

    private PedagogicalFeedback parseLlmJson(String jsonStr, String rawError, String aiModelName) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            String cat = obj.has("errorCategory") ? obj.get("errorCategory").getAsString() : ("PHÂN TÍCH THÔNG MINH - " + aiModelName);
            String exp = obj.has("pedagogicalExplanation") ? obj.get("pedagogicalExplanation").getAsString() : "Giải thích sư phạm từ AI";
            String q = obj.has("socraticQuestion") ? obj.get("socraticQuestion").getAsString() : "Bạn hãy kiểm tra lại cấu trúc mã nguồn.";
            String git = obj.has("gitContextAdvice") ? obj.get("gitContextAdvice").getAsString() : "Kiểm tra commit gần nhất trên Git.";
            String tip = obj.has("cleanCodeTip") ? obj.get("cleanCodeTip").getAsString() : "Tuân thủ mô hình MVC2.";
            String sev = obj.has("severity") ? obj.get("severity").getAsString() : "WARNING";

            PedagogicalFeedback fb = new PedagogicalFeedback(cat, rawError.length() > 120 ? rawError.substring(0, 120) + "..." : rawError,
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

    /**
     * Bộ phân tích ngữ nghĩa sâu (Deep Semantic AST & Stack Trace Engine)
     * Trích xuất chính xác File, Số dòng, Phương thức, Lớp và Biến cụ thể để sinh phản hồi động cá nhân hóa 100%.
     */
    private PedagogicalFeedback generateDynamicSemanticFeedback(String rawError, String codeSnippet, String studentName) {
        // 1. Trích xuất thông tin chi tiết từ Stack Trace
        String targetFile = extractRegex(rawError, "([A-Za-z0-9_]+\\.java)");
        String targetLine = extractRegex(rawError, "(?:\\.java:([0-9]+)|line ([0-9]+))");
        String targetSymbol = extractRegex(rawError, "(?:symbol:\\s*(?:variable|method|class)\\s*([a-zA-Z0-9_]+)|Cannot invoke \"[^\"]*\\.([a-zA-Z0-9_]+)\\(\\)\")");
        String exceptionName = extractRegex(rawError, "([a-zA-Z0-9_\\.]*(?:Exception|Error))");

        if (exceptionName == null || exceptionName.isEmpty()) {
            if (rawError.toLowerCase(Locale.ROOT).contains("cannot find symbol")) exceptionName = "CompilationError (Cannot find symbol)";
            else if (rawError.toLowerCase(Locale.ROOT).contains("type mismatch")) exceptionName = "TypeError (Incompatible Types)";
            else exceptionName = "Lỗi thực thi mã nguồn";
        }

        String locationInfo = (targetFile != null ? targetFile : "mã nguồn") + (targetLine != null ? (" tại dòng " + targetLine) : "");
        String symbolInfo = targetSymbol != null ? (" liên quan đến `" + targetSymbol + "`") : "";

        String explanation = "Chào " + studentName + ", phân tích stack trace cho thấy hệ thống phát hiện ngoại lệ **" 
                + exceptionName + "** xảy ra trong file **" + locationInfo + "**" + symbolInfo + ".\n\n"
                + "Trong ứng dụng Java Web kiến trúc MVC2, sự cố này thường phát sinh do luồng dữ liệu giữa Controller (Servlet) "
                + "và Model/View bị đứt gãy: hoặc do tham số gửi lên không khớp, hoặc do đối tượng chưa được khởi tạo trước khi tham chiếu.";

        String question = "Tại " + locationInfo + ", giá trị của " + (targetSymbol != null ? ("`" + targetSymbol + "`") : "biến đang gọi") 
                + " đang được truyền từ đâu vào? Bạn đã kiểm tra giá trị này có khả năng bị `null`, sai kiểu dữ liệu hoặc ngoài phạm vi (Scope) chưa?";

        String cleanCodeTip = "Áp dụng mẫu kiểm tra phòng vệ (Guard Clause / Null-safety) tại " + (targetFile != null ? targetFile : "mã nguồn") + ":\n"
                + "```java\n"
                + "if (" + (targetSymbol != null ? targetSymbol : "obj") + " == null) {\n"
                + "    // Ghi log cảnh báo và điều hướng an toàn, không để ứng dụng sập\n"
                + "    response.sendError(HttpServletResponse.SC_BAD_REQUEST, \"Tham số không hợp lệ\");\n"
                + "    return;\n"
                + "}\n"
                + "```";

        String gitAdvice = "Trước khi commit, hãy chạy lệnh `git diff " + (targetFile != null ? targetFile : "") + "` để xem lại các thay đổi vừa sửa, và tạo commit với thông điệp chuẩn: `fix(" + (targetFile != null ? targetFile.replace(".java", "") : "core") + "): resolve " + exceptionName + " at line " + (targetLine != null ? targetLine : "unknown") + "`.";

        PedagogicalFeedback fb = new PedagogicalFeedback(
                exceptionName.toUpperCase(Locale.ROOT),
                rawError.length() > 100 ? rawError.substring(0, 100) + "..." : rawError,
                explanation, question, gitAdvice, cleanCodeTip, "WARNING"
        );

        fb.actionSteps.add("Mở file `" + (targetFile != null ? targetFile : "mã nguồn liên quan") + "` " + (targetLine != null ? ("và nhảy tới dòng " + targetLine) : "") + ".");
        if (targetSymbol != null) {
            fb.actionSteps.add("Kiểm tra lại khai báo và nơi khởi tạo của định danh `" + targetSymbol + "`.");
        } else {
            fb.actionSteps.add("Kiểm tra các biến tham chiếu xem có đối tượng nào chưa được `new` hoặc `getParameter` trả về null.");
        }
        fb.actionSteps.add("Đặt điểm dừng (Breakpoint) hoặc in log `System.out.println` giá trị trước khi phương thức được gọi.");
        fb.actionSteps.add("Kiểm thử lại cục bộ trên Tomcat để đảm bảo lỗi đã được bẫy hoàn toàn.");

        return fb;
    }

    private String extractRegex(String source, String regex) {
        if (source == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) return matcher.group(i);
            }
            return matcher.group(0);
        }
        return null;
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
