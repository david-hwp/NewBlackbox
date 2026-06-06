package com.duodian.admin.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

@Component
public class SoftDeleteSchemaInitializer implements CommandLineRunner {

    private static final List<String> TABLES = List.of(
            "users",
            "shops",
            "transaction_logs",
            "announcements",
            "engine_versions",
            "platform_configs",
            "feedbacks"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public SoftDeleteSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        for (String table : TABLES) {
            if (!hasColumn(table, "deleted")) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0");
            }
        }
    }

    private boolean hasColumn(String table, String column) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            return hasColumn(metaData, catalog, table, column)
                    || hasColumn(metaData, catalog, table.toUpperCase(), column.toUpperCase());
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String catalog, String table, String column) throws Exception {
        try (ResultSet resultSet = metaData.getColumns(catalog, null, table, column)) {
            return resultSet.next();
        }
    }
}
