package at.jku.dke.task_app.or_view.evaluation;

import at.jku.dke.etutor.task_app.dto.CriterionDto;
import at.jku.dke.etutor.task_app.dto.GradingDto;
import at.jku.dke.etutor.task_app.dto.SubmissionMode;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates structured feedback and calculates points based on detected error categories.
 * Supports four feedback levels ranging from simple correct/incorrect to detailed error descriptions.
 */
public class OrViewReport {

    private final MessageSource messageSource;

    private record Entry(String detail, EvaluationService.ErrorCategory category) {}

    public OrViewReport(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Builds the grading result including feedback criteria and point calculation.
     *
     * @param task            the task configuration with penalties and max points
     * @param mode            the submission mode (RUN, DIAGNOSE, or SUBMIT)
     * @param level           the feedback detail level (0-3)
     * @param student         the query result of the student's view, or null if execution failed
     * @param teacher         the query result of the reference solution, or null
     * @param correct         whether the submission is fully correct
     * @param error           the Oracle SQL exception, or null if execution succeeded
     * @param locale          the locale for localized feedback messages
     * @param oidValid        whether the object identifier matches the expected value
     * @param typeValid       whether the view object type is correct
     * @param superviewValid  whether the UNDER relationship is correct
     * @param makeRefMissing  whether a MAKE_REF expression is missing
     * @param detectedErrors  map of error categories to their occurrence count
     * @param missingColumnNames   list of column names missing from the student solution
     * @param extraColumnNames     list of extra or invalid column names in the student solution
     * @param expectedMakeRefArgs  expected MAKE_REF arguments from the reference solution
     * @param actualMakeRefArgs    actual MAKE_REF arguments from the student solution
     * @param expectedConstructorCall expected constructor call from the reference solution
     * @param actualConstructorCall   actual constructor call from the student solution
     * @param expectedOfType     expected object type in the OF clause
     * @param actualOfType       actual object type in the OF clause
     * @param expectedCastType   expected collection type in the CAST expression
     * @param actualCastType     actual collection type in the CAST expression
     * @param expectedOid        expected object identifier value
     * @param actualOid          actual object identifier value
     * @param expectedWhereClause expected WHERE/JOIN clauses from the reference solution
     * @param actualWhereClause   actual WHERE/JOIN clauses from the student solution
     * @param expectedColumnOrder expected column order from the reference solution
     * @param actualColumnOrder   actual column order from the student solution
     * @return a GradingDto with points, general feedback, and detailed criteria
     */
    public GradingDto build(
        OrViewTask task,
        SubmissionMode mode,
        int level,
        EvaluationService.QueryResult student,
        EvaluationService.QueryResult teacher,
        boolean correct,
        SQLException error,
        Locale locale,
        boolean oidValid,
        boolean typeValid,
        boolean superviewValid,
        boolean makeRefMissing,
        Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
        List<String> missingColumnNames,
        List<String> extraColumnNames,
        List<String> expectedMakeRefArgs,
        List<String> actualMakeRefArgs,
        List<String> expectedConstructorCall,
        List<String> actualConstructorCall,
        List<String> expectedOfType,
        List<String> actualOfType,
        List<String> expectedCastType,
        List<String> actualCastType,
        List<String> expectedOid,
        List<String> actualOid,
        List<String> expectedWhereClause,
        List<String> actualWhereClause,
        List<String> expectedColumnOrder,
        List<String> actualColumnOrder
    ) {
        List<CriterionDto> criteria = new ArrayList<>();

        boolean hasError = error != null;
        boolean isSyntaxError = false;
        boolean isStructureError = false;

        if (hasError) {
            String msg = error.getMessage();
            if (msg == null) {
                isSyntaxError = true;
            } else if ("MISSING_SEMICOLON".equals(msg) ||
                "UNBALANCED_PARENTHESIS".equals(msg) ||
                "MISSING_SELECT_IN_MULTISET".equals(msg) ||
                "MISSING_FROM_IN_MULTISET".equals(msg) ||
                "MISSING_WHERE_IN_MULTISET".equals(msg) ||
                "MISSING_CAST_FOR_MULTISET".equals(msg) ||
                "WRONG_VIEW_NAME".equals(msg)) {
                isSyntaxError = true;
            } else if ("MISSING_UNDER".equals(msg)) {
                isStructureError = true;
            } else if (msg.contains("ORA-02303") || msg.contains("ORA-22907") ||
                msg.contains("ORA-00902") || msg.contains("ORA-02306") ||
                msg.contains("ORA-02315") || msg.contains("ORA-01730") ||
                msg.contains("ORA-22903") ||
                msg.contains("ORA-00904") || msg.contains("ORA-03048") ||
                msg.contains("ORA-00909") || msg.contains("ORA-00913") ||
                (msg.contains("ORA-00932") && (msg.toUpperCase().contains("TYPE") || msg.toUpperCase().contains("TYP")))) {
                isStructureError = true;
            } else if (msg.contains("ORA-01722") || msg.contains("ORA-61800")) {
                isStructureError = true;
            } else if (msg.contains("ORA-30738") || msg.contains("ORA-04043") ||
                msg.contains("ORA-00942") || msg.contains("ORA-00903") ||
                msg.contains("ORA-00917") || msg.contains("ORA-00971") ||
                msg.contains("ORA-00923") || msg.contains("ORA-00905") ||
                msg.contains("ORA-00900") || msg.contains("ORA-00922") ||
                msg.contains("ORA-02000") || msg.contains("ORA-00928") ||
                msg.contains("ORA-00907") || msg.contains("ORA-22974") ||
                msg.contains("ORA-02338") || msg.contains("ORA-00906") ||
                msg.contains("ORA-00936") ||
                msg.contains("ORA-00920") ||
                msg.contains("ORA-00999") ||
                msg.contains("ORA-02302") ||
                msg.contains("ORA-03049")) {
                isSyntaxError = true;
            } else {
                isSyntaxError = true;
            }
        }

        String general = switch (mode) {
            case RUN -> (isSyntaxError || isStructureError)
                ? messageSource.getMessage("runError", null, locale)
                : messageSource.getMessage("runSuccess", null, locale);
            case SUBMIT -> correct
                ? messageSource.getMessage("correct", null, locale)
                : messageSource.getMessage("incorrect", null, locale);
            default -> correct
                ? messageSource.getMessage("possiblyCorrect", null, locale)
                : messageSource.getMessage("incorrect", null, locale);
        };

        if (mode == SubmissionMode.RUN) {
            if (isSyntaxError) {
                criteria.add(new CriterionDto(
                    messageSource.getMessage("criterium.syntax", null, locale),
                    null, false,
                    resolveSyntaxDetail(error, locale, makeRefMissing)
                ));
            } else {
                criteria.add(new CriterionDto(
                    messageSource.getMessage("criterium.syntax", null, locale),
                    null, true,
                    messageSource.getMessage("syntax.valid", null, locale)
                ));
                addResultCriterion(criteria, student, true, locale);
            }
            return new GradingDto(task.getMaxPoints(), BigDecimal.ZERO, general, criteria);
        }

        if (isSyntaxError) {
            String syntaxDetail = resolveSyntaxDetail(error, locale, makeRefMissing);
            if (level >= 3) {
                syntaxDetail += " [" + messageSource.getMessage("penalty", null, locale)
                    + ": -" + task.getMaxPoints().toPlainString() + "]";
            }
            criteria.add(new CriterionDto(
                messageSource.getMessage("criterium.syntax", null, locale),
                null, false, syntaxDetail
            ));
        } else {
            criteria.add(new CriterionDto(
                messageSource.getMessage("criterium.syntax", null, locale),
                null, true,
                messageSource.getMessage("syntax.valid", null, locale)
            ));
        }

        if (isSyntaxError) {
            addResultCriterion(criteria, student, false, locale);
            return new GradingDto(task.getMaxPoints(), BigDecimal.ZERO, general, criteria);
        }

        if (isStructureError) {
            if (level == 1) {
                criteria.add(new CriterionDto(
                    messageSource.getMessage("criterium.structure", null, locale),
                    null, false,
                    messageSource.getMessage("structure.error", null, locale)
                ));
            } else if (level >= 2) {
                List<Entry> structureEntries = new ArrayList<>();
                List<Entry> contentEntries = new ArrayList<>();

                EvaluationService.ErrorCategory oracleCategory =
                    resolveOraclePrimaryCategory(error, oidValid, detectedErrors);

                Entry oracleEntry = new Entry(
                    resolveOracleStructureDetail(error, locale, level, oidValid, task, detectedErrors,
                        missingColumnNames, extraColumnNames,
                        expectedMakeRefArgs, actualMakeRefArgs,
                        expectedConstructorCall, actualConstructorCall,
                        expectedOfType, actualOfType,
                        expectedCastType, actualCastType,
                        expectedOid,
                        expectedWhereClause, actualWhereClause, expectedColumnOrder, actualColumnOrder),
                    oracleCategory);

                if (oracleCategory == EvaluationService.ErrorCategory.WRONG_CONTENT) {
                    contentEntries.add(oracleEntry);
                } else {
                    structureEntries.add(oracleEntry);
                }

                if (!oidValid && oracleCategory != EvaluationService.ErrorCategory.WRONG_OID) {
                    String granularDetail = level >= 3 ? resolveOidGranular(task, locale, actualOid) : null;
                    structureEntries.add(new Entry(
                        buildDetail(
                            messageSource.getMessage("error.invalidOid", null, locale),
                            messageSource.getMessage("error.location.oid", null, locale),
                            granularDetail),
                        EvaluationService.ErrorCategory.WRONG_OID));
                }

                if (!superviewValid) {
                    String granularDetail = level >= 3 ? resolveSuperviewGranular(task, locale) : null;
                    structureEntries.add(new Entry(
                        buildDetail(
                            messageSource.getMessage("error.invalidSuperview", null, locale),
                            messageSource.getMessage("error.location.superview", null, locale),
                            granularDetail),
                        EvaluationService.ErrorCategory.WRONG_SUPERVIEW));
                }

                if (detectedErrors != null) {
                    Set<EvaluationService.ErrorCategory> alreadyShown = new HashSet<>();
                    if (oracleCategory != null) alreadyShown.add(oracleCategory);
                    if (!oidValid) alreadyShown.add(EvaluationService.ErrorCategory.WRONG_OID);

                    for (EvaluationService.ErrorCategory cat : detectedErrors.keySet()) {
                        if (alreadyShown.contains(cat)) continue;
                        String detail = resolveErrorCategoryDetail(cat, locale, level, task,
                            missingColumnNames, extraColumnNames,
                            expectedMakeRefArgs, actualMakeRefArgs,
                            expectedConstructorCall, actualConstructorCall,
                            expectedOfType, actualOfType,
                            expectedCastType, actualCastType,
                            expectedOid,
                            expectedWhereClause, actualWhereClause, expectedColumnOrder, actualColumnOrder);
                        if (cat == EvaluationService.ErrorCategory.WRONG_CONTENT) {
                            contentEntries.add(new Entry(detail, cat));
                        } else {
                            structureEntries.add(new Entry(detail, cat));
                        }
                    }
                }

                renderEntries(criteria, structureEntries, "criterium.structure", level, task, detectedErrors, locale);
                renderEntries(criteria, contentEntries, "criterium.content", level, task, detectedErrors, locale);
            }
            addResultCriterion(criteria, student, false, locale);
            BigDecimal points = calculatePenaltyPoints(task, detectedErrors);
            return new GradingDto(task.getMaxPoints(), points, general, criteria);
        }

        if (level == 0) {
            addResultCriterion(criteria, student, correct, locale);
        } else if (level == 1) {
            if (!correct) addStructureContentCriteriaLevel1(
                criteria, student, teacher, oidValid, typeValid, superviewValid, locale, detectedErrors);
            addResultCriterion(criteria, student, correct, locale);
        } else {
            if (!correct) addStructureContentCriteria(criteria, student, teacher,
                oidValid, typeValid, superviewValid, level >= 3, locale, task, detectedErrors,
                missingColumnNames, extraColumnNames,
                expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
                expectedOfType, actualOfType, expectedCastType, actualCastType, expectedOid,
                expectedWhereClause, actualWhereClause,expectedColumnOrder, actualColumnOrder);
            addResultCriterion(criteria, student, correct, locale);
        }

        BigDecimal points = correct
            ? task.getMaxPoints()
            : calculatePenaltyPoints(task, detectedErrors);
        return new GradingDto(task.getMaxPoints(), points, general, criteria);
    }

    // Renders entries as criteria rows, appending penalty info at level 3.
    private void renderEntries(List<CriterionDto> criteria,
                               List<Entry> entries,
                               String criteriumKey,
                               int level,
                               OrViewTask task,
                               Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
                               Locale locale) {
        boolean first = true;
        for (Entry entry : entries) {
            String detail = entry.detail();
            if (level >= 3 && entry.category() != null) {
                BigDecimal totalPenalty = getTotalPenaltyForCategory(task, detectedErrors, entry.category());
                if (totalPenalty != null) {
                    int count = detectedErrors.getOrDefault(entry.category(), 1);
                    if (count > 1)
                        detail += " [" + count + "x " + messageSource.getMessage("penalty", null, locale)
                            + ": -" + totalPenalty.toPlainString() + "]";
                    else
                        detail += " [" + messageSource.getMessage("penalty", null, locale)
                            + ": -" + totalPenalty.toPlainString() + "]";
                } else {
                    detail += " [" + messageSource.getMessage("penalty", null, locale)
                        + ": -" + task.getMaxPoints().toPlainString() + "]";
                }
            }
            criteria.add(new CriterionDto(
                first ? messageSource.getMessage(criteriumKey, null, locale) : "\u00A0",
                null, false, detail));
            first = false;
        }
    }

    // Resolves the most relevant column-related error category from detected errors.
    private EvaluationService.ErrorCategory resolveColCategory(Map<EvaluationService.ErrorCategory, Integer> detectedErrors) {
        if (detectedErrors == null) return null;
        if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD))
            return EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD;
        if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR))
            return EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR;
        if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD))
            return EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD;
        if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_NESTED_TABLE))
            return EvaluationService.ErrorCategory.MISSING_NESTED_TABLE;
        if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE))
            return EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE;
        return null;
    }

    private String resolveOidGranular(OrViewTask task, Locale locale, List<String> actualOid) {
        String expected = task.getExpectedIdentifier();
        String base = (expected != null && !expected.isBlank())
            ? messageSource.getMessage("error.invalidOid.detail", null, locale)
            + " " + messageSource.getMessage("error.detail.expectedOid", null, locale)
            + ": (" + expected + ")"
            : messageSource.getMessage("error.detail.missingOid", null, locale);
        if (!actualOid.isEmpty()) {
            base += " - " + messageSource.getMessage("error.detail.actualOid", null, locale)
                + ": (" + actualOid.getFirst() + ")";
        }
        return base;
    }

    private String resolveSuperviewGranular(OrViewTask task, Locale locale) {
        String superviewName = extractSuperviewName(task);
        return messageSource.getMessage("error.invalidSuperview.detail", null, locale)
            + (superviewName != null
            ? " " + messageSource.getMessage("error.detail.expectedSuperview", null, locale)
            + ": UNDER " + superviewName : "");
    }

    // Maps an Oracle error to the most specific detected error category.
    private EvaluationService.ErrorCategory resolveOraclePrimaryCategory(
        SQLException error,
        boolean oidValid,
        Map<EvaluationService.ErrorCategory, Integer> detectedErrors
    ) {
        if (error == null || detectedErrors == null) return null;
        String msg = error.getMessage();
        if (msg == null) return null;

        if (msg.contains("ORA-22903") || msg.contains("ORA-00913"))
            return detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE)
                ? EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE : null;

        if (msg.contains("ORA-02303") || msg.contains("ORA-22907") || msg.contains("ORA-00902") ||
            msg.contains("ORA-02306") ||
            (msg.contains("ORA-00932") && (msg.toUpperCase().contains("TYPE") || msg.toUpperCase().contains("TYP")))) {
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_COLUMN_ORDER))
                return EvaluationService.ErrorCategory.WRONG_COLUMN_ORDER;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE))
                return EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE))
                return EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD))
                return EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD))
                return EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR))
                return EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR;
            return null;
        }

        if (msg.contains("ORA-01730") || msg.contains("ORA-02315")) {
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE))
                return EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_NESTED_TABLE))
                return EvaluationService.ErrorCategory.MISSING_NESTED_TABLE;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD))
                return EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR))
                return EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD))
                return EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME))
                return EvaluationService.ErrorCategory.INVALID_COLUMN_NAME;
        }

        if (msg.contains("ORA-00909"))
            return detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD)
                ? EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD
                : detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR)
                ? EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR : null;

        if (msg.contains("ORA-00904")) {
            String msgUpper = msg.toUpperCase();
            if (msgUpper.contains("MAKE_REF") || msgUpper.contains("MAKE_EF")
                || msgUpper.contains("MAKE_RF") || msgUpper.contains("MAK_REF"))
                return detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD)
                    ? EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD : null;
            if (!oidValid)
                return detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_OID)
                    ? EvaluationService.ErrorCategory.WRONG_OID : null;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME))
                return EvaluationService.ErrorCategory.INVALID_COLUMN_NAME;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD))
                return EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD))
                return EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR))
                return EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_CONTENT))
                return EvaluationService.ErrorCategory.WRONG_CONTENT;
        }

        if (msg.contains("ORA-01722") || msg.contains("ORA-61800")) {
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_COLUMN_ORDER))
                return EvaluationService.ErrorCategory.WRONG_COLUMN_ORDER;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD))
                return EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD;
            if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_CONTENT))
                return EvaluationService.ErrorCategory.WRONG_CONTENT;
            return null;
        }

        if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_COLUMN_ORDER))
            return EvaluationService.ErrorCategory.WRONG_COLUMN_ORDER;

        return null;
    }

    private String resolveErrorCategoryDetail(
        EvaluationService.ErrorCategory cat,
        Locale locale, int level, OrViewTask task,
        List<String> missingColumnNames,
        List<String> extraColumnNames,
        List<String> expectedMakeRefArgs,
        List<String> actualMakeRefArgs,
        List<String> expectedConstructorCall,
        List<String> actualConstructorCall,
        List<String> expectedOfType,
        List<String> actualOfType,
        List<String> expectedCastType,
        List<String> actualCastType,
        List<String> actualOid,
        List<String> expectedWhereClause,
        List<String> actualWhereClause,
        List<String> expectedColumnOrder,
        List<String> actualColumnOrder
    ) {
        return switch (cat) {
            case WRONG_OID -> {
                if (level >= 3) {
                    yield resolveOidGranular(task, locale, actualOid);
                }
                yield messageSource.getMessage("error.location.oid", null, locale);
            }
            case WRONG_NESTED_TABLE_TYPE -> {
                String base = level >= 3
                    ? messageSource.getMessage("error.invalidCastMultiset.detail", null, locale)
                    : messageSource.getMessage("error.location.castMultisetType", null, locale);
                yield level >= 3 ? appendExpectedActualType(base, locale, expectedCastType, actualCastType) : base;
            }
            case WRONG_VIEW_OBJECT_TYPE -> {
                String base = level >= 3
                    ? messageSource.getMessage("error.invalidType.detail", null, locale)
                    : messageSource.getMessage("error.location.type", null, locale);
                yield level >= 3 ? appendExpectedActualType(base, locale, expectedOfType, actualOfType) : base;
            }
            case WRONG_COLUMN_ORDER -> {
                String base = messageSource.getMessage("error.location.columnOrder", null, locale);
                if (level >= 3 && !expectedColumnOrder.isEmpty()) {
                    base += " - " + messageSource.getMessage("error.detail.expectedOrder", null, locale)
                        + ": " + String.join(", ", expectedColumnOrder).toUpperCase();
                    base += " - " + messageSource.getMessage("error.detail.actualOrder", null, locale)
                        + ": " + String.join(", ", actualColumnOrder).toUpperCase();
                }
                yield base;
            }
            case WRONG_SUPERVIEW -> {
                String base = level >= 3
                    ? messageSource.getMessage("error.invalidSuperview.detail", null, locale)
                    : messageSource.getMessage("error.location.superview", null, locale);
                if (level >= 3) {
                    String superviewName = extractSuperviewName(task);
                    if (superviewName != null) {
                        base += " - " + messageSource.getMessage("error.detail.expectedSuperview", null, locale)
                            + ": UNDER " + superviewName;
                    }
                }
                yield base;
            }
            case MISSING_NESTED_TABLE -> level >= 3
                ? messageSource.getMessage("error.missingNestedTable.detail", null, locale)
                : messageSource.getMessage("error.location.castMultiset", null, locale);
            case MISSING_OBJECT_FIELD -> {
                String base = level >= 3
                    ? messageSource.getMessage("error.missingMakeRef.detail", null, locale)
                    : messageSource.getMessage("error.location.makeRef", null, locale);
                if (level >= 3 && !expectedMakeRefArgs.isEmpty()) {
                    for (int i = 0; i < expectedMakeRefArgs.size(); i++) {
                        base += " - " + messageSource.getMessage("error.detail.expectedMakeRef", null, locale)
                            + ": MAKE_REF(" + expectedMakeRefArgs.get(i).toUpperCase() + ")";
                        if (i < actualMakeRefArgs.size()) {
                            base += " - " + messageSource.getMessage("error.detail.actualMakeRef", null, locale)
                                + ": MAKE_REF(" + actualMakeRefArgs.get(i).toUpperCase() + ")";
                        } else {
                            base += " - " + messageSource.getMessage("error.detail.actualMakeRef", null, locale) + ": -";
                        }
                    }
                }
                yield base;
            }
            case MISSING_CONSTRUCTOR -> {
                String base = level >= 3
                    ? messageSource.getMessage("error.missingConstructor.detail", null, locale)
                    : messageSource.getMessage("error.location.constructor", null, locale);
                if (level >= 3 && !expectedConstructorCall.isEmpty()) {
                    base += " - " + messageSource.getMessage("error.detail.expectedConstructor", null, locale)
                        + ": " + expectedConstructorCall.getFirst();
                    if (!actualConstructorCall.isEmpty()) {
                        base += " - " + messageSource.getMessage("error.detail.actualConstructor", null, locale)
                            + ": " + actualConstructorCall.getFirst();
                    }
                }
                yield base;
            }
            case MISSING_PRIMITIVE_FIELD -> {
                if (level == 2) {
                    if (missingColumnNames.isEmpty() && !expectedColumnOrder.isEmpty()) {
                        yield messageSource.getMessage("error.location.columnOrder", null, locale);
                    }
                    yield messageSource.getMessage("error.location.selectList", null, locale);
                }
                String base = messageSource.getMessage("error.missingPrimitiveField.detail", null, locale);
                if (!missingColumnNames.isEmpty()) {
                    base = appendColumnInfo(base, missingColumnNames, "error.detail.missingColumn", locale);
                } else if (!expectedColumnOrder.isEmpty()) {
                    base += " - " + messageSource.getMessage("error.detail.expectedOrder", null, locale)
                        + ": " + String.join(", ", expectedColumnOrder).toUpperCase();
                    base += " - " + messageSource.getMessage("error.detail.actualOrder", null, locale)
                        + ": " + String.join(", ", actualColumnOrder).toUpperCase();
                }
                yield base;
            }
            case INVALID_COLUMN_NAME -> {
                String base = level >= 3
                    ? messageSource.getMessage("error.invalidColumnName.detail", null, locale)
                    : messageSource.getMessage("error.location.invalidColumn", null, locale);
                if (level >= 3) {
                    ColumnClassification classification = classifyExtraColumns(extraColumnNames, missingColumnNames);
                    if (!classification.typos().isEmpty()) {
                        base += " - " + String.join(", ", classification.typos()).toUpperCase()
                            + ": " + messageSource.getMessage("error.detail.typoColumn", null, locale);
                    }
                    if (!classification.extras().isEmpty()) {
                        base += " - " + String.join(", ", classification.extras()).toUpperCase()
                            + ": " + messageSource.getMessage("error.detail.extraColumn", null, locale);
                    }
                }
                yield base;
            }
            case WRONG_CONTENT -> {
                String base = level >= 3
                    ? messageSource.getMessage("content.error.detail", null, locale)
                    : messageSource.getMessage("error.location.rows", null, locale);
                if (level >= 3 && !expectedWhereClause.isEmpty()) {
                    for (int i = 0; i < expectedWhereClause.size(); i++) {
                        base += " - " + messageSource.getMessage("error.detail.expectedWhere", null, locale)
                            + ": " + expectedWhereClause.get(i);
                        if (i < actualWhereClause.size()) {
                            base += " - " + messageSource.getMessage("error.detail.actualWhere", null, locale)
                                + ": " + actualWhereClause.get(i);
                        } else {
                            base += " - " + messageSource.getMessage("error.detail.actualWhere", null, locale) + ": -";
                        }
                    }
                }
                yield base;
            }
        };
    }

    private void addResultCriterion(List<CriterionDto> criteria,
                                    EvaluationService.QueryResult student,
                                    boolean passed, Locale locale) {
        if (student != null) {
            criteria.add(new CriterionDto(
                messageSource.getMessage("criterium.result", null, locale),
                null, passed,
                createResultTable(student, locale)
            ));
        }
    }

    // Calculates points after penalty deductions. Each penalty is multiplied by the error count.
    private BigDecimal calculatePenaltyPoints(OrViewTask task, Map<EvaluationService.ErrorCategory, Integer> errors) {
        if (errors == null || errors.isEmpty()) return BigDecimal.ZERO;
        BigDecimal points = task.getMaxPoints();
        for (var entry : errors.entrySet()) {
            BigDecimal penalty = getPenalty(task, entry.getKey());
            if (penalty != null) {
                BigDecimal totalPenalty = penalty.multiply(BigDecimal.valueOf(entry.getValue()));
                points = points.subtract(totalPenalty);
            }
        }
        return points.max(BigDecimal.ZERO);
    }

    private BigDecimal getPenalty(OrViewTask task, EvaluationService.ErrorCategory category) {
        return switch (category) {
            case MISSING_PRIMITIVE_FIELD, INVALID_COLUMN_NAME -> task.getMissingPrimitiveFieldPenalty();
            case MISSING_OBJECT_FIELD, MISSING_CONSTRUCTOR -> task.getMissingObjectFieldPenalty();
            case MISSING_NESTED_TABLE -> task.getMissingNestedTablePenalty();
            case WRONG_NESTED_TABLE_TYPE -> task.getWrongNestedTableTypePenalty();
            case WRONG_VIEW_OBJECT_TYPE -> task.getWrongViewObjectTypePenalty();
            case WRONG_OID -> task.getWrongOidPenalty();
            case WRONG_CONTENT -> task.getWrongContentPenalty();
            case WRONG_COLUMN_ORDER -> task.getWrongColumnOrderPenalty();
            case WRONG_SUPERVIEW -> task.getWrongSuperviewPenalty();
        };
    }

    // Returns the total penalty for a category (unit penalty x count), or null if not applicable.
    private BigDecimal getTotalPenaltyForCategory(OrViewTask task,
                                                  Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
                                                  EvaluationService.ErrorCategory category) {
        if (category == null || detectedErrors == null || !detectedErrors.containsKey(category)) return null;
        BigDecimal penalty = getPenalty(task, category);
        if (penalty == null) return null;
        int count = detectedErrors.getOrDefault(category, 1);
        return penalty.multiply(BigDecimal.valueOf(count));
    }

    // Level 1: shows error type only, no location or detail.
    private void addStructureContentCriteriaLevel1(
        List<CriterionDto> criteria,
        EvaluationService.QueryResult student,
        EvaluationService.QueryResult teacher,
        boolean oidValid,
        boolean typeValid,
        boolean superviewValid,
        Locale locale,
        Map<EvaluationService.ErrorCategory, Integer> detectedErrors
    ) {
        boolean hasStructureError = !typeValid || !oidValid || !superviewValid;
        boolean hasContentError = false;

        if (detectedErrors != null && (
            detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD)
                || detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD)
                || detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR)
                || detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME)))
            hasStructureError = true;

        if (student != null && teacher != null) {
            boolean colMismatch = !student.columns().equals(teacher.columns());
            boolean rowMismatch = !new HashSet<>(student.rows()).equals(new HashSet<>(teacher.rows()));
            if (colMismatch) hasStructureError = true;
            boolean fatalStructureError = detectedErrors != null && (
                detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_NESTED_TABLE)
                    || detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE)
                    || detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE)
                    || detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR));
            if (!fatalStructureError && rowMismatch
                && (detectedErrors == null || !detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD)))
                hasContentError = true;
        }

        if (detectedErrors != null && detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_CONTENT)) {
            hasContentError = true;
        }

        if (hasStructureError) {
            criteria.add(new CriterionDto(
                messageSource.getMessage("criterium.structure", null, locale),
                null, false,
                messageSource.getMessage("structure.error", null, locale)
            ));
        }
        if (hasContentError) {
            criteria.add(new CriterionDto(
                messageSource.getMessage("criterium.content", null, locale),
                null, false,
                messageSource.getMessage("content.error", null, locale)
            ));
        }
    }

    // Level 2+: shows error location (level 2) and granular detail with penalties (level 3).
    private void addStructureContentCriteria(
        List<CriterionDto> criteria,
        EvaluationService.QueryResult student,
        EvaluationService.QueryResult teacher,
        boolean oidValid,
        boolean typeValid,
        boolean superviewValid,
        boolean granular,
        Locale locale,
        OrViewTask task,
        Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
        List<String> missingColumnNames,
        List<String> extraColumnNames,
        List<String> expectedMakeRefArgs,
        List<String> actualMakeRefArgs,
        List<String> expectedConstructorCall,
        List<String> actualConstructorCall,
        List<String> expectedOfType,
        List<String> actualOfType,
        List<String> expectedCastType,
        List<String> actualCastType,
        List<String> actualOid,
        List<String> expectedWhereClause,
        List<String> actualWhereClause,
        List<String> expectedColumnOrder,
        List<String> actualColumnOrder
    ) {
        List<Entry> structureEntries = new ArrayList<>();
        List<Entry> contentEntries = new ArrayList<>();

        Set<EvaluationService.ErrorCategory> alreadyShown = new HashSet<>();

        if (!typeValid) {
            structureEntries.add(new Entry(
                buildDetail(
                    messageSource.getMessage("error.invalidType", null, locale),
                    messageSource.getMessage("error.location.type", null, locale),
                    granular ? resolveErrorCategoryDetail(
                        EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE,
                        locale, 3, task, missingColumnNames, extraColumnNames,
                        expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
                        expectedOfType, actualOfType, expectedCastType, actualCastType, actualOid,
                        expectedWhereClause, actualWhereClause, expectedColumnOrder, actualColumnOrder) : null),
                EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE));
            alreadyShown.add(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE);
        }

        if (!oidValid) {
            structureEntries.add(new Entry(
                buildDetail(
                    messageSource.getMessage("error.invalidOid", null, locale),
                    messageSource.getMessage("error.location.oid", null, locale),
                    granular ? resolveOidGranular(task, locale, actualOid) : null),
                EvaluationService.ErrorCategory.WRONG_OID));
            alreadyShown.add(EvaluationService.ErrorCategory.WRONG_OID);
        }

        if (!superviewValid) {
            structureEntries.add(new Entry(
                buildDetail(
                    messageSource.getMessage("error.invalidSuperview", null, locale),
                    messageSource.getMessage("error.location.superview", null, locale),
                    granular ? resolveSuperviewGranular(task, locale) : null),
                EvaluationService.ErrorCategory.WRONG_SUPERVIEW));
            alreadyShown.add(EvaluationService.ErrorCategory.WRONG_SUPERVIEW);
        }

        if (student != null && teacher != null) {
            boolean colMismatch = !student.columns().equals(teacher.columns());

            if (colMismatch) {
                String granularDetail = granular
                    ? messageSource.getMessage("structure.error.detail", null, locale)
                    + " " + messageSource.getMessage("error.detail.columns", null, locale)
                    + ": " + teacher.columns()
                    + " - " + messageSource.getMessage("error.detail.got", null, locale)
                    + ": " + student.columns()
                    : null;
                EvaluationService.ErrorCategory colCat = resolveColCategory(detectedErrors);
                structureEntries.add(new Entry(
                    buildDetail(
                        messageSource.getMessage("structure.error", null, locale),
                        messageSource.getMessage("error.location.columns", null, locale),
                        granularDetail),
                    colCat));
                if (colCat != null) alreadyShown.add(colCat);
            }
        }

        if (detectedErrors != null) {
            for (EvaluationService.ErrorCategory cat : detectedErrors.keySet()) {
                if (alreadyShown.contains(cat)) continue;
                alreadyShown.add(cat);
                String detail = resolveErrorCategoryDetail(
                    cat, locale, granular ? 3 : 2, task,
                    missingColumnNames, extraColumnNames,
                    expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
                    expectedOfType, actualOfType, expectedCastType, actualCastType, actualOid,
                    expectedWhereClause, actualWhereClause, expectedColumnOrder, actualColumnOrder);
                if (cat == EvaluationService.ErrorCategory.WRONG_CONTENT) {
                    contentEntries.add(new Entry(detail, cat));
                } else {
                    structureEntries.add(new Entry(detail, cat));
                }
            }
        }

        int level = granular ? 3 : 2;
        renderEntries(criteria, structureEntries, "criterium.structure", level, task, detectedErrors, locale);
        renderEntries(criteria, contentEntries, "criterium.content", level, task, detectedErrors, locale);
    }

    private String resolveOracleStructureDetail(SQLException error, Locale locale,
                                                int level, boolean oidValid,
                                                OrViewTask task,
                                                Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
                                                List<String> missingColumnNames,
                                                List<String> extraColumnNames,
                                                List<String> expectedMakeRefArgs,
                                                List<String> actualMakeRefArgs,
                                                List<String> expectedConstructorCall,
                                                List<String> actualConstructorCall,
                                                List<String> expectedOfType,
                                                List<String> actualOfType,
                                                List<String> expectedCastType,
                                                List<String> actualCastType,
                                                List<String> expectedOid,
                                                List<String> expectedWhereClause,
                                                List<String> actualWhereClause,
                                                List<String> expectedColumnOrder,
                                                List<String> actualColumnOrder){
        if (error == null) return messageSource.getMessage("structure.error", null, locale);
        String msg = error.getMessage();
        if (msg == null) return messageSource.getMessage("system.error", null, locale);

        if ("MISSING_UNDER".equals(msg))
            return messageSource.getMessage("error.invalidSuperview", null, locale);

        if (level >= 2) {
            EvaluationService.ErrorCategory oracleCategory =
                resolveOraclePrimaryCategory(error, oidValid, detectedErrors);
            if (oracleCategory != null) {
                return resolveErrorCategoryDetail(oracleCategory, locale, level, task,
                    missingColumnNames, extraColumnNames,
                    expectedMakeRefArgs, actualMakeRefArgs,
                    expectedConstructorCall, actualConstructorCall,
                    expectedOfType, actualOfType,
                    expectedCastType, actualCastType,
                    expectedOid, expectedWhereClause, actualWhereClause, expectedColumnOrder, actualColumnOrder);
            }
            if (level == 2) {
                String location = resolveOracleLocation(msg, locale, oidValid);
                return location != null ? location : messageSource.getMessage("structure.error", null, locale);
            }
        }
        return resolveOracleGranular(msg, locale, oidValid, detectedErrors, missingColumnNames, extraColumnNames);
    }

    // Maps Oracle error codes to error location messages (level 2).
    private String resolveOracleLocation(String msg, Locale locale, boolean oidValid) {
        if (msg.contains("ORA-03048"))
            return messageSource.getMessage("error.location.whereClause", null, locale);
        if (msg.contains("ORA-01730") || msg.contains("ORA-02315"))
            return messageSource.getMessage("error.location.columnCount", null, locale);
        if (msg.contains("ORA-22903") || msg.contains("ORA-00913"))
            return messageSource.getMessage("error.location.castMultiset", null, locale);
        if (msg.contains("ORA-00936"))
            return messageSource.getMessage("error.location.expression", null, locale);
        if (msg.contains("ORA-01722") || msg.contains("ORA-61800"))
            return messageSource.getMessage("error.location.whereClause", null, locale);
        if (msg.contains("ORA-00909"))
            return messageSource.getMessage("error.location.makeRef", null, locale);
        if (msg.contains("ORA-22907"))
            return messageSource.getMessage("error.location.castMultiset", null, locale);
        if (msg.contains("ORA-00932")) {
            String msgUpper = msg.toUpperCase();
            if (msgUpper.contains("DATENTYP") || msgUpper.contains("DATATYPE") || msgUpper.contains("DATA TYPE"))
                return messageSource.getMessage("error.location.columnsOrName", null, locale);
            if (msgUpper.contains("TYPE") || msgUpper.contains("TYP"))
                return messageSource.getMessage("error.location.type", null, locale);
            return messageSource.getMessage("error.location.columnsOrName", null, locale);
        }
        if (msg.contains("ORA-02303") || msg.contains("ORA-00902") || msg.contains("ORA-02306"))
            return messageSource.getMessage("error.location.type", null, locale);
        if (msg.contains("ORA-00904")) {
            if (msg.toUpperCase().contains("MAKE_REF") || msg.toUpperCase().contains("MAKE_EF")
                || msg.toUpperCase().contains("MAKE_RF") || msg.toUpperCase().contains("MAK_REF"))
                return messageSource.getMessage("error.location.makeRef", null, locale);
            if (!oidValid)
                return messageSource.getMessage("error.location.oid", null, locale);
            return messageSource.getMessage("error.location.invalidColumn", null, locale);
        }
        return null;
    }

    // Maps Oracle error codes to granular detail messages (level 3).
    private String resolveOracleGranular(String msg, Locale locale, boolean oidValid,
                                         Map<EvaluationService.ErrorCategory, Integer> detectedErrors,
                                         List<String> missingColumnNames,
                                         List<String> extraColumnNames) {
        if (msg.contains("ORA-03048"))
            return messageSource.getMessage("error.missingWhere.detail", null, locale);
        if (msg.contains("ORA-01730") || msg.contains("ORA-02315")) {
            if (detectedErrors != null) {
                if (detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_VIEW_OBJECT_TYPE))
                    return messageSource.getMessage("error.invalidType.detail", null, locale);
                if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_NESTED_TABLE))
                    return messageSource.getMessage("error.missingNestedTable.detail", null, locale);
                if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD))
                    return messageSource.getMessage("error.missingMakeRef.detail", null, locale);
                if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR))
                    return messageSource.getMessage("error.missingConstructor.detail", null, locale);
                if (detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD)) {
                    String base = messageSource.getMessage("error.missingPrimitiveField.detail", null, locale);
                    base = appendColumnInfo(base, missingColumnNames, "error.detail.missingColumn", locale);
                    return base;
                }
                if (detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME)) {
                    String base = messageSource.getMessage("error.invalidColumnName.detail", null, locale);
                    base = appendColumnInfo(base, extraColumnNames, "error.detail.extraColumn", locale);
                    return base;
                }
            }
            return messageSource.getMessage("error.invalidColumnCount.detail", null, locale);
        }
        if (msg.contains("ORA-22903") || msg.contains("ORA-00913"))
            return messageSource.getMessage("error.invalidCastMultiset.detail", null, locale);
        if (msg.contains("ORA-00936"))
            return messageSource.getMessage("error.missingExpression.detail", null, locale);
        if (msg.contains("ORA-01722") || msg.contains("ORA-61800"))
            return messageSource.getMessage("error.invalidDatatypeComparison.detail", null, locale);
        if (msg.contains("ORA-00909"))
            return messageSource.getMessage("error.invalidMakeRef.detail", null, locale);
        if (msg.contains("ORA-00904")) {
            String msgUpper = msg.toUpperCase();
            if (msgUpper.contains("MAKE_REF") || msgUpper.contains("MAKE_EF")
                || msgUpper.contains("MAKE_RF") || msgUpper.contains("MAK_REF"))
                return messageSource.getMessage("error.invalidMakeRef.detail", null, locale);
            if (!oidValid) {
                String detail = extractOracleDetail(msg);
                String base = messageSource.getMessage("error.invalidOid.detail", null, locale);
                return detail != null ? base + " - " + detail : base;
            }
            if (detectedErrors != null
                && detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD)
                && !detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME)) {
                return messageSource.getMessage("error.missingMakeRef.detail", null, locale);
            }
            if (detectedErrors != null
                && detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR)
                && !detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME)) {
                return messageSource.getMessage("error.missingConstructor.detail", null, locale);
            }
            if (detectedErrors != null
                && detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_CONTENT)
                && !detectedErrors.containsKey(EvaluationService.ErrorCategory.INVALID_COLUMN_NAME)) {
                return messageSource.getMessage("content.error.detail", null, locale);
            }
            String base = messageSource.getMessage("error.invalidColumnName.detail", null, locale);
            base = appendColumnInfo(base, extraColumnNames, "error.detail.invalidOrExtraColumn", locale);
            return base;
        }
        if (msg.contains("ORA-00942") || msg.contains("ORA-04043") || msg.contains("ORA-30738")) {
            String detail = extractOracleDetail(msg);
            String base = messageSource.getMessage("error.tableNotFound.detail", null, locale);
            return detail != null ? base + " - " + detail : base;
        }
        if (msg.contains("ORA-02303") || msg.contains("ORA-22907") || msg.contains("ORA-00902") ||
            msg.contains("ORA-02306") ||
            (msg.contains("ORA-00932") && (msg.toUpperCase().contains("TYPE") || msg.toUpperCase().contains("TYP")))) {
            if (detectedErrors != null && detectedErrors.containsKey(EvaluationService.ErrorCategory.WRONG_NESTED_TABLE_TYPE))
                return messageSource.getMessage("error.invalidCastMultiset.detail", null, locale);
            if (detectedErrors != null && detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_PRIMITIVE_FIELD)) {
                String base = messageSource.getMessage("error.missingPrimitiveField.detail", null, locale);
                base = appendColumnInfo(base, missingColumnNames, "error.detail.missingColumn", locale);
                return base;
            }
            if (detectedErrors != null && detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_OBJECT_FIELD))
                return messageSource.getMessage("error.missingMakeRef.detail", null, locale);
            if (detectedErrors != null && detectedErrors.containsKey(EvaluationService.ErrorCategory.MISSING_CONSTRUCTOR))
                return messageSource.getMessage("error.missingConstructor.detail", null, locale);
        }
        return simplify(msg);
    }

    private String extractOracleDetail(String msg) {
        if (msg == null) return null;
        Pattern p = Pattern.compile("ORA-\\d+:\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(msg);
        if (m.find()) {
            String detail = m.group(1).trim();
            return detail.length() > 100 ? detail.substring(0, 100) + "..." : detail;
        }
        return null;
    }

    private String resolveSyntaxDetail(SQLException error, Locale locale, boolean makeRefMissing) {
        if (error == null) return messageSource.getMessage("syntax.error", null, locale);
        String msg = error.getMessage();
        if (msg == null) return messageSource.getMessage("system.error", null, locale);

        return switch (msg) {
            case "MISSING_SEMICOLON" -> messageSource.getMessage("error.missingSemicolon", null, locale);
            case "UNBALANCED_PARENTHESIS" -> messageSource.getMessage("error.missingParenthesis", null, locale);
            case "MISSING_SELECT_IN_MULTISET", "MISSING_FROM_IN_MULTISET" ->
                messageSource.getMessage("error.missingKeyword", null, locale);
            case "MISSING_WHERE_IN_MULTISET" -> messageSource.getMessage("error.missingWhere", null, locale);
            case "MISSING_CAST_FOR_MULTISET" -> messageSource.getMessage("error.missingCastForMultiset", null, locale);
            case "WRONG_VIEW_NAME" -> messageSource.getMessage("error.invalidViewName", null, locale);
            default -> {
                String specific = resolveSyntaxSpecific(msg, makeRefMissing, locale);
                yield specific != null ? specific : simplify(msg);
            }
        };
    }

    // Maps Oracle syntax error codes to specific user-facing messages.
    private String resolveSyntaxSpecific(String msg, boolean makeRefMissing, Locale locale) {
        if (makeRefMissing && (msg.contains("ORA-00907") || msg.contains("ORA-00936")))
            return messageSource.getMessage("error.missingMakeRef", null, locale);
        if (msg.contains("ORA-30738") || msg.contains("ORA-04043") || msg.contains("ORA-00942"))
            return messageSource.getMessage("error.tableNotFound", null, locale);
        if (msg.contains("ORA-00903"))
            return messageSource.getMessage("error.missingTable", null, locale);
        if (msg.contains("ORA-00917") || msg.contains("ORA-00971"))
            return messageSource.getMessage("error.missingSeparator", null, locale);
        if (msg.contains("ORA-00905") || msg.contains("ORA-00900") ||
            msg.contains("ORA-00922") || msg.contains("ORA-02000") || msg.contains("ORA-00928") ||
            msg.contains("ORA-03049"))
            return messageSource.getMessage("error.missingKeyword", null, locale);
        if (msg.contains("ORA-22974") || msg.contains("ORA-02338") || msg.contains("ORA-00906"))
            return messageSource.getMessage("error.missingObjectIdentifier", null, locale);
        if (msg.contains("ORA-00923"))
            return messageSource.getMessage("error.from", null, locale);
        if (msg.contains("ORA-00907"))
            return messageSource.getMessage("error.missingParenthesis", null, locale);
        if (msg.contains("ORA-00936"))
            return messageSource.getMessage("error.missingExpression", null, locale);
        if (msg.contains("ORA-00920"))
            return messageSource.getMessage("error.invalidRelationalOperator", null, locale);
        if (msg.contains("ORA-00999"))
            return messageSource.getMessage("error.invalidViewName", null, locale);
        if (msg.contains("ORA-61800"))
            return messageSource.getMessage("error.invalidBooleanLiteral", null, locale);
        if (msg.contains("ORA-02302"))
            return messageSource.getMessage("error.invalidType", null, locale);
        return null;
    }

    private String appendColumnInfo(String base, List<String> columnNames, String messageKey, Locale locale) {
        if (columnNames != null && !columnNames.isEmpty()) {
            base += " - " + String.join(", ", columnNames).toUpperCase()
                + ": " + messageSource.getMessage(messageKey, null, locale);
        }
        return base;
    }

    private String buildDetail(String base, String location, String granularDetail) {
        if (granularDetail != null && !granularDetail.isBlank()) return granularDetail;
        if (location != null && !location.isBlank()) return location;
        return base;
    }

    private String extractSuperviewName(OrViewTask task) {
        if (task.getUnderSuperview() == null) return null;
        Pattern p = Pattern.compile(
            "CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(task.getUnderSuperview());
        return m.find() ? m.group(1) : null;
    }

    private record ColumnClassification(List<String> typos, List<String> extras) {}

    // Classifies extra columns as typos (close to a missing column) or truly extra columns.
    private ColumnClassification classifyExtraColumns(List<String> extraColumnNames,
                                                      List<String> missingColumnNames) {
        List<String> typos = new ArrayList<>();
        List<String> extras = new ArrayList<>();

        if (extraColumnNames == null || extraColumnNames.isEmpty())
            return new ColumnClassification(typos, extras);

        List<String> unmatchedMissing = new ArrayList<>(missingColumnNames != null ? missingColumnNames : List.of());

        for (String extra : extraColumnNames) {
            String bestMatch = null;
            int bestDistance = Integer.MAX_VALUE;

            for (String missing : unmatchedMissing) {
                int distance = levenshteinDistance(extra.toLowerCase(), missing.toLowerCase());
                if (distance <= 2 && distance < missing.length() / 2 && distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = missing;
                }
            }

            if (bestMatch != null) {
                typos.add(extra);
                unmatchedMissing.remove(bestMatch);
            } else {
                extras.add(extra);
            }
        }

        return new ColumnClassification(typos, extras);
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private String appendExpectedActualType(String base, Locale locale,
                                            List<String> expected, List<String> actual) {
        if (!expected.isEmpty()) {
            base += " - " + messageSource.getMessage("error.detail.expectedType", null, locale)
                + ": " + expected.getFirst().toUpperCase();
            if (!actual.isEmpty()) {
                base += " - " + messageSource.getMessage("error.detail.actualType", null, locale)
                    + ": " + actual.getFirst().toUpperCase();
            }
        }
        return base;
    }

    private String createResultTable(EvaluationService.QueryResult result, Locale locale) {
        if (result == null || result.columns().isEmpty() || result.rows().isEmpty())
            return "<div>" + messageSource.getMessage("result.empty", null, locale) + "</div>";

        StringBuilder b = new StringBuilder(
            "<table border=\"1\" style=\"border-collapse:collapse;width:100%;margin-top:10px;\">"
                + "<thead><tr>");
        for (String col : result.columns())
            b.append("<th style=\"padding:5px;background-color:#f2f2f2;\">").append(escapeHtml(col)).append("</th>");
        b.append("</tr></thead><tbody>");
        for (List<String> row : result.rows()) {
            b.append("<tr>");
            for (String val : row)
                b.append("<td style=\"padding:5px;\">").append(escapeHtml(val)).append("</td>");
            b.append("</tr>");
        }
        return b.append("</tbody></table>").toString();
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String simplify(String msg) {
        return msg.length() > 120 ? msg.substring(0, 120) + "..." : msg;
    }
}
