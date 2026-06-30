package at.jku.dke.task_app.or_view.evaluation;

import java.util.*;
import java.util.regex.*;

/**
 * Statically analyzes OR-View SQL statements for error category detection.
 * Compares student and reference solutions structurally without database access.
 */
public class SchemaTypeAnalyzer {

    private static final Pattern MAKE_REF_PATTERN = Pattern.compile(
        "\\bMAKE_REF\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts the type name from the OF clause of a view definition.
     */
    public static String extractOfType(String sql) {
        Pattern p = Pattern.compile(
            "CREATE\\s+(?:OR\\s+REPLACE\\s+)?VIEW\\s+\\w+\\s+OF\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extracts the type name from a CAST(MULTISET(...) AS type) expression.
     * Uses parenthesis counting to handle nested brackets correctly.
     */
    public static String extractCastMultisetType(String sql) {
        if (sql == null) return null;

        Pattern p = Pattern.compile("CAST\\s*\\(\\s*MULTISET\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (!m.find()) return null;

        int depth = 1;
        int i = m.end();
        while (i < sql.length() && depth > 0) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            i++;
        }

        String rest = sql.substring(i).trim();
        Matcher am = Pattern.compile("^AS\\s+(\\w+)\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(rest);
        return am.find() ? am.group(1) : null;
    }

    /**
     * Checks whether the SQL contains a CAST(MULTISET(...)) expression.
     */
    public static boolean hasCastMultiset(String sql) {
        return Pattern.compile("CAST\\s*\\(\\s*MULTISET\\s*\\(", Pattern.CASE_INSENSITIVE)
            .matcher(sql).find();
    }

    /**
     * Checks whether MAKE_REF appears in the outer SELECT list (not inside a MULTISET subquery).
     */
    public static boolean hasTopLevelMakeRef(String sql) {
        if (!MAKE_REF_PATTERN.matcher(sql).find()) return false;
        return MAKE_REF_PATTERN.matcher(removeMultisetContent(sql)).find();
    }

    /**
     * Checks whether the MULTISET subquery contains a MAKE_REF expression.
     */
    public static boolean multisetContainsMakeRef(String sql) {
        Pattern p = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher m = p.matcher(sql);
        return m.find() && MAKE_REF_PATTERN.matcher(m.group(1)).find();
    }

    /**
     * Counts the number of columns in the MULTISET SELECT list.
     */
    public static int countMultisetColumns(String sql) {
        Pattern p = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher m = p.matcher(sql);
        return m.find() ? countTopLevelCommas(m.group(1).trim()) + 1 : 0;
    }

    /**
     * Extracts column names from the MULTISET SELECT list.
     * Handles simple columns (s.name), aliases (s.name AS n), and expressions.
     * Returns a list preserving duplicates (not a set).
     */
    public static List<String> extractMultisetColumnNames(String sql) {
        Pattern p = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher m = p.matcher(sql);
        if (!m.find()) return Collections.emptyList();

        String selectList = m.group(1).trim();
        List<String> columns = new ArrayList<>();

        for (String col : splitTopLevel(selectList)) {
            columns.add(extractColumnName(col.trim()));
        }
        return columns;
    }

    /**
     * Counts columns in the outer SELECT list (MULTISET content is excluded).
     */
    public static int countOuterSelectColumns(String sql) {
        Pattern p = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher m = p.matcher(removeMultisetContent(sql));
        return m.find() ? countTopLevelCommas(m.group(1).trim()) + 1 : 0;
    }

    /**
     * Extracts the outer SELECT list as a raw string (MULTISET content excluded).
     */
    public static String extractOuterSelectList(String sql) {
        Pattern p = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher m = p.matcher(removeMultisetContent(sql));
        return m.find() ? m.group(1) : "";
    }

    /**
     * Extracts the base column name from a SELECT expression.
     * "s.description" → "description"
     * "s.name AS n" → "name"
     * "UPPER(s.name)" → "upper(s.name)"
     */
    private static String extractColumnName(String expr) {
        String trimmed = expr.trim();
        String withoutAlias = trimmed.replaceAll("(?i)\\s+AS\\s+\\w+$", "").trim();

        Matcher dotMatcher = Pattern.compile("^\\w+\\.(\\w+)$").matcher(withoutAlias);
        if (dotMatcher.find()) return dotMatcher.group(1).toLowerCase();

        return withoutAlias.toLowerCase();
    }

    /**
     * Splits a SELECT list by top-level commas (respecting parentheses).
     */
    private static List<String> splitTopLevel(String selectList) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < selectList.length(); i++) {
            char c = selectList.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(selectList.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(selectList.substring(start));
        return parts;
    }

    private static int countTopLevelCommas(String selectList) {
        int depth = 0, count = 0;
        for (char c : selectList.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) count++;
        }
        return count;
    }

    /**
     * Removes the content of all MULTISET(...) expressions so only the outer SELECT list is analyzed.
     */
    private static String removeMultisetContent(String sql) {
        StringBuilder result = new StringBuilder(sql);
        Pattern p = Pattern.compile("MULTISET\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);

        int offset = 0;
        while (m.find()) {
            int start = m.end() - 1 + offset;
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

    /**
     * Extracts all MAKE_REF arguments from the SQL statement.
     */
    public static List<String> extractMakeRefArgs(String sql) {
        return extractMakeRefArgsFromText(sql);
    }

    /**
     * Extracts MAKE_REF arguments from within the MULTISET subquery.
     */
    public static List<String> extractMultisetMakeRefArgs(String sql) {
        Pattern multiset = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = multiset.matcher(sql);
        return m.find() ? extractMakeRefArgsFromText(m.group(1)) : Collections.emptyList();
    }

    /**
     * Extracts JOIN clauses from the outer query (excluding MULTISET content).
     */
    public static List<String> extractOuterJoinClauses(String sql) {
        List<String> joins = new ArrayList<>();
        Pattern p = Pattern.compile(
            "\\b((?:LEFT|RIGHT|INNER|OUTER|FULL)?\\s*JOIN\\s+\\w+(?:\\s+\\w+)?\\s+ON\\s+.+?)(?=\\b(?:LEFT|RIGHT|INNER|OUTER|FULL)?\\s*JOIN\\b|WHERE|ORDER\\s+BY|GROUP\\s+BY|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(removeMultisetContent(sql));
        while (m.find()) {
            joins.add(m.group(1).trim().replaceAll(";$", "").trim());
        }
        return joins;
    }

    /**
     * Extracts the WHERE clause from the outer query (excluding MULTISET content).
     */
    public static String extractOuterWhereClause(String sql) {
        String outer = removeMultisetContent(sql);
        Pattern p = Pattern.compile("\\bWHERE\\b(.+?)(?:ORDER\\s+BY|GROUP\\s+BY|HAVING|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(outer);
        return m.find() ? m.group(1).trim().replaceAll(";$", "").trim() : null;
    }

    /**
     * Extracts the WHERE clause from within the MULTISET subquery.
     */
    public static String extractMultisetWhereClause(String sql) {
        Pattern p = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT.+?\\bWHERE\\b(.+?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(sql);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Extracts column names from the outer SELECT list (excluding MULTISET content).
     * Returns simplified names: "t.name" → "name", "CAST(...) AS steps" → "__nested__",
     * "adresse_ty(...)" → "__object__", "MAKE_REF(...)" → "__makeref__".
     */
    public static List<String> extractOuterColumnNames(String sql, List<String> typeNames) {
        String outer = extractOuterSelectList(sql);
        if (outer == null || outer.isBlank()) return Collections.emptyList();

        List<String> names = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : outer.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                addColumnName(names, current.toString(), typeNames);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        addColumnName(names, current.toString(), typeNames);

        return names;
    }

    private static void addColumnName(List<String> names, String expr, List<String> typeNames) {
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) return;

        Matcher aliasMatcher = Pattern.compile("\\bAS\\s+(\\w+)\\s*$", Pattern.CASE_INSENSITIVE).matcher(trimmed);
        if (aliasMatcher.find()) {
            String upper = trimmed.toUpperCase();
            if (upper.contains("CAST") || upper.contains("MULTISET")) {
                names.add("__nested__");
            } else if (isConstructorCall(trimmed, typeNames)) {
                names.add("__object__");
            } else if (MAKE_REF_PATTERN.matcher(trimmed).find()) {
                names.add("__makeref__");
            } else {
                names.add(aliasMatcher.group(1).toLowerCase());
            }
            return;
        }

        String upper = trimmed.toUpperCase();
        if (upper.contains("CAST") || upper.contains("MULTISET")) {
            names.add("__nested__");
            return;
        }
        if (isConstructorCall(trimmed, typeNames)) {
            names.add("__object__");
            return;
        }
        if (MAKE_REF_PATTERN.matcher(trimmed).find()) {
            names.add("__makeref__");
            return;
        }

        Matcher dotMatcher = Pattern.compile("^\\w+\\.(\\w+)$").matcher(trimmed);
        if (dotMatcher.find()) {
            names.add(dotMatcher.group(1).toLowerCase());
            return;
        }
        names.add(trimmed.toLowerCase());
    }

    private static boolean isConstructorCall(String expr, List<String> typeNames) {
        for (String typeName : typeNames) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\s*\\(", Pattern.CASE_INSENSITIVE);
            if (p.matcher(expr).find()) return true;
        }
        return false;
    }

    /**
     * Counts the total number of MAKE_REF expressions in the SQL.
     */
    public static int countMakeRefs(String sql) {
        if (sql == null) return 0;
        Matcher m = MAKE_REF_PATTERN.matcher(sql);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private static List<String> extractMakeRefArgsFromText(String text) {
        List<String> args = new ArrayList<>();
        Pattern p = Pattern.compile("MAKE_REF\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        while (m.find()) {
            args.add(m.group(1).trim().toLowerCase());
        }
        return args;
    }

    /**
     * Extracts constructor arguments from the outer SELECT list.
     */
    public static List<String> extractConstructorArgs(String sql, List<String> typeNames) {
        String outerSelect = extractOuterSelectList(sql);
        if (outerSelect.isEmpty()) return List.of();

        for (String typeName : typeNames) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(outerSelect);
            if (m.find()) {
                String argsStr = m.group(1);
                String[] args = argsStr.split("\\s*,\\s*");
                List<String> result = new ArrayList<>();
                for (String arg : args) {
                    String cleaned = arg.trim();
                    int dot = cleaned.lastIndexOf('.');
                    if (dot >= 0) cleaned = cleaned.substring(dot + 1);
                    result.add(cleaned.toLowerCase());
                }
                return result;
            }
        }
        return List.of();
    }

    /**
     * Extracts MAKE_REF arguments from the outer SELECT list only.
     */
    public static List<String> extractOuterMakeRefArgs(String sql) {
        String outer = extractOuterSelectList(sql);
        return outer.isEmpty() ? List.of() : extractMakeRefArgsFromText(outer);
    }

    /**
     * Extracts table aliases from the outer FROM clause.
     */
    public static List<String> extractFromAliases(String sql) {
        List<String> aliases = new ArrayList<>();
        Pattern p = Pattern.compile(
            "\\bFROM\\b\\s+(\\w+)(?:\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (m.find() && m.group(2) != null) {
            aliases.add(m.group(2).toLowerCase());
        }
        Pattern joinP = Pattern.compile(
            "\\bJOIN\\b\\s+(\\w+)(?:\\s+(\\w+))?\\s+ON",
            Pattern.CASE_INSENSITIVE);
        Matcher joinM = joinP.matcher(sql);
        while (joinM.find()) {
            if (joinM.group(2) != null) {
                aliases.add(joinM.group(2).toLowerCase());
            }
        }
        return aliases;
    }

    /**
     * Extracts table aliases from the MULTISET subquery's FROM clause.
     */
    public static List<String> extractMultisetFromAliases(String sql) {
        List<String> aliases = new ArrayList<>();
        Pattern multiset = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+.+?\\bFROM\\b\\s+(\\w+)(?:\\s+(\\w+))?",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = multiset.matcher(sql);
        if (m.find() && m.group(2) != null) {
            aliases.add(m.group(2).toLowerCase());
        }
        return aliases;
    }

    /**
     * Finds column references with undefined aliases in the outer SELECT list.
     */
    public static List<String> countUndefinedAliasRefs(String sql, List<String> fromAliases) {
        return findUndefinedRefs(extractOuterSelectList(sql), fromAliases);
    }

    private static List<String> findUndefinedRefs(String selectList, List<String> validAliases) {
        List<String> undefined = new ArrayList<>();
        if (selectList == null) return undefined;
        Pattern p = Pattern.compile("(\\w+)\\.(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(selectList);
        while (m.find()) {
            String prefix = m.group(1).toLowerCase();
            if (!validAliases.contains(prefix)) {
                undefined.add(prefix + "." + m.group(2).toLowerCase());
            }
        }
        return undefined;
    }

    /**
     * Finds column references with undefined aliases in the MULTISET SELECT list.
     */
    public static List<String> countUndefinedMultisetAliasRefs(String sql, List<String> allAliases) {
        Pattern p = Pattern.compile(
            "MULTISET\\s*\\(\\s*SELECT\\s+(.+?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(sql);
        if (!m.find()) return new ArrayList<>();
        return findUndefinedRefs(m.group(1), allAliases);
    }

    /**
     * Counts the number of MAKE_REF expressions inside the MULTISET subquery.
     */
    public static int countMultisetMakeRefs(String sql) {
        Pattern p = Pattern.compile(
            "MULTISET\\s*\\(.*?MAKE_REF\\s*\\(",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(sql);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /**
     * Extracts object type names from the intensional schema (CREATE TYPE ... AS OBJECT).
     */
    public static List<String> extractObjectTypeNames(String intensionalSchema) {
        List<String> typeNames = new ArrayList<>();
        Pattern p = Pattern.compile(
            "CREATE\\s+(?:OR\\s+REPLACE\\s+)?TYPE\\s+(\\w+)\\s+AS\\s+OBJECT",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(intensionalSchema);
        while (m.find()) {
            typeNames.add(m.group(1).toLowerCase());
        }
        return typeNames;
    }

    /**
     * Checks whether the SQL contains an object constructor call for any of the given type names.
     */
    public static boolean hasConstructorField(String sql, List<String> typeNames) {
        for (String typeName : typeNames) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(typeName) + "\\s*\\(", Pattern.CASE_INSENSITIVE);
            if (p.matcher(sql).find()) return true;
        }
        return false;
    }
}
