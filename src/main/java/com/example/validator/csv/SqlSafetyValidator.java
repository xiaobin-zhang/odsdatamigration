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
 * <p>校验 CSV 中的表名、字段名、条件片段和最终 SQL，避免执行非 SELECT 或危险 SQL。</p>
 */
@Component
public class SqlSafetyValidator {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z0-9_.$]+");
    private static final Set<String> DANGEROUS_WORDS = new HashSet<String>(Arrays.asList(
            "delete", "update", "insert", "drop", "alter", "truncate", "create", "merge", "replace", "grant", "revoke",
            "call", "load", "lock", "unlock"
    ));

    public void assertIdentifier(String value, String fieldName) {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " 非法，只允许字母、数字、下划线、点号和美元符号: " + value);
        }
    }

    public void assertIdentifierList(Iterable<String> values, String fieldName) {
        for (String value : values) {
            assertIdentifier(value, fieldName);
        }
    }

    public void assertSafeCondition(String condition, String fieldName) {
        if (!StringUtils.hasText(condition)) {
            return;
        }
        String normalized = condition.toLowerCase(Locale.ROOT);
        if (containsDangerousToken(normalized)) {
            throw new IllegalArgumentException(fieldName + " 包含危险 SQL 符号: " + condition);
        }
        String dangerousWord = dangerousWord(normalized);
        if (dangerousWord != null) {
            throw new IllegalArgumentException(fieldName + " 包含危险 SQL 关键字: " + dangerousWord);
        }
    }

    public void assertSelectOnly(String sql) {
        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("select ") || containsDangerousToken(normalized)
                || dangerousWord(normalized) != null
                || Pattern.compile("\\bfor\\s+update\\b").matcher(normalized).find()
                || Pattern.compile("\\binto\\s+(out|dump)file\\b").matcher(normalized).find()) {
            throw new IllegalArgumentException("仅允许执行单条 SELECT SQL: " + sql);
        }
    }

    private boolean containsDangerousToken(String normalized) {
        return normalized.contains(";")
                || normalized.contains("--")
                || normalized.contains("/*")
                || normalized.contains("*/")
                || normalized.contains("#");
    }

    private String dangerousWord(String normalized) {
        for (String word : DANGEROUS_WORDS) {
            if (Pattern.compile("\\b" + word + "\\b").matcher(normalized).find()) {
                return word;
            }
        }
        return null;
    }
}
