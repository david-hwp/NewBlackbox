-- 多店管家后台数据库初始化脚本
-- 运行前请先创建数据库: CREATE DATABASE duodian_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;

USE duodian_admin;

-- 渠道表
CREATE TABLE IF NOT EXISTS channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL COMMENT '渠道标识',
    name VARCHAR(128) NOT NULL COMMENT '渠道名称',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
    admin_user_id BIGINT COMMENT '渠道管理员用户ID',
    app_display_name VARCHAR(128) COMMENT '主APK展示名称',
    app_application_id VARCHAR(128) NOT NULL COMMENT '主APK applicationId',
    app_icon_file_name VARCHAR(255) COMMENT '主APK图标文件名',
    app_icon_url VARCHAR(512) COMMENT '主APK图标URL',
    app_icon_checksum VARCHAR(128) COMMENT '主APK图标校验值',
    engine_display_name VARCHAR(128) COMMENT '引擎APK展示名称',
    engine_application_id VARCHAR(128) NOT NULL COMMENT '引擎APK applicationId',
    engine_icon_file_name VARCHAR(255) COMMENT '引擎APK图标文件名',
    engine_icon_url VARCHAR(512) COMMENT '引擎APK图标URL',
    engine_icon_checksum VARCHAR(128) COMMENT '引擎APK图标校验值',
    engine_notification_title VARCHAR(128) COMMENT '引擎通知标题',
    engine_notification_text VARCHAR(255) COMMENT '引擎通知内容',
    register_bonus_compute INT NOT NULL DEFAULT 3 COMMENT '注册赠送算力',
    remark VARCHAR(512) COMMENT '备注',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_channels_code (code),
    INDEX idx_channels_deleted (deleted),
    INDEX idx_channels_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道表';

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    avatar_url VARCHAR(512) COMMENT '头像URL',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    password VARCHAR(128) NOT NULL COMMENT '密码',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: SUPER_ADMIN/CHANNEL/USER',
    channel_id BIGINT COMMENT '渠道ID',
    compute_balance INT NOT NULL DEFAULT 0 COMMENT '算力余额',
    non_transferable_compute_balance INT NOT NULL DEFAULT 0 COMMENT '不可转赠算力余额',
    shop_count INT NOT NULL DEFAULT 0 COMMENT '店铺数量',
    platform_count INT NOT NULL DEFAULT 0 COMMENT '覆盖平台数',
    apk_channel VARCHAR(64) NOT NULL DEFAULT 'main' COMMENT '主APK渠道标识',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME COMMENT '最后登录时间',
    INDEX idx_phone (phone),
    INDEX idx_channel_id (channel_id),
    INDEX idx_role (role),
    INDEX idx_deleted (deleted),
    UNIQUE KEY uk_users_channel_phone_deleted (channel_id, phone, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 店铺表
CREATE TABLE IF NOT EXISTS shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    channel_id BIGINT COMMENT '渠道ID',
    shop_name VARCHAR(128) NOT NULL COMMENT '店铺名称',
    shop_id VARCHAR(64) NOT NULL COMMENT '店铺ID',
    platform VARCHAR(32) NOT NULL COMMENT '平台标识: meituan/taobao/jd/kuaishou/xiaohongshu/ali',
    platform_name VARCHAR(64) COMMENT '平台名称',
    remaining_days INT NOT NULL DEFAULT 0 COMMENT '剩余天数',
    auto_renew TINYINT(1) NOT NULL DEFAULT 0 COMMENT '自动续时: 0-关闭 1-开启',
    package_name VARCHAR(128) COMMENT '分身应用包名',
    clone_instance_id VARCHAR(255) COMMENT '分身实例唯一标识',
    clone_sequence INT COMMENT '同用户同应用下第几个分身',
    local_virtual_user_id INT COMMENT '本机虚拟用户目录号',
    clone_validation_code VARCHAR(64) COMMENT '服务端分身校验随机码',
    clone_validation_hash VARCHAR(128) COMMENT '服务端分身校验哈希',
    credential_version INT NOT NULL DEFAULT 1 COMMENT '分身授权凭证版本',
    auth_start_at DATETIME COMMENT '分身授权开始时间',
    auth_expire_at DATETIME COMMENT '分身授权过期时间',
    authorization_jti VARCHAR(64) COMMENT '当前授权令牌ID',
    last_deducted_at DATETIME COMMENT '最后扣减时间',
    expire_at DATETIME COMMENT '过期时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_platform (platform),
    INDEX idx_deleted (deleted),
    INDEX idx_clone_instance_id (clone_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

-- 算力扣费幂等表
CREATE TABLE IF NOT EXISTS compute_deductions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    channel_id BIGINT COMMENT '渠道ID',
    clone_instance_id VARCHAR(255) NOT NULL COMMENT '分身实例唯一标识',
    deduction_type VARCHAR(20) NOT NULL COMMENT '扣费类型: CREATE/RENEW',
    operation_key VARCHAR(128) NOT NULL COMMENT '客户端操作幂等键',
    amount INT NOT NULL DEFAULT 1 COMMENT '扣费数量',
    transaction_log_id BIGINT COMMENT '交易日志ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_compute_deduction_operation (user_id, clone_instance_id, deduction_type, operation_key),
    UNIQUE KEY uk_compute_deduction_operation_key (user_id, deduction_type, operation_key),
    INDEX idx_user_id (user_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_clone_instance_id (clone_instance_id),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力扣费幂等表';

-- 交易日志表
CREATE TABLE IF NOT EXISTS transaction_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '所属用户ID',
    channel_id BIGINT COMMENT '渠道ID',
    related_log_id BIGINT COMMENT '关联交易日志ID',
    type VARCHAR(20) NOT NULL COMMENT '类型: CONSUME/OUT/IN',
    amount INT NOT NULL COMMENT '金额',
    platform VARCHAR(32) COMMENT '关联平台',
    shop_name VARCHAR(128) COMMENT '关联店铺',
    from_phone VARCHAR(20) COMMENT '转出方手机号',
    from_name VARCHAR(64) COMMENT '转出方姓名',
    to_phone VARCHAR(20) COMMENT '接收方手机号',
    to_name VARCHAR(64) COMMENT '接收方姓名',
    remark VARCHAR(256) COMMENT '备注',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_related_log_id (related_log_id),
    INDEX idx_type (type),
    INDEX idx_deleted (deleted),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易日志表';

-- 公告表
CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT COMMENT '渠道ID',
    title VARCHAR(128) NOT NULL COMMENT '公告标题',
    content VARCHAR(4000) NOT NULL COMMENT '公告内容',
    type VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '公告类型: NORMAL/APP_RELEASE',
    published TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否发布',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_channel_id (channel_id),
    INDEX idx_published (published),
    INDEX idx_deleted (deleted),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- 引擎版本表
CREATE TABLE IF NOT EXISTS engine_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT COMMENT '渠道ID',
    version_code INT NOT NULL COMMENT '版本号',
    version_name VARCHAR(64) NOT NULL COMMENT '版本名称',
    application_id VARCHAR(128) NOT NULL DEFAULT 'com.zhirang.zhanghaoguanjia.engine' COMMENT '引擎APK applicationId',
    apk_url VARCHAR(512) NOT NULL COMMENT '引擎APK下载地址',
    checksum VARCHAR(64) COMMENT 'APK校验值',
    changelog TEXT COMMENT '更新日志',
    available TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_available (available),
    INDEX idx_channel_id (channel_id),
    INDEX idx_application_id (application_id),
    INDEX idx_deleted (deleted),
    INDEX idx_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引擎版本表';

-- 主APK版本表
CREATE TABLE IF NOT EXISTS app_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT COMMENT '渠道ID',
    version_code INT NOT NULL COMMENT '版本号',
    version_name VARCHAR(64) NOT NULL COMMENT '版本名称',
    application_id VARCHAR(128) NOT NULL DEFAULT 'com.zhirang.zhanghaoguanjia' COMMENT '主APK applicationId',
    apk_url VARCHAR(512) NOT NULL COMMENT '主APK下载地址',
    checksum VARCHAR(64) COMMENT 'APK校验值',
    file_size BIGINT COMMENT '文件大小，单位字节',
    changelog TEXT COMMENT '更新日志',
    published TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否发布',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_published (published),
    INDEX idx_channel_id (channel_id),
    INDEX idx_application_id (application_id),
    INDEX idx_deleted (deleted),
    INDEX idx_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主APK版本表';

-- 统一发布任务表
CREATE TABLE IF NOT EXISTS release_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL COMMENT '渠道ID',
    source_release_branch VARCHAR(128) NOT NULL COMMENT '源发布分支',
    channel_release_branch VARCHAR(128) NOT NULL COMMENT '渠道发布分支',
    app_version_name VARCHAR(64) NOT NULL COMMENT '主APK版本名称',
    app_version_code INT NOT NULL COMMENT '主APK版本号',
    engine_version_name VARCHAR(64) NOT NULL COMMENT '引擎版本名称',
    engine_version_code INT NOT NULL COMMENT '引擎版本号',
    announcement_content VARCHAR(4000) NOT NULL COMMENT '发布公告内容',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度百分比',
    log_excerpt TEXT COMMENT '日志摘要',
    app_artifact_url VARCHAR(512) COMMENT '主APK产物URL',
    app_artifact_md5 VARCHAR(32) COMMENT '主APK MD5',
    app_artifact_sha256 VARCHAR(64) COMMENT '主APK SHA-256',
    app_artifact_size BIGINT COMMENT '主APK大小',
    engine_artifact_url VARCHAR(512) COMMENT '引擎APK产物URL',
    engine_artifact_md5 VARCHAR(32) COMMENT '引擎APK MD5',
    engine_artifact_sha256 VARCHAR(64) COMMENT '引擎APK SHA-256',
    engine_artifact_size BIGINT COMMENT '引擎APK大小',
    requested_by BIGINT COMMENT '发起用户ID',
    retry_of_job_id BIGINT COMMENT '重试来源任务ID',
    callback_token_hash VARCHAR(128) COMMENT '回调令牌哈希',
    started_at DATETIME COMMENT '开始时间',
    finished_at DATETIME COMMENT '完成时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_release_jobs_channel_id (channel_id),
    INDEX idx_release_jobs_status (status),
    INDEX idx_release_jobs_deleted (deleted),
    INDEX idx_release_jobs_created_at (created_at),
    INDEX idx_release_jobs_retry_of (retry_of_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一发布任务表';

-- 支持平台配置表
CREATE TABLE IF NOT EXISTS platform_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform_id VARCHAR(64) NOT NULL COMMENT '平台标识',
    name VARCHAR(128) NOT NULL COMMENT '平台名称',
    package_name VARCHAR(256) COMMENT '应用包名',
    icon_url VARCHAR(512) COMMENT '平台图标URL',
    available TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_id (platform_id),
    INDEX idx_available (available),
    INDEX idx_deleted (deleted),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支持平台配置表';

-- 问题反馈表
CREATE TABLE IF NOT EXISTS feedbacks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '用户ID',
    channel_id BIGINT COMMENT '渠道ID',
    user_phone VARCHAR(20) COMMENT '用户手机号',
    content VARCHAR(2000) NOT NULL COMMENT '反馈内容',
    image_urls VARCHAR(1000) COMMENT '图片URL，逗号分隔',
    attachment_urls VARCHAR(1000) COMMENT '附件URL，逗号分隔',
    log_url VARCHAR(512) COMMENT '日志ZIP URL',
    source VARCHAR(32) DEFAULT 'APP' COMMENT '来源: APP/ENGINE_LOG',
    log_caption VARCHAR(1000) COMMENT '日志说明',
    device_info TEXT COMMENT '设备信息',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME COMMENT '处理时间',
    INDEX idx_user_id (user_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题反馈表';

-- 插入默认管理员账号，密码使用 BCrypt 密文存储
INSERT INTO channels (code, name, status, app_display_name, app_application_id, engine_display_name, engine_application_id, register_bonus_compute, deleted)
VALUES ('main', '默认渠道', 'ACTIVE', '账号管家', 'com.zhirang.zhanghaoguanjia', '账号管家引擎', 'com.zhirang.zhanghaoguanjia.engine', 3, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO users (username, phone, password, role, channel_id, compute_balance, non_transferable_compute_balance, shop_count, platform_count, apk_channel)
VALUES ('管理员', '13800138000', '$2y$12$xRCi/REAIr6LB5YhvqMIOeJ6aim.wGMW5l19JiJO3U8gpCjGAVssS', 'SUPER_ADMIN', (SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), 9999, 0, 0, 0, 'main')
ON DUPLICATE KEY UPDATE id=id;
