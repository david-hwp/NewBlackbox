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
            "channels",
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
        ensureChannelTable();
        ensureComputeDeductionTable();
        for (String table : TABLES) {
            if (!hasColumn(table, "deleted")) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0");
            }
        }
        ensureMainChannel();
        ensureAnnouncementTypeColumn();
        ensureUserApkChannelColumn();
        ensureUserSubscriptionColumns();
        ensureUserPhoneMinutesColumn();
        ensureChannelColumns();
        ensurePackageVersionIdentityColumns();
        backfillMainChannel();
        ensureUserPhoneChannelUniqueIndex();
        migrateLegacyAdminRole();
        ensureCloneColumns();
        ensureShopLoginStateColumns();
    }

    private void ensureChannelTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS channels (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    admin_user_id BIGINT,
                    app_display_name VARCHAR(128),
                    app_application_id VARCHAR(128) NOT NULL,
                    app_icon_file_name VARCHAR(255),
                    app_icon_url VARCHAR(512),
                    app_icon_checksum VARCHAR(128),
                    engine_display_name VARCHAR(128),
                    engine_application_id VARCHAR(128) NOT NULL,
                    engine_icon_file_name VARCHAR(255),
                    engine_icon_url VARCHAR(512),
                    engine_icon_checksum VARCHAR(128),
                    engine_notification_title VARCHAR(128),
                    engine_notification_text VARCHAR(255),
                    register_bonus_compute INT NOT NULL DEFAULT 3,
                    remark VARCHAR(512),
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        if (!hasIndexQuietly("channels", "uk_channels_code")) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_channels_code ON channels (code)");
        }
        if (!hasIndexQuietly("channels", "idx_channels_deleted")) {
            jdbcTemplate.execute("CREATE INDEX idx_channels_deleted ON channels (deleted)");
        }
        if (!hasIndexQuietly("channels", "idx_channels_status")) {
            jdbcTemplate.execute("CREATE INDEX idx_channels_status ON channels (status)");
        }
    }

    private void ensureMainChannel() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM channels WHERE code = 'main' AND deleted = 0",
                Integer.class
        );
        if (count == null || count == 0) {
            jdbcTemplate.update("""
                    INSERT INTO channels (
                        code,
                        name,
                        status,
                        app_display_name,
                        app_application_id,
                        engine_display_name,
                        engine_application_id,
                        register_bonus_compute,
                        deleted
                    ) VALUES (
                        'main',
                        '默认渠道',
                        'ACTIVE',
                        '账号管家',
                        'com.zhirang.zhanghaoguanjia',
                        '账号管家引擎',
                        'com.zhirang.zhanghaoguanjia.engine',
                        3,
                        0
                    )
                    """);
        }
    }

    private void ensureUserApkChannelColumn() throws Exception {
        if (!hasColumn("users", "apk_channel")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN apk_channel VARCHAR(64) NOT NULL DEFAULT 'main'");
        }
        jdbcTemplate.execute("UPDATE users SET apk_channel = 'main' WHERE apk_channel IS NULL OR apk_channel = ''");
        jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN apk_channel VARCHAR(64) NOT NULL DEFAULT 'main'");
    }

    private void ensureUserSubscriptionColumns() throws Exception {
        if (!hasColumn("users", "subscription_plan")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN subscription_plan VARCHAR(32) NOT NULL DEFAULT 'NONE'");
        }
        if (!hasColumn("users", "subscription_expires_at")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN subscription_expires_at DATETIME");
        }
        if (!hasColumn("users", "subscription_updated_at")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN subscription_updated_at DATETIME");
        }
        jdbcTemplate.execute("UPDATE users SET subscription_plan = 'NONE' WHERE subscription_plan IS NULL OR subscription_plan = ''");
        jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN subscription_plan VARCHAR(32) NOT NULL DEFAULT 'NONE'");
        if (!hasIndexQuietly("users", "idx_subscription_expires_at")) {
            jdbcTemplate.execute("CREATE INDEX idx_subscription_expires_at ON users (subscription_expires_at)");
        }
    }

    private void ensureUserPhoneMinutesColumn() throws Exception {
        if (!hasColumn("users", "phone_minutes_balance")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN phone_minutes_balance INT NOT NULL DEFAULT 0");
        }
        jdbcTemplate.execute("UPDATE users SET phone_minutes_balance = 0 WHERE phone_minutes_balance IS NULL");
        jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN phone_minutes_balance INT NOT NULL DEFAULT 0");
    }

    private void ensureChannelColumns() throws Exception {
        addChannelColumn("users");
        addChannelColumn("shops");
        addChannelColumn("transaction_logs");
        addChannelColumn("compute_deductions");
        addChannelColumn("feedbacks");
        addChannelColumn("announcements");
        addChannelColumn("app_versions");
        addChannelColumn("engine_versions");
        if (!hasColumn("transaction_logs", "related_log_id")) {
            jdbcTemplate.execute("ALTER TABLE transaction_logs ADD COLUMN related_log_id BIGINT");
        }
        if (!hasIndexQuietly("transaction_logs", "idx_related_log_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_related_log_id ON transaction_logs (related_log_id)");
        }
    }

    private void addChannelColumn(String table) throws Exception {
        if (!hasColumn(table, "channel_id")) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN channel_id BIGINT");
        }
        String indexName = "idx_" + table + "_channel_id";
        if (!hasIndexQuietly(table, indexName)) {
            jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + table + " (channel_id)");
        }
    }

    private void backfillMainChannel() {
        Long mainChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1",
                Long.class
        );
        if (mainChannelId == null) {
            throw new IllegalStateException("默认渠道不存在");
        }
        jdbcTemplate.update("UPDATE users SET channel_id = ?, apk_channel = 'main' WHERE channel_id IS NULL", mainChannelId);
        jdbcTemplate.update("""
                UPDATE shops s
                LEFT JOIN users u ON u.id = s.user_id
                SET s.channel_id = COALESCE(u.channel_id, ?)
                WHERE s.channel_id IS NULL
                """, mainChannelId);
        jdbcTemplate.update("""
                UPDATE transaction_logs t
                LEFT JOIN users u ON u.id = t.user_id
                SET t.channel_id = COALESCE(u.channel_id, ?)
                WHERE t.channel_id IS NULL
                """, mainChannelId);
        jdbcTemplate.update("""
                UPDATE compute_deductions d
                LEFT JOIN users u ON u.id = d.user_id
                SET d.channel_id = COALESCE(u.channel_id, ?)
                WHERE d.channel_id IS NULL
                """, mainChannelId);
        jdbcTemplate.update("""
                UPDATE feedbacks f
                LEFT JOIN users u ON u.id = f.user_id
                SET f.channel_id = COALESCE(u.channel_id, ?)
                WHERE f.channel_id IS NULL
                """, mainChannelId);
        jdbcTemplate.update("UPDATE announcements SET channel_id = ? WHERE channel_id IS NULL", mainChannelId);
        jdbcTemplate.update("UPDATE app_versions SET channel_id = ? WHERE channel_id IS NULL", mainChannelId);
        jdbcTemplate.update("UPDATE engine_versions SET channel_id = ? WHERE channel_id IS NULL", mainChannelId);
        jdbcTemplate.update("""
                UPDATE app_versions v
                LEFT JOIN channels c ON c.id = v.channel_id AND c.deleted = 0
                SET v.application_id = COALESCE(c.app_application_id, 'com.zhirang.zhanghaoguanjia')
                WHERE v.application_id IS NULL
                   OR v.application_id = ''
                   OR v.application_id = 'com.zhirang.zhanghaoguanjia'
                """);
        jdbcTemplate.update("""
                UPDATE engine_versions v
                LEFT JOIN channels c ON c.id = v.channel_id AND c.deleted = 0
                SET v.application_id = COALESCE(c.engine_application_id, 'com.zhirang.zhanghaoguanjia.engine')
                WHERE v.application_id IS NULL
                   OR v.application_id = ''
                   OR v.application_id = 'com.zhirang.zhanghaoguanjia.engine'
                """);
    }

    private void migrateLegacyAdminRole() {
        jdbcTemplate.update("UPDATE users SET role = 'SUPER_ADMIN' WHERE UPPER(role) = 'ADMIN'");
    }

    private void ensureUserPhoneChannelUniqueIndex() throws Exception {
        for (String indexName : uniqueSingleColumnIndexes("users", "phone")) {
            if (!"uk_users_channel_phone_deleted".equalsIgnoreCase(indexName)) {
                jdbcTemplate.execute("ALTER TABLE users DROP INDEX " + indexName);
            }
        }
        if (!hasIndexQuietly("users", "uk_users_channel_phone_deleted")) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_users_channel_phone_deleted ON users (channel_id, phone, deleted)");
        }
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
        if (hasIndexQuietly("shops", "uk_clone_instance_id")) {
            jdbcTemplate.execute("ALTER TABLE shops DROP INDEX uk_clone_instance_id");
        }
        if (!hasIndexQuietly("shops", "idx_clone_instance_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_clone_instance_id ON shops (clone_instance_id)");
        }
    }

    private void ensureShopLoginStateColumns() throws Exception {
        if (!hasColumn("shops", "login_state_artifact_created_at")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN login_state_artifact_created_at DATETIME");
        }
    }

    private void ensurePackageVersionIdentityColumns() throws Exception {
        if (!hasColumn("app_versions", "application_id")) {
            jdbcTemplate.execute("ALTER TABLE app_versions ADD COLUMN application_id VARCHAR(128) NOT NULL DEFAULT 'com.zhirang.zhanghaoguanjia'");
        }
        if (!hasColumn("engine_versions", "application_id")) {
            jdbcTemplate.execute("ALTER TABLE engine_versions ADD COLUMN application_id VARCHAR(128) NOT NULL DEFAULT 'com.zhirang.zhanghaoguanjia.engine'");
        }
        if (!hasIndexQuietly("app_versions", "idx_app_versions_application_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_app_versions_application_id ON app_versions (application_id)");
        }
        if (!hasIndexQuietly("engine_versions", "idx_engine_versions_application_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_engine_versions_application_id ON engine_versions (application_id)");
        }
    }

    private void ensureComputeDeductionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS compute_deductions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    channel_id BIGINT,
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
                    INDEX idx_channel_id (channel_id),
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

    private boolean hasIndexQuietly(String table, String index) {
        try {
            return hasIndex(table, index);
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> uniqueSingleColumnIndexes(String table, String column) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet resultSet = metaData.getIndexInfo(catalog, null, table, false, false)) {
                java.util.Map<String, java.util.List<String>> columnsByIndex = new java.util.LinkedHashMap<>();
                java.util.Map<String, Boolean> uniqueByIndex = new java.util.LinkedHashMap<>();
                while (resultSet.next()) {
                    String indexName = resultSet.getString("INDEX_NAME");
                    String columnName = resultSet.getString("COLUMN_NAME");
                    if (indexName == null || columnName == null || "PRIMARY".equalsIgnoreCase(indexName)) {
                        continue;
                    }
                    columnsByIndex.computeIfAbsent(indexName, ignored -> new java.util.ArrayList<>()).add(columnName);
                    uniqueByIndex.put(indexName, !resultSet.getBoolean("NON_UNIQUE"));
                }
                return columnsByIndex.entrySet().stream()
                        .filter(entry -> Boolean.TRUE.equals(uniqueByIndex.get(entry.getKey())))
                        .filter(entry -> entry.getValue().size() == 1)
                        .filter(entry -> column.equalsIgnoreCase(entry.getValue().get(0)))
                        .map(java.util.Map.Entry::getKey)
                        .toList();
            }
        }
    }
}
