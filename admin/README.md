# 多店管家 - 后台管理系统

技术栈：Vue3 + Element Plus + Spring Boot + MySQL

---

## 项目结构

```
admin/
├── backend/          # Spring Boot 后端
│   ├── src/main/java/com/duodian/admin/
│   │   ├── AdminApplication.java          # 启动类
│   │   ├── config/                        # 配置类（CORS、全局异常）
│   │   ├── controller/                    # REST API 控制器
│   │   │   ├── dto/                       # 请求/响应 DTO
│   │   ├── service/                       # 业务逻辑层
│   │   ├── entity/                        # JPA 实体类
│   │   └── repository/                    # Spring Data JPA 仓库
│   ├── src/main/resources/
│   │   ├── application.properties         # 配置（含MySQL连接）
│   │   └── schema.sql                     # 数据库初始化脚本
│   └── pom.xml
│
└── frontend/         # Vue3 前端
    ├── src/
    │   ├── views/
    │   │   ├── Login.vue      # 登录页
    │   │   ├── Layout.vue     # 后台布局框架
    │   │   ├── Dashboard.vue  # 概览仪表盘
    │   │   ├── Users.vue      # 用户管理
    │   │   ├── Shops.vue      # 店铺管理
    │   │   └── Logs.vue       # 交易日志
    │   ├── router/index.js    # 路由配置
    │   ├── utils/request.js   # Axios 封装
    │   ├── App.vue
    │   └── main.js
    ├── index.html
    ├── package.json
    └── vite.config.js
```

---

## 快速启动

### Docker 一键部署

```bash
cd admin
./deploy.sh
```

默认访问地址：`http://localhost:8006`

默认只暴露一个宿主机端口：

| 服务 | 宿主机端口 |
|------|------------|
| 前端 Nginx | `8006` |

后端接口通过前端 Nginx 的 `/api` 代理转发，MySQL 和后端不暴露到宿主机。

宿主机挂载目录：

| 类型 | 路径 |
|------|------|
| 前后端日志 | `~/dianpuguanjia/logs` |
| 上传文件 | `~/dianpuguanjia/files` |
| 数据库备份 | `~/dianpuguanjia/db_backup` |

如需修改端口或数据库密码，可复制 `.env.example` 为 `.env` 后调整。
数据库备份默认在容器启动时执行一次，之后每 86400 秒执行一次，最多保留 30 个备份。

`deploy.sh` 会在宿主机本地执行后端 `mvn package` 和前端 `npm run build`，Docker 容器只负责运行已构建产物和 MySQL。若产物已经存在，也可以直接执行：

```bash
cd admin
docker compose up -d --build
```

### 1. 初始化数据库

```bash
# 登录 MySQL 创建数据库
mysql -u root -p

# 执行 SQL
CREATE DATABASE duodian_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE duodian_admin;
SOURCE admin/backend/src/main/resources/schema.sql;
```

### 2. 启动后端

```bash
cd admin/backend

# 编译运行
mvn spring-boot:run

# 或先打包再运行
mvn clean package
java -jar target/admin-backend-1.0.0.jar
```

后端默认端口：`http://localhost:8080/api`

### 3. 启动前端

```bash
cd admin/frontend

# 安装依赖
npm install

# 开发模式启动
npm run dev
```

前端默认端口：`http://localhost:5173`

---

## 默认账号

| 字段 | 值 |
|------|-----|
| 手机号 | `13800138000` |
| 密码 | `admin123` |
| 角色 | ADMIN |

---

## API 文档

### 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users` | 用户列表 |
| GET | `/api/users/{id}` | 用户详情 |
| POST | `/api/users` | 创建用户 |
| PUT | `/api/users/{id}` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |
| POST | `/api/users/login` | 登录 |

### 店铺管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/shops` | 店铺列表（支持 `?userId=` `?platform=` 过滤）|
| GET | `/api/shops/{id}` | 店铺详情 |
| POST | `/api/shops` | 创建店铺 |
| PUT | `/api/shops/{id}` | 更新店铺 |
| DELETE | `/api/shops/{id}` | 删除店铺 |

### 交易日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/logs` | 日志列表（支持 `?userId=` `?type=` 过滤）|
| GET | `/api/logs/{id}` | 日志详情 |
| POST | `/api/logs` | 创建日志 |
| DELETE | `/api/logs/{id}` | 删除日志 |

---

## 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 登录 | `/login` | 手机号+密码登录 |
| 概览 | `/dashboard` | 统计卡片 + 平台分布 + 最近交易 |
| 用户管理 | `/users` | 增删改查用户 |
| 店铺管理 | `/shops` | 增删改查店铺（支持平台筛选）|
| 交易日志 | `/logs` | 按类型筛选 + 增删记录 |
