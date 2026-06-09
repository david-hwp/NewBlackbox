INSERT INTO channels (code, name, status, app_display_name, app_application_id, engine_display_name, engine_application_id, register_bonus_compute, deleted)
VALUES ('main', '默认渠道', 'ACTIVE', '账号管家', 'com.zhirang.zhanghaoguanjia', '账号管家引擎', 'com.zhirang.zhanghaoguanjia.engine', 3, 0)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO users (id, username, phone, password, role, channel_id, compute_balance, shop_count, platform_count, apk_channel)
VALUES (1, '管理员', '13800138000', '$2y$12$xRCi/REAIr6LB5YhvqMIOeJ6aim.wGMW5l19JiJO3U8gpCjGAVssS', 'SUPER_ADMIN', (SELECT id FROM channels WHERE code = 'main' AND deleted = 0 LIMIT 1), 200, 6, 4, 'main');
