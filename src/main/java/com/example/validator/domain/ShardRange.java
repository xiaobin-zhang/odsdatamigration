package com.example.validator.domain;

/**
 * 表级分片范围。
 *
 * <p>职责：表示大表按某个分片字段拆分后的起止范围，用于生成更小粒度的核验任务。</p>
 *
 * @author Codex
 * @since 2026-06-03
 */
public class ShardRange {
    private final String from;
    private final String to;

    /**
     * 创建分片范围。
     *
     * @param from 分片起始值
     * @param to 分片结束值
     */
    public ShardRange(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
}
