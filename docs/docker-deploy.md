# Docker Compose 部署

适用于单服务器、单应用实例、本机或内网 HTTP、全新数据库。前端是随 JAR 发布的 Thymeleaf 页面，没有 Node 构建步骤。Compose 启动 MySQL 和应用两个服务，不导入本机开发数据。

## 1. 准备与首次启动

服务器安装 Docker Engine / Docker Desktop 与 Docker Compose v2（建议 2.33 或更新），启动 Docker 引擎；安装 Bash、OpenSSL、tar、shasum（Linux 通常由 perl 包提供）。建议至少 2 核、4 GiB 内存和足够的持久化磁盘，上传卷剩余空间低于 100 MiB 时健康检查失败。构建需要联网拉取镜像和 Maven 依赖，宿主机无需 Java、Maven 或 MySQL。

在项目根目录执行：

```sh
scripts/docker.sh init
# 按需修改 deploy/docker/deploy.env，例如端口或绑定地址
scripts/docker.sh up
scripts/docker.sh status
```

访问 `http://localhost`；内网其他设备访问 `http://服务器IP`，需要宿主机防火墙放行该端口。默认绑定 `0.0.0.0`，仅本机使用时改为 `127.0.0.1`。全新安装时普通用户自行注册，没有预置账号、笔记和管理员。

`init` 以仅当前用户可读写的权限生成两个独立随机数据库密码，已有文件不会覆盖。`deploy/docker/deploy.env.example` 是配置模板，手动复制时必须自行填写两个密码。不要提交实际环境文件或输出含凭据的 `compose config`。

所有脚本显式使用 `deploy/docker/deploy.env`，不执行该文件，也不读取开发 `.env`。可用绝对路径指定其他环境文件：

```sh
REDNOTE_DEPLOY_ENV=/absolute/path/deploy.env scripts/docker.sh status
```

环境变量优先于配置文件；避免终端里遗留同名变量。密码建议保持随机十六进制；手动填写含 `$` 的值需遵循 Compose 单引号规则。Compose 插值规则见 [Docker 官方文档](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)。

## 2. 配置与持久化

| 配置 | 默认值 / 用途 |
| --- | --- |
| `REDNOTE_PROJECT_NAME` | `rednote`，决定网络和卷名称；上线后不要随意修改 |
| `REDNOTE_IMAGE_TAG` | `local`，应用构建标签；发布建议设为代码版本号 |
| `REDNOTE_BIND_ADDRESS` / `REDNOTE_HTTP_PORT` | `0.0.0.0` / `80` |
| `REDNOTE_DB_BIND_ADDRESS` / `REDNOTE_DB_PORT` | `127.0.0.1` / `3306`；公网连接显式改为 `0.0.0.0` 并放行安全组 |
| `REDNOTE_DB_PASSWORD` | 专用 `rednote` 数据库账号密码，必填 |
| `MYSQL_ROOT_PASSWORD` | 数据库维护账号密码，必填，仅传给数据库容器 |
| `REDNOTE_COOKIE_SECURE` | `false`，HTTP 模式；HTTPS 必须改为 `true` |
| `REDNOTE_FORWARD_HEADERS_STRATEGY` | `none`；受控 HTTPS 代理见下文 |
| `REDNOTE_IMAGE_PREFIX` | 空；可设置可信镜像源前缀，须以 `/` 结尾 |
| `REDNOTE_APP_MEMORY_LIMIT` / `REDNOTE_DB_MEMORY_LIMIT` | `1536m` / `512m`；2 GiB 主机将应用限制改为 `1024m` |
| `REDNOTE_JAVA_OPTIONS` | `-XX:MaxRAMPercentage=65.0`；堆最多使用容器内存的 65% |

构建基础镜像固定为 `maven:3.9.9-eclipse-temurin-17`、`eclipse-temurin:17.0.18_8-jre-jammy`，数据库为 `mysql:8.4.8`。标签不等于不可变摘要；正式发布应记录 `docker image inspect` 的镜像 ID / RepoDigests，按安全更新计划验证并升级版本。支持宿主机原生 amd64 / arm64，不强制模拟架构。

应用以 UID/GID `10001:10001` 运行。镜像预建并设置上传、日志目录权限，新命名卷由 Docker 初始化内容和权限。已有外部卷需要管理员检查所有权，不自动递归修改历史文件。卷为：

- `<项目名>_mysql-data`：MySQL 数据。
- `<项目名>_uploads`：上传图片，容器内 `/var/lib/rednote/uploads`。
- `<项目名>_app-logs`：应用滚动日志，容器内 `/var/log/rednote`。

应用容器内的 8080 端口映射到宿主机 80 端口；MySQL 在 Compose 网络中通过 `db:3306` 访问，默认也映射到宿主机 `127.0.0.1:3306`；公网开放设置见服务器记录。数据库专用账号拥有 `rednote` 库权限，供业务和 Flyway 使用，不具有服务器级 root 权限。数据库结构由现有 Flyway 自动迁移，JPA 仅校验。

`prod,docker` 组合保留模板缓存和文件滚动日志，禁用本地开发凭据导入；HTTP 模式关闭 Secure Cookie。上传目录不直接暴露为静态目录。MySQL 查询通过后才启动应用，详见 [Compose 健康依赖说明](https://docs.docker.com/compose/how-tos/startup-order/)。

## 3. 管理员、运行与升级

先成功启动应用，再交互创建管理员：

```sh
scripts/docker-admin.sh
```

脚本停止常规应用，使用同一镜像和数据卷临时启动初始化容器；成功或失败后删除临时容器并恢复常规应用。凭据不写入配置文件或命令行参数，但容器运行期间仍可由 Docker 管理员检查环境变量。用户名为 4–20 位字母、数字或下划线，密码遵循应用规则。已有管理员不会重置密码，同名普通用户会失败，不会升级权限。初始化期间有维护停机，请勿并行执行发布、备份或其他初始化。

```sh
scripts/docker.sh logs                       # 应用及数据库最近日志，Ctrl-C 退出
scripts/docker.sh compose logs --tail 100 app
scripts/docker.sh stop                       # 优雅停止，保留容器与卷
scripts/docker.sh up                         # 构建并等待健康
scripts/docker.sh down                       # 删除容器与网络，保留命名卷
scripts/docker.sh compose exec -T app curl -fsS http://localhost:8080/actuator/health
```

应用日志文件滚动保留七天，Docker 标准输出日志限制为每容器三份、每份 10 MiB。应用优雅关闭等待 45 秒，容器停止宽限为 60 秒。Session 保存在内存，重启后需要重新登录。健康检查失败会标记 unhealthy，Docker 不会仅因 unhealthy 自动重启，需检查告警或人工处理；进程退出按 `unless-stopped` 重启。

更新前执行配套备份、记录旧代码版本和镜像 ID；更新源码，设置新的 `REDNOTE_IMAGE_TAG`，执行 `scripts/docker.sh up`。Flyway 或 JPA 校验失败应检查日志，不要修改已执行迁移，也不要删除卷重试。含不兼容数据库迁移的版本不能只换旧镜像回退，需要恢复同一发布前备份集。

数据库初始化变量只在空数据目录首次初始化时生效，修改环境文件不会修改现有数据库密码，参见 [MySQL 官方镜像说明](https://hub.docker.com/_/mysql/)。轮换密码时先在数据库中执行相应 `ALTER USER`，同步环境文件，再重建相关容器；不要把改配置误当作改数据库。

**不要执行 `docker compose down -v` 或删除持久化卷，除非明确要永久清空数据。** 不要改变项目名后误以为旧数据消失，旧卷仍属于原项目名。

### 3.1 日常代码增量更新（本地改代码 → 当前服务器）

本文中的 `server.example.com` 为占位地址，请替换为自己的服务器域名或 IP；文档不记录实际公网地址。

在本地项目根目录修改并验证代码后，执行：

```sh
scripts/docker-remote.sh root@server.example.com /opt/rednote update
```

SSH/SCP 提示时输入服务器登录密码。此命令会使用当前工作目录中的源码，包含尚未提交 Git 的修改；不会自动执行 git pull。请先完成与本次修改相关的测试，再发布。

一次更新依次完成：

1. 使用服务器当前配置执行配套备份；失败立即停止更新，不上传覆盖源码。
2. 上传源码与部署文件，保留服务器的 `deploy/docker/deploy.env`、数据库密码、镜像源和内存设置。替换整个源码 `src` 目录，因此本地删除或重命名的 Java 类、模板及静态文件不会在服务器残留。
3. 给当前应用镜像增加 `rednote-app:rollback-时间-进程号` 标签，并在终端打印，供回退使用。
4. 复用 Docker 构建缓存，仅构建应用；构建成功后才替换应用容器，等待健康检查通过。数据库容器和持久化卷保持原样。

这里“增量”指 Docker 层缓存和仅替换应用容器，源码压缩包仍完整上传。只修改 Java、HTML、CSS、JS 时会重新打包 JAR，Maven 依赖层通常复用；修改 `pom.xml` 会重新解析依赖，修改 Dockerfile 可能重建相关镜像层。不是热更新，也不会在运行容器中直接覆盖文件。

每次备份有一个短暂停机窗口，替换应用容器时也有短暂中断，Session 会失效，需要重新登录。不要并行执行发布、备份或管理员初始化。不需要重新 `init`、创建管理员、删除卷或重新初始化数据库；用户、笔记和图片会保留。

**首次部署继续用原命令（不带 update），日常更新使用上面的 update 命令。**

### 3.2 代码已经更新到服务器时

如果通过 Git 或其他方式自行更新了 `/opt/rednote` 的源码，在服务器执行：

```sh
cd /opt/rednote
scripts/docker-backup.sh
# 上一条成功后继续；若放在脚本中，使用 && 连接保证失败时停止
scripts/docker.sh update
scripts/docker.sh status
scripts/docker.sh compose logs --tail 100 app
curl -fsS http://127.0.0.1/actuator/health
```

`update` 要求现有数据库健康运行；先保留旧镜像、完成新镜像构建，再仅替换应用。若应用构建失败，旧应用继续运行；若新应用启动或健康检查失败，命令返回失败，查看日志后按下文处理。命令不自动回退，避免掩盖数据库迁移结果。

配置文件与更新场景的对应方式：

| 修改内容 | 更新方式 |
| --- | --- |
| Java、模板、CSS、JS、`pom.xml` | 本地远程 `update`，或服务器 `docker.sh update` |
| 应用环境变量 | 编辑服务器 `deploy.env`，执行 `scripts/docker.sh update`；仅 restart 不会加载新环境变量 |
| Compose 中数据库端口、内存、MySQL 版本等 | 维护窗口备份后执行 `scripts/docker.sh up`，使数据库配置也生效；先阅读对应版本升级要求 |
| 数据库表结构 | 新增 Flyway 迁移并随应用更新，不能修改已执行迁移；上线前演练迁移和恢复 |

日常更新不会为已有 `deploy.env` 自动追加新配置。新增必填环境变量需要先在服务器文件中填写；本地开发 `.env` 不会被上传。

### 3.3 更新失败与镜像回退

先查看 `scripts/docker.sh compose logs --tail 150 app`。构建失败可修复本地代码后重新执行远程更新；数据库连接、Flyway/JPA 校验失败时不要删卷重试。

**仅当新旧代码兼容当前数据库结构时**，使用刚才终端打印的旧镜像标签回退。以下标签为示例，需替换为实际值，在服务器执行：

```sh
cd /opt/rednote
export REDNOTE_IMAGE_TAG=rollback-20261019T140000Z-1234
scripts/docker.sh compose up -d --no-deps --no-build --wait --wait-timeout 300 app
unset REDNOTE_IMAGE_TAG
curl -fsS http://127.0.0.1/actuator/health
```

命令必须保留 `--no-build`，否则可能用新源码覆盖回退镜像。回退只切换应用镜像，不恢复源码或环境文件；若也修改了应用配置，需要恢复对应旧配置。回退后先在本地修复或切回正确代码版本，再进行下一次发布；不要直接执行 `up` 把尚未修复的新源码重新构建上线。旧镜像应在确认新版本稳定后再手动清理，不要提前 prune。

若新版本已执行不兼容的数据迁移，不能只退镜像；停止应用，按第 4 节恢复同一发布前备份集。恢复到备份时刻会丢失该时刻之后的写入，先保留现场数据并明确恢复范围。

## 4. 配套备份与恢复

备份包含数据库、上传图片、Flyway 版本、镜像列表和 SHA256：

```sh
scripts/docker-backup.sh
# 可选独立备份磁盘
REDNOTE_BACKUP_ROOT=/absolute/path/backups scripts/docker-backup.sh
```

脚本要求服务可健康启动，然后停止应用并等待优雅关闭，保持数据库运行，导出数据库与图片后恢复应用。备份期间不得有其他应用或数据库客户端写入。仅完整备份成功后保留最近七份；失败目录以 `.incomplete-` 开头，不计入成功备份，不自动删除诊断。备份是明文敏感数据，应加密并复制到服务器以外的受控存储。脚本不自动建立定时任务，可由运维调度每日维护窗口运行。

恢复必须在独立空数据库及空上传卷中演练，不要向正在运行的生产库覆盖导入。以下 Bash 命令使用新的 Compose 项目名创建隔离卷；先核实备份路径和新项目名，并使用与备份兼容的代码、镜像：

```sh
BACKUP=/absolute/path/backups/20261019T000000Z-1234
(cd "$BACKUP" && shasum -a 256 -c SHA256SUMS)
# 上一步全部 OK 后继续；新项目名必须从未使用过
export REDNOTE_PROJECT_NAME=rednote-restore-20261019
export REDNOTE_HTTP_PORT=18080
export REDNOTE_BIND_ADDRESS=127.0.0.1
export REDNOTE_DB_BIND_ADDRESS=127.0.0.1
export REDNOTE_DB_PORT=13306
scripts/docker.sh compose up -d --wait db
scripts/docker.sh compose exec -T db sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"' \
  < "$BACKUP/database.sql"
scripts/docker.sh compose run --rm --no-deps -T --entrypoint tar app \
  -C /var/lib/rednote/uploads -xzf - < "$BACKUP/uploads.tar.gz"
scripts/docker.sh compose up -d --wait --wait-timeout 300 app
scripts/docker.sh status
```

恢复命令不启动第二个连接原数据库的应用；新项目拥有独立网络和卷。仅恢复可信备份。tar 以应用 UID 解压，图片保持应用可读写。不要从其他备份集单独替换数据库或图片。

恢复后对比 `flyway.tsv` 与恢复库迁移历史，核对用户、笔记、评论、文件表行数；检查 READY 文件对应的存储文件及有效图片引用是否完整，登录并抽查头像、历史笔记图片、上传和评论。核对方式可通过只读 SQL、应用页面及上传卷检查完成。验证通过后再安排正式切换端口/入口；原部署先停止，保留原卷作为回退依据。退出恢复环境时执行 `unset REDNOTE_PROJECT_NAME REDNOTE_HTTP_PORT REDNOTE_BIND_ADDRESS REDNOTE_DB_BIND_ADDRESS REDNOTE_DB_PORT`，避免后续命令操作错项目。

## 5. HTTPS 与故障排查

公网使用时需先部署可信 HTTPS 反向代理，本交付不自动申请证书。宿主机代理部署时，将应用绑定地址改为 `127.0.0.1`，设置 `REDNOTE_COOKIE_SECURE=true`、`REDNOTE_FORWARD_HEADERS_STRATEGY=framework` 后重建应用。代理必须覆盖客户端传入的转发头，设置真实的 `Host`、`X-Forwarded-Proto`、`X-Forwarded-For`，且确保客户端不能绕过代理直连应用，否则不要开启转发头处理。

Nginx 建议设置 `client_max_body_size 12m`（大于应用 11 MiB multipart 上限），适当增加读取超时，并透传请求 ID；应用保持 10 MiB 单图片限制。HTTPS 下验证登录成功、重定向协议正确、Cookie 含 Secure。不要用 HTTP 测试已开启 Secure Cookie 的登录。

常见问题：

- Docker daemon 无法连接：先启动 Docker Desktop / Engine，检查 `docker info`。
- 构建拉取失败：检查镜像仓库和 Maven 网络；不要换用未经验证的数据库或 Java 主版本。
- 数据库 unhealthy：检查数据库日志、磁盘、实际账号密码；已有卷不会重新应用初始化密码。
- 应用 unhealthy：检查迁移、数据库连接、上传目录 UID 和磁盘余量；启动慢可调整健康等待时间。
- HTTP 登录循环：确认 `REDNOTE_COOKIE_SECURE=false`，修改配置后重建应用，不能只执行 restart。
- 上传 413：核对文件不超过 10 MiB、请求不超过 11 MiB，以及外部代理限制。
- 端口冲突或云防火墙未放行：默认使用宿主机 80，需云安全组允许 TCP 80；修改 `REDNOTE_HTTP_PORT`；同一项目保持一个应用实例，不使用 scale 扩容。

## 6. 远程部署与服务器记录

已安装 Docker 并配置 SSH 登录的服务器可用以下脚本上传当前源码并部署：

```sh
scripts/docker-remote.sh root@server.example.com /opt/rednote
```

脚本通过 SSH/SCP 交互认证或复用密钥（可选 `REDNOTE_SSH_CONTROL_PATH` 指定已建立的 ControlMaster socket），不保存服务器密码；打包排除本地部署凭据及 macOS 扩展属性，保留远端已有 `deploy.env`。首次上传后自动生成随机数据库密码。需要定制镜像源和内存时，先在服务器初始化并编辑部署环境文件，再执行部署。不要将服务器 root 登录凭据提交到仓库。

本次服务器为 OpenCloudOS 9.6 / amd64，约 2 GiB 内存、1 GiB swap；项目目录 `/opt/rednote`，使用系统仓库安装 Docker Engine 29.7.2、Compose 5.4.0，并启用 Docker 开机启动。系统原有服务保留。安装命令（仅适用于本次系统）：

```sh
dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
```

其他系统按 [Docker 官方安装说明](https://docs.docker.com/engine/install/) 配置支持的仓库。

服务器直连 Docker Hub 超时，当前部署设置 `REDNOTE_IMAGE_PREFIX=mirror.ccs.tencentyun.com/library/`；构建和 MySQL 都从该腾讯云镜像源拉取。镜像源必须由部署者信任且提供选定标签。服务器 Docker daemon 也配置了腾讯云镜像加速。2 GiB 配置使用应用 `1024m`、数据库 `512m`；MySQL 缓冲池限制为 128 MiB、连接数 50，关闭 performance schema。构建阶段 Maven 最大堆限制为 384 MiB。课程低并发可运行，正式容量仍需压测，建议 4 GiB 以上。

服务器入口为 `http://server.example.com/`，宿主机 80 → 容器 8080，MySQL 按后续要求映射到公网 `server.example.com:3306`（`REDNOTE_DB_BIND_ADDRESS=0.0.0.0`、`REDNOTE_DB_PORT=3306`）。远程使用 `rednote` 账号连接 `rednote` 库，root 不额外开放远程授权；云安全组需要放行 TCP 3306。本次通过一次性脚本创建管理员 `rednote_admin`，随机凭据仅存服务器 `/root/rednote-admin-credentials.json`（权限 600），未写入仓库。首次使用后可通过设置页面修改密码。

此前 8080 被云防火墙阻止，保持默认 80 并在安全组放行 TCP 80；已有环境文件须同步修改 `REDNOTE_HTTP_PORT=80`，然后执行 `scripts/docker.sh up`。

## 7. 交付验证记录

2026-10-19 在服务器完成以下验证：

- 镜像实际构建、MySQL 空卷初始化、Flyway/JPA 启动校验通过；应用 UID/GID 为 `10001:10001`。
- 公网 80 首页返回 HTTP 200，游客健康汇总 UP，静态 CSS 可读取。
- 后续开放公网 MySQL `server.example.com:3306`，已从本机通过 MySQL 客户端使用 `rednote` 账号登录并成功执行查询；应用重启后健康状态仍为 UP。
- 注册、HTTP Cookie 登录、CSRF 拒绝及合法提交、JPEG 上传、笔记发布、评论和点赞通过。
- 强制重建应用容器后，原账号可登录、原笔记存在、图片 SHA256 与原文件一致。
- 一次性管理员初始化通过；后台、用户管理及管理员健康详情可访问，初始化容器已删除。
- 配套备份 `/opt/rednote/backups/20261019T135105Z-18966` 完成，SHA256 校验通过；在隔离项目 `rednote-restore-20261019-1351` 的空数据库和空上传卷中恢复并启动成功。
- 恢复环境原账号可登录、原笔记及图片可读取；六张业务表行数、迁移版本、READY 文件存在性和全部文件摘要与原部署一致。演练结束后删除隔离项目及其测试卷，保留正式数据和备份。

验收使用 `tests/deployment/smoke.py`（Python 标准库），会创建一个随机普通用户和一篇测试笔记；不要对已有业务环境随意运行。验收状态和随机凭据保存在被忽略的 `target/docker-smoke.json`，测试笔记清理后随机用户仍保留。分阶段命令如下：

```sh
python3 tests/deployment/smoke.py create http://localhost
# 重建应用或恢复备份后
python3 tests/deployment/smoke.py verify http://localhost
python3 tests/deployment/smoke.py cleanup http://localhost
```

新增增量更新命令通过 Bash 语法和模拟命令验证：构建先于容器替换、构建失败不替换应用、旧镜像保留、远程先备份后上传、部署凭据不进入上传包。增量命令和本节文档已同步服务器；此次仅更新脚本与文档，未为验证而重新发布业务应用。

同时通过 Compose 静态解析、Bash 语法检查、`git diff --check`、Maven 打包。以上是部署功能验收，不包含生产容量压测或所有既有集成测试。
