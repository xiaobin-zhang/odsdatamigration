package com.example.validator.checker;

import com.example.validator.domain.QueryResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ResultComparator {
    private ResultComparator() {
    }

    static boolean rowsEqual(QueryResult sourceResult, QueryResult targetResult, List<String> columns) {
        return normalize(sourceResult, columns).equals(normalize(targetResult, columns));
    }

    private static List<List<Object>> normalize(QueryResult result, List<String> columns) {
        List<List<Object>> rows = new ArrayList<List<Object>>();
        for (Map<String, Object> row : result.getRows()) {
            List<Object> values = new ArrayList<Object>();
            for (String column : columns) {
                values.add(normalizeValue(value(row, column)));
            }
            rows.add(values);
        }
        return rows;
    }

    private static Object value(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value != null || row.containsKey(column)) {
            return value;
        }
        value = row.get(column.toUpperCase(Locale.ROOT));
        if (value != null || row.containsKey(column.toUpperCase(Locale.ROOT))) {
            return value;
        }
        return row.get(column.toLowerCase(Locale.ROOT));
    }

    private static Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }
}
