package com.java2nb.novel.core.i18n;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViLocalizationMigrationTest {

    private static final Pattern UPDATE_PREFIX = Pattern.compile(
        "(?is)^UPDATE\\s+`?([a-zA-Z0-9_]+)`?\\s+SET\\s+");
    private static final Pattern ASSIGNMENT_COLUMN = Pattern.compile(
        "(?is)^\\s*`?([a-zA-Z0-9_]+)`?\\s*=");
    private static final Pattern IN_PREDICATE = Pattern.compile(
        "(?is)^\\s*`?([a-zA-Z0-9_]+)`?\\s+IN\\s*\\((.*)\\)\\s*$");
    private static final Pattern EQUALS_PREDICATE = Pattern.compile(
        "(?is)^\\s*`?([a-zA-Z0-9_]+)`?\\s*=\\s*(.+?)\\s*$");

    @Test
    void migrationUpdatesDefaultsPreservesCustomValuesAndIsIdempotent() throws Exception {
        String sql = Files.readString(findMigration(), StandardCharsets.UTF_8)
            .replaceAll("(?m)^\\s*--.*(?:\\R|$)", "");
        List<UpdateSpec> updates = splitOutsideQuotes(sql, ';').stream()
            .map(String::trim)
            .filter(statement -> !statement.isEmpty())
            .map(this::parseUpdate)
            .toList();

        assertEquals(267, updates.size(), "Phải kiểm tra toàn bộ câu UPDATE trong migration Việt hóa");
        assertEquals(Set.of(
            "book_category", "news", "news_category", "sys_data_perm", "sys_dept", "sys_dict",
            "sys_menu", "sys_role", "sys_user", "website_info"
        ), updates.stream().map(UpdateSpec::table).collect(java.util.stream.Collectors.toSet()));

        try (Connection connection = DriverManager.getConnection(
            "jdbc:h2:mem:vi_localization;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE")) {
            createSchema(connection, updates);
            for (int index = 0; index < updates.size(); index++) {
                verifyUpdate(connection, updates.get(index), index);
            }
        }
    }

    private void createSchema(Connection connection, List<UpdateSpec> updates) throws Exception {
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();
        for (UpdateSpec update : updates) {
            Set<String> columns = columnsByTable.computeIfAbsent(update.table(), ignored -> new LinkedHashSet<>());
            columns.addAll(update.setColumns());
            update.predicates().forEach(predicate -> columns.add(predicate.column()));
        }

        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, Set<String>> entry : columnsByTable.entrySet()) {
                StringBuilder ddl = new StringBuilder("CREATE TABLE `")
                    .append(entry.getKey()).append("` (`test_marker` VARCHAR(64)");
                for (String column : entry.getValue()) {
                    ddl.append(", `").append(column).append("` ")
                        .append(isNumericIdentifier(column) ? "BIGINT" : "VARCHAR(4000)");
                }
                ddl.append(')');
                statement.execute(ddl.toString());
            }
        }
    }

    private void verifyUpdate(Connection connection, UpdateSpec update, int statementIndex) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM `" + update.table() + "`");
        }

        List<Map<String, Object>> defaultRows = expandPredicateRows(update.predicates());
        assertFalse(defaultRows.isEmpty(), "UPDATE phải có ít nhất một bộ điều kiện: " + update.sql());
        for (int rowIndex = 0; rowIndex < defaultRows.size(); rowIndex++) {
            insertRow(connection, update.table(), "default-" + rowIndex, defaultRows.get(rowIndex));
        }

        Map<String, Object> customRow = new LinkedHashMap<>(defaultRows.get(0));
        String guardedColumn = update.setColumns().stream()
            .filter(customRow::containsKey)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "Câu UPDATE không khóa giá trị được sửa trong WHERE: " + update.sql()));
        customRow.put(guardedColumn, customValue(customRow.get(guardedColumn)));
        insertRow(connection, update.table(), "custom", customRow);
        Map<String, Object> customBefore = readMarkedRow(connection, update.table(), "custom");

        int firstRun;
        int secondRun;
        try (Statement statement = connection.createStatement()) {
            firstRun = statement.executeUpdate(update.sql());
            secondRun = statement.executeUpdate(update.sql());
        }

        assertEquals(defaultRows.size(), firstRun,
            () -> "Số dòng mặc định được cập nhật sai tại câu " + (statementIndex + 1) + ": " + update.sql());
        assertEquals(0, secondRun,
            () -> "Migration không idempotent tại câu " + (statementIndex + 1) + ": " + update.sql());
        assertEquals(customBefore, readMarkedRow(connection, update.table(), "custom"),
            () -> "Dữ liệu tùy chỉnh bị ghi đè tại câu " + (statementIndex + 1) + ": " + update.sql());
    }

    private UpdateSpec parseUpdate(String sql) {
        Matcher prefix = UPDATE_PREFIX.matcher(sql);
        assertTrue(prefix.find(), () -> "Migration chứa câu lệnh ngoài UPDATE: " + sql);
        int whereIndex = indexOfKeywordOutsideQuotes(sql, "WHERE", prefix.end());
        assertTrue(whereIndex >= 0, () -> "UPDATE thiếu WHERE: " + sql);

        String setClause = sql.substring(prefix.end(), whereIndex).trim();
        String whereClause = sql.substring(whereIndex + "WHERE".length()).trim();
        List<String> setColumns = splitOutsideQuotes(setClause, ',').stream()
            .map(ASSIGNMENT_COLUMN::matcher)
            .map(matcher -> {
                assertTrue(matcher.find(), () -> "Không đọc được cột SET: " + setClause);
                return matcher.group(1).toLowerCase(Locale.ROOT);
            })
            .toList();
        List<Predicate> predicates = splitKeywordOutsideQuotes(whereClause, "AND").stream()
            .map(this::parsePredicate)
            .toList();

        return new UpdateSpec(prefix.group(1).toLowerCase(Locale.ROOT), sql, setColumns, predicates);
    }

    private Predicate parsePredicate(String expression) {
        Matcher in = IN_PREDICATE.matcher(expression);
        if (in.matches()) {
            List<Object> values = splitOutsideQuotes(in.group(2), ',').stream()
                .map(this::parseLiteral)
                .toList();
            return new Predicate(in.group(1).toLowerCase(Locale.ROOT), values);
        }

        Matcher equals = EQUALS_PREDICATE.matcher(expression);
        assertTrue(equals.matches(), () -> "Điều kiện migration không được hỗ trợ: " + expression);
        return new Predicate(equals.group(1).toLowerCase(Locale.ROOT), List.of(parseLiteral(equals.group(2))));
    }

    private List<Map<String, Object>> expandPredicateRows(List<Predicate> predicates) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>());
        for (Predicate predicate : predicates) {
            List<Map<String, Object>> expanded = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                for (Object value : predicate.values()) {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    copy.put(predicate.column(), value);
                    expanded.add(copy);
                }
            }
            rows = expanded;
        }
        return rows;
    }

    private void insertRow(Connection connection, String table, String marker,
                           Map<String, Object> values) throws Exception {
        List<String> columns = new ArrayList<>(values.keySet());
        String columnSql = columns.stream().map(column -> "`" + column + "`")
            .collect(java.util.stream.Collectors.joining(", "));
        String placeholders = java.util.stream.IntStream.range(0, columns.size() + 1)
            .mapToObj(ignored -> "?")
            .collect(java.util.stream.Collectors.joining(", "));
        String sql = "INSERT INTO `" + table + "` (`test_marker`"
            + (columnSql.isEmpty() ? "" : ", " + columnSql) + ") VALUES (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, marker);
            for (int index = 0; index < columns.size(); index++) {
                statement.setObject(index + 2, values.get(columns.get(index)));
            }
            statement.executeUpdate();
        }
    }

    private Map<String, Object> readMarkedRow(Connection connection, String table, String marker) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM `" + table + "` WHERE `test_marker` = ?")) {
            statement.setString(1, marker);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "Thiếu dòng kiểm tra dữ liệu tùy chỉnh");
                ResultSetMetaData metadata = resultSet.getMetaData();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
                }
                return row;
            }
        }
    }

    private Object parseLiteral(String raw) {
        String value = raw.trim();
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if (value.matches("-?\\d+")) {
            return Long.parseLong(value);
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            return new BigDecimal(value);
        }
        throw new AssertionError("Literal migration không được hỗ trợ: " + raw);
    }

    private Object customValue(Object original) {
        if (original instanceof Number number) {
            return number.longValue() + 1_000_000L;
        }
        return String.valueOf(original) + "__CUSTOM__";
    }

    private boolean isNumericIdentifier(String column) {
        return column.equals("id") || column.endsWith("_id");
    }

    private int indexOfKeywordOutsideQuotes(String text, String keyword, int startIndex) {
        boolean quoted = false;
        int depth = 0;
        for (int index = startIndex; index <= text.length() - keyword.length(); index++) {
            char current = text.charAt(index);
            if (current == '\'' && (index + 1 >= text.length() || text.charAt(index + 1) != '\'')) {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                if (current == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    index++;
                }
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0 && regionMatchesKeyword(text, index, keyword)) {
                return index;
            }
        }
        return -1;
    }

    private List<String> splitKeywordOutsideQuotes(String text, String keyword) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        boolean quoted = false;
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\'') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (quoted) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0 && regionMatchesKeyword(text, index, keyword)) {
                parts.add(text.substring(start, index).trim());
                index += keyword.length() - 1;
                start = index + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }

    private boolean regionMatchesKeyword(String text, int index, String keyword) {
        if (!text.regionMatches(true, index, keyword, 0, keyword.length())) {
            return false;
        }
        boolean before = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
        int afterIndex = index + keyword.length();
        boolean after = afterIndex >= text.length() || !Character.isLetterOrDigit(text.charAt(afterIndex));
        return before && after;
    }

    private List<String> splitOutsideQuotes(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\'') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && current == delimiter) {
                parts.add(text.substring(start, index));
                start = index + 1;
            }
        }
        assertFalse(quoted, "Chuỗi SQL chưa đóng dấu nháy");
        parts.add(text.substring(start));
        return parts;
    }

    private Path findMigration() {
        for (Path candidate : List.of(
            Paths.get("doc/sql/20260712_vi_localization.sql"),
            Paths.get("../doc/sql/20260712_vi_localization.sql")
        )) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Không tìm thấy migration Việt hóa");
    }

    private record Predicate(String column, List<Object> values) {
    }

    private record UpdateSpec(String table, String sql, List<String> setColumns,
                              List<Predicate> predicates) {
    }
}
