package at.jku.dke.task_app.or_view.evaluation;

import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrViewReportTest {

    private OrViewReport report;
    private OrViewTask task;

    @BeforeEach
    void setup() {
        MessageSource ms = mock(MessageSource.class);
        when(ms.getMessage(anyString(), any(), any()))
            .thenAnswer(i -> i.getArgument(0));
        report = new OrViewReport(ms);

        task = new OrViewTask();
        task.setMaxPoints(BigDecimal.TEN);
        task.setExpectedIdentifier("id");
    }

    private EvaluationService.QueryResult qr(List<String> cols, List<List<String>> rows) {
        return new EvaluationService.QueryResult(cols, rows);
    }

    // Helper to call build() with default empty lists for all detail parameters
    private at.jku.dke.etutor.task_app.dto.GradingDto build(
        SubmissionMode mode, int level,
        EvaluationService.QueryResult student, EvaluationService.QueryResult teacher,
        boolean correct, SQLException error,
        boolean oidValid, boolean typeValid, boolean superviewValid, boolean makeRefMissing,
        Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
        List<String> missingColumnNames, List<String> extraColumnNames
    ) {
        return report.build(task, mode, level, student, teacher, correct, error,
            Locale.GERMAN, oidValid, typeValid, superviewValid, makeRefMissing,
            detectedErrors, missingColumnNames, extraColumnNames,
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
    }

    // ========================================
    // RUN mode tests
    // ========================================

    @Test
    void runMode_syntaxError_shouldReturnZeroPoints() {
        var result = build(SubmissionMode.RUN, 3, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertEquals(BigDecimal.TEN, result.maxPoints());
    }

    @Test
    void runMode_syntaxError_shouldShowSyntaxCriterionFailed() {
        var result = build(SubmissionMode.RUN, 3, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(1, result.criteria().size());
        assertEquals("criterium.syntax", result.criteria().getFirst().name());
        assertFalse(result.criteria().getFirst().passed());
    }

    @Test
    void runMode_validSolution_shouldReturnZeroPoints() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));
        var result = build(SubmissionMode.RUN, 3, student, null, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
    }

    @Test
    void runMode_validSolution_shouldShowSyntaxPassedAndQuery() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));
        var result = build(SubmissionMode.RUN, 0, student, null, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(2, result.criteria().size());
        assertEquals("criterium.syntax", result.criteria().get(0).name());
        assertTrue(result.criteria().get(0).passed());
        assertEquals("criterium.result", result.criteria().get(1).name());
    }

    @Test
    void runMode_ignoresFeedbackLevel() {
        var student = qr(List.of("ID"), List.of(List.of("1")));

        var level0 = build(SubmissionMode.RUN, 0, student, null, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
        var level3 = build(SubmissionMode.RUN, 3, student, null, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(level0.criteria().size(), level3.criteria().size());
        assertEquals(level0.points(), level3.points());
    }

    // Syntax error tests
    @Test
    void submit_syntaxError_shouldReturnZeroPoints() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertFalse(result.criteria().getFirst().passed());
    }

    @Test
    void submit_syntaxError_level0_showsSyntaxCriterionOnly() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(1, result.criteria().size());
        assertEquals("criterium.syntax", result.criteria().getFirst().name());
    }

    @Test
    void submit_syntaxError_feedbackIsMissingSemicolon() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals("error.missingSemicolon", result.criteria().getFirst().feedback());
    }

    @Test
    void submit_syntaxError_unbalancedParenthesis() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("UNBALANCED_PARENTHESIS"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertEquals("error.missingParenthesis", result.criteria().getFirst().feedback());
    }

    @Test
    void submit_syntaxError_missingCastForMultiset() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("MISSING_CAST_FOR_MULTISET"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertEquals("error.missingCastForMultiset", result.criteria().getFirst().feedback());
    }

    @Test
    void submit_syntaxError_wrongViewName() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("WRONG_VIEW_NAME"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertEquals("error.invalidViewName", result.criteria().getFirst().feedback());
    }

    @Test
    void submit_syntaxError_missingSelectInMultiset() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("MISSING_SELECT_IN_MULTISET"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertEquals("error.missingKeyword", result.criteria().getFirst().feedback());
    }

    @Test
    void submit_syntaxError_level3_showsPenaltyInFeedback() {
        var result = build(SubmissionMode.SUBMIT, 3, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().getFirst().feedback().contains("penalty"));
    }

    // Oracle syntax error tests
    @Test
    void submit_oracleSyntaxError_shouldBeSyntaxError() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("ORA-00942: table or view does not exist"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertEquals("criterium.syntax", result.criteria().getFirst().name());
        assertFalse(result.criteria().getFirst().passed());
    }

    @Test
    void submit_oracleSyntaxError_tableNotFound_feedback() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("ORA-00942: table or view does not exist"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals("error.tableNotFound", result.criteria().getFirst().feedback());
    }

    @Test
    void submit_oracleSyntaxError_missingSeparator_feedback() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false,
            new SQLException("ORA-00917: missing comma"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals("error.missingSeparator", result.criteria().getFirst().feedback());
    }

    // Correct solution tests
    @Test
    void submit_correct_shouldReturnFullPoints() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 3, student, teacher, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.TEN, result.points());
    }

    @Test
    void submit_correct_level0_showsSyntaxAndQuery() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 0, student, teacher, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(2, result.criteria().size());
        assertEquals("criterium.syntax", result.criteria().get(0).name());
        assertEquals("criterium.result", result.criteria().get(1).name());
        assertTrue(result.criteria().get(0).passed());
        assertTrue(result.criteria().get(1).passed());
    }

    @Test
    void diagnose_correct_shouldReturnFullPoints() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.DIAGNOSE, 3, student, teacher, true, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.TEN, result.points());
    }

    @Test
    void diagnose_syntaxError_shouldReturnZeroPoints() {
        var result = build(SubmissionMode.DIAGNOSE, 3, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
        assertFalse(result.criteria().getFirst().passed());
    }

    // Level 0 tests
    @Test
    void diagnose_incorrect_level0_showsSyntaxAndQuery() {
        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.DIAGNOSE, 0, student, teacher, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(2, result.criteria().size());
        assertEquals("criterium.syntax", result.criteria().get(0).name());
        assertEquals("criterium.result", result.criteria().get(1).name());
    }

    @Test
    void submit_incorrect_noErrors_shouldReturnZeroPoints() {
        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 0, student, teacher, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
    }

    // Level 1 tests
    @Test
    void submit_incorrectOid_level1_showsStructureCriterion() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            false, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .anyMatch(c -> "criterium.structure".equals(c.name()) && !c.passed()));
    }

    @Test
    void submit_incorrectSuperview_level1_showsStructureCriterion() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, false, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .anyMatch(c -> "criterium.structure".equals(c.name()) && !c.passed()));
    }

    @Test
    void submit_columnMismatch_level1_showsStructureCriterion() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .anyMatch(c -> "criterium.structure".equals(c.name()) && !c.passed()));
    }

    @Test
    void submit_rowMismatch_level1_showsContentCriterion() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("2", "Bob")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .anyMatch(c -> "criterium.content".equals(c.name()) && !c.passed()));
    }

    @Test
    void diagnose_incorrect_level1_showsContentCriterion() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("2", "Bob")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.DIAGNOSE, 1, student, teacher, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .anyMatch(c -> "criterium.content".equals(c.name()) && !c.passed()));
    }

    // Level 2 tests
    @Test
    void submit_incorrectOid_level2_showsOidLocation() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 2, student, teacher, false, null,
            false, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_OID, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.oid")));
    }

    @Test
    void submit_columnMismatch_level2_showsColumnLocation() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 2, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.columns")));
    }

    @Test
    void submit_rowMismatch_level2_showsRowLocation() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("2", "Bob")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 2, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_CONTENT, 1),
            Collections.emptyList(), Collections.emptyList());

        var contentCriteria = result.criteria().stream()
            .filter(c -> "criterium.content".equals(c.name()))
            .toList();
        assertFalse(contentCriteria.isEmpty());
        assertTrue(contentCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.rows")));
    }

    @Test
    void submit_missingPrimitiveField_noColMismatch_level2_showsSelectListLocation() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 2, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            List.of("DUE"), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()) || "\u00A0".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.selectList")));
    }

    @Test
    void submit_invalidColumnName_noColMismatch_level2_showsInvalidColumnLocation() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 2, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME, 1),
            Collections.emptyList(), List.of("EXTRA"));

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()) || "\u00A0".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.invalidColumn")));
    }

    @Test
    void submit_missingObjectField_noColMismatch_level2_showsMakeRefLocation() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 2, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()) || "\u00A0".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.makeRef")));
    }

    // Level 3 tests
    @Test
    void submit_incorrectOid_level3_showsGranularDetail() {
        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 3, student, teacher, false, null,
            false, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_OID, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.detail.expectedOid")));
    }

    @Test
    void submit_rowMismatch_level3_showsGranularRowDetail() {
        var student = qr(List.of("ID", "NAME"), List.of(List.of("2", "Bob")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 3, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_CONTENT, 1),
            Collections.emptyList(), Collections.emptyList());

        var contentCriteria = result.criteria().stream()
            .filter(c -> "criterium.content".equals(c.name()))
            .toList();
        assertFalse(contentCriteria.isEmpty());
        assertTrue(contentCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("content.error.detail")));
    }

    @Test
    void submit_level3_showsPenaltyInStructureFeedback() {
        task.setWrongOidPenalty(new BigDecimal("3"));

        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 3, student, teacher, false, null,
            false, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_OID, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("penalty")));
    }

    @Test
    void submit_missingPrimitiveField_level3_showsColumnNames() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("1"));

        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.SUBMIT, 3, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            List.of("NAME"), Collections.emptyList());

        var allCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()) || "\u00A0".equals(c.name()))
            .toList();
        assertTrue(allCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("NAME")));
    }

    // Oracle structure error tests
    @Test
    void submit_oracleStructureError_shouldNotBeSyntaxError() {
        var result = build(SubmissionMode.SUBMIT, 2, null, null, false,
            new SQLException("ORA-02303: cannot drop or replace a type with type or table dependents"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE, 1),
            Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .anyMatch(c -> "criterium.structure".equals(c.name())));
    }

    @Test
    void submit_oracleStructureError_level2_showsTypeLocation() {
        var result = build(SubmissionMode.SUBMIT, 2, null, null, false,
            new SQLException("ORA-02303: cannot drop or replace type"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.type")));
    }

    @Test
    void submit_oracleStructureError_castMultiset_level2_showsCastMultisetTypeLocation() {
        var result = build(SubmissionMode.SUBMIT, 2, null, null, false,
            new SQLException("ORA-22903: MULTISET expression not allowed"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.castMultisetType")));
    }

    @Test
    void submit_oracleStructureError_columnCount_level2_showsSelectListLocation() {
        var result = build(SubmissionMode.SUBMIT, 2, null, null, false,
            new SQLException("ORA-01730: invalid number of column names specified"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.selectList")));
    }

    @Test
    void submit_oracleStructureError_level3_showsGranularDetail() {
        var result = build(SubmissionMode.SUBMIT, 3, null, null, false,
            new SQLException("ORA-22903: MULTISET expression not allowed"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE, 1),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null
                && c.feedback().contains("error.invalidCastMultiset.detail")));
    }

    @Test
    void submit_oracleStructureError_ora00904_level2_showsInvalidColumnLocation() {
        var result = build(SubmissionMode.SUBMIT, 2, null, null, false,
            new SQLException("ORA-00904: \"S\".\"FINISHE\": invalid identifier"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME, 1),
            Collections.emptyList(), List.of("FINISHE"));

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()))
            .toList();
        assertFalse(structureCriteria.isEmpty());
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null && c.feedback().contains("error.location.invalidColumn")));
    }

    @Test
    void submit_oracleStructureError_ora00904_wrongContent_showsContentCriterion() {
        var result = build(SubmissionMode.SUBMIT, 3, null, null, false,
            new SQLException("ORA-00904: \"S\".\"ID\": invalid identifier"),
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_CONTENT, 1),
            Collections.emptyList(), Collections.emptyList());

        var contentCriteria = result.criteria().stream()
            .filter(c -> "criterium.content".equals(c.name()))
            .toList();
        assertFalse(contentCriteria.isEmpty());
    }

    // Penalty calculation tests
    @Test
    void submit_incorrect_withPenalty_shouldDeductPoints() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("3"));

        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(new BigDecimal("7"), result.points());
    }

    @Test
    void submit_incorrect_withMultiplePenalties_shouldDeductAll() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("3"));
        task.setMissingNestedTablePenalty(new BigDecimal("4"));

        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(
                EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1,
                EvaluationService.ErrorCategory.MISSING_NESTED_TABLE, 1
            ),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(new BigDecimal("3"), result.points());
    }

    @Test
    void submit_incorrect_penaltyExceedsMaxPoints_shouldReturnZero() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("15"));

        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
    }

    @Test
    void submit_incorrect_noPenaltyConfigured_shouldNotDeductPoints() {
        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.TEN, result.points());
    }

    @Test
    void submit_syntaxError_withPenalty_shouldStillReturnZero() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("3"));

        var result = build(SubmissionMode.SUBMIT, 1, null, null, false,
            new SQLException("MISSING_SEMICOLON"),
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertEquals(BigDecimal.ZERO, result.points());
    }

    @Test
    void submit_incorrect_allPenalties_shouldDeductAll() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("1"));
        task.setMissingObjectFieldPenalty(new BigDecimal("1"));
        task.setMissingNestedTablePenalty(new BigDecimal("1"));
        task.setWrongNestedTableTypePenalty(new BigDecimal("1"));
        task.setWrongViewObjectTypePenalty(new BigDecimal("1"));
        task.setWrongOidPenalty(new BigDecimal("1"));
        task.setWrongContentPenalty(new BigDecimal("1"));

        var student = qr(List.of("ID"), List.of(List.of("2")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            false, true, false, false,
            Map.of(
                EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 1,
                EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD, 1,
                EvaluationService.ErrorCategory.MISSING_NESTED_TABLE, 1,
                EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE, 1,
                EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE, 1,
                EvaluationService.ErrorCategory.WRONG_OID, 1,
                EvaluationService.ErrorCategory.WRONG_CONTENT, 1
            ),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(new BigDecimal("3"), result.points());
    }

    @Test
    void submit_multipleMissingPrimitiveFields_penaltyMultiplied() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("2"));

        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID", "A", "B", "C"), List.of(List.of("1", "x", "y", "z")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 3),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(new BigDecimal("4"), result.points());
    }

    @Test
    void submit_multipleMissingPrimitiveFields_level3_showsCountInFeedback() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("2"));

        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID", "A", "B", "C"), List.of(List.of("1", "x", "y", "z")));

        var result = build(SubmissionMode.SUBMIT, 3, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD, 3),
            Collections.emptyList(), Collections.emptyList());

        var structureCriteria = result.criteria().stream()
            .filter(c -> "criterium.structure".equals(c.name()) || "\u00A0".equals(c.name()))
            .toList();
        assertTrue(structureCriteria.stream()
            .anyMatch(c -> c.feedback() != null
                && c.feedback().contains("3x")
                && c.feedback().contains("penalty")));
    }

    @Test
    void diagnose_incorrect_withPenalty_shouldDeductPoints() {
        task.setWrongContentPenalty(new BigDecimal("4"));

        var student = qr(List.of("ID", "NAME"), List.of(List.of("2", "Bob")));
        var teacher = qr(List.of("ID", "NAME"), List.of(List.of("1", "Alice")));

        var result = build(SubmissionMode.DIAGNOSE, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.WRONG_CONTENT, 1),
            Collections.emptyList(), Collections.emptyList());

        assertEquals(new BigDecimal("6"), result.points());
    }

    @Test
    void submit_invalidColumnName_penaltyUsesMissingPrimitiveFieldPenalty() {
        task.setMissingPrimitiveFieldPenalty(new BigDecimal("2"));

        var student = qr(List.of("ID"), List.of(List.of("1")));
        var teacher = qr(List.of("ID"), List.of(List.of("1")));

        var result = build(SubmissionMode.SUBMIT, 1, student, teacher, false, null,
            true, true, true, false,
            Map.of(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME, 1),
            Collections.emptyList(), List.of("EXTRA"));

        assertEquals(new BigDecimal("8"), result.points());
    }


    // Edge cases
    @Test
    void submit_nullStudentResult_noQueryCriterion() {
        var result = build(SubmissionMode.SUBMIT, 0, null, null, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        assertTrue(result.criteria().stream()
            .noneMatch(c -> "criterium.result".equals(c.name())));
    }

    @Test
    void submit_emptyRows_showsEmptyResultMessage() {
        var student = qr(List.of(), List.of());

        var result = build(SubmissionMode.SUBMIT, 0, student, null, false, null,
            true, true, true, false,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

        var resultCriteria = result.criteria().stream()
            .filter(c -> "criterium.result".equals(c.name()))
            .toList();
        assertFalse(resultCriteria.isEmpty());
        assertTrue(resultCriteria.getFirst().feedback().contains("result.empty"));
    }
}
