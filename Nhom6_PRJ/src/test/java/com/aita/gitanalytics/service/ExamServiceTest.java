package com.aita.gitanalytics.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

public class ExamServiceTest {

    private final ExamService examService = new ExamService();

    @Test
    public void parsesCsvWithMultipleQuestionTypes() {
        String csv = "type,question,points,option_a,option_b,option_c,option_d,correct_answer\n"
                + "MCQ,Java là gì?,2,Ngôn ngữ,Trình duyệt,,,A\n"
                + "SHORT_TEXT,Thủ đô Việt Nam?,3,,,,,Hà Nội\n";

        List<ExamService.QuestionDraft> questions = examService.parseSpreadsheet(
                "exam.csv", new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, questions.size());
        assertEquals("MCQ", questions.get(0).getType());
        assertEquals("A", questions.get(0).getCorrectAnswer());
        assertEquals("SHORT_TEXT", questions.get(1).getType());
        assertEquals("Hà Nội", questions.get(1).getCorrectAnswer());
    }

    @Test
    public void gradesShortTextIgnoringOuterWhitespaceAndCase() {
        assertTrue(ExamService.isCorrect("SHORT_TEXT", "Hà Nội", "  hà nội "));
        assertFalse(ExamService.isCorrect("SHORT_TEXT", "Hà Nội", "Hà Nội, Việt Nam"));
    }

    @Test
    public void parsesXlsxQuestions() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet();
            String[] headers = {"type", "question", "points", "option_a", "option_b",
                "option_c", "option_d", "correct_answer"};
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("MCQ");
            row.createCell(1).setCellValue("Java là gì?");
            row.createCell(2).setCellValue(2);
            row.createCell(3).setCellValue("Ngôn ngữ");
            row.createCell(4).setCellValue("Trình duyệt");
            row.createCell(7).setCellValue("A");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        List<ExamService.QuestionDraft> questions = examService.parseSpreadsheet(
                "exam.xlsx", new ByteArrayInputStream(workbookBytes));

        assertEquals(1, questions.size());
        assertEquals("A", questions.get(0).getCorrectAnswer());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMcqAnswerWithoutMatchingOption() {
        String csv = "type,question,points,option_a,option_b,option_c,option_d,correct_answer\n"
                + "MCQ,Java là gì?,2,Ngôn ngữ,Trình duyệt,,,C\n";

        examService.parseSpreadsheet("exam.csv",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }
}