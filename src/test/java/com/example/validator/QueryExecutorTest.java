package com.example.validator;

import com.example.validator.config.ValidatorProperties;
import com.example.validator.datasource.NamedDataSourceManager;
import com.example.validator.datasource.QueryExecutor;
import com.example.validator.safety.TooManyResultRowsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class QueryExecutorTest {
    @Autowired
    private QueryExecutor queryExecutor;
    @Autowired
    private NamedDataSourceManager dataSourceManager;
    @Autowired
    private ValidatorProperties properties;

    private int oldMaxResultRows;

    @BeforeEach
    void setUp() {
        oldMaxResultRows = properties.getSafety().getMaxResultRows();
        properties.getSafety().setMaxResultRows(1);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSourceManager.getRequired("tdsql_01"));
        jdbcTemplate.execute("drop table if exists t_many_rows");
        jdbcTemplate.execute("create table t_many_rows (id bigint primary key)");
        jdbcTemplate.update("insert into t_many_rows(id) values (1)");
        jdbcTemplate.update("insert into t_many_rows(id) values (2)");
    }

    @AfterEach
    void tearDown() {
        properties.getSafety().setMaxResultRows(oldMaxResultRows);
    }

    @Test
    void queryStopsWhenResultRowsExceedLimit() {
        assertThrows(TooManyResultRowsException.class,
                () -> queryExecutor.query("tdsql_01", "select id from t_many_rows order by id", 30));
    }
}
