package com.example.validator.common;

/**
 * 核验插件类型枚举。
 *
 * <p>职责：定义 CSV 中 checkers 字段允许声明的校验能力名称。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
public enum CheckType {
    ROW_COUNT,
    AMOUNT_SUM,
    DATE_GROUP,
    NULL_COUNT,
    ORDER_SAMPLE,
    MD5_SAMPLE
}
