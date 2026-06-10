package com.example.validator.common;

/**
 * 核验批次状态枚举。
 *
 * <p>职责：描述整个核验作业的生命周期，用于进度查看和暂停/恢复控制。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public enum BatchStatus {
    CREATED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}
