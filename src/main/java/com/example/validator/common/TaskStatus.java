package com.example.validator.common;

/**
 * 核验任务执行状态枚举。
 *
 * <p>职责：统一描述任务从生成、执行到完成/失败的状态，支持断点续跑判断。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    PASS,
    FAIL,
    ERROR,
    SKIPPED
}
