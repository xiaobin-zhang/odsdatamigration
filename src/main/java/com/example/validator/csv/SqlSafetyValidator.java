package com.example.validator.csv;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * SQL 安全校验器。
 *
 * <p>职责：校验 CSV 中的表名、字段名、条件片段和最终 SQL，避免执行非 SELECT 或危险 SQL。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
@Component
public class SqlSafetyValidator {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z0-9_.$]+");
    private static final Set<String> DANGEROUS_WORDS = new HashSet<String>(Arrays.asList(
            "delete", "update", "insert", "drop", "alter", "truncate", "create", "merge", "replace", "grant", "revoke"
    ));

    /**
     * 校验单个表名或字段名是否为安全标识符。
     *
     * @param value 待校验的标识符
     * @param fieldName 配置字段名称，用于错误提示
     */
    public void assertIdentifier(String value, String fieldName) {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " 非法，只允许字母、数字、下划线、点号和美元符号: " + value);
        }
    }

    /**
     * 批量校验表名或字段名集合。
     *
     * @param values 待校验的标识符集合
     * @param fieldName 配置字段名称，用于错误提示
     */
    public void assertIdentifierList(Iterable<String> values, String fieldName) {
        for (String value : values) {
            assertIdentifier(value, fieldName);
        }
    }

    /**
     * 校验 where 条件片段是否安全。
     *
     * @param condition CSV 中配置的过滤条件
     * @param fieldName 配置字段名称，用于错误提示
     */
    public void assertSafeCondition(String condition, String fieldName) {
        if (!StringUtils.hasText(condition)) {
            return;
        }
        String normalized = condition.toLowerCase(Locale.ROOT);
        // 条件片段来自 CSV，无法使用 JDBC 参数占位符表达表级过滤，所以这里做保守拦截：
        // 禁止分号和写操作关键字，确保框架只会拼接 SELECT 类核验语句。
        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*")) {
            throw new IllegalArgumentException(fieldName + " 包含危险 SQL 符号: " + condition);
        }
        for (String word : DANGEROUS_WORDS) {
            if (Pattern.compile("\\b" + word + "\\b").matcher(normalized).find()) {
                throw new IllegalArgumentException(fieldName + " 包含危险 SQL 关键字: " + word);
            }
        }
    }

    /**
     * 校验最终执行 SQL 只能是单条 SELECT。
     *
     * @param sql 待执行 SQL
     */
    public void assertSelectOnly(String sql) {
        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select ") || normalized.contains(";")) {
            throw new IllegalArgumentException("仅允许执行单条 SELECT SQL: " + sql);
        }
    }
}
