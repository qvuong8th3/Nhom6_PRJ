package com.aita.gitanalytics.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

public class ExamService {

    private static final int MAX_QUESTIONS = 200;
    private static final List<String> HEADERS = List.of(
            "type", "question", "points", "option_a", "option_b", "option_c",
            "option_d", "correct_answer");

    public List<QuestionDraft> parseSpreadsheet(String fileName, InputStream content) {
        if (fileName == null || content == null) {
            throw new IllegalArgumentException("Vui lòng chọn file CSV hoặc XLSX.");
        }
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".csv")) {
            return parseCsv(content);
        }
        if (normalizedName.endsWith(".xlsx")) {
            return parseXlsx(content);
        }
        throw new IllegalArgumentException("Chỉ hỗ trợ file .csv hoặc .xlsx.");
    }

    public static boolean isCorrect(String type, String expected, String submitted) {
        if (expected == null || submitted == null) {
            return false;
        }
        String answer = submitted.trim();
        if ("MCQ".equals(type)) {
            return expected.trim().equalsIgnoreCase(answer);
        }
        return "SHORT_TEXT".equals(type)
                && expected.trim().equalsIgnoreCase(answer);
    }

    private List<QuestionDraft> parseCsv(InputStream content) {
        try (Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            Map<String, String> headers = normalizedHeaders(parser.getHeaderMap().keySet());
            validateHeaders(headers.keySet());
            List<QuestionDraft> questions = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String header : HEADERS) {
                    values.put(header, record.get(headers.get(header)));
                }
                addQuestion(questions, values);
            }
            return requireQuestions(questions);
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("Không đọc được file CSV.", e);
        }
    }

    private List<QuestionDraft> parseXlsx(InputStream content) {
        try (Workbook workbook = WorkbookFactory.create(content)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("File Excel không có trang tính.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("File Excel chưa có dòng tiêu đề.");
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Map<String, Integer> headers = new LinkedHashMap<>();
            for (int index = 0; index < headerRow.getLastCellNum(); index++) {
                String header = normalizeHeader(formatter.formatCellValue(
                        headerRow.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
                if (!header.isEmpty()) {
                    headers.put(header, index);
                }
            }
            validateHeaders(headers.keySet());

            List<QuestionDraft> questions = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String header : HEADERS) {
                    values.put(header, formatter.formatCellValue(
                            row.getCell(headers.get(header), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
                }
                addQuestion(questions, values);
            }
            return requireQuestions(questions);
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được file XLSX.", e);
        }
    }

    private Map<String, String> normalizedHeaders(Set<String> originalHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String header : originalHeaders) {
            headers.put(normalizeHeader(header), header);
        }
        return headers;
    }

    private String normalizeHeader(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validateHeaders(Set<String> headers) {
        if (!headers.containsAll(HEADERS)) {
            throw new IllegalArgumentException(
                    "File cần có các cột: type, question, points, option_a, option_b, "
                            + "option_c, option_d, correct_answer.");
        }
    }

    private void addQuestion(List<QuestionDraft> questions, Map<String, String> values) {
        boolean emptyRow = values.values().stream().allMatch(value -> value == null || value.trim().isEmpty());
        if (emptyRow) {
            return;
        }
        if (questions.size() >= MAX_QUESTIONS) {
            throw new IllegalArgumentException("Một đề không được vượt quá 200 câu hỏi.");
        }

        String type = value(values, "type").toUpperCase(Locale.ROOT);
        if ("TEXT".equals(type)) {
            type = "SHORT_TEXT";
        }
        String question = value(values, "question");
        if (!"MCQ".equals(type) && !"SHORT_TEXT".equals(type)) {
            throw new IllegalArgumentException("Loại câu hỏi chỉ nhận MCQ hoặc SHORT_TEXT.");
        }
        if (question.isEmpty()) {
            throw new IllegalArgumentException("Nội dung câu hỏi không được để trống.");
        }
        if (question.length() > 2000) {
            throw new IllegalArgumentException("Nội dung câu hỏi tối đa 2000 ký tự.");
        }

        BigDecimal points;
        try {
            points = new BigDecimal(value(values, "points"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Điểm mỗi câu phải là một số lớn hơn 0.", e);
        }
        if (points.signum() <= 0) {
            throw new IllegalArgumentException("Điểm mỗi câu phải lớn hơn 0.");
        }

        String correctAnswer = value(values, "correct_answer");
        Map<String, String> options = new LinkedHashMap<>();
        if ("MCQ".equals(type)) {
            String[] keys = {"A", "B", "C", "D"};
            String[] headers = {"option_a", "option_b", "option_c", "option_d"};
            for (int index = 0; index < keys.length; index++) {
                String option = value(values, headers[index]);
                if (!option.isEmpty()) {
                    if (option.length() > 1000) {
                        throw new IllegalArgumentException("Mỗi lựa chọn tối đa 1000 ký tự.");
                    }
                    options.put(keys[index], option);
                }
            }
            if (options.size() < 2) {
                throw new IllegalArgumentException("Câu trắc nghiệm cần ít nhất hai lựa chọn.");
            }
            correctAnswer = correctAnswer.toUpperCase(Locale.ROOT);
            if (!options.containsKey(correctAnswer)) {
                throw new IllegalArgumentException("Đáp án đúng phải là A, B, C hoặc D có lựa chọn tương ứng.");
            }
        } else if (correctAnswer.isEmpty()) {
            throw new IllegalArgumentException("Câu trả lời ngắn cần có đáp án chuẩn.");
        } else if (correctAnswer.length() > 1000) {
            throw new IllegalArgumentException("Đáp án tối đa 1000 ký tự.");
        }

        questions.add(new QuestionDraft(type, question, points, correctAnswer, options));
    }

    private String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    private List<QuestionDraft> requireQuestions(List<QuestionDraft> questions) {
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("File chưa có câu hỏi nào.");
        }
        return questions;
    }

    public static final class QuestionDraft {
        private final String type;
        private final String question;
        private final BigDecimal points;
        private final String correctAnswer;
        private final Map<String, String> options;

        private QuestionDraft(String type, String question, BigDecimal points,
                              String correctAnswer, Map<String, String> options) {
            this.type = type;
            this.question = question;
            this.points = points;
            this.correctAnswer = correctAnswer;
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        }

        public String getType() {
            return type;
        }

        public String getQuestion() {
            return question;
        }

        public BigDecimal getPoints() {
            return points;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public Map<String, String> getOptions() {
            return options;
        }
    }
}