package com.example.validator;

import com.example.validator.config.ValidatorProperties;
import com.example.validator.config.ValidatorProperties.SqlDialect;
import com.example.validator.datasource.NamedDataSourceManager;
import com.example.validator.safety.QueryRiskLevel;
import com.example.validator.safety.SqlGuardDecision;
import com.example.validator.safety.SqlGuardService;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class SqlGuardServiceTest {
    @Autowired
    private SqlGuardService sqlGuardService;
    @Autowired
    private NamedDataSourceManager dataSourceManager;
    @Autowired
    private ValidatorProperties properties;

    @BeforeEach
    void setUp() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-tdsql.sql"));
        populator.addScript(new ClassPathResource("data-tdsql.sql"));
        populator.execute(dataSource());
        properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.MYSQL);
    }

    @AfterEach
    void tearDown() {
        properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.H2);
    }

    @Test
    void largeTableUnshardedCountIsBlocked() {
        SqlGuardDecision decision = sqlGuardService.inspect("tdsql_01", dataSource(),
                "select count(*) as total_count from t_order where 1=1");

        assertEquals(QueryRiskLevel.BLOCK, decision.getRiskLevel());
    }

    @Test
    void largeTableUnshardedSumIsBlocked() {
        SqlGuardDecision decision = sqlGuardService.inspect("tdsql_01", dataSource(),
                "select sum(order_amount) as order_amount_sum from t_order where 1=1");

        assertEquals(QueryRiskLevel.BLOCK, decision.getRiskLevel());
    }

    @Test
    void largeTableUnindexedOrderByIsBlocked() {
        SqlGuardDecision decision = sqlGuardService.inspect("tdsql_01", dataSource(),
                "select remark from t_order where order_id between 1 and 10 order by remark limit 5");

        assertEquals(QueryRiskLevel.BLOCK, decision.getRiskLevel());
    }

    @Test
    void largeTableShardedPrimaryKeyOrderByIsAllowed() {
        SqlGuardDecision decision = sqlGuardService.inspect("tdsql_01", dataSource(),
                "select order_id from t_order where order_id between 1 and 10 order by order_id limit 5");

        assertEquals(QueryRiskLevel.ALLOW, decision.getRiskLevel());
    }

    @Test
    void unsupportedExplainDialectDoesNotBlockOtherwiseSafeSql() {
        properties.getDatasources().get("tdsql_01").setDialect(SqlDialect.OCEANBASE_ORACLE);

        SqlGuardDecision decision = sqlGuardService.inspect("tdsql_01", dataSource(),
                "select order_id from t_order where order_id between 1 and 10 order by order_id limit 5");

        assertEquals(QueryRiskLevel.ALLOW, decision.getRiskLevel());
    }

    @Test
    void largeTableOffsetShardIsBlocked() {
        SqlGuardDecision decision = sqlGuardService.inspect("tdsql_01", dataSource(),
                "select count(*) as total_count from t_order where order_id in "
                        + "(select order_id from (select order_id from t_order where 1=1 order by order_id limit 10 offset 10) shard_window)");

        assertEquals(QueryRiskLevel.BLOCK, decision.getRiskLevel());
    }

    private DataSource dataSource() {
        return dataSourceManager.getRequired("tdsql_01");
    }
}
