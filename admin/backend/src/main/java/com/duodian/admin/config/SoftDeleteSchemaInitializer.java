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
            "app_versions",
            "platform_configs",
            "feedbacks",
            "compute_deductions"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public SoftDeleteSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureComputeDeductionTable();
        for (String table : TABLES) {
            if (!hasColumn(table, "deleted")) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0");
            }
        }
        ensureAnnouncementTypeColumn();
        ensureCloneColumns();
    }

    private void ensureAnnouncementTypeColumn() throws Exception {
        if (!hasColumn("announcements", "type")) {
            jdbcTemplate.execute("ALTER TABLE announcements ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'NORMAL'");
            jdbcTemplate.execute("CREATE INDEX idx_type ON announcements (type)");
        }
        jdbcTemplate.execute("UPDATE announcements SET type = 'NORMAL' WHERE type IS NULL OR type = ''");
    }

    private void ensureCloneColumns() throws Exception {
        if (!hasColumn("shops", "clone_sequence")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN clone_sequence INT");
        }
        if (!hasColumn("shops", "local_virtual_user_id")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN local_virtual_user_id INT");
        }
        if (!hasColumn("shops", "clone_validation_code")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN clone_validation_code VARCHAR(64)");
        }
        if (!hasColumn("shops", "clone_validation_hash")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN clone_validation_hash VARCHAR(128)");
        }
        if (!hasColumn("shops", "credential_version")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN credential_version INT NOT NULL DEFAULT 1");
        }
        if (!hasColumn("shops", "auth_start_at")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN auth_start_at DATETIME");
        }
        if (!hasColumn("shops", "auth_expire_at")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN auth_expire_at DATETIME");
        }
        if (!hasColumn("shops", "authorization_jti")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN authorization_jti VARCHAR(64)");
        }
        jdbcTemplate.execute("ALTER TABLE shops MODIFY COLUMN clone_instance_id VARCHAR(255)");
        if (hasIndex("shops", "uk_clone_instance_id")) {
            jdbcTemplate.execute("ALTER TABLE shops DROP INDEX uk_clone_instance_id");
        }
        if (!hasIndex("shops", "idx_clone_instance_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_clone_instance_id ON shops (clone_instance_id)");
        }
    }

    private void ensureComputeDeductionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS compute_deductions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    clone_instance_id VARCHAR(255) NOT NULL,
                    deduction_type VARCHAR(20) NOT NULL,
                    operation_key VARCHAR(128) NOT NULL,
                    amount INT NOT NULL DEFAULT 1,
                    transaction_log_id BIGINT,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_compute_deduction_operation (user_id, clone_instance_id, deduction_type, operation_key),
                    UNIQUE KEY uk_compute_deduction_operation_key (user_id, deduction_type, operation_key),
                    INDEX idx_user_id (user_id),
                    INDEX idx_clone_instance_id (clone_instance_id),
                    INDEX idx_deleted (deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
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

    private boolean hasIndex(String table, String index) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            return hasIndex(metaData, catalog, table, index)
                    || hasIndex(metaData, catalog, table.toUpperCase(), index.toUpperCase());
        }
    }

    private boolean hasIndex(DatabaseMetaData metaData, String catalog, String table, String index) throws Exception {
        try (ResultSet resultSet = metaData.getIndexInfo(catalog, null, table, false, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (index.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
