package at.jku.dke.task_app.or_view.evaluation;

import at.jku.dke.etutor.task_app.dto.*;
import at.jku.dke.task_app.or_view.data.entities.OrViewTask;
import at.jku.dke.task_app.or_view.data.entities.OrViewTaskGroup;
import at.jku.dke.task_app.or_view.data.repositories.OrViewTaskRepository;
import at.jku.dke.task_app.or_view.dto.SubmissionDto;
import at.jku.dke.task_app.or_view.services.OrViewSchemaServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central service for evaluating student submissions of Object-Relational Views.
 * Coordinates schema creation, SQL execution, error detection, and feedback generation.
 */
@Service
public class EvaluationService {

    /**
     * Result of a test query execution containing column names and row data.
     *
     * @param columns the column names
     * @param rows the row data as string values
     */
    public record QueryResult(List<String> columns, List<List<String>> rows) {}

    /**
     * Error categories for classifying detected errors in student submissions.
     */
    public enum ErrorCategory {
        MISSING_PRIMITIVE_FIELD,
        MISSING_OBJECT_FIELD,
        MISSING_CONSTRUCTOR,
        MISSING_NESTED_TABLE,
        WRONG_NESTED_TABLE_TYPE,
        WRONG_VIEW_OBJECT_TYPE,
        WRONG_OID,
        WRONG_CONTENT,
        INVALID_COLUMN_NAME,
        WRONG_COLUMN_ORDER,
        WRONG_SUPERVIEW
    }

    private static final Logger LOG = LoggerFactory.getLogger(EvaluationService.class);

    private static final Pattern UNDER_PATTERN =
        Pattern.compile("\\bUNDER\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern MAKE_REF_PATTERN =
        Pattern.compile("\\bMAKE_REF\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern VIEW_NAME_PATTERN =
        Pattern.compile("CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    private final OrViewTaskRepository taskRepository;
    private final OrViewDataSource dataSource;
    private final MessageSource messageSource;

    /**
     * Creates a new EvaluationService.
     *
     * @param taskRepository the task repository for loading tasks
     * @param dataSource     the data source for database connections
     * @param messageSource  the message source for localized messages
     */
    public EvaluationService(OrViewTaskRepository taskRepository,
                             OrViewDataSource dataSource,
                             MessageSource messageSource) {
        this.taskRepository = taskRepository;
        this.dataSource = dataSource;
        this.messageSource = messageSource;
    }

    /**
     * Evaluates a student submission by executing it in an isolated Oracle schema
     * and comparing the result against the reference solution.
     *
     * @param dto the submission data including student SQL, task ID, mode, and feedback level
     * @return a GradingDto containing points, feedback, and detected error categories
     */
    public GradingDto evaluate(SubmitSubmissionDto<SubmissionDto> dto) {

        Locale locale = (dto.language() != null && !dto.language().isBlank())
            ? Locale.of(dto.language())
            : Locale.GERMAN;

        OrViewTask task = taskRepository.findByIdWithTaskGroup(dto.taskId())
            .orElseThrow(() -> new EntityNotFoundException("Task not found"));

        OrViewTaskGroup group = task.getTaskGroup();
        List<String> typeNames = SchemaTypeAnalyzer.extractObjectTypeNames(group.getIntensionalSchema());

        boolean hasSuperview    = task.getUnderSuperview() != null && !task.getUnderSuperview().isBlank();
        boolean hasRefSuperview = task.getRefSuperview() != null && !task.getRefSuperview().isBlank();
        String expectedOidStr   = task.getExpectedIdentifier();

        String studentSql = dto.submission().input();
        String teacherSql = task.getSolution();

        boolean studentUsesUnder   = UNDER_PATTERN.matcher(studentSql).find();
        boolean teacherUsesUnder   = UNDER_PATTERN.matcher(teacherSql).find();
        boolean teacherUsesMakeRef = MAKE_REF_PATTERN.matcher(teacherSql).find();
        boolean studentUsesMakeRef = MAKE_REF_PATTERN.matcher(studentSql).find();

        boolean makeRefMissing = teacherUsesMakeRef && !studentUsesMakeRef;

        if (!hasSuperview && teacherUsesUnder)
            throw new RuntimeException("CONFIG ERROR: UNDER used but no superview configured");
        if (!hasRefSuperview && teacherUsesMakeRef)
            throw new RuntimeException("CONFIG ERROR: MAKE_REF used but no refSuperview configured");

        boolean underSuperviewValid;
        boolean oidValid;

        if (hasSuperview) {
            underSuperviewValid = studentUsesUnder && validateSubviewRelation(task.getUnderSuperview(), studentSql);
            oidValid = true;
        } else if (hasRefSuperview) {
            underSuperviewValid = true;
            oidValid = expectedOidStr == null || expectedOidStr.isBlank()
                || validateObjectIdentifier(studentSql, expectedOidStr);
        } else {
            underSuperviewValid = !studentUsesUnder;
            oidValid = expectedOidStr == null || expectedOidStr.isBlank()
                || validateObjectIdentifier(studentSql, expectedOidStr);
        }

        try {
            validateSemicolon(studentSql);
            validateSqlStructure(studentSql);
            String teacherViewName = extractViewName(teacherSql);
            String studentViewName = extractViewName(studentSql);
            if (teacherViewName != null && !teacherViewName.equalsIgnoreCase(studentViewName))
                throw new SQLException("WRONG_VIEW_NAME");
        } catch (SQLException e) {
            return buildReport(task, dto, null, null, false, e, locale,
                oidValid, underSuperviewValid, makeRefMissing,
                Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        }

        // Early return when UNDER is required but missing
        if (hasSuperview && !studentUsesUnder) {
            List<String> earlyMissing = new ArrayList<>();
            List<String> earlyExtra = new ArrayList<>();
            List<String> earlyExpMakeRef = new ArrayList<>();
            List<String> earlyActMakeRef = new ArrayList<>();
            List<String> earlyExpCtor = new ArrayList<>();
            List<String> earlyActCtor = new ArrayList<>();
            List<String> earlyExpOfType = new ArrayList<>();
            List<String> earlyActOfType = new ArrayList<>();
            List<String> earlyExpCast = new ArrayList<>();
            List<String> earlyActCast = new ArrayList<>();
            List<String> earlyExpOrder = new ArrayList<>();
            List<String> earlyActOrder = new ArrayList<>();
            List<String> earlyExpWhere = new ArrayList<>();
            List<String> earlyActWhere = new ArrayList<>();

            Map<ErrorCategory, Integer> superviewErrors = detectErrorCategories(
                null, studentSql, teacherSql, null, null,
                oidValid, underSuperviewValid, true,
                earlyMissing, earlyExtra, typeNames,
                earlyExpMakeRef, earlyActMakeRef, earlyExpCtor, earlyActCtor,
                earlyExpOfType, earlyActOfType, earlyExpCast, earlyActCast,
                earlyExpOrder, earlyActOrder);

            if (superviewErrors.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
                fillColumnOrderLists(studentSql, teacherSql, typeNames,
                    earlyExpOrder, earlyActOrder);
            }

            if (superviewErrors.containsKey(ErrorCategory.WRONG_CONTENT)) {
                collectWhereClauseInfo(superviewErrors, studentSql, teacherSql,
                    earlyExpWhere, earlyActWhere);
            }

            return buildReport(task, dto, null, null, false, null, locale,
                oidValid, underSuperviewValid, makeRefMissing,
                superviewErrors, earlyMissing, earlyExtra,
                earlyExpMakeRef, earlyActMakeRef, earlyExpCtor, earlyActCtor,
                earlyExpOfType, earlyActOfType, earlyExpCast, earlyActCast,
                Collections.emptyList(), Collections.emptyList(),
                earlyExpWhere, earlyActWhere,
                earlyExpOrder, earlyActOrder);
        }

        if (dto.mode() == SubmissionMode.RUN) {
            QueryResult studentRun = null;
            SQLException errorRun = null;
            String schemaRun = "SUB_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            try {
                setup(schemaRun, group, false);
                if (hasSuperview)    execute(schemaRun, task.getUnderSuperview());
                if (hasRefSuperview) execute(schemaRun, task.getRefSuperview());
                recompileTypeBody(schemaRun);
                execute(schemaRun, studentSql);
                studentRun = query(schemaRun, task.getTestQuery());
            } catch (SQLException e) {
                LOG.warn("Run mode failed: {}", e.getMessage());
                errorRun = e;
            } finally {
                cleanup(schemaRun);
            }
            return buildReport(task, dto, studentRun, null, false, errorRun, locale,
                oidValid, underSuperviewValid, makeRefMissing,
                Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        }

        boolean useSubmitData = dto.mode() == SubmissionMode.SUBMIT;

        QueryResult student = null;
        QueryResult teacher = null;
        SQLException error = null;
        boolean correct = false;
        Map<ErrorCategory, Integer> detectedErrors = new HashMap<>();
        List<String> missingColumnNames = new ArrayList<>();
        List<String> extraColumnNames = new ArrayList<>();
        List<String> expectedMakeRefArgs = new ArrayList<>();
        List<String> actualMakeRefArgs = new ArrayList<>();
        List<String> expectedConstructorCall = new ArrayList<>();
        List<String> actualConstructorCall = new ArrayList<>();
        List<String> expectedOfType = new ArrayList<>();
        List<String> actualOfType = new ArrayList<>();
        List<String> expectedCastType = new ArrayList<>();
        List<String> actualCastType = new ArrayList<>();
        List<String> expectedOid = new ArrayList<>();
        List<String> actualOid = new ArrayList<>();
        List<String> expectedWhereClause = new ArrayList<>();
        List<String> actualWhereClause = new ArrayList<>();
        List<String> expectedColumnOrder = new ArrayList<>();
        List<String> actualColumnOrder = new ArrayList<>();

        if (!oidValid && expectedOidStr != null) {
            expectedOid.add(expectedOidStr);
            Pattern oidPattern = Pattern.compile(
                "WITH\\s+OBJECT\\s+IDENTIFIER\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher oidMatcher = oidPattern.matcher(studentSql);
            if (oidMatcher.find()) {
                actualOid.add(oidMatcher.group(1).trim());
            }
        }

        String schema = "SUB_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            setup(schema, group, useSubmitData);
            if (hasSuperview)    execute(schema, task.getUnderSuperview());
            if (hasRefSuperview) execute(schema, task.getRefSuperview());
            recompileTypeBody(schema);

            execute(schema, studentSql);
            student = query(schema, task.getTestQuery());

            execute(schema, teacherSql);
            teacher = query(schema, task.getTestQuery());

            correct = compare(student, teacher) && oidValid && underSuperviewValid;

            if (!correct) {
                detectedErrors = detectErrorCategories(null, studentSql, teacherSql, student, teacher,
                    oidValid, underSuperviewValid, hasSuperview,
                    missingColumnNames, extraColumnNames, typeNames,
                    expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
                    expectedOfType, actualOfType, expectedCastType, actualCastType,
                    expectedColumnOrder, actualColumnOrder);

                collectWhereClauseInfo(detectedErrors, studentSql, teacherSql,
                    expectedWhereClause, actualWhereClause);

                if (detectedErrors.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)
                    || detectedErrors.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)) {
                    String repairedSql = repairStudentSql(studentSql, teacherSql);
                    LOG.info("Repaired SQL: {}", repairedSql);
                    if (repairedSql != null) {
                        try {
                            execute(schema, repairedSql);
                            QueryResult repairedResult = query(schema, task.getTestQuery());
                            if (compare(repairedResult, teacher)) {
                                detectedErrors.remove(ErrorCategory.WRONG_CONTENT);
                            }
                        } catch (Exception ex) {
                            LOG.debug("Repair failed (try block): {}", ex.getMessage());
                            checkRepairWhereError(ex.getMessage(), studentSql, detectedErrors);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOG.warn("{} run failed: {}", dto.mode(), e.getMessage());
            error = e;
            detectedErrors = detectErrorCategories(e, studentSql, teacherSql, null, null,
                oidValid, underSuperviewValid, hasSuperview,
                missingColumnNames, extraColumnNames, typeNames,
                expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
                expectedOfType, actualOfType, expectedCastType, actualCastType,
                expectedColumnOrder, actualColumnOrder);

            collectWhereClauseInfo(detectedErrors, studentSql, teacherSql,
                expectedWhereClause, actualWhereClause);

            if (shouldAttemptRepair(detectedErrors)) {
                String repairedSql = repairStudentSql(studentSql, teacherSql);
                if (repairedSql != null) {
                    attemptRepairExecution(repairedSql, studentSql, teacherSql, group, task,
                        hasSuperview, hasRefSuperview, useSubmitData, detectedErrors,
                        expectedWhereClause, actualWhereClause);
                }
            }
        } finally {
            cleanup(schema);
        }

        return buildReport(task, dto, student, teacher, correct, error, locale,
            oidValid, underSuperviewValid, makeRefMissing, detectedErrors,
            missingColumnNames, extraColumnNames,
            expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
            expectedOfType, actualOfType, expectedCastType, actualCastType, expectedOid, actualOid,
            expectedWhereClause, actualWhereClause,
            expectedColumnOrder, actualColumnOrder);
    }

    // Collects WHERE/JOIN clause differences for WRONG_CONTENT feedback
    private void collectWhereClauseInfo(Map<ErrorCategory, Integer> detectedErrors,
                                        String studentSql, String teacherSql,
                                        List<String> expectedWhereClause,
                                        List<String> actualWhereClause) {
        if (!detectedErrors.containsKey(ErrorCategory.WRONG_CONTENT)) return;

        String teacherWhere = SchemaTypeAnalyzer.extractOuterWhereClause(teacherSql);
        String studentWhere = SchemaTypeAnalyzer.extractOuterWhereClause(studentSql);
        String teacherMultisetWhere = SchemaTypeAnalyzer.extractMultisetWhereClause(teacherSql);
        String studentMultisetWhere = SchemaTypeAnalyzer.extractMultisetWhereClause(studentSql);

        if (teacherWhere != null) expectedWhereClause.add("WHERE " + teacherWhere.trim());
        if (studentWhere != null) actualWhereClause.add("WHERE " + studentWhere.trim());
        if (teacherMultisetWhere != null) expectedWhereClause.add("WHERE " + teacherMultisetWhere.trim());
        if (studentMultisetWhere != null) actualWhereClause.add("WHERE " + studentMultisetWhere.trim());

        List<String> teacherJoins = SchemaTypeAnalyzer.extractOuterJoinClauses(teacherSql);
        List<String> studentJoins = SchemaTypeAnalyzer.extractOuterJoinClauses(studentSql);
        if (!teacherJoins.equals(studentJoins)) {
            for (int i = 0; i < teacherJoins.size(); i++) {
                expectedWhereClause.add(teacherJoins.get(i));
                if (i < studentJoins.size()) {
                    actualWhereClause.add(studentJoins.get(i));
                }
            }
        }
    }

    private boolean shouldAttemptRepair(Map<ErrorCategory, Integer> detectedErrors) {
        return (detectedErrors.containsKey(ErrorCategory.MISSING_PRIMITIVE_FIELD)
            || detectedErrors.containsKey(ErrorCategory.INVALID_COLUMN_NAME)
            || detectedErrors.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)
            || detectedErrors.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)
            || detectedErrors.containsKey(ErrorCategory.WRONG_NESTED_TABLE_TYPE)
            || detectedErrors.containsKey(ErrorCategory.WRONG_COLUMN_ORDER))
            && !detectedErrors.containsKey(ErrorCategory.MISSING_NESTED_TABLE);
    }

    private String findConstructorCallString(String sql, List<String> typeNames, List<String> ctorArgs) {
        for (String typeName : typeNames) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\s*\\(", Pattern.CASE_INSENSITIVE);
            if (p.matcher(SchemaTypeAnalyzer.extractOuterSelectList(sql)).find()) {
                return typeName.toUpperCase() + "(" + String.join(", ", ctorArgs).toUpperCase() + ")";
            }
        }
        return null;
    }

    // Attempts repair execution in a separate schema
    private void attemptRepairExecution(String repairedSql, String studentSql, String teacherSql,
                                        OrViewTaskGroup group, OrViewTask task,
                                        boolean hasSuperview, boolean hasRefSuperview,
                                        boolean useSubmitData,
                                        Map<ErrorCategory, Integer> detectedErrors,
                                        List<String> expectedWhereClause,
                                        List<String> actualWhereClause) {
        String schema2 = "SUB_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        try {
            setup(schema2, group, useSubmitData);
            if (hasSuperview) execute(schema2, task.getUnderSuperview());
            if (hasRefSuperview) execute(schema2, task.getRefSuperview());
            recompileTypeBody(schema2);
            execute(schema2, repairedSql);
            QueryResult repairedResult = query(schema2, task.getTestQuery());
            execute(schema2, teacherSql);
            QueryResult teacherResult = query(schema2, task.getTestQuery());
            if (!compare(repairedResult, teacherResult)) {
                detectedErrors.put(ErrorCategory.WRONG_CONTENT, 1);
                if (expectedWhereClause.isEmpty()) {
                    collectWhereClauseInfo(detectedErrors, studentSql, teacherSql,
                        expectedWhereClause, actualWhereClause);
                }
            } else if (repairedSql.equals(teacherSql)) {
                long sJoins = Pattern.compile("\\bJOIN\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(studentSql).results().count();
                long tJoins = Pattern.compile("\\bJOIN\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(teacherSql).results().count();
                if (sJoins < tJoins) {
                    detectedErrors.put(ErrorCategory.WRONG_CONTENT, 1);
                    detectedErrors.remove(ErrorCategory.INVALID_COLUMN_NAME);
                }
            }
        } catch (SQLException ex) {
            LOG.debug("Repair failed (catch block): {}", ex.getMessage());
            checkRepairWhereError(ex.getMessage(), studentSql, detectedErrors);
        } finally {
            cleanup(schema2);
        }
    }

    private void checkRepairWhereError(String errorMsg, String studentSql,
                                       Map<ErrorCategory, Integer> detectedErrors) {
        if (errorMsg == null) return;

        if (errorMsg.contains("ORA-01722") || errorMsg.contains("ORA-61800")) {
            detectedErrors.put(ErrorCategory.WRONG_CONTENT, 1);
            return;
        }

        if (!errorMsg.contains("ORA-00904")) return;

        String colName = extractColumnFromOracleError(errorMsg);
        if (colName != null) {
            String whereClause = extractWhereClause(studentSql);
            if (whereClause != null && whereClause.toLowerCase().contains(colName)) {
                detectedErrors.put(ErrorCategory.WRONG_CONTENT, 1);
            }
        }
    }

    // Extracts column name from Oracle error message (supports "TABLE"."COL" and "COL" formats)
    private String extractColumnFromOracleError(String errorMsg) {
        Pattern fullRefPattern = Pattern.compile("\"(\\w+)\"\\.\"(\\w+)\"");
        Matcher fullRefMatcher = fullRefPattern.matcher(errorMsg);
        if (fullRefMatcher.find()) {
            return fullRefMatcher.group(1).toLowerCase() + "." + fullRefMatcher.group(2).toLowerCase();
        }
        Pattern simplePattern = Pattern.compile("\"(\\w+)\"");
        Matcher simpleMatcher = simplePattern.matcher(errorMsg);
        return simpleMatcher.find() ? simpleMatcher.group(1).toLowerCase() : null;
    }

    private Map<ErrorCategory, Integer> detectErrorCategories(
        SQLException error,
        String studentSql,
        String teacherSql,
        QueryResult student,
        QueryResult teacher,
        boolean oidValid,
        boolean underSuperviewValid,
        boolean hasSuperview,
        List<String> missingColumnNames,
        List<String> extraColumnNames,
        List<String> typeNames,
        List<String> expectedMakeRefArgs,
        List<String> actualMakeRefArgs,
        List<String> expectedConstructorCall,
        List<String> actualConstructorCall,
        List<String> expectedOfType,
        List<String> actualOfType,
        List<String> expectedCastType,
        List<String> actualCastType,
        List<String> expectedColumnOrder,
        List<String> actualColumnOrder
    ) {
        Map<ErrorCategory, Integer> categories = new HashMap<>();

        if (!oidValid) {
            categories.put(ErrorCategory.WRONG_OID, 1);
        }

        if (hasSuperview && !underSuperviewValid) {
            categories.put(ErrorCategory.WRONG_SUPERVIEW, 1);
        }

        String studentOfType = SchemaTypeAnalyzer.extractOfType(studentSql);
        String teacherOfType = SchemaTypeAnalyzer.extractOfType(teacherSql);
        if (studentOfType != null && teacherOfType != null
            && !studentOfType.equalsIgnoreCase(teacherOfType)) {
            categories.put(ErrorCategory.WRONG_VIEW_OBJECT_TYPE, 1);
            expectedOfType.add(teacherOfType);
            actualOfType.add(studentOfType);
        }

        // Count outer MAKE_REFs only (MULTISET handled separately)
        int teacherMakeRefs = SchemaTypeAnalyzer.countMakeRefs(removeMultisetContent(teacherSql));
        int studentMakeRefs = SchemaTypeAnalyzer.countMakeRefs(removeMultisetContent(studentSql));
        int missingMakeRefs = teacherMakeRefs - studentMakeRefs;
        if (missingMakeRefs > 0) {
            categories.put(ErrorCategory.MISSING_OBJECT_FIELD, missingMakeRefs);
            if (expectedMakeRefArgs.isEmpty()) {
                List<String> tArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(teacherSql);
                List<String> sArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(studentSql);
                if (!tArgs.equals(sArgs)) {
                    if (tArgs.isEmpty()) tArgs = SchemaTypeAnalyzer.extractMakeRefArgs(teacherSql);
                    expectedMakeRefArgs.addAll(tArgs);
                    actualMakeRefArgs.addAll(sArgs);
                }
            }
        }

        boolean teacherHasCastMultiset = SchemaTypeAnalyzer.hasCastMultiset(teacherSql);
        boolean studentHasCastMultiset = SchemaTypeAnalyzer.hasCastMultiset(studentSql);

        if (teacherHasCastMultiset && !studentHasCastMultiset)
            categories.put(ErrorCategory.MISSING_NESTED_TABLE, 1);

        if (teacherHasCastMultiset && studentHasCastMultiset) {
            String studentCastType = SchemaTypeAnalyzer.extractCastMultisetType(studentSql);
            String teacherCastType = SchemaTypeAnalyzer.extractCastMultisetType(teacherSql);
            if (studentCastType != null && teacherCastType != null
                && !studentCastType.equalsIgnoreCase(teacherCastType)) {
                categories.put(ErrorCategory.WRONG_NESTED_TABLE_TYPE, 1);
                expectedCastType.add(teacherCastType);
                actualCastType.add(studentCastType);
            }
        }

        List<String> teacherOuterMakeRefs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(teacherSql);
        List<String> studentOuterMakeRefs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(studentSql);
        if (!teacherOuterMakeRefs.isEmpty() && !studentOuterMakeRefs.isEmpty()
            && !teacherOuterMakeRefs.equals(studentOuterMakeRefs)) {
            categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::sum);
            if (expectedMakeRefArgs.isEmpty()) expectedMakeRefArgs.addAll(teacherOuterMakeRefs);
            if (actualMakeRefArgs.isEmpty()) actualMakeRefArgs.addAll(studentOuterMakeRefs);
        }

        if (teacherHasCastMultiset && studentHasCastMultiset) {
            boolean teacherMultisetHasMakeRef = SchemaTypeAnalyzer.multisetContainsMakeRef(teacherSql);
            boolean studentMultisetHasMakeRef = SchemaTypeAnalyzer.multisetContainsMakeRef(studentSql);
            compareMultisetColumns(teacherSql, studentSql, categories, missingColumnNames, extraColumnNames,
                teacherMultisetHasMakeRef && !studentMultisetHasMakeRef,
                expectedMakeRefArgs, actualMakeRefArgs);
        }

        List<String> teacherOuterNames = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames);
        List<String> studentOuterNames = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames);

        if (!teacherOuterNames.isEmpty() && !studentOuterNames.isEmpty()) {
            analyzeOuterColumns(categories, teacherOuterNames, studentOuterNames,
                teacherSql, studentSql, typeNames,
                missingColumnNames, extraColumnNames,
                expectedMakeRefArgs, actualMakeRefArgs,
                expectedConstructorCall, actualConstructorCall);
        }

        if (!categories.containsKey(ErrorCategory.WRONG_VIEW_OBJECT_TYPE) || teacherHasCastMultiset) {
            checkOuterColumnCountMismatch(categories, teacherSql, studentSql,
                expectedMakeRefArgs, actualMakeRefArgs);
        }

        // Column order check (pre-error)
        if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
            boolean swapped = hasSwappedColumns(studentSql, teacherSql, typeNames);
            if (swapped) {
                int wrongPositions = countWrongPositions(studentSql, teacherSql, typeNames);
                if (wrongPositions > 0) {
                    categories.put(ErrorCategory.WRONG_COLUMN_ORDER, wrongPositions);
                    fillColumnOrderLists(studentSql, teacherSql, typeNames,
                        expectedColumnOrder, actualColumnOrder);
                }
            }
        }

        if (error == null && student != null && teacher != null) {
            analyzeQueryResults(categories, student, teacher, studentSql, teacherSql,
                typeNames, missingColumnNames, extraColumnNames,
                expectedColumnOrder, actualColumnOrder);
        }

        if (error != null) {
            mapOracleErrorCodes(error, categories, studentSql, teacherSql, oidValid, typeNames);
            LOG.info("hasSwappedColumns after mapOracle: {}", hasSwappedColumns(studentSql, teacherSql, typeNames));
            LOG.info("categories after mapOracle: {}", categories);

            // Post-mapOracle column order check
            if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)
                && hasSwappedColumns(studentSql, teacherSql, typeNames)) {
                int wrongPositions = countPositionsInline(teacherSql, studentSql, typeNames);
                if (wrongPositions > 0) {
                    categories.put(ErrorCategory.WRONG_COLUMN_ORDER, wrongPositions);
                }
                fillColumnOrderLists(studentSql, teacherSql, typeNames,
                    expectedColumnOrder, actualColumnOrder);
            }

            // Fallback column order check
            if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
                int wrongPos = countFallbackWrongPositions(teacherSql, studentSql, typeNames);
                if (wrongPos > 0) {
                    categories.put(ErrorCategory.WRONG_COLUMN_ORDER, wrongPos);
                    fillColumnOrderLists(studentSql, teacherSql, typeNames,
                        expectedColumnOrder, actualColumnOrder);
                }
            }
        }

        analyzeUndefinedAliases(categories, studentSql);
        if (student == null && teacher == null && error == null) {
            if (whereClausesDiffer(studentSql, teacherSql)) {
                categories.put(ErrorCategory.WRONG_CONTENT, 1);
            }
            String outerOnly = removeMultisetContent(studentSql);
            List<String> fromAliases = SchemaTypeAnalyzer.extractFromAliases(outerOnly);
            String whereClause = extractWhereClause(studentSql);
            String selectList = SchemaTypeAnalyzer.extractOuterSelectList(studentSql);
            String checkArea = (whereClause != null ? whereClause : "") + " " + selectList;
            Pattern aliasRef = Pattern.compile("(\\w+)\\.(\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher m = aliasRef.matcher(checkArea);
            while (m.find()) {
                if (!fromAliases.contains(m.group(1).toLowerCase())) {
                    categories.put(ErrorCategory.WRONG_CONTENT, 1);
                    break;
                }
            }
        }
        cleanupMakeRefColumnNames(categories, extraColumnNames, teacherSql);
        cleanupConstructorColumnNames(categories, extraColumnNames, typeNames);

        if (categories.containsKey(ErrorCategory.WRONG_CONTENT)
            && categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME)
            && !categories.containsKey(ErrorCategory.MISSING_PRIMITIVE_FIELD)) {
            categories.remove(ErrorCategory.INVALID_COLUMN_NAME);
        }

        if (categories.containsKey(ErrorCategory.MISSING_NESTED_TABLE)) {
            if (SchemaTypeAnalyzer.hasCastMultiset(studentSql)) {
                int multisetMakeRefs = SchemaTypeAnalyzer.countMultisetMakeRefs(teacherSql);
                if (multisetMakeRefs > 0 && categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)) {
                    int current = categories.get(ErrorCategory.MISSING_OBJECT_FIELD);
                    int remaining = current - multisetMakeRefs;
                    if (remaining > 0) {
                        categories.put(ErrorCategory.MISSING_OBJECT_FIELD, remaining);
                    } else {
                        categories.remove(ErrorCategory.MISSING_OBJECT_FIELD);
                    }
                }
            }
            categories.remove(ErrorCategory.WRONG_CONTENT);
        }

        LOG.info("detectedErrors: {}", categories);
        LOG.info("missingColumnNames: {}, extraColumnNames: {}", missingColumnNames, extraColumnNames);

        return categories;
    }

    // Analyzes outer column names for missing/extra/constructor/makeref errors
    private void analyzeOuterColumns(Map<ErrorCategory, Integer> categories,
                                     List<String> teacherOuterNames,
                                     List<String> studentOuterNames,
                                     String teacherSql, String studentSql,
                                     List<String> typeNames,
                                     List<String> missingColumnNames,
                                     List<String> extraColumnNames,
                                     List<String> expectedMakeRefArgs,
                                     List<String> actualMakeRefArgs,
                                     List<String> expectedConstructorCall,
                                     List<String> actualConstructorCall) {
        List<String> teacherPrimitive = teacherOuterNames.stream()
            .filter(c -> !c.startsWith("__")).toList();
        List<String> studentPrimitive = studentOuterNames.stream()
            .filter(c -> !c.startsWith("__")).toList();

        List<String> studentRemaining = new ArrayList<>(studentPrimitive);
        int outerMissing = 0;

        for (String tc : teacherPrimitive) {
            if (studentRemaining.contains(tc)) {
                studentRemaining.remove(tc);
            } else {
                outerMissing++;
                if (!missingColumnNames.contains(tc)) {
                    missingColumnNames.add(tc);
                }
            }
        }

        for (String extra : studentRemaining) {
            if (!extraColumnNames.contains(extra)) {
                extraColumnNames.add(extra);
            }
        }

        if (outerMissing > 0) {
            categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, outerMissing, Integer::sum);
        }
        if (!studentRemaining.isEmpty()) {
            categories.merge(ErrorCategory.INVALID_COLUMN_NAME, studentRemaining.size(), Integer::sum);
        }

        boolean teacherHasMakeRef = teacherOuterNames.stream().anyMatch(c -> c.equals("__makeref__"));
        boolean studentHasMakeRef = studentOuterNames.stream().anyMatch(c -> c.equals("__makeref__"));
        boolean teacherHasConstructor = teacherOuterNames.stream().anyMatch(c -> c.equals("__object__"));
        boolean studentHasConstructor = studentOuterNames.stream().anyMatch(c -> c.equals("__object__"));

        if (teacherHasMakeRef && !studentHasMakeRef
            && !categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)) {
            categories.put(ErrorCategory.MISSING_OBJECT_FIELD, 1);
            List<String> tArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(teacherSql);
            List<String> sArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(studentSql);
            if (expectedMakeRefArgs.isEmpty()) expectedMakeRefArgs.addAll(tArgs);
            if (actualMakeRefArgs.isEmpty()) actualMakeRefArgs.addAll(sArgs);
        }
        if (teacherHasConstructor && !studentHasConstructor
            && !categories.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)) {
            categories.put(ErrorCategory.MISSING_CONSTRUCTOR, 1);
        }

        List<String> teacherCtorArgs = SchemaTypeAnalyzer.extractConstructorArgs(teacherSql, typeNames);
        List<String> studentCtorArgs = SchemaTypeAnalyzer.extractConstructorArgs(studentSql, typeNames);

        if (!teacherCtorArgs.isEmpty()) {
            if (studentCtorArgs.isEmpty() || !teacherCtorArgs.equals(studentCtorArgs)) {
                categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
                if (expectedConstructorCall.isEmpty()) {
                    populateConstructorCallLists(teacherSql, studentSql, typeNames,
                        teacherCtorArgs, studentCtorArgs,
                        expectedConstructorCall, actualConstructorCall);
                }
            }
        }

        if (teacherOuterNames.contains("__nested__") && !studentOuterNames.contains("__nested__")) {
            categories.putIfAbsent(ErrorCategory.MISSING_NESTED_TABLE, 1);
        }
    }

    // Populates expected/actual constructor call strings for feedback
    private void populateConstructorCallLists(String teacherSql, String studentSql,
                                              List<String> typeNames,
                                              List<String> teacherCtorArgs,
                                              List<String> studentCtorArgs,
                                              List<String> expectedConstructorCall,
                                              List<String> actualConstructorCall) {
        String expected = findConstructorCallString(teacherSql, typeNames, teacherCtorArgs);
        if (expected != null) expectedConstructorCall.add(expected);

        if (!studentCtorArgs.isEmpty() && actualConstructorCall.isEmpty()) {
            String actual = findConstructorCallString(studentSql, typeNames, studentCtorArgs);
            if (actual != null) actualConstructorCall.add(actual);
        }
    }

    // Checks for outer column count mismatch and missing MAKE_REF
    private void checkOuterColumnCountMismatch(Map<ErrorCategory, Integer> categories,
                                               String teacherSql, String studentSql,
                                               List<String> expectedMakeRefArgs,
                                               List<String> actualMakeRefArgs) {
        int teacherOuterCols = SchemaTypeAnalyzer.countOuterSelectColumns(teacherSql);
        int studentOuterCols = SchemaTypeAnalyzer.countOuterSelectColumns(studentSql);
        if (studentOuterCols > 0 && studentOuterCols != teacherOuterCols) {
            int diff = Math.abs(teacherOuterCols - studentOuterCols);
            if (categories.containsKey(ErrorCategory.MISSING_NESTED_TABLE)) diff--;
            if (diff > 0) {
                boolean teacherHasTopMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(teacherSql);
                boolean studentHasTopMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(studentSql);
                if (teacherHasTopMakeRef && !studentHasTopMakeRef
                    && !categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)) {
                    categories.put(ErrorCategory.MISSING_OBJECT_FIELD, 1);
                    if (expectedMakeRefArgs.isEmpty())
                        expectedMakeRefArgs.addAll(SchemaTypeAnalyzer.extractOuterMakeRefArgs(teacherSql));
                    if (actualMakeRefArgs.isEmpty())
                        actualMakeRefArgs.addAll(SchemaTypeAnalyzer.extractOuterMakeRefArgs(studentSql));
                }
            }
        }
    }

    // Analyzes query results for column/row mismatches
    private void analyzeQueryResults(Map<ErrorCategory, Integer> categories,
                                     QueryResult student, QueryResult teacher,
                                     String studentSql, String teacherSql,
                                     List<String> typeNames,
                                     List<String> missingColumnNames,
                                     List<String> extraColumnNames,
                                     List<String> expectedColumnOrder,
                                     List<String> actualColumnOrder) {
        boolean colMismatch = !student.columns().equals(teacher.columns());
        boolean rowMismatch = !new HashSet<>(student.rows()).equals(new HashSet<>(teacher.rows()));

        if (colMismatch) {
            handleColumnMismatch(categories, student, teacher, studentSql, teacherSql,
                missingColumnNames, extraColumnNames, typeNames);
        }

        if (!colMismatch && rowMismatch) {
            if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
                boolean swapped = hasSwappedColumns(studentSql, teacherSql, typeNames);
                if (swapped) {
                    int wrongPositions = countWrongPositions(studentSql, teacherSql, typeNames);
                    categories.put(ErrorCategory.WRONG_COLUMN_ORDER, wrongPositions);
                    fillColumnOrderLists(studentSql, teacherSql, typeNames,
                        expectedColumnOrder, actualColumnOrder);
                }
            }

            if (whereClausesDiffer(studentSql, teacherSql)) {
                categories.put(ErrorCategory.WRONG_CONTENT, 1);
            } else if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
                boolean structuralColumnErrors =
                    categories.containsKey(ErrorCategory.MISSING_PRIMITIVE_FIELD)
                        || categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME);

                boolean onlyColumnNameErrors = !categories.isEmpty()
                    && !categories.containsKey(ErrorCategory.MISSING_PRIMITIVE_FIELD)
                    && !categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)
                    && !categories.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)
                    && !categories.containsKey(ErrorCategory.MISSING_NESTED_TABLE)
                    && !categories.containsKey(ErrorCategory.WRONG_NESTED_TABLE_TYPE)
                    && !categories.containsKey(ErrorCategory.WRONG_VIEW_OBJECT_TYPE)
                    && !categories.containsKey(ErrorCategory.WRONG_OID)
                    && categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME);

                if (!onlyColumnNameErrors && !structuralColumnErrors)
                    categories.put(ErrorCategory.WRONG_CONTENT, 1);
            }
        }
    }

    // Analyzes undefined alias references in student SQL
    private void analyzeUndefinedAliases(Map<ErrorCategory, Integer> categories, String studentSql) {
        String outerOnlySql = removeMultisetContent(studentSql);
        List<String> fromAliases = SchemaTypeAnalyzer.extractFromAliases(outerOnlySql);
        List<String> undefinedOuterRefs = SchemaTypeAnalyzer.countUndefinedAliasRefs(outerOnlySql, fromAliases);

        List<String> multisetFromAliases = SchemaTypeAnalyzer.extractMultisetFromAliases(studentSql);
        List<String> allAliases = new ArrayList<>(fromAliases);
        allAliases.addAll(multisetFromAliases);
        List<String> undefinedMultisetRefs = SchemaTypeAnalyzer.countUndefinedMultisetAliasRefs(studentSql, allAliases);

        int undefinedAliasCount = undefinedOuterRefs.size() + undefinedMultisetRefs.size();
        if (undefinedAliasCount > 1 && categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME)) {
            categories.put(ErrorCategory.INVALID_COLUMN_NAME, undefinedAliasCount);
        }
    }

    // Counts wrong positions using inline outer + multiset analysis (for post-mapOracle check)
    private int countPositionsInline(String teacherSql, String studentSql, List<String> typeNames) {
        List<String> teacherColsAll = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();
        List<String> studentColsAll = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();

        int wrongPositions = 0;
        for (int i = 0; i < Math.min(teacherColsAll.size(), studentColsAll.size()); i++) {
            String t = teacherColsAll.get(i);
            String s = studentColsAll.get(i);
            LOG.info("outer pos {}: t={}, s={}", i, t, s);
            if (!t.startsWith("__") && !s.startsWith("__") && !t.equals(s)) {
                if (teacherColsAll.contains(s)) {
                    wrongPositions++;
                    LOG.info("wrongPositions++ primitive mismatch");
                }
            } else if (!t.startsWith("__") && s.startsWith("__")) {
                if (!(s.equals("__makeref__") && looksLikeMakeRef(t))
                    && !(s.equals("__object__") && t.contains("("))) {
                    wrongPositions++;
                    LOG.info("wrongPositions++ primitive vs special");
                }
            } else if (t.startsWith("__") && !s.startsWith("__")) {
                if (!(t.equals("__makeref__") && looksLikeMakeRef(s))
                    && !(t.equals("__object__") && s.contains("("))) {
                    wrongPositions++;
                    LOG.info("wrongPositions++ special vs primitive");
                }
            }
        }

        List<String> teacherMultisetAll = SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql);
        List<String> studentMultisetAll = SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql);

        if (!teacherMultisetAll.isEmpty() && !studentMultisetAll.isEmpty()) {
            for (int i = 0; i < Math.min(teacherMultisetAll.size(), studentMultisetAll.size()); i++) {
                String t = teacherMultisetAll.get(i);
                String s = studentMultisetAll.get(i);
                if (!t.equals(s)) {
                    if (t.startsWith("make_ref") && s.startsWith("make_ref")) continue;
                    boolean tIsMakeRef = t.startsWith("make_ref");
                    boolean sIsMakeRef = s.startsWith("make_ref");
                    if (tIsMakeRef != sIsMakeRef) {
                        wrongPositions++;
                    } else if (!sIsMakeRef && teacherMultisetAll.contains(s)) {
                        wrongPositions++;
                    }
                }
            }
        }

        return wrongPositions;
    }

    // Fallback column order check using simpler comparison
    private int countFallbackWrongPositions(String teacherSql, String studentSql, List<String> typeNames) {
        List<String> teacherColsFB = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();
        List<String> studentColsFB = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();

        // Filter to common columns to avoid counting shifted positions due to extra/missing columns
        Set<String> commonSet = new HashSet<>(teacherColsFB);
        commonSet.retainAll(new HashSet<>(studentColsFB));

        List<String> teacherFiltered = teacherColsFB.stream()
            .filter(commonSet::contains).toList();
        List<String> studentFiltered = new ArrayList<>();
        List<String> teacherRemaining = new ArrayList<>(teacherFiltered);
        for (String s : studentColsFB) {
            if (teacherRemaining.contains(s)) {
                studentFiltered.add(s);
                teacherRemaining.remove(s);
            }
        }

        int wrongPos = 0;
        for (int i = 0; i < Math.min(teacherFiltered.size(), studentFiltered.size()); i++) {
            String t = teacherFiltered.get(i);
            String s = studentFiltered.get(i);
            if (!t.equals(s)) {
                if (!t.startsWith("__") && s.startsWith("__")) {
                    wrongPos++;
                } else if (t.startsWith("__") && !s.startsWith("__")) {
                    if (!s.contains("(")) wrongPos++;
                } else if (!t.startsWith("__") && !s.startsWith("__")) {
                    wrongPos++;
                }
            }
        }
        return wrongPos;
    }

    private void compareMultisetColumns(String teacherSql, String studentSql,
                                        Map<ErrorCategory, Integer> categories,
                                        List<String> missingColumnNames,
                                        List<String> extraColumnNames,
                                        boolean subtractOneForMakeRef,
                                        List<String> expectedMakeRefArgs,
                                        List<String> actualMakeRefArgs) {
        List<String> teacherCols = SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql);
        List<String> studentCols = SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql);

        if (!teacherCols.isEmpty() && !studentCols.isEmpty()) {
            List<String> teacherMakeRefEntries = teacherCols.stream()
                .filter(c -> c.startsWith("make_ref")).toList();
            List<String> studentMakeRefEntries = studentCols.stream()
                .filter(c -> c.startsWith("make_ref")).toList();
            if (!teacherMakeRefEntries.isEmpty()
                && !teacherMakeRefEntries.equals(studentMakeRefEntries)) {
                categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::sum);
                List<String> tArgs = SchemaTypeAnalyzer.extractMultisetMakeRefArgs(teacherSql);
                List<String> sArgs = SchemaTypeAnalyzer.extractMultisetMakeRefArgs(studentSql);
                for (String arg : tArgs) {
                    if (!expectedMakeRefArgs.contains(arg)) expectedMakeRefArgs.add(arg);
                }
                for (String arg : sArgs) {
                    if (!actualMakeRefArgs.contains(arg)) actualMakeRefArgs.add(arg);
                }
            }
            List<String> studentRemaining = new ArrayList<>(studentCols);
            studentRemaining.removeIf(s -> s.startsWith("make_ref"));
            int missingCount = 0;
            for (String tc : teacherCols) {
                if (tc.startsWith("make_ref")) continue;
                if (studentRemaining.contains(tc)) {
                    studentRemaining.remove(tc);
                } else {
                    missingCount++;
                    missingColumnNames.add(tc);
                }
            }
            extraColumnNames.addAll(studentRemaining);
            if (missingCount > 0) {
                categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, missingCount, Integer::sum);
            }
            if (!studentRemaining.isEmpty()) {
                categories.merge(ErrorCategory.INVALID_COLUMN_NAME, studentRemaining.size(), Integer::sum);
            }
        } else {
            int teacherMultisetCols = SchemaTypeAnalyzer.countMultisetColumns(teacherSql);
            int studentMultisetCols = SchemaTypeAnalyzer.countMultisetColumns(studentSql);
            if (studentMultisetCols > 0 && studentMultisetCols != teacherMultisetCols) {
                int diff = Math.abs(teacherMultisetCols - studentMultisetCols);
                if (subtractOneForMakeRef) diff--;
                if (diff > 0) {
                    if (studentMultisetCols < teacherMultisetCols)
                        categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, diff, Integer::sum);
                    else
                        categories.merge(ErrorCategory.INVALID_COLUMN_NAME, diff, Integer::sum);
                }
            }
        }
    }

    private void handleColumnMismatch(Map<ErrorCategory, Integer> categories,
                                      QueryResult student, QueryResult teacher,
                                      String studentSql, String teacherSql,
                                      List<String> missingColumnNames, List<String> extraColumnNames,
                                      List<String> typeNames) {
        if (!categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)
            && !categories.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)) {
            int diff = Math.abs(student.columns().size() - teacher.columns().size());
            if (diff == 0) diff = 1;

            boolean teacherHasMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(teacherSql);
            boolean studentHasMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(studentSql);
            boolean teacherHasConstructor = SchemaTypeAnalyzer.hasConstructorField(
                SchemaTypeAnalyzer.extractOuterSelectList(teacherSql), typeNames);
            boolean studentHasConstructor = SchemaTypeAnalyzer.hasConstructorField(
                SchemaTypeAnalyzer.extractOuterSelectList(studentSql), typeNames);

            populateColumnLists(student, teacher, missingColumnNames, extraColumnNames);

            if (student.columns().size() < teacher.columns().size()) {
                if (teacherHasMakeRef && !studentHasMakeRef) {
                    categories.put(ErrorCategory.MISSING_OBJECT_FIELD, 1);
                    if (diff > 1)
                        categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, diff - 1, Integer::sum);
                } else if (teacherHasConstructor && !studentHasConstructor) {
                    categories.put(ErrorCategory.MISSING_CONSTRUCTOR, 1);
                    if (diff > 1)
                        categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, diff - 1, Integer::sum);
                } else if (teacherHasConstructor) {
                    int teacherArgs = countConstructorArgs(SchemaTypeAnalyzer.extractOuterSelectList(teacherSql), typeNames);
                    int studentArgs = countConstructorArgs(SchemaTypeAnalyzer.extractOuterSelectList(studentSql), typeNames);
                    if (teacherArgs > studentArgs)
                        categories.put(ErrorCategory.MISSING_CONSTRUCTOR, 1);
                    else
                        categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, diff, Integer::sum);
                } else {
                    categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, diff, Integer::sum);
                }
            } else {
                categories.merge(ErrorCategory.INVALID_COLUMN_NAME, diff, Integer::sum);
            }
        } else if (!categories.containsKey(ErrorCategory.MISSING_PRIMITIVE_FIELD)
            && !categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME)) {
            populateColumnLists(student, teacher, missingColumnNames, extraColumnNames);

            int missingFromTeacher = 0;
            int extraFromStudent = 0;
            for (String tc : teacher.columns()) {
                if (!student.columns().contains(tc)) missingFromTeacher++;
            }
            for (String sc : student.columns()) {
                if (!teacher.columns().contains(sc)) extraFromStudent++;
            }

            if (missingFromTeacher > 0)
                categories.merge(ErrorCategory.MISSING_PRIMITIVE_FIELD, missingFromTeacher, Integer::sum);
            if (extraFromStudent > 0)
                categories.merge(ErrorCategory.INVALID_COLUMN_NAME, extraFromStudent, Integer::sum);
        }
    }

    private void populateColumnLists(QueryResult student, QueryResult teacher,
                                     List<String> missingColumnNames, List<String> extraColumnNames) {
        if (missingColumnNames.isEmpty()) {
            for (String tc : teacher.columns()) {
                if (!student.columns().contains(tc)) missingColumnNames.add(tc);
            }
        }
        if (extraColumnNames.isEmpty()) {
            for (String sc : student.columns()) {
                if (!teacher.columns().contains(sc)) extraColumnNames.add(sc);
            }
        }
    }

    private void mapOracleErrorCodes(SQLException error,
                                     Map<ErrorCategory, Integer> categories,
                                     String studentSql, String teacherSql,
                                     boolean oidValid, List<String> typeNames) {
        String msg = error.getMessage();
        if (msg == null) return;

        if (msg.contains("ORA-22903") || msg.contains("ORA-00913")) {
            categories.putIfAbsent(ErrorCategory.WRONG_NESTED_TABLE_TYPE, 1);
        } else if (msg.contains("ORA-02303") || msg.contains("ORA-22907") ||
            msg.contains("ORA-00902") || msg.contains("ORA-02306") ||
            msg.contains("ORA-02302")) {
            if (!categories.containsKey(ErrorCategory.WRONG_NESTED_TABLE_TYPE))
                categories.putIfAbsent(ErrorCategory.WRONG_VIEW_OBJECT_TYPE, 1);
        } else if (msg.contains("ORA-01730") || msg.contains("ORA-02315")) {
            handleOra01730(categories, studentSql, teacherSql, typeNames);
        } else if (msg.contains("ORA-00909")) {
            categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::max);
        } else if (msg.contains("ORA-00932")) {
            handleOra00932(categories, studentSql, teacherSql, typeNames);
        } else if (msg.contains("ORA-00904")) {
            handleOra00904(msg, categories, studentSql, teacherSql, oidValid, typeNames);
        } else if (msg.contains("ORA-00918")) {
            categories.putIfAbsent(ErrorCategory.INVALID_COLUMN_NAME, 1);
        } else if (msg.contains("ORA-01722") || msg.contains("ORA-61800")) {
            handleOraWhereError(categories, studentSql, teacherSql);
        }
    }

    // Handles ORA-00932 (inconsistent datatypes) — column order or type mismatch
    private void handleOra00932(Map<ErrorCategory, Integer> categories,
                                String studentSql, String teacherSql,
                                List<String> typeNames) {
        int teacherCols = SchemaTypeAnalyzer.countOuterSelectColumns(teacherSql);
        int studentCols = SchemaTypeAnalyzer.countOuterSelectColumns(studentSql);

        boolean makeRefPositionWrong = SchemaTypeAnalyzer.countMakeRefs(teacherSql)
            == SchemaTypeAnalyzer.countMakeRefs(studentSql)
            && !SchemaTypeAnalyzer.extractMultisetMakeRefArgs(teacherSql)
            .equals(SchemaTypeAnalyzer.extractMultisetMakeRefArgs(studentSql));

        boolean outerMakeRefPositionWrong = isMakeRefAtWrongPosition(teacherSql, studentSql, typeNames);
        boolean multisetMakeRefPositionWrong = isMultisetMakeRefAtWrongPosition(teacherSql, studentSql);

        boolean constructorPositionWrong = SchemaTypeAnalyzer.hasConstructorField(teacherSql, typeNames)
            && SchemaTypeAnalyzer.hasConstructorField(studentSql, typeNames)
            && teacherCols == studentCols;

        if (makeRefPositionWrong || outerMakeRefPositionWrong || multisetMakeRefPositionWrong) {
            if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
                int count = 0;
                if (outerMakeRefPositionWrong) count++;
                if (multisetMakeRefPositionWrong) count++;
                if (makeRefPositionWrong) count++;
                categories.put(ErrorCategory.WRONG_COLUMN_ORDER, count);
            }

            if (!categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)) {
                if (outerMakeRefPositionWrong || makeRefPositionWrong) {
                    List<String> tArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(teacherSql);
                    List<String> sArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(studentSql);
                    if (!tArgs.equals(sArgs)) {
                        categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::sum);
                    }
                }
                if (multisetMakeRefPositionWrong) {
                    List<String> tArgs = SchemaTypeAnalyzer.extractMultisetMakeRefArgs(teacherSql);
                    List<String> sArgs = SchemaTypeAnalyzer.extractMultisetMakeRefArgs(studentSql);
                    if (!tArgs.equals(sArgs)) {
                        categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::sum);
                    }
                }
            }
        } else if (constructorPositionWrong) {
            if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
                categories.put(ErrorCategory.WRONG_COLUMN_ORDER, 1);
            }
            List<String> teacherCtorArgs = SchemaTypeAnalyzer.extractConstructorArgs(teacherSql, typeNames);
            List<String> studentCtorArgs = SchemaTypeAnalyzer.extractConstructorArgs(studentSql, typeNames);
            if (!teacherCtorArgs.equals(studentCtorArgs)) {
                categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
            }
        } else if (hasSwappedColumns(studentSql, teacherSql, typeNames)) {
            int wrongPositions = countWrongPositions(studentSql, teacherSql, typeNames);
            if (wrongPositions > 0) {
                categories.put(ErrorCategory.WRONG_COLUMN_ORDER, wrongPositions);
            }
        } else if (teacherCols != studentCols) {
            categories.putIfAbsent(ErrorCategory.MISSING_PRIMITIVE_FIELD, 1);
        }
    }

    // Handles ORA-01722/ORA-61800 (invalid number / boolean literal)
    private void handleOraWhereError(Map<ErrorCategory, Integer> categories,
                                     String studentSql, String teacherSql) {
        if (whereClausesDiffer(studentSql, teacherSql)) {
            categories.putIfAbsent(ErrorCategory.WRONG_CONTENT, 1);
        } else if (!categories.containsKey(ErrorCategory.WRONG_COLUMN_ORDER)) {
            categories.putIfAbsent(ErrorCategory.MISSING_PRIMITIVE_FIELD, 1);
        }
    }

    private boolean isMultisetMakeRefAtWrongPosition(String teacherSql, String studentSql) {
        if (!SchemaTypeAnalyzer.multisetContainsMakeRef(teacherSql)
            || !SchemaTypeAnalyzer.multisetContainsMakeRef(studentSql)) return false;
        List<String> tCols = SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql);
        List<String> sCols = SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql);
        int tPos = -1, sPos = -1;
        for (int i = 0; i < tCols.size(); i++) if (tCols.get(i).startsWith("make_ref")) { tPos = i; break; }
        for (int i = 0; i < sCols.size(); i++) if (sCols.get(i).startsWith("make_ref")) { sPos = i; break; }
        return tPos != sPos;
    }

    private boolean isMakeRefAtWrongPosition(String teacherSql, String studentSql, List<String> typeNames) {
        if (!SchemaTypeAnalyzer.hasTopLevelMakeRef(teacherSql)
            || !SchemaTypeAnalyzer.hasTopLevelMakeRef(studentSql)) return false;
        if (SchemaTypeAnalyzer.countMakeRefs(teacherSql) != SchemaTypeAnalyzer.countMakeRefs(studentSql)) return false;
        List<String> tCols = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames);
        List<String> sCols = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames);
        return tCols.indexOf("__makeref__") != sCols.indexOf("__makeref__");
    }

    private void handleOra01730(Map<ErrorCategory, Integer> categories,
                                String studentSql, String teacherSql,
                                List<String> typeNames) {
        if (categories.containsKey(ErrorCategory.MISSING_NESTED_TABLE)
            || categories.containsKey(ErrorCategory.WRONG_VIEW_OBJECT_TYPE)
            || categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)
            || categories.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)
            || categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME)) {
            return;
        }

        boolean teacherHasMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(teacherSql);
        boolean studentHasMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(studentSql);
        boolean teacherHasConstructor = SchemaTypeAnalyzer.hasConstructorField(
            SchemaTypeAnalyzer.extractOuterSelectList(teacherSql), typeNames);
        boolean studentHasConstructor = SchemaTypeAnalyzer.hasConstructorField(
            SchemaTypeAnalyzer.extractOuterSelectList(studentSql), typeNames);

        if (teacherHasMakeRef && !studentHasMakeRef) {
            categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::max);
        } else if (teacherHasConstructor && !studentHasConstructor) {
            categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
        } else if (teacherHasConstructor) {
            int teacherArgs = countConstructorArgs(SchemaTypeAnalyzer.extractOuterSelectList(teacherSql), typeNames);
            int studentArgs = countConstructorArgs(SchemaTypeAnalyzer.extractOuterSelectList(studentSql), typeNames);
            if (teacherArgs > studentArgs)
                categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
            else
                categories.putIfAbsent(ErrorCategory.MISSING_PRIMITIVE_FIELD, 1);
        } else {
            categories.putIfAbsent(ErrorCategory.MISSING_PRIMITIVE_FIELD, 1);
        }
    }

    private boolean whereClausesDiffer(String studentSql, String teacherSql) {
        String teacherWhere = SchemaTypeAnalyzer.extractOuterWhereClause(teacherSql);
        String studentWhere = SchemaTypeAnalyzer.extractOuterWhereClause(studentSql);
        String teacherMultisetWhere = SchemaTypeAnalyzer.extractMultisetWhereClause(teacherSql);
        String studentMultisetWhere = SchemaTypeAnalyzer.extractMultisetWhereClause(studentSql);

        return (teacherWhere != null && !teacherWhere.equalsIgnoreCase(Objects.requireNonNullElse(studentWhere, "")))
            || (teacherMultisetWhere != null && !teacherMultisetWhere.equalsIgnoreCase(Objects.requireNonNullElse(studentMultisetWhere, "")));
    }

    private void fillColumnOrderLists(String studentSql, String teacherSql,
                                      List<String> typeNames,
                                      List<String> expectedColumnOrder,
                                      List<String> actualColumnOrder) {
        List<String> teacherCols = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();
        List<String> studentCols = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();

        if (!teacherCols.equals(studentCols)) {
            List<String> teacherOuter = mapColsToDisplay(teacherCols, teacherSql);
            List<String> studentOuter = mapColsToDisplay(studentCols, studentSql);

            if (!teacherOuter.equals(studentOuter)) {
                expectedColumnOrder.add("Äußere SELECT: " + String.join(", ", teacherOuter));
                actualColumnOrder.add("Äußere SELECT: " + String.join(", ", studentOuter));
            }
        }

        List<String> allTeacherMultiset = SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql);
        List<String> allStudentMultiset = SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql);

        if (!allTeacherMultiset.equals(allStudentMultiset)) {
            List<String> teacherM = mapMultisetColsToDisplay(allTeacherMultiset, teacherSql);
            List<String> studentM = mapMultisetColsToDisplay(allStudentMultiset, studentSql);

            if (!teacherM.equals(studentM)) {
                expectedColumnOrder.add("MULTISET: " + String.join(", ", teacherM));
                actualColumnOrder.add("MULTISET: " + String.join(", ", studentM));
            }
        }
    }

    // Maps outer column identifiers to display strings
    private List<String> mapColsToDisplay(List<String> cols, String sql) {
        return cols.stream().map(c -> {
            if (c.equals("__makeref__")) {
                List<String> args = SchemaTypeAnalyzer.extractOuterMakeRefArgs(sql);
                return args.isEmpty() ? "MAKE_REF(...)" : "MAKE_REF(" + args.getFirst().toUpperCase() + ")";
            }
            if (c.equals("__object__")) return "CONSTRUCTOR(...)";
            return c.toUpperCase();
        }).toList();
    }

    // Maps multiset column identifiers to display strings
    private List<String> mapMultisetColsToDisplay(List<String> cols, String sql) {
        List<String> makeRefArgs = SchemaTypeAnalyzer.extractMultisetMakeRefArgs(sql);
        int idx = 0;
        List<String> result = new ArrayList<>();
        for (String c : cols) {
            if (c.startsWith("make_ref")) {
                result.add(idx < makeRefArgs.size()
                    ? "MAKE_REF(" + makeRefArgs.get(idx++).toUpperCase() + ")"
                    : "MAKE_REF(...)");
            } else {
                result.add(c.toUpperCase());
            }
        }
        return result;
    }

    private void handleOra00904(String msg, Map<ErrorCategory, Integer> categories,
                                String studentSql, String teacherSql, boolean oidValid,
                                List<String> typeNames) {
        String msgUpper = msg.toUpperCase();
        if (looksLikeMakeRef(msgUpper)) {
            categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::max);
            return;
        }

        if (!oidValid) {
            categories.putIfAbsent(ErrorCategory.WRONG_OID, 1);
            return;
        }

        String oracleCol = extractColumnFromOracleError(msg);

        if (oracleCol != null && isColumnInMakeRef(oracleCol, studentSql)) {
            categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::max);
            return;
        }

        if (oracleCol != null && isColumnInWhereOnly(msg, studentSql)) {
            categories.putIfAbsent(ErrorCategory.WRONG_CONTENT, 1);
            return;
        }

        if (oracleCol != null) {
            boolean isTypeName = typeNames.stream()
                .anyMatch(t -> t.equalsIgnoreCase(oracleCol));
            if (isTypeName) {
                categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
                categories.remove(ErrorCategory.INVALID_COLUMN_NAME);
                return;
            }
            Pattern ctorPattern = Pattern.compile(
                "\\b" + Pattern.quote(oracleCol.toUpperCase()) + "\\s*\\(", Pattern.CASE_INSENSITIVE);
            if (ctorPattern.matcher(studentSql).find()) {
                categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
                categories.remove(ErrorCategory.INVALID_COLUMN_NAME);
                return;
            }
        }

        boolean teacherHasMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(teacherSql);
        boolean studentHasMakeRef = SchemaTypeAnalyzer.hasTopLevelMakeRef(studentSql);
        boolean teacherHasConstructor = SchemaTypeAnalyzer.hasConstructorField(
            SchemaTypeAnalyzer.extractOuterSelectList(teacherSql), typeNames);
        boolean studentHasConstructor = SchemaTypeAnalyzer.hasConstructorField(
            SchemaTypeAnalyzer.extractOuterSelectList(studentSql), typeNames);

        if (teacherHasMakeRef && !studentHasMakeRef)
            categories.merge(ErrorCategory.MISSING_OBJECT_FIELD, 1, Integer::max);
        else if (teacherHasConstructor && !studentHasConstructor)
            categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
        else if (oracleCol != null && isColumnInConstructor(oracleCol, studentSql, typeNames))
            categories.merge(ErrorCategory.MISSING_CONSTRUCTOR, 1, Integer::max);
        else
            categories.putIfAbsent(ErrorCategory.INVALID_COLUMN_NAME, 1);
    }

    // Simplified overload without schema parameters (unused in swapped-column detection)
    private boolean hasSwappedColumns(String studentSql, String teacherSql, List<String> typeNames) {
        List<String> studentCols = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();
        List<String> teacherCols = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();

        if (studentCols.size() == teacherCols.size()
            && !studentCols.equals(teacherCols)
            && new HashSet<>(studentCols).equals(new HashSet<>(teacherCols))) {
            return true;
        }

        if (studentCols.size() == teacherCols.size() && !studentCols.equals(teacherCols)) {
            for (int i = 0; i < teacherCols.size(); i++) {
                String t = teacherCols.get(i);
                String s = studentCols.get(i);
                if ((t.equals("__object__") || t.equals("__makeref__")) && !s.equals(t)) return true;
                if ((s.equals("__object__") || s.equals("__makeref__")) && !t.equals(s)) return true;
            }
        }

        List<String> teacherMultisetAll = SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql);
        List<String> studentMultisetAll = SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql);

        // Multiset columns - same size
        if (teacherMultisetAll.size() == studentMultisetAll.size()
            && !teacherMultisetAll.equals(studentMultisetAll)) {
            for (int i = 0; i < teacherMultisetAll.size(); i++) {
                String t = teacherMultisetAll.get(i);
                String s = studentMultisetAll.get(i);
                if (!looksLikeMakeRef(t) && looksLikeMakeRef(s)) return true;
            }
            List<String> teacherMultisetPrim = teacherMultisetAll.stream()
                .filter(c -> !looksLikeMakeRef(c)).toList();
            List<String> studentMultisetPrim = studentMultisetAll.stream()
                .filter(c -> !looksLikeMakeRef(c)).toList();
            if (studentMultisetPrim.size() == teacherMultisetPrim.size()
                && !studentMultisetPrim.equals(teacherMultisetPrim)
                && new HashSet<>(studentMultisetPrim).equals(new HashSet<>(teacherMultisetPrim))) {
                return true;
            }

            // Fallback: filter to common columns and check order
            Set<String> commonSet = new HashSet<>(teacherMultisetPrim);
            commonSet.retainAll(new HashSet<>(studentMultisetPrim));
            List<String> teacherFiltered = teacherMultisetPrim.stream()
                .filter(commonSet::contains).toList();
            List<String> studentFiltered = studentMultisetPrim.stream()
                .filter(commonSet::contains).toList();
            if (studentFiltered.size() >= 2 && !studentFiltered.equals(teacherFiltered)) {
                return true;
            }
        }

        // Multiset columns - different size: filter both sides to common columns
        if (!teacherMultisetAll.isEmpty() && !studentMultisetAll.isEmpty()
            && teacherMultisetAll.size() != studentMultisetAll.size()) {
            // Check MAKE_REF position
            if (SchemaTypeAnalyzer.multisetContainsMakeRef(teacherSql)
                && SchemaTypeAnalyzer.multisetContainsMakeRef(studentSql)) {
                int tPos = -1, sPos = -1;
                for (int i = 0; i < teacherMultisetAll.size(); i++) {
                    if (looksLikeMakeRef(teacherMultisetAll.get(i))) { tPos = i; break; }
                }
                for (int i = 0; i < studentMultisetAll.size(); i++) {
                    if (looksLikeMakeRef(studentMultisetAll.get(i))) { sPos = i; break; }
                }
                if (tPos != sPos) return true;
            }

            List<String> teacherMultisetPrim = teacherMultisetAll.stream()
                .filter(c -> !looksLikeMakeRef(c)).toList();
            List<String> studentMultisetPrim = studentMultisetAll.stream()
                .filter(c -> !looksLikeMakeRef(c)).toList();

            Set<String> commonSet = new HashSet<>(teacherMultisetPrim);
            commonSet.retainAll(new HashSet<>(studentMultisetPrim));

            List<String> teacherFiltered = teacherMultisetPrim.stream()
                .filter(commonSet::contains).toList();
            List<String> studentFiltered = studentMultisetPrim.stream()
                .filter(commonSet::contains).toList();

            if (studentFiltered.size() >= 2
                && !studentFiltered.equals(teacherFiltered)) {
                return true;
            }
        }

        return false;
    }

    private void cleanupConstructorColumnNames(Map<ErrorCategory, Integer> categories,
                                               List<String> extraColumnNames,
                                               List<String> typeNames) {
        if (!categories.containsKey(ErrorCategory.MISSING_CONSTRUCTOR)
            || !categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME)) {
            return;
        }

        long constructorLikeCount = extraColumnNames.stream()
            .filter(col -> isConstructorLikeName(col, typeNames))
            .count();

        if (constructorLikeCount > 0) {
            int currentCount = categories.get(ErrorCategory.INVALID_COLUMN_NAME);
            int newCount = currentCount - (int) constructorLikeCount;
            if (newCount <= 0) {
                categories.remove(ErrorCategory.INVALID_COLUMN_NAME);
            } else {
                categories.put(ErrorCategory.INVALID_COLUMN_NAME, newCount);
            }
            extraColumnNames.removeIf(col -> isConstructorLikeName(col, typeNames));
        }
    }

    private boolean isConstructorLikeName(String col, List<String> typeNames) {
        String lower = col.toLowerCase();
        for (String typeName : typeNames) {
            if (lower.startsWith(typeName) || levenshteinDistance(lower.split("\\(")[0], typeName) <= 2) {
                return true;
            }
        }
        return lower.contains("(") && lower.contains(")") && !looksLikeMakeRef(lower);
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

    private boolean isColumnInMakeRef(String oracleCol, String studentSql) {
        Pattern makeRefContent = Pattern.compile("MAKE_REF\\s*\\([^)]*", Pattern.CASE_INSENSITIVE);
        Matcher makeRefMatcher = makeRefContent.matcher(studentSql);
        while (makeRefMatcher.find()) {
            if (makeRefMatcher.group().toUpperCase().contains(oracleCol.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isColumnInWhereOnly(String errorMsg, String studentSql) {
        Pattern fullRefPattern = Pattern.compile("\"(\\w+)\"\\.\"(\\w+)\"");
        Matcher fullRefMatcher = fullRefPattern.matcher(errorMsg);
        if (!fullRefMatcher.find()) return false;

        String fullRef = fullRefMatcher.group(1).toLowerCase() + "." + fullRefMatcher.group(2).toLowerCase();

        String whereClause = extractWhereClause(studentSql);
        if (whereClause == null) return false;

        Pattern whereCheck = Pattern.compile("\\b" + Pattern.quote(fullRef) + "\\b", Pattern.CASE_INSENSITIVE);
        if (!whereCheck.matcher(whereClause).find()) return false;

        String selectList = SchemaTypeAnalyzer.extractOuterSelectList(studentSql);
        Pattern mp = Pattern.compile("MULTISET\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher mm = mp.matcher(studentSql);
        String multisetSelect = mm.find() ? mm.group(1) : "";
        String allSelects = (selectList + " " + multisetSelect).toLowerCase();

        Pattern selectCheck = Pattern.compile("\\b" + Pattern.quote(fullRef) + "\\b", Pattern.CASE_INSENSITIVE);
        return !selectCheck.matcher(allSelects).find();
    }

    private int countWrongPositions(String studentSql, String teacherSql, List<String> typeNames) {
        LOG.info("teacherMultiset: {}", SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql));
        LOG.info("studentMultiset: {}", SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql));
        List<String> studentCols = SchemaTypeAnalyzer.extractOuterColumnNames(studentSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();
        List<String> teacherCols = SchemaTypeAnalyzer.extractOuterColumnNames(teacherSql, typeNames)
            .stream().filter(c -> !c.equals("__nested__")).toList();
        List<String> studentMultiset = SchemaTypeAnalyzer.extractMultisetColumnNames(studentSql);
        List<String> teacherMultiset = SchemaTypeAnalyzer.extractMultisetColumnNames(teacherSql);

        int wrong = 0;

        // Outer columns
        if (studentCols.size() == teacherCols.size()) {
            for (int i = 0; i < studentCols.size(); i++) {
                String t = teacherCols.get(i);
                String s = studentCols.get(i);
                if (!t.equals(s)) {
                    if (t.startsWith("__makeref__") && looksLikeMakeRef(s)) continue;
                    if (s.startsWith("__makeref__") && looksLikeMakeRef(t)) continue;
                    if (t.equals("__object__") && s.contains("(")) continue;
                    if (s.equals("__object__") && t.contains("(")) continue;
                    boolean sExistsInTeacher;
                    if (s.startsWith("__makeref__")) {
                        sExistsInTeacher = teacherCols.stream().anyMatch(c -> c.equals("__makeref__"));
                    } else if (s.startsWith("__object__")) {
                        sExistsInTeacher = teacherCols.stream().anyMatch(c -> c.equals("__object__"));
                    } else {
                        sExistsInTeacher = teacherCols.contains(s);
                    }
                    if (sExistsInTeacher) {
                        wrong++;
                    }
                }
            }
        } else {
            // Different size: filter to common columns
            Set<String> commonSet = new HashSet<>(teacherCols);
            commonSet.retainAll(new HashSet<>(studentCols));

            List<String> teacherFiltered = teacherCols.stream()
                .filter(commonSet::contains).toList();
            List<String> studentFiltered = new ArrayList<>();
            List<String> teacherRemaining = new ArrayList<>(teacherFiltered);
            for (String s : studentCols) {
                if (teacherRemaining.contains(s)) {
                    studentFiltered.add(s);
                    teacherRemaining.remove(s);
                }
            }

            for (int i = 0; i < Math.min(teacherFiltered.size(), studentFiltered.size()); i++) {
                if (!teacherFiltered.get(i).equals(studentFiltered.get(i))) {
                    wrong++;
                }
            }
        }

        // Multiset columns - same size
        if (!studentMultiset.isEmpty() && !teacherMultiset.isEmpty()
            && studentMultiset.size() == teacherMultiset.size()) {
            for (int i = 0; i < studentMultiset.size(); i++) {
                String t = teacherMultiset.get(i);
                String s = studentMultiset.get(i);
                if (!t.equals(s)) {
                    if (looksLikeMakeRef(t) && looksLikeMakeRef(s)) continue;
                    boolean sExistsInTeacher;
                    if (looksLikeMakeRef(s)) {
                        sExistsInTeacher = teacherMultiset.stream().anyMatch(this::looksLikeMakeRef);
                    } else {
                        sExistsInTeacher = teacherMultiset.contains(s);
                    }
                    if (sExistsInTeacher) {
                        wrong++;
                    }
                }
            }

            // Fallback: if no swaps found above, check common columns
            if (wrong == 0) {
                List<String> teacherPrim = teacherMultiset.stream()
                    .filter(c -> !looksLikeMakeRef(c)).toList();
                List<String> studentPrim = studentMultiset.stream()
                    .filter(c -> !looksLikeMakeRef(c)).toList();
                Set<String> commonSet = new HashSet<>(teacherPrim);
                commonSet.retainAll(new HashSet<>(studentPrim));
                List<String> teacherFiltered = teacherPrim.stream()
                    .filter(commonSet::contains).toList();
                List<String> studentFiltered = studentPrim.stream()
                    .filter(commonSet::contains).toList();
                for (int i = 0; i < Math.min(teacherFiltered.size(), studentFiltered.size()); i++) {
                    if (!teacherFiltered.get(i).equals(studentFiltered.get(i))) {
                        wrong++;
                    }
                }
            }
        }

        // Multiset columns - different size: filter both sides to common columns
        if (!studentMultiset.isEmpty() && !teacherMultiset.isEmpty()
            && studentMultiset.size() != teacherMultiset.size()) {
            // Check MAKE_REF position
            if (SchemaTypeAnalyzer.multisetContainsMakeRef(teacherSql)
                && SchemaTypeAnalyzer.multisetContainsMakeRef(studentSql)) {
                int tPos = -1, sPos = -1;
                for (int i = 0; i < teacherMultiset.size(); i++) {
                    if (looksLikeMakeRef(teacherMultiset.get(i))) { tPos = i; break; }
                }
                for (int i = 0; i < studentMultiset.size(); i++) {
                    if (looksLikeMakeRef(studentMultiset.get(i))) { sPos = i; break; }
                }
                if (tPos != sPos) {
                    wrong++;
                }
            }

            List<String> teacherPrim = teacherMultiset.stream()
                .filter(c -> !looksLikeMakeRef(c)).toList();
            List<String> studentPrim = studentMultiset.stream()
                .filter(c -> !looksLikeMakeRef(c)).toList();

            Set<String> commonSet = new HashSet<>(teacherPrim);
            commonSet.retainAll(new HashSet<>(studentPrim));

            List<String> teacherFiltered = teacherPrim.stream()
                .filter(commonSet::contains).toList();
            List<String> studentFiltered = new ArrayList<>();
            List<String> teacherRemaining = new ArrayList<>(teacherFiltered);
            for (String s : studentPrim) {
                if (teacherRemaining.contains(s)) {
                    studentFiltered.add(s);
                    teacherRemaining.remove(s);
                }
            }

            for (int i = 0; i < Math.min(teacherFiltered.size(), studentFiltered.size()); i++) {
                if (!teacherFiltered.get(i).equals(studentFiltered.get(i))) {
                    wrong++;
                }
            }
        }

        LOG.info("studentCols: {}", studentCols);
        LOG.info("teacherCols: {}", teacherCols);
        LOG.info("wrongPositions: {}", wrong);
        return wrong;
    }

    private boolean isColumnInConstructor(String oracleCol, String studentSql, List<String> typeNames) {
        for (String typeName : typeNames) {
            Pattern p = Pattern.compile(
                "\\b" + Pattern.quote(typeName) + "\\s*\\([^)]*",
                Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(studentSql);
            while (m.find()) {
                if (m.group().toUpperCase().contains(oracleCol.toUpperCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cleanupMakeRefColumnNames(Map<ErrorCategory, Integer> categories,
                                           List<String> extraColumnNames,
                                           String teacherSql) {
        if (!categories.containsKey(ErrorCategory.MISSING_OBJECT_FIELD)
            || !categories.containsKey(ErrorCategory.INVALID_COLUMN_NAME)) {
            return;
        }

        long makeRefLikeCount = extraColumnNames.stream()
            .filter(this::looksLikeMakeRef)
            .count();

        List<String> teacherMakeRefArgs = SchemaTypeAnalyzer.extractOuterMakeRefArgs(teacherSql);
        String teacherArgsJoined = String.join(" ", teacherMakeRefArgs).toLowerCase();
        long makeRefArgCount = extraColumnNames.stream()
            .filter(col -> teacherArgsJoined.contains(col.toLowerCase()))
            .count();

        long totalToRemove = makeRefLikeCount + makeRefArgCount;
        if (totalToRemove > 0) {
            int currentCount = categories.get(ErrorCategory.INVALID_COLUMN_NAME);
            int newCount = currentCount - (int) totalToRemove;
            if (newCount <= 0) {
                categories.remove(ErrorCategory.INVALID_COLUMN_NAME);
            } else {
                categories.put(ErrorCategory.INVALID_COLUMN_NAME, newCount);
            }
            extraColumnNames.removeIf(col -> looksLikeMakeRef(col)
                || teacherArgsJoined.contains(col.toLowerCase()));
        }
    }

    private boolean looksLikeMakeRef(String input) {
        String lower = input.toLowerCase();
        if (lower.contains("make_ref") || lower.contains("makeref")) return true;
        String[] tokens = lower.split("[^a-z_]+");
        for (String token : tokens) {
            if (levenshteinDistance(token, "make_ref") <= 2) return true;
            if (levenshteinDistance(token, "makeref") <= 2) return true;
        }
        return false;
    }

    private int countConstructorArgs(String selectList, List<String> typeNames) {
        for (String typeName : typeNames) {
            Pattern p = Pattern.compile(
                "\\b" + Pattern.quote(typeName) + "\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(selectList);
            if (m.find()) {
                return m.group(1).split(",").length;
            }
        }
        return 0;
    }

    private GradingDto buildReport(OrViewTask task,
                                   SubmitSubmissionDto<SubmissionDto> dto,
                                   QueryResult student,
                                   QueryResult teacher,
                                   boolean correct,
                                   SQLException error,
                                   Locale locale,
                                   boolean oidValid,
                                   boolean superviewValid,
                                   boolean makeRefMissing,
                                   Map<ErrorCategory, Integer> detectedErrors,
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
                                   List<String> actualColumnOrder) {
        OrViewReport report = new OrViewReport(messageSource);
        return report.build(
            task, dto.mode(), dto.feedbackLevel(),
            student, teacher, correct,
            error,
            locale, oidValid, true, superviewValid, makeRefMissing,
            detectedErrors, missingColumnNames, extraColumnNames,
            expectedMakeRefArgs, actualMakeRefArgs, expectedConstructorCall, actualConstructorCall,
            expectedOfType, actualOfType, expectedCastType, actualCastType, expectedOid, actualOid,
            expectedWhereClause, actualWhereClause,
            expectedColumnOrder, actualColumnOrder
        );
    }

    private void validateSemicolon(String sql) throws SQLException {
        if (sql == null || !sql.trim().endsWith(";"))
            throw new SQLException("MISSING_SEMICOLON");
    }

    private String extractWhereClause(String sql) {
        Pattern p = Pattern.compile("\\b(?:WHERE|ON)\\b(.+?)(?:ORDER\\s+BY|GROUP\\s+BY|HAVING|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(sql);
        StringBuilder allWhere = new StringBuilder();
        while (m.find()) {
            allWhere.append(m.group(1)).append(" ");
        }
        return allWhere.isEmpty() ? null : allWhere.toString();
    }

    private boolean validateObjectIdentifier(String sql, String expected) {
        Pattern oidPattern = Pattern.compile(
            "WITH\\s+OBJECT\\s+IDENTIFIER\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = oidPattern.matcher(sql);
        if (!matcher.find()) return false;
        String studentOid = matcher.group(1).toLowerCase().trim();
        List<String> studentCols = Arrays.asList(studentOid.split("\\s*,\\s*"));
        for (String col : expected.split(",")) {
            if (!studentCols.contains(col.trim().toLowerCase())) return false;
        }
        return true;
    }

    private String extractViewName(String ddl) {
        if (ddl == null || ddl.isBlank()) return null;
        Matcher matcher = VIEW_NAME_PATTERN.matcher(ddl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean validateSubviewRelation(String superviewDdl, String studentSolution) {
        String name = extractViewName(superviewDdl);
        if (name == null) return true;
        Pattern underPattern = Pattern.compile(
            "\\bUNDER\\s+" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE);
        return underPattern.matcher(studentSolution).find();
    }

    private void validateSqlStructure(String sql) throws SQLException {
        if (sql == null) return;

        int open = 0;
        boolean inString = false;
        for (char c : sql.toCharArray()) {
            if (c == '\'') inString = !inString;
            if (inString) continue;
            if (c == '(') open++;
            if (c == ')') open--;
            if (open < 0) throw new SQLException("UNBALANCED_PARENTHESIS");
        }
        if (open != 0) throw new SQLException("UNBALANCED_PARENTHESIS");

        String upper = sql.toUpperCase();
        if (upper.contains("MULTISET(") || upper.contains("MULTISET (")) {
            String multisetContent = extractMultisetContent(upper);
            if (!multisetContent.contains("SELECT")) throw new SQLException("MISSING_SELECT_IN_MULTISET");
            if (!multisetContent.contains("FROM")) throw new SQLException("MISSING_FROM_IN_MULTISET");
        }

        Pattern castMultisetPattern = Pattern.compile(
            "CAST\\s*\\(\\s*MULTISET\\s*\\(", Pattern.CASE_INSENSITIVE);
        Pattern multisetPattern = Pattern.compile(
            "\\bMULTISET\\s*\\(", Pattern.CASE_INSENSITIVE);
        if (multisetPattern.matcher(sql).find() && !castMultisetPattern.matcher(sql).find())
            throw new SQLException("MISSING_CAST_FOR_MULTISET");
    }

    private String extractMultisetContent(String upper) {
        int start = upper.indexOf("MULTISET") + "MULTISET".length();
        while (start < upper.length() && upper.charAt(start) != '(') start++;
        int depth = 0, end = start;
        for (int i = start; i < upper.length(); i++) {
            if (upper.charAt(i) == '(') depth++;
            if (upper.charAt(i) == ')') depth--;
            if (depth == 0) { end = i; break; }
        }
        return upper.substring(start, end + 1);
    }

    private String sanitizeSql(String sql) {
        if (sql == null) return null;
        String s = sql.trim();
        while (s.endsWith(";") || s.endsWith("/")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private void setup(String schema, OrViewTaskGroup group, boolean submit) throws SQLException {
        try (var s = new OrViewSchemaServiceImpl(dataSource)) {
            s.initForSchema(schema);
            s.createSchema(schema);
            s.executeStatements(schema, group.getIntensionalSchema());
            s.executeStatements(schema, group.getExtensionalSchema());
            if (submit) s.executeStatements(schema, group.getSubmitInserts());
            else s.executeStatements(schema, group.getDiagnoseInserts());
            s.commit();
        }
    }

    private void execute(String schema, String sql) throws SQLException {
        try (var s = new OrViewSchemaServiceImpl(dataSource)) {
            s.initForSchema(schema);
            s.executeStatements(schema, sanitizeSql(sql));
            s.commit();
        }
    }

    private QueryResult query(String schema, String q) throws SQLException {
        String cleanQuery = sanitizeSql(q);
        try (var conn = dataSource.connectExecutor(); var stmt = conn.createStatement()) {
            stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
            var rs = stmt.executeQuery(cleanQuery);
            var meta = rs.getMetaData();
            List<String> cols = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) cols.add(meta.getColumnName(i));
            List<List<String>> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++)
                    row.add(OracleTypeConverter.convertOracleObject(rs.getObject(i)));
                rows.add(row);
            }
            return new QueryResult(cols, rows);
        }
    }

    private boolean compare(QueryResult a, QueryResult b) {
        if (a == null || b == null) return false;
        return a.columns().equals(b.columns())
            && new HashSet<>(a.rows()).equals(new HashSet<>(b.rows()));
    }

    private void recompileTypeBody(String schema) {
        try (var conn = dataSource.connectAdmin(); var stmt = conn.createStatement()) {
            stmt.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schema.toUpperCase());
            var rs = stmt.executeQuery(
                "SELECT object_name FROM all_objects WHERE owner = '" + schema.toUpperCase() +
                    "' AND object_type = 'TYPE BODY' AND status = 'INVALID'");
            while (rs.next()) {
                String typeName = rs.getString(1);
                try (var stmt2 = conn.createStatement()) {
                    stmt2.execute("ALTER TYPE " + schema.toUpperCase() + "." + typeName + " COMPILE BODY");
                } catch (SQLException e) {
                    LOG.warn("Could not recompile {}.{}: {}", schema, typeName, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.warn("Recompile failed for schema {}: {}", schema, e.getMessage());
        }
    }

    private void cleanup(String schema) {
        try (var s = new OrViewSchemaServiceImpl(dataSource)) {
            s.initForSchema(schema);
            s.dropSchema(schema);
        } catch (Exception ignored) {}
    }

    // Repairs student SQL by replacing structural parts with teacher's, keeping student's WHERE/ON
    private String repairStudentSql(String studentSql, String teacherSql) {
        try {
            String repaired = teacherSql;

            String studentOuterWhere = extractOuterWhereClause(studentSql);
            String teacherOuterWhere = extractOuterWhereClause(teacherSql);

            if (studentOuterWhere != null && teacherOuterWhere != null) {
                repaired = repaired.replace(teacherOuterWhere, studentOuterWhere);
            } else if (studentOuterWhere == null && teacherOuterWhere != null) {
                repaired = repaired.replace("WHERE" + teacherOuterWhere, "");
            } else if (studentOuterWhere != null) {
                int fromEnd = findOuterFromEnd(repaired);
                if (fromEnd > 0) {
                    repaired = repaired.substring(0, fromEnd) + " WHERE" + studentOuterWhere + repaired.substring(fromEnd);
                }
            }

            String studentMultisetWhere = extractMultisetWhereClause(studentSql);
            String teacherMultisetWhere = extractMultisetWhereClause(teacherSql);

            if (studentMultisetWhere != null && teacherMultisetWhere != null) {
                repaired = repaired.replace(teacherMultisetWhere, studentMultisetWhere);
            } else if (studentMultisetWhere == null && teacherMultisetWhere != null) {
                repaired = repaired.replace("WHERE" + teacherMultisetWhere, "");
            }

            List<String> studentOns = extractOnClauses(studentSql);
            List<String> teacherOns = extractOnClauses(teacherSql);

            if (!studentOns.isEmpty() && !teacherOns.isEmpty()) {
                for (int i = 0; i < Math.min(studentOns.size(), teacherOns.size()); i++) {
                    repaired = repaired.replace(teacherOns.get(i), studentOns.get(i));
                }
            }

            if (repaired.equals(studentSql)) return null;

            LOG.debug("Repaired SQL: {}", repaired);
            return repaired;
        } catch (Exception e) {
            LOG.debug("Repair failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractOuterWhereClause(String sql) {
        String withoutMultiset = removeMultisetContent(sql);
        Pattern p = Pattern.compile("\\bWHERE\\b(.+?)(?:;|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(withoutMultiset);
        if (m.find()) {
            String whereContent = m.group(1).trim();
            if (sql.contains(whereContent)) {
                return m.group(1);
            }
        }
        return null;
    }

    private String extractMultisetWhereClause(String sql) {
        Pattern multisetPattern = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+.+?\\bWHERE\\b(.+?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher m = multisetPattern.matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    private List<String> extractOnClauses(String sql) {
        List<String> ons = new ArrayList<>();
        Pattern p = Pattern.compile("\\bON\\b(.+?)(?:WHERE|JOIN|\\)|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(sql);
        while (m.find()) {
            ons.add(m.group(1).trim());
        }
        return ons;
    }

    private String removeMultisetContent(String sql) {
        Pattern p = Pattern.compile("MULTISET\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return sql;

        StringBuilder result = new StringBuilder(sql);
        m.reset();
        while (m.find()) {
            int start = m.end() - 1;
            int depth = 1;
            int end = start + 1;
            while (end < result.length() && depth > 0) {
                char c = result.charAt(end);
                if (c == '(') depth++;
                if (c == ')') depth--;
                end++;
            }
            String replacement = " ".repeat(end - start - 2);
            result.replace(start + 1, end - 1, replacement);
        }
        return result.toString();
    }

    private int findOuterFromEnd(String sql) {
        String withoutMultiset = removeMultisetContent(sql);
        Pattern p = Pattern.compile("\\bFROM\\b.+?(?:;|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(withoutMultiset);
        return m.find() ? m.end() : -1;
    }
}
