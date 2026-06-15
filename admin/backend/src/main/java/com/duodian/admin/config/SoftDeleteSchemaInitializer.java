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
            "compute_deductions",
            "system_parameters",
            "advanced_features"
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
        ensureSystemParameterTable();
        ensureAdvancedFeatureTable();
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
        ensureShopAuthorizationColumns();
        ensureShopCardSortColumn();
        ensurePlatformAuthorizationUrlColumn();
        ensureDefaultSystemParameters();
        ensureDefaultAdvancedFeatures();
        removeLegacyShopFeatureSystemParameters();
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

    private void ensureShopAuthorizationColumns() throws Exception {
        if (!hasColumn("shops", "shop_authorization_status")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN shop_authorization_status VARCHAR(32) NOT NULL DEFAULT 'UNAUTHORIZED'");
        }
        if (!hasColumn("shops", "shop_authorization_checked_at")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN shop_authorization_checked_at DATETIME");
        }
        if (!hasColumn("shops", "shop_authorization_signals")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN shop_authorization_signals TEXT");
        }
        if (!hasColumn("shops", "shop_authorization_url")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN shop_authorization_url VARCHAR(1024)");
        }
        jdbcTemplate.execute("""
                UPDATE shops
                SET shop_authorization_status = 'UNAUTHORIZED'
                WHERE shop_authorization_status IS NULL OR shop_authorization_status = ''
                """);
        if (!hasIndexQuietly("shops", "idx_shop_authorization_status")) {
            jdbcTemplate.execute("CREATE INDEX idx_shop_authorization_status ON shops (shop_authorization_status)");
        }
    }

    private void ensureShopCardSortColumn() throws Exception {
        if (!hasColumn("shops", "card_sort_order")) {
            jdbcTemplate.execute("ALTER TABLE shops ADD COLUMN card_sort_order INT NOT NULL DEFAULT 0");
        }
        jdbcTemplate.execute("UPDATE shops SET card_sort_order = 0 WHERE card_sort_order IS NULL");
        if (!hasIndexQuietly("shops", "idx_shop_card_sort")) {
            jdbcTemplate.execute("CREATE INDEX idx_shop_card_sort ON shops (user_id, package_name, platform, card_sort_order)");
        }
    }

    private void ensurePlatformAuthorizationUrlColumn() throws Exception {
        if (!hasColumn("platform_configs", "authorization_url")) {
            jdbcTemplate.execute("ALTER TABLE platform_configs ADD COLUMN authorization_url VARCHAR(1024)");
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

    private void ensureSystemParameterTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_parameters (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    channel_id BIGINT,
                    name VARCHAR(128) NOT NULL,
                    code VARCHAR(128) NOT NULL,
                    param_value VARCHAR(1024) NOT NULL,
                    description VARCHAR(512),
                    is_builtin TINYINT NOT NULL DEFAULT 0,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        if (!hasColumnQuietly("system_parameters", "is_builtin")) {
            jdbcTemplate.execute("ALTER TABLE system_parameters ADD COLUMN is_builtin TINYINT NOT NULL DEFAULT 0");
        }
        if (!hasIndexQuietly("system_parameters", "idx_system_parameters_channel_id")) {
            jdbcTemplate.execute("CREATE INDEX idx_system_parameters_channel_id ON system_parameters (channel_id)");
        }
        if (!hasIndexQuietly("system_parameters", "idx_system_parameters_code")) {
            jdbcTemplate.execute("CREATE INDEX idx_system_parameters_code ON system_parameters (code)");
        }
        if (!hasIndexQuietly("system_parameters", "idx_system_parameters_deleted")) {
            jdbcTemplate.execute("CREATE INDEX idx_system_parameters_deleted ON system_parameters (deleted)");
        }
        if (!hasIndexQuietly("system_parameters", "uk_system_parameters_channel_code_deleted")) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_system_parameters_channel_code_deleted ON system_parameters (channel_id, code, deleted)");
        }
    }

    private void ensureAdvancedFeatureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS advanced_features (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(64) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    is_online TINYINT NOT NULL DEFAULT 1,
                    monthly_compute_cost INT NOT NULL DEFAULT 1,
                    supported_platform_packages VARCHAR(2048),
                    title_code VARCHAR(128) NOT NULL,
                    line1_code VARCHAR(128) NOT NULL,
                    line2_code VARCHAR(128) NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    line1_text VARCHAR(255) NOT NULL DEFAULT '-',
                    line2_text VARCHAR(255) NOT NULL DEFAULT '-',
                    outbound_enabled TINYINT NOT NULL DEFAULT 0,
                    sort_order INT NOT NULL DEFAULT 0,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        addColumnIfMissing("advanced_features", "is_online", "TINYINT NOT NULL DEFAULT 1");
        addColumnIfMissing("advanced_features", "monthly_compute_cost", "INT NOT NULL DEFAULT 1");
        addColumnIfMissing("advanced_features", "supported_platform_packages", "VARCHAR(2048)");
        addColumnIfMissing("advanced_features", "title_code", "VARCHAR(128)");
        addColumnIfMissing("advanced_features", "line1_code", "VARCHAR(128)");
        addColumnIfMissing("advanced_features", "line2_code", "VARCHAR(128)");
        addColumnIfMissing("advanced_features", "title", "VARCHAR(128)");
        addColumnIfMissing("advanced_features", "line1_text", "VARCHAR(255) NOT NULL DEFAULT '-'");
        addColumnIfMissing("advanced_features", "line2_text", "VARCHAR(255) NOT NULL DEFAULT '-'");
        addColumnIfMissing("advanced_features", "outbound_enabled", "TINYINT NOT NULL DEFAULT 0");
        addColumnIfMissing("advanced_features", "sort_order", "INT NOT NULL DEFAULT 0");
        if (hasColumnQuietly("advanced_features", "title_param_code")) {
            jdbcTemplate.execute("UPDATE advanced_features SET title_code = title_param_code WHERE (title_code IS NULL OR title_code = '')");
        }
        if (hasColumnQuietly("advanced_features", "line1_param_code")) {
            jdbcTemplate.execute("UPDATE advanced_features SET line1_code = line1_param_code WHERE (line1_code IS NULL OR line1_code = '')");
        }
        if (hasColumnQuietly("advanced_features", "line2_param_code")) {
            jdbcTemplate.execute("UPDATE advanced_features SET line2_code = line2_param_code WHERE (line2_code IS NULL OR line2_code = '')");
        }
        jdbcTemplate.execute("UPDATE advanced_features SET is_online = 1 WHERE is_online IS NULL");
        jdbcTemplate.execute("UPDATE advanced_features SET monthly_compute_cost = 1 WHERE monthly_compute_cost IS NULL");
        jdbcTemplate.execute("UPDATE advanced_features SET outbound_enabled = 0 WHERE outbound_enabled IS NULL");
        jdbcTemplate.execute("UPDATE advanced_features SET sort_order = 0 WHERE sort_order IS NULL");
        jdbcTemplate.execute("UPDATE advanced_features SET title_code = code WHERE title_code IS NULL OR title_code = ''");
        jdbcTemplate.execute("UPDATE advanced_features SET line1_code = concat(code, '.line1') WHERE line1_code IS NULL OR line1_code = ''");
        jdbcTemplate.execute("UPDATE advanced_features SET line2_code = concat(code, '.line2') WHERE line2_code IS NULL OR line2_code = ''");
        jdbcTemplate.execute("UPDATE advanced_features SET title = name WHERE title IS NULL OR title = ''");
        jdbcTemplate.execute("UPDATE advanced_features SET line1_text = '-' WHERE line1_text IS NULL OR line1_text = ''");
        jdbcTemplate.execute("UPDATE advanced_features SET line2_text = '-' WHERE line2_text IS NULL OR line2_text = ''");
        if (!hasIndexQuietly("advanced_features", "idx_advanced_features_code")) {
            jdbcTemplate.execute("CREATE INDEX idx_advanced_features_code ON advanced_features (code)");
        }
        if (!hasIndexQuietly("advanced_features", "idx_advanced_features_deleted")) {
            jdbcTemplate.execute("CREATE INDEX idx_advanced_features_deleted ON advanced_features (deleted)");
        }
        if (!hasIndexQuietly("advanced_features", "uk_advanced_features_code_deleted")) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX uk_advanced_features_code_deleted ON advanced_features (code, deleted)");
        }
    }

    private void ensureDefaultSystemParameters() {
        Long mainChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1",
                Long.class
        );
        if (mainChannelId == null) {
            throw new IllegalStateException("默认渠道不存在");
        }
        upsertDefaultParameter(mainChannelId, "新用户注册赠送订阅时长", "register.trial.subscription.days", "30", "单位：天");
        upsertDefaultParameter(mainChannelId, "算力赠送按钮名称", "app.menu.gift_compute.label", "算力赠送", "APP 交易中心入口文案");
        upsertDefaultParameter(mainChannelId, "算力取回按钮名称", "app.menu.reclaim_compute.label", "算力取回", "APP 交易中心入口文案");
        upsertDefaultParameter(mainChannelId, "话费赠送按钮名称", "app.menu.gift_phone_minutes.label", "话费赠送", "APP 交易中心入口文案");
        upsertDefaultParameter(mainChannelId, "话费取回按钮名称", "app.menu.reclaim_phone_minutes.label", "话费取回", "APP 交易中心入口文案");
        upsertDefaultParameter(mainChannelId, "交易日志按钮名称", "app.menu.transaction_logs.label", "交易日志", "APP 交易中心入口文案");
    }

    private void ensureDefaultAdvancedFeatures() {
        Long mainChannelId = jdbcTemplate.queryForObject(
                "SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1",
                Long.class
        );
        if (mainChannelId == null) {
            throw new IllegalStateException("默认渠道不存在");
        }
        upsertDefaultAdvancedFeature(
                mainChannelId,
                "bad_review_location",
                "差评定位",
                "app.shop_feature.bad_review_location.label",
                "app.shop_feature.bad_review_location.line1",
                "app.shop_feature.bad_review_location.line2",
                "差评定位",
                10
        );
        upsertDefaultAdvancedFeature(
                mainChannelId,
                "business_report",
                "经营日报",
                "app.shop_feature.business_report.label",
                "app.shop_feature.business_report.line1",
                "app.shop_feature.business_report.line2",
                "经营日报",
                20
        );
        upsertDefaultAdvancedFeature(
                mainChannelId,
                "outbound_praise",
                "外呼好评",
                "app.shop_feature.outbound_praise.label",
                "app.shop_feature.outbound_praise.line1",
                "app.shop_feature.outbound_praise.line2",
                "外呼好评",
                30
        );
        upsertDefaultAdvancedFeature(
                mainChannelId,
                "review_appeal",
                "评价申诉",
                "app.shop_feature.review_appeal.label",
                "app.shop_feature.review_appeal.line1",
                "app.shop_feature.review_appeal.line2",
                "评价申诉",
                40
        );
        upsertDefaultAdvancedFeature(
                mainChannelId,
                "private_traffic",
                "私域吸粉",
                "app.shop_feature.private_traffic.label",
                "app.shop_feature.private_traffic.line1",
                "app.shop_feature.private_traffic.line2",
                "私域吸粉",
                50
        );
    }

    private void upsertDefaultAdvancedFeature(
            Long mainChannelId,
            String code,
            String name,
            String titleCode,
            String line1Code,
            String line2Code,
            String fallbackTitle,
            int sortOrder
    ) {
        String title = legacyParameterValue(mainChannelId, titleCode, fallbackTitle);
        String line1 = legacyParameterValue(mainChannelId, line1Code, "-");
        String line2 = legacyParameterValue(mainChannelId, line2Code, "-");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM advanced_features WHERE code = ? AND deleted = 0",
                Integer.class,
                code
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE advanced_features
                    SET name = ?,
                        title_code = ?,
                        line1_code = ?,
                        line2_code = ?,
                        title = CASE WHEN title IS NULL OR title = '' THEN ? ELSE title END,
                        line1_text = CASE WHEN line1_text IS NULL OR line1_text = '' THEN ? ELSE line1_text END,
                        line2_text = CASE WHEN line2_text IS NULL OR line2_text = '' THEN ? ELSE line2_text END,
                        sort_order = ?
                    WHERE code = ? AND deleted = 0
                    """, name, titleCode, line1Code, line2Code, title, line1, line2, sortOrder, code);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO advanced_features (
                    code,
                    name,
                    is_online,
                    monthly_compute_cost,
                    title_code,
                    line1_code,
                    line2_code,
                    title,
                    line1_text,
                    line2_text,
                    outbound_enabled,
                    sort_order,
                    deleted
                ) VALUES (?, ?, 1, 1, ?, ?, ?, ?, ?, ?, 0, ?, 0)
                """, code, name, titleCode, line1Code, line2Code, title, line1, line2, sortOrder);
    }

    private String legacyParameterValue(Long mainChannelId, String code, String fallback) {
        List<String> values = jdbcTemplate.query(
                "SELECT param_value FROM system_parameters WHERE channel_id = ? AND code = ? AND deleted = 0 ORDER BY updated_at DESC",
                (rs, rowNum) -> rs.getString("param_value"),
                mainChannelId,
                code
        );
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(String::trim)
                .orElse(fallback);
    }

    private void removeLegacyShopFeatureSystemParameters() {
        jdbcTemplate.update("DELETE FROM system_parameters WHERE code LIKE 'app.shop_feature.%'");
    }

    private void upsertDefaultParameter(Long channelId, String name, String code, String value, String description) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_parameters WHERE channel_id = ? AND code = ? AND deleted = 0",
                Integer.class,
                channelId,
                code
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE system_parameters
                    SET is_builtin = 1
                    WHERE channel_id = ? AND code = ? AND deleted = 0
                    """, channelId, code);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO system_parameters (channel_id, name, code, param_value, description, is_builtin, deleted)
                VALUES (?, ?, ?, ?, ?, 1, 0)
                """, channelId, name, code, value, description);
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!hasColumnQuietly(table, column)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumnQuietly(String table, String column) {
        try {
            return hasColumn(table, column);
        } catch (Exception e) {
            return false;
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
