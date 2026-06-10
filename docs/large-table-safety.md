# 大表 SQL 安全保护机制

## 目标

当迁移校验涉及 100G 以上大表时，程序默认采用保守策略：无法证明 SQL 安全时不执行，避免全表扫描、大排序、高并发聚合或一次性拉取过多结果导致数据库内存、临时空间或应用内存被打爆。

## 配置

```yaml
validator:
  safety:
    enabled: true
    largeTableThresholdGb: 100
    maxConcurrentHeavyQueries: 1
    maxResultRows: 10000
    requireShardForLargeTable: true
    forbidOffsetShardForLargeTable: true
    forbidUnindexedOrderByForLargeTable: true
    explainBeforeExecute: true
    explainMaxRows: 50000000
    explainMaxFullScanTables: 0
    onViolation: SKIP
```

`onViolation=SKIP` 时，危险 SQL 对应任务会标记为 `SKIPPED`，并在 `result_summary` 写入原因；设置为 `ERROR` 时任务会标记为 `ERROR`。保护性拦截不会重试。

## 拦截规则

- 大表上的 `count`、`sum`、`group by`、空值统计等聚合 SQL 必须带有效分片条件。
- 大表禁止 `OFFSET` 分片，因为它需要先 `count(*)`，并依赖 `order by limit offset` 窗口切片。
- 大表上的 `ORDER BY` 必须匹配主键或索引前缀，否则拦截。
- 大表 SQL 执行前会跑 `EXPLAIN`，出现全表扫描、临时表、文件排序或预估扫描行数超阈值时拦截。
- 查询结果超过 `maxResultRows` 会立即中断，防止应用侧一次性加载过多数据。
- `count`、`sum`、`group by`、`order by` 等重查询受 `maxConcurrentHeavyQueries` 限流，默认全局同一时间只允许 1 条。

## 推荐 CSV 写法

优先使用主键、时间字段或业务分区字段做范围分片：

```csv
pair_name,enabled,source_table,target_table,primary_key,checkers,where_clause,amount_fields,date_field,null_fields,order_fields,compare_fields,sample_where,sample_limit,shard_column,shard_type,shard_ranges,amount_tolerance
db1_compare,true,t_order,t_order,order_id,"ROW_COUNT,AMOUNT_SUM",1=1,order_amount,create_time,,order_id,"order_id,status",1=1,1000,create_time,DATETIME_INTERVAL,"2026-01-01 00:00:00~2026-02-01 00:00:00~1d",0.00
```

不推荐大表使用：

```csv
db1_compare,true,t_order,t_order,order_id,ROW_COUNT,1=1,,,,,,1=1,1000,order_id,OFFSET,"20",0.00
```

如需抽样校验，`order_fields` 应使用主键或已有索引前缀；如果按非索引字段排序，程序会拦截。
