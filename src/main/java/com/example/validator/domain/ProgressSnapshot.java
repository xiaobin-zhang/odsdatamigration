package com.example.validator.domain;

import com.example.validator.common.BatchStatus;

/**
 * 核验进度快照。
 *
 * <p>职责：对外展示当前作业状态、任务数量统计、完成百分比以及正在比对的库表信息。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public class ProgressSnapshot {
    private String batchId;
    private BatchStatus status;
    private int totalCount;
    private int pendingCount;
    private int runningCount;
    private int passCount;
    private int failCount;
    private int errorCount;
    private int skippedCount;
    private double progressPercent;
    private String currentPairName;
    private String currentSourceTable;
    private String currentTargetTable;
    private String currentCheckType;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    public int getRunningCount() { return runningCount; }
    public void setRunningCount(int runningCount) { this.runningCount = runningCount; }
    public int getPassCount() { return passCount; }
    public void setPassCount(int passCount) { this.passCount = passCount; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    public double getProgressPercent() { return progressPercent; }
    public void setProgressPercent(double progressPercent) { this.progressPercent = progressPercent; }
    public String getCurrentPairName() { return currentPairName; }
    public void setCurrentPairName(String currentPairName) { this.currentPairName = currentPairName; }
    public String getCurrentSourceTable() { return currentSourceTable; }
    public void setCurrentSourceTable(String currentSourceTable) { this.currentSourceTable = currentSourceTable; }
    public String getCurrentTargetTable() { return currentTargetTable; }
    public void setCurrentTargetTable(String currentTargetTable) { this.currentTargetTable = currentTargetTable; }
    public String getCurrentCheckType() { return currentCheckType; }
    public void setCurrentCheckType(String currentCheckType) { this.currentCheckType = currentCheckType; }
}
