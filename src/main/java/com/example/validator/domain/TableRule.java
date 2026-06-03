package com.example.validator.domain;

import com.example.validator.common.CheckType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 中一行表级核验规则。
 *
 * <p>职责：描述某张表在哪个数据库配对下启用、使用哪些 Checker、核验哪些字段以及抽样/分片规则。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
public class TableRule {
    private String pairName;
    private boolean enabled;
    private String sourceTable;
    private String targetTable;
    private String primaryKey;
    private List<CheckType> checkers = new ArrayList<CheckType>();
    private String whereClause;
    private List<String> amountFields = new ArrayList<String>();
    private String dateField;
    private List<String> nullFields = new ArrayList<String>();
    private List<String> orderFields = new ArrayList<String>();
    private List<String> compareFields = new ArrayList<String>();
    private String sampleWhere;
    private int sampleLimit = 1000;
    private String shardColumn;
    private List<ShardRange> shardRanges = new ArrayList<ShardRange>();
    private BigDecimal amountTolerance = new BigDecimal("0.00");

    public String getPairName() { return pairName; }
    public void setPairName(String pairName) { this.pairName = pairName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(String primaryKey) { this.primaryKey = primaryKey; }
    public List<CheckType> getCheckers() { return checkers; }
    public void setCheckers(List<CheckType> checkers) { this.checkers = checkers; }
    public String getWhereClause() { return whereClause; }
    public void setWhereClause(String whereClause) { this.whereClause = whereClause; }
    public List<String> getAmountFields() { return amountFields; }
    public void setAmountFields(List<String> amountFields) { this.amountFields = amountFields; }
    public String getDateField() { return dateField; }
    public void setDateField(String dateField) { this.dateField = dateField; }
    public List<String> getNullFields() { return nullFields; }
    public void setNullFields(List<String> nullFields) { this.nullFields = nullFields; }
    public List<String> getOrderFields() { return orderFields; }
    public void setOrderFields(List<String> orderFields) { this.orderFields = orderFields; }
    public List<String> getCompareFields() { return compareFields; }
    public void setCompareFields(List<String> compareFields) { this.compareFields = compareFields; }
    public String getSampleWhere() { return sampleWhere; }
    public void setSampleWhere(String sampleWhere) { this.sampleWhere = sampleWhere; }
    public int getSampleLimit() { return sampleLimit; }
    public void setSampleLimit(int sampleLimit) { this.sampleLimit = sampleLimit; }
    public String getShardColumn() { return shardColumn; }
    public void setShardColumn(String shardColumn) { this.shardColumn = shardColumn; }
    public List<ShardRange> getShardRanges() { return shardRanges; }
    public void setShardRanges(List<ShardRange> shardRanges) { this.shardRanges = shardRanges; }
    public BigDecimal getAmountTolerance() { return amountTolerance; }
    public void setAmountTolerance(BigDecimal amountTolerance) { this.amountTolerance = amountTolerance; }
}
