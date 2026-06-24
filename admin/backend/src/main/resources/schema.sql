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
    phone_minutes_balance INT NOT NULL DEFAULT 0 COMMENT '话费分钟余额',
    shop_count INT NOT NULL DEFAULT 0 COMMENT '店铺数量',
    platform_count INT NOT NULL DEFAULT 0 COMMENT '覆盖平台数',
    apk_channel VARCHAR(64) NOT NULL DEFAULT 'main' COMMENT '主APK渠道标识',
    subscription_plan VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '订阅套餐: NONE/TRIAL/MONTHLY/QUARTERLY/YEARLY',
    subscription_expires_at DATETIME COMMENT '订阅到期时间',
    subscription_updated_at DATETIME COMMENT '订阅更新时间',
    legacy_engine_migrated TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已从旧引擎目录迁移',
    legacy_engine_migrated_at DATETIME COMMENT '旧引擎目录迁移完成时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME COMMENT '最后登录时间',
    INDEX idx_phone (phone),
    INDEX idx_users_legacy_engine_migrated (legacy_engine_migrated),
    INDEX idx_subscription_expires_at (subscription_expires_at),
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
    identity_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '店铺身份是否已由引擎验证',
    identity_verified_at DATETIME COMMENT '店铺身份验证时间',
    platform VARCHAR(32) NOT NULL COMMENT '平台标识: meituan/taobao/jd/kuaishou/xiaohongshu/ali',
    platform_name VARCHAR(64) COMMENT '平台名称',
    card_sort_order INT NOT NULL DEFAULT 0 COMMENT '同用户同平台店铺卡片排序序号',
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
    login_state_profile VARCHAR(128) COMMENT '登录态同步档位/配置',
    login_state_size BIGINT COMMENT '登录态文件大小',
    login_state_sha256 VARCHAR(64) COMMENT '登录态文件SHA-256',
    login_state_manifest TEXT COMMENT '登录态文件清单',
    login_state_blob LONGBLOB COMMENT '登录态压缩包',
    login_state_updated_at DATETIME COMMENT '登录态更新时间',
    login_state_artifact_created_at DATETIME COMMENT '登录态文件创建/导出时间',
    shop_authorization_status VARCHAR(32) NOT NULL DEFAULT 'UNAUTHORIZED' COMMENT '店铺授权状态: UNAUTHORIZED/AUTHORIZING/AUTHORIZED/FAILED/UNKNOWN',
    shop_authorization_checked_at DATETIME COMMENT '店铺授权状态检测时间',
    shop_authorization_signals TEXT COMMENT '店铺授权状态检测信号摘要',
    shop_authorization_url VARCHAR(1024) COMMENT '店铺级远程授权管理地址',
    wechat_receiver_id VARCHAR(128) COMMENT '微信接收方ID',
    wechat_receiver_name VARCHAR(128) COMMENT '微信接收方名称',
    wechat_receiver_type VARCHAR(32) COMMENT '微信接收方类型: CONTACT/GROUP',
    remark VARCHAR(512) COMMENT '店铺备注',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_shop_card_sort (user_id, package_name, platform, card_sort_order),
    INDEX idx_platform (platform),
    INDEX idx_deleted (deleted),
    INDEX idx_shop_authorization_status (shop_authorization_status),
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

-- 店铺订单表
CREATE TABLE IF NOT EXISTS shop_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL COMMENT '后台系统店铺ID',
    user_id BIGINT COMMENT '店铺所属用户ID快照',
    channel_id BIGINT COMMENT '渠道ID快照',
    platform VARCHAR(32) NOT NULL COMMENT '平台标识',
    platform_name VARCHAR(64) COMMENT '平台名称快照',
    platform_shop_id VARCHAR(128) COMMENT '平台侧店铺ID快照',
    shop_name VARCHAR(128) COMMENT '店铺名称快照',
    platform_order_id VARCHAR(128) NOT NULL COMMENT '平台侧订单ID',
    platform_order_no VARCHAR(64) COMMENT '页面展示订单序号',
    order_sequence VARCHAR(64) COMMENT '订单卡片序号',
    source VARCHAR(64) COMMENT '采集来源',
    order_time_text VARCHAR(128) COMMENT '页面原始订单时间文本',
    ordered_at DATETIME COMMENT '下单时间',
    expected_delivery_at DATETIME COMMENT '预计送达时间',
    completed_at DATETIME COMMENT '订单完成时间',
    cancelled_at DATETIME COMMENT '取消时间',
    refunded_at DATETIME COMMENT '退款时间',
    fetched_at DATETIME COMMENT '爬虫采集时间',
    last_seen_at DATETIME COMMENT '最后一次采集看到时间',
    status VARCHAR(64) COMMENT '订单状态',
    status_text VARCHAR(128) COMMENT '订单状态文本',
    order_type VARCHAR(64) COMMENT '订单类型',
    tags_json TEXT COMMENT '订单标签JSON',
    estimated_income DECIMAL(12,2) COMMENT '预计收入',
    customer_paid_amount DECIMAL(12,2) COMMENT '顾客实付',
    merchant_income DECIMAL(12,2) COMMENT '商家收入',
    original_amount DECIMAL(12,2) COMMENT '原价',
    discount_amount DECIMAL(12,2) COMMENT '优惠金额',
    delivery_fee DECIMAL(12,2) COMMENT '配送费',
    package_fee DECIMAL(12,2) COMMENT '包装费',
    refund_amount DECIMAL(12,2) COMMENT '退款金额',
    currency VARCHAR(8) DEFAULT 'CNY' COMMENT '币种',
    customer_name VARCHAR(128) COMMENT '顾客名称',
    customer_phone_tail VARCHAR(16) COMMENT '顾客电话尾号',
    privacy_phone VARCHAR(64) COMMENT '隐私号码',
    backup_phone VARCHAR(64) COMMENT '备用号码',
    address TEXT COMMENT '顾客地址',
    recipient_address TEXT COMMENT '收货地址',
    delivery_type VARCHAR(64) COMMENT '配送类型',
    rider_name VARCHAR(128) COMMENT '骑手姓名',
    rider_phone VARCHAR(64) COMMENT '骑手电话',
    remark VARCHAR(512) COMMENT '备注',
    item_summary TEXT COMMENT '商品摘要',
    item_count INT COMMENT '商品数量',
    items_json TEXT COMMENT '商品明细JSON',
    raw_text TEXT COMMENT '订单卡片原始文本',
    raw_payload TEXT COMMENT '脱敏业务原始载荷JSON',
    ingest_batch_id VARCHAR(128) COMMENT '入库批次ID',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_shop_orders_platform_order (shop_id, platform, platform_order_id, deleted),
    INDEX idx_shop_orders_shop_id (shop_id),
    INDEX idx_shop_orders_user_id (user_id),
    INDEX idx_shop_orders_channel_id (channel_id),
    INDEX idx_shop_orders_platform (platform),
    INDEX idx_shop_orders_status (status),
    INDEX idx_shop_orders_completed_at (completed_at),
    INDEX idx_shop_orders_last_seen_at (last_seen_at),
    INDEX idx_shop_orders_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺订单表';

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

-- 系统参数表
CREATE TABLE IF NOT EXISTS system_parameters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT COMMENT '渠道ID',
    name VARCHAR(128) NOT NULL COMMENT '中文名称',
    code VARCHAR(128) NOT NULL COMMENT '参数编码',
    param_value VARCHAR(1024) NOT NULL COMMENT '参数值',
    description VARCHAR(512) COMMENT '说明',
    is_builtin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否内置: 0-否 1-是',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_system_parameters_channel_code_deleted (channel_id, code, deleted),
    INDEX idx_system_parameters_channel_id (channel_id),
    INDEX idx_system_parameters_code (code),
    INDEX idx_system_parameters_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数表';

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

INSERT INTO users (username, phone, password, role, channel_id, compute_balance, non_transferable_compute_balance, phone_minutes_balance, shop_count, platform_count, apk_channel, subscription_plan)
VALUES ('管理员', '13800138000', '$2y$12$xRCi/REAIr6LB5YhvqMIOeJ6aim.wGMW5l19JiJO3U8gpCjGAVssS', 'SUPER_ADMIN', (SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), 9999, 0, 0, 0, 0, 'main', 'NONE')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO system_parameters (channel_id, name, code, param_value, description, is_builtin, deleted)
VALUES
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '新用户注册赠送订阅时长', 'register.trial.subscription.days', '30', '单位：天', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '算力赠送按钮名称', 'app.menu.gift_compute.label', '算力赠送', 'APP 交易中心入口文案', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '算力取回按钮名称', 'app.menu.reclaim_compute.label', '算力取回', 'APP 交易中心入口文案', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '话费赠送按钮名称', 'app.menu.gift_phone_minutes.label', '话费赠送', 'APP 交易中心入口文案', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '话费取回按钮名称', 'app.menu.reclaim_phone_minutes.label', '话费取回', 'APP 交易中心入口文案', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '交易日志按钮名称', 'app.menu.transaction_logs.label', '交易日志', 'APP 交易中心入口文案', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '差评定位标题', 'app.shop_feature.bad_review_location.label', '差评定位', 'APP 店铺卡片操作栏标题', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '差评定位第一行内容', 'app.shop_feature.bad_review_location.line1', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '差评定位第二行内容', 'app.shop_feature.bad_review_location.line2', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '经营日报标题', 'app.shop_feature.business_report.label', '经营日报', 'APP 店铺卡片操作栏标题', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '经营日报第一行内容', 'app.shop_feature.business_report.line1', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '经营日报第二行内容', 'app.shop_feature.business_report.line2', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '外呼好评标题', 'app.shop_feature.outbound_praise.label', '外呼好评', 'APP 店铺卡片操作栏标题', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '外呼好评第一行内容', 'app.shop_feature.outbound_praise.line1', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '外呼好评第二行内容', 'app.shop_feature.outbound_praise.line2', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '评价申诉标题', 'app.shop_feature.review_appeal.label', '评价申诉', 'APP 店铺卡片操作栏标题', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '评价申诉第一行内容', 'app.shop_feature.review_appeal.line1', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '评价申诉第二行内容', 'app.shop_feature.review_appeal.line2', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '私域吸粉标题', 'app.shop_feature.private_traffic.label', '私域吸粉', 'APP 店铺卡片操作栏标题', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '私域吸粉第一行内容', 'app.shop_feature.private_traffic.line1', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0),
((SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), '私域吸粉第二行内容', 'app.shop_feature.private_traffic.line2', '-', 'APP 店铺卡片操作栏自定义内容', 1, 0)
ON DUPLICATE KEY UPDATE id=id;
