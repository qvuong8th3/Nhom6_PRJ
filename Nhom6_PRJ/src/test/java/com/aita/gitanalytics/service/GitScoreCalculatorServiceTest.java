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
}
