package com.example.validator.checker;

import com.example.validator.domain.QueryResult;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultComparatorTest {
    @Test
    void preservesStringWhitespaceDuringComparison() {
        QueryResult source = result(row("remark", "abc "));
        QueryResult target = result(row("REMARK", "abc"));

        assertFalse(ResultComparator.rowsEqual(source, target, Collections.singletonList("remark")));
    }

    @Test
    void normalizesNumericValuesAndColumnCase() {
        QueryResult source = result(row("cnt", 1));
        QueryResult target = result(row("CNT", new BigDecimal("1.00")));

        assertTrue(ResultComparator.rowsEqual(source, target, Collections.singletonList("cnt")));
    }

    private QueryResult result(Map<String, Object> row) {
        return new QueryResult(Collections.singletonList(row), 0L);
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put(key, value);
        return row;
    }
}
