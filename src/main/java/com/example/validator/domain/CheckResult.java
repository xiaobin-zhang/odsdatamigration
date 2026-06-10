package com.example.validator.domain;

import com.example.validator.common.TaskStatus;

/**
 * Checker 比对结果。
 *
 * <p>职责：承载单个核验任务的最终状态和差异摘要。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public class CheckResult {
    private TaskStatus status;
    private String summary;

    /**
     * 创建比对结果。
     *
     * @param status 核验状态
     * @param summary 核验摘要或差异说明
     */
    public CheckResult(TaskStatus status, String summary) {
        this.status = status;
        this.summary = summary;
    }

    /**
     * 创建通过结果。
     *
     * @param summary 通过摘要
     * @return 通过状态结果
     */
    public static CheckResult pass(String summary) {
        return new CheckResult(TaskStatus.PASS, summary);
    }

    /**
     * 创建失败结果。
     *
     * @param summary 失败差异说明
     * @return 失败状态结果
     */
    public static CheckResult fail(String summary) {
        return new CheckResult(TaskStatus.FAIL, summary);
    }

    /**
     * 创建异常结果。
     *
     * @param summary 异常说明
     * @return 异常状态结果
     */
    public static CheckResult error(String summary) {
        return new CheckResult(TaskStatus.ERROR, summary);
    }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
