-- 多店管家后台数据库初始化脚本
-- 运行前请先创建数据库: CREATE DATABASE duodian_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;

USE duodian_admin;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    avatar_url VARCHAR(512) COMMENT '头像URL',
    phone VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号',
    password VARCHAR(128) NOT NULL COMMENT '密码',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
    compute_balance INT NOT NULL DEFAULT 0 COMMENT '算力余额',
    non_transferable_compute_balance INT NOT NULL DEFAULT 0 COMMENT '不可转赠算力余额',
    shop_count INT NOT NULL DEFAULT 0 COMMENT '店铺数量',
    platform_count INT NOT NULL DEFAULT 0 COMMENT '覆盖平台数',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at DATETIME COMMENT '最后登录时间',
    INDEX idx_phone (phone),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 店铺表
CREATE TABLE IF NOT EXISTS shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
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
    INDEX idx_platform (platform),
    INDEX idx_deleted (deleted),
    INDEX idx_clone_instance_id (clone_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

-- 算力扣费幂等表
CREATE TABLE IF NOT EXISTS compute_deductions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
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
    INDEX idx_clone_instance_id (clone_instance_id),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算力扣费幂等表';

-- 交易日志表
CREATE TABLE IF NOT EXISTS transaction_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '所属用户ID',
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
    INDEX idx_type (type),
    INDEX idx_deleted (deleted),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易日志表';

-- 公告表
CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL COMMENT '公告标题',
    content VARCHAR(4000) NOT NULL COMMENT '公告内容',
    type VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '公告类型: NORMAL/APP_RELEASE',
    published TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否发布',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_published (published),
    INDEX idx_deleted (deleted),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- 引擎版本表
CREATE TABLE IF NOT EXISTS engine_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_code INT NOT NULL COMMENT '版本号',
    version_name VARCHAR(64) NOT NULL COMMENT '版本名称',
    apk_url VARCHAR(512) NOT NULL COMMENT '引擎APK下载地址',
    checksum VARCHAR(64) COMMENT 'APK校验值',
    changelog TEXT COMMENT '更新日志',
    available TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可用',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_available (available),
    INDEX idx_deleted (deleted),
    INDEX idx_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引擎版本表';

-- 主APK版本表
CREATE TABLE IF NOT EXISTS app_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version_code INT NOT NULL COMMENT '版本号',
    version_name VARCHAR(64) NOT NULL COMMENT '版本名称',
    apk_url VARCHAR(512) NOT NULL COMMENT '主APK下载地址',
    checksum VARCHAR(64) COMMENT 'APK校验值',
    file_size BIGINT COMMENT '文件大小，单位字节',
    changelog TEXT COMMENT '更新日志',
    published TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否发布',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0-正常 1-已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_published (published),
    INDEX idx_deleted (deleted),
    INDEX idx_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主APK版本表';

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
    INDEX idx_status (status),
    INDEX idx_deleted (deleted),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题反馈表';

-- 插入默认管理员账号，密码使用 BCrypt 密文存储
INSERT INTO users (username, phone, password, role, compute_balance, non_transferable_compute_balance, shop_count, platform_count)
VALUES ('管理员', '13800138000', '$2y$12$xRCi/REAIr6LB5YhvqMIOeJ6aim.wGMW5l19JiJO3U8gpCjGAVssS', 'ADMIN', 9999, 0, 0, 0)
ON DUPLICATE KEY UPDATE id=id;
