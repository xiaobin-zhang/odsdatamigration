package com.example.validator.domain;

/**
 * 表级分片范围。
 *
 * <p>职责：表示大表按某个分片字段拆分后的起止范围，用于生成更小粒度的核验任务。</p>
 *
 * @author zxb
 * @since 2026-06-03
 */
public class ShardRange {
    public enum Strategy {
        RANGE,
        MOD,
        INTERVAL,
        OFFSET
    }

    private final Strategy strategy;
    private final String from;
    private final String to;
    private final int modulus;
    private final int remainder;
    private final int offset;
    private final int limit;
    private final int shardCount;

    /**
     * 创建分片范围。
     *
     * @param from 分片起始值
     * @param to 分片结束值
     */
    public ShardRange(String from, String to) {
        this(Strategy.RANGE, from, to, 0, 0, 0, 0, 0);
    }

    private ShardRange(Strategy strategy, String from, String to, int modulus, int remainder,
                       int offset, int limit, int shardCount) {
        this.strategy = strategy;
        this.from = from;
        this.to = to;
        this.modulus = modulus;
        this.remainder = remainder;
        this.offset = offset;
        this.limit = limit;
        this.shardCount = shardCount;
    }

    public static ShardRange interval(String from, String to) {
        return new ShardRange(Strategy.INTERVAL, from, to, 0, 0, 0, 0, 0);
    }

    public static ShardRange mod(int modulus, int remainder) {
        return new ShardRange(Strategy.MOD, null, null, modulus, remainder, 0, 0, 0);
    }

    public static ShardRange offsetPlan(int shardCount) {
        return new ShardRange(Strategy.OFFSET, null, null, 0, 0, 0, 0, shardCount);
    }

    public static ShardRange offset(int offset, int limit) {
        return new ShardRange(Strategy.OFFSET, null, null, 0, 0, offset, limit, 0);
    }

    public Strategy getStrategy() { return strategy; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public int getModulus() { return modulus; }
    public int getRemainder() { return remainder; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public int getShardCount() { return shardCount; }
}
