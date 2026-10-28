package com.aita.gitanalytics.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class GitScoreCalculatorServiceTest {

    private final GitScoreCalculatorService calculator = new GitScoreCalculatorService();

    @Test
    public void calculatesSuggestedScoresFromGitHistoryAndNormalizesGroupToOneHundred() {
        Map<String, GitScoreCalculatorService.StudentGitData> group = new LinkedHashMap<>();
        group.put("member-a", new GitScoreCalculatorService.StudentGitData("A", 3, 100, 100));
        group.put("member-b", new GitScoreCalculatorService.StudentGitData("B", 1, 100, 0));

        Map<String, Double> scores = calculator.calculateGroupContributions(group);

        assertEquals(67.5, scores.get("member-a"), 0.001);
        assertEquals(32.5, scores.get("member-b"), 0.001);
        assertEquals(100.0, scores.values().stream().mapToDouble(Double::doubleValue).sum(), 0.001);
    }

    @Test
    public void returnsZeroRecommendationsWhenGroupHasNoGitActivity() {
        Map<String, GitScoreCalculatorService.StudentGitData> group = new LinkedHashMap<>();
        group.put("member-a", new GitScoreCalculatorService.StudentGitData("A", 0, 0, 0));
        group.put("member-b", new GitScoreCalculatorService.StudentGitData("B", 0, 0, 0));

        Map<String, Double> scores = calculator.calculateGroupContributions(group);

        assertEquals(0.0, scores.get("member-a"), 0.001);
        assertEquals(0.0, scores.get("member-b"), 0.001);
        assertTrue(calculator.calculateGroupContributions(null).isEmpty());
    }

    @Test
    public void detectsSpamCommitMessagesAccurately() {
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("fix"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("update"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("asdf"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("wip"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("done"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("fix bug"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage("ok"));
        assertTrue(GitScoreCalculatorService.isSpamCommitMessage(""));
        assertTrue(!GitScoreCalculatorService.isSpamCommitMessage("feat(auth): implement remember-me login filter"));
        assertTrue(!GitScoreCalculatorService.isSpamCommitMessage("refactor(dao): optimize commit logs query index"));
    }

    @Test
    public void identifiesFreeRiderAndAppliesPenaltiesForLastMinuteDump() {
        Map<String, GitScoreCalculatorService.StudentGitData> group = new LinkedHashMap<>();
        
        // Thành viên chăm chỉ: Nhiều LOC, đều đặn, có PRs và review
        group.put("member-hardworking", new GitScoreCalculatorService.StudentGitData(
                1, "Chăm Chỉ", 15, 1200, 95.0, 3, 4,
                java.util.List.of("feat: add core servlet", "test: add unit test", "fix(dao): null check"),
                false
        ));

        // Thành viên Free-rider: Ít LOC, dồn commit phút chót (isLastMinuteDumper = true), commit rác
        group.put("member-freerider", new GitScoreCalculatorService.StudentGitData(
                2, "Ỷ Lại", 2, 20, 15.0, 0, 0,
                java.util.List.of("update", "fix"),
                true
        ));

        Map<String, GitScoreCalculatorService.EvaluatedContribution> eval =
                calculator.evaluateGroupContributions(group, new GitScoreCalculatorService.AssessorWeights());

        // Kiểm tra thành viên ỷ lại bị gắn cờ Free-rider
        GitScoreCalculatorService.EvaluatedContribution freeRiderEval = eval.get("member-freerider");
        assertTrue(freeRiderEval.isFreeRiderSuspect);
        assertTrue(freeRiderEval.penaltyScore > 0);
        assertTrue(!freeRiderEval.penaltyReasons.isEmpty());

        // Tổng tỉ lệ đóng góp của nhóm vẫn được chuẩn hóa về 100%
        double sum = eval.values().stream().mapToDouble(e -> e.normalizedPercentage).sum();
        assertEquals(100.0, sum, 0.01);
    }
}
