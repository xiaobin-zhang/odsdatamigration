package com.example.validator;

import com.example.validator.csv.SqlSafetyValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 安全校验器测试。
 *
 * <p>职责：验证标识符、条件片段和最终 SQL 的安全拦截规则。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
class SqlSafetyValidatorTest {
    private final SqlSafetyValidator validator = new SqlSafetyValidator();

    /**
     * 验证非 SELECT SQL 会被拒绝。
     */
    @Test
    void rejectsNonSelectSql() {
        assertThrows(IllegalArgumentException.class, () -> validator.assertSelectOnly("delete from t_order"));
    }

    /**
     * 验证非法表名或字段名会被拒绝。
     */
    @Test
    void rejectsIllegalIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> validator.assertIdentifier("t_order;drop", "table"));
    }

    /**
     * 验证安全 where 条件可以通过。
     */
    @Test
    void acceptsSafeCondition() {
        assertDoesNotThrow(() -> validator.assertSafeCondition("order_id between 1 and 10", "where_clause"));
    }
}
