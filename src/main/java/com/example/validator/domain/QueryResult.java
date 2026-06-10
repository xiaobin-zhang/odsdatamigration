package com.example.validator.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 查询结果。
 *
 * <p>职责：保存某个数据源执行核验 SQL 后返回的行数据和耗时。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public class QueryResult {
    private List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    private long costMs;

    /**
     * 创建空查询结果。
     */
    public QueryResult() {
    }

    /**
     * 创建查询结果。
     *
     * @param rows 查询返回的行集合
     * @param costMs 查询耗时，单位毫秒
     */
    public QueryResult(List<Map<String, Object>> rows, long costMs) {
        this.rows = rows;
        this.costMs = costMs;
    }

    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    public long getCostMs() { return costMs; }
    public void setCostMs(long costMs) { this.costMs = costMs; }
}
