# Rednote 后端运行与维护

后端使用 Java 17、Spring Boot 4.0.8、Spring Security、Thymeleaf、Spring Data JPA、Flyway、MySQL 8.4 和本地持久化图片。Controller → Service → Repository / FileStorage 分层，`DatabaseApplication` 是页面应用接口的薄适配器。

## 本地启动

开发环境默认连接本机 MySQL 8：`127.0.0.1:3306/rednote_v1`，账号 `root`。先启动本机 MySQL 8 并创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS rednote_v1 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
```

本地密码通过被 Git 忽略的 `.env.dev.properties` 中的 `rednote.dev.db.password` 保存；也可以设置环境变量 `REDNOTE_DEV_DB_PASSWORD`。开发配置会自动读取该本地文件，IDE 直接运行主类即可连接默认本地数据库；需要关闭模板缓存时选择 `dev` profile。工作目录设为项目根目录。使用 `REDNOTE_DEV_DB_URL`、`REDNOTE_DEV_DB_USER` 可以覆盖开发连接地址和账号。

执行 `scripts/dev.sh`，访问 [首页](http://localhost:8080/)。macOS 脚本自动选择 Java 17，其他系统先设置 `JAVA_HOME`。密码不写入受版本控制的配置。


不提供默认密码、默认管理员或演示内容。首次注册会创建普通用户。开发图片目录为 `${user.home}/.rednote/uploads`，重启不会删除；Session 使用内存，重启后重新登录。

### 管理员初始化

通过环境提供 `REDNOTE_ADMIN_USERNAME`、`REDNOTE_ADMIN_PASSWORD`，仅本次启动设置 `REDNOTE_ADMIN_INITIALIZE=true`。例如在已设置这些变量后：

```sh
REDNOTE_ADMIN_INITIALIZE=true scripts/dev.sh
```

启动后移除初始化开关。重复初始化不重置已有管理员密码；同名普通用户会导致初始化失败，禁止自动升级。管理员仍无权修改他人笔记、删除他人评论或私有图片。

### 配置

| 环境变量 | 含义 |
| --- | --- |
| `REDNOTE_DB_URL` | JDBC URL；默认 `127.0.0.1:3306/rednote_v1`，UTC 连接时区 |
| `REDNOTE_DB_USER` / `REDNOTE_DB_PASSWORD` | 数据库应用凭据；默认账号 root，密码由环境或本地忽略文件提供 |
| `REDNOTE_UPLOAD_ROOT` | 外部绝对路径；prod 必填，禁止映射为公开静态目录 |
| `REDNOTE_LOG_FILE` | prod 滚动日志文件；默认 `/var/log/rednote/application.log` |
| `REDNOTE_TEST_DB_URL` / `REDNOTE_TEST_DB_USER` / `REDNOTE_TEST_DB_PASSWORD` | 独立测试库连接 |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | 可选独立迁移凭据；生产应用账号仅授予业务所需权限 |

`dev` 关闭模板缓存；`test` 使用独立测试库和临时上传目录；`prod` 启用 Secure Cookie、模板缓存及滚动日志。部署使用 HTTPS、非 root 账号和单应用实例，反向代理上传限制须大于 11 MiB。默认不信任任意转发头，代理部署时按受控网络配置。

生产启动示例：先设置数据库凭据及绝对上传路径，再执行 `java -jar target/rednote-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod`。上传目录和数据库必须跨发布保留，目录由应用运行账号独占写入。

## 数据与事务

`V1__initial_schema.sql` 管理六张表、唯一键、外键、CHECK 和索引。所有环境启用 Flyway 与 JPA `validate`；禁止 `ddl-auto=update` 和修改已执行迁移。时间使用 UTC `Instant`，看板以 Asia/Shanghai 的自然日构造查询区间。

写事务统一按用户 → 笔记 → 评论 → 文件、同类 ID 升序加锁，并在用户锁内验证认证版本。文件引用增减、清理和评论写事务使用 READ COMMITTED；回复写入在用户层按 ID 升序锁定操作者及被回复用户，覆盖外键隐式锁；其他用例默认 REPEATABLE READ。图片重排删除旧关系后 flush，再按新顺序写入。点赞目标状态幂等，创建笔记和评论失败时不自动重放。

笔记及评论软删除；根评论删除会使全部回复失效，单条回复删除保留其他回复的被回复用户。发布趋势包含后来删除的笔记，有效总量排除已删除内容。

文件 ID 是数据库正整数的十进制字符串。上传接受 JPEG、PNG、WebP，单张 **10 MiB（含边界）、4000 万像素**；实际解码校验，每次最多两张，等待超过 10 秒返回可重试冲突。前端文件名不能决定存储路径。

`STAGING → READY → DELETING → DELETED` 状态机隔离数据库事务与物理文件操作。公开头像或有效笔记引用的 READY 图片允许游客访问，否则仅上传者预览。主动删除只能删除本人无引用图片。清理默认每小时执行，连续无引用 24 小时后复核并删除，失败保留状态重试。STAGING 超过一小时且无活跃上传时回收；临时/孤立文件至少等待 24 小时，数据库异常时禁止孤立删除。

## 构建与验证

```sh
scripts/test.sh
mvn -DskipTests package
```

`test.sh` 使用本机 MySQL 客户端创建或复用独立 `rednote_test` 库，不清空现有库；测试使用随机用户名和独立临时目录。客户端不在 PATH 时设置 `MYSQL_BIN`，macOS 自动尝试 `/usr/local/mysql/bin/mysql`。测试默认连接 3306、root，密码通过环境或本地配置提供。指定 `REDNOTE_TEST_DB_URL` 时需自行预建隔离测试库。不使用 H2 替代 MySQL 并发验证。

浏览器脚本需要外部 Playwright 和 Chromium，只对独立验收库运行：

```sh
NODE_PATH=/path/to/node_modules REDNOTE_URL=http://localhost:8085 \
REDNOTE_BROWSER_PATH=/path/to/chromium node tests/browser/smoke.cjs
NODE_PATH=/path/to/node_modules node tests/browser/comments-layout.cjs
```

设置管理员环境凭据后，smoke 同时验证后台。smoke 产生 `target/browser-fixture.json`，专项评论测试复用其中随机账号。测试账号凭据只保存在被忽略的 target 目录，不是生产演示账号。

性能准备及执行：

```sh
python3 tests/performance/seed.py --database rednote_browser
python3 tests/performance/query-load.py --seconds 300 --warmup 20
```

性能 seed 仅允许独立 browser/test 库，拒绝生产数据库名；每次增加一万篇笔记、两万条点赞和三万条评论，不应重复运行来替代清理测试库。结果与执行计划输出到 target。实际结果见 [后端验收记录](backend-verification.md)。

## 健康、日志与备份恢复

`GET /actuator/health` 对游客只显示汇总状态，管理员可见数据库、上传目录可写性/磁盘余量和文件清理积压详情。其他 Actuator 端点未暴露。请求响应头包含 `X-Request-ID`，JSON 错误含同一 requestId；日志记录路径、耗时、状态、后台操作者与清理失败，不记录密码、Cookie 或请求正文。

备份必须在维护窗口执行：停止应用并等待优雅关闭，确认上传、事务和清理均已结束。使用本机 `mysqldump` 导出数据库，同时归档持久化上传目录并记录 Flyway 版本、备份编号与 SHA256；默认保留最近七份完整备份。

恢复时应用保持停止，将同一备份集导入独立空库、解压到独立空上传目录。核对表行数、READY 图片存在性和引用、迁移版本及文件摘要，设置恢复后的连接与上传路径，再启动应用检查 Flyway/JPA 和页面。不要混合不同备份批次的数据库与图片。
