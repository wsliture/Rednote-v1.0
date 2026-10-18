# Thymeleaf 前端接入与预览

本次交付覆盖用户端 9 页、后台 2 页。页面首屏由 Thymeleaf 渲染，Bootstrap 5.3.8、原生 ES modules 与图片全部由应用本地提供，无 Node 构建或 CDN 运行依赖。原型保留在 `docs/prototype/`。

## 启动与账号

要求 JDK 17、Maven。macOS 可先执行 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`，其他平台设置本机 JDK 17 路径。

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=preview
```

访问 [首页](http://localhost:8080/)。也可打包后启动：

```sh
mvn clean package
java -jar target/rednote-1.0.0-SNAPSHOT.jar --spring.profiles.active=preview
```

预览账号仅在 `preview` 中初始化，统一密码 `Preview123`：

| 用户名        | 身份                        |
| ------------- | --------------------------- |
| linxia        | 普通用户，初始笔记 1 的作者 |
| mumu / yuchen | 其他普通用户                |
| admin         | 管理员                      |
| xiaoyu        | 禁用账号，不能登录          |

内存预览支持注册、登录、上传发布、编辑删除、点赞、两层评论、资料与密码修改、后台筛选及账号状态变更。重启后全部重置。图片写入操作系统独立临时目录，正常关闭时删除；异常退出后可能残留临时目录。预置图片是公开静态素材，新上传图片未被内容引用时只有上传者可预览。

`preview` 单独排除数据库自动配置，不要求 MySQL；默认配置不启用模拟业务。预览 Controller、安全配置及内存适配器均受 profile 控制，不能将此 profile 用于生产部署。

## 页面与模块

| 地址                             | 模板                         |
| -------------------------------- | ---------------------------- |
| `/`、`/search?q=…`               | `feed.html`                  |
| `/notes/{id}`                    | `note/detail.html`           |
| `/notes/new`、`/notes/{id}/edit` | `note/editor.html`，共用表单 |
| `/login`、`/register`            | `auth/`                      |
| `/users/{id}`、`/settings`       | `user/`                      |
| `/admin`、`/admin/users`         | `admin/`                     |

- `layouts/shell.html`：用户端与后台侧栏、顶栏、手机导航、页面 head、反馈容器。
- `fragments/components.html`：头像、卡片、点赞、评论回复、字段错误、分页、空态和用户抽屉内容。
- `static/css/rednote.css`：沿用原型样式；`integration.css`：Bootstrap 兼容覆盖及新增状态。
- `static/js/app.js`：唯一公共点击委托与初始化；`pages/` 按表单、列表、详情、后台拆分；`http.js`、`ui.js`、`uploads.js`、`validation.js`、`likes.js` 提供共享行为。
- `Views`：只包含展示数据。公开作者 `Author` 与后台 `User` 分开，公开评论响应不含角色、账号状态或用户名。
- `Forms`：注册、笔记、资料与密码命令。密码命令不可放入页面模型，日志字符串已屏蔽。

页面均使用普通站内链接及上下文路径，无 Hash 导航、客户端业务数据库、localStorage 身份或演示面板。用户文本通过 `th:text` 或 `textContent` 展示；HTML 片段解析仅处理同源服务端模板输出。

## 请求契约

所有需要登录的操作使用 Session；管理员操作还须管理员角色。POST、PUT、DELETE 均需 CSRF。完整页面 head 输出令牌及请求头名称，普通表单由 Thymeleaf 自动增加隐藏字段。`http.js` 使用同源 Cookie 并读取 CSRF 请求头。

### 普通表单

| 方法与地址                | 字段                                     | 成功                                           |
| ------------------------- | ---------------------------------------- | ---------------------------------------------- |
| POST `/login`             | username、password、可选 returnTo        | 303 → 安全站内 GET 地址，默认首页              |
| POST `/register`          | username、password、confirm              | 303 → `/login?registered`                      |
| POST `/notes`             | title、body、重复 images 图片标识        | 303 → 新笔记详情                               |
| POST `/notes/{id}`        | title、body、version、重复 images        | 303 → 原笔记详情                               |
| POST `/settings/profile`  | name、bio、avatar 图片标识或现有头像地址 | 303 → 本人主页                                 |
| POST `/settings/password` | old、password、confirm                   | 所有旧会话失效，303 → `/login?passwordChanged` |
| POST `/logout`            | CSRF                                     | 清会话，302 → 首页                             |

无 JavaScript 时，字段失败返回原表单页并回显非敏感输入。JavaScript 渐进增强提交携带 `X-Rednote-Form: 1`，失败返回下述 JSON 并保留当前 DOM；成功仍走服务端 PRG，再导航至最终 GET 地址。不会重新拼装整页或自动重发创建请求。

用户文本校验按 Unicode code point 计数，标题、正文、昵称、简介、评论先 trim；密码不 trim。用户名使用独立格式约束。详情编辑提交版本用于检测旧版本覆盖。

### 局部 HTML

| GET 地址                       | 参数与输出                                                                                                 |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------- |
| `/fragments/feed`              | q、cursor；最多 20 卡片；包装节点 `data-next`、`data-end`                                                  |
| `/fragments/comments/{noteId}` | root=0 表示主评论，其他为根评论 ID；page 从 0 开始，每批最多 20 条；包装节点含 data-total 和 data-has-more |
| `/fragments/admin/dashboard`   | 最新统计、趋势及统计时间 HTML，失败时保留当前数据                                                          |
| `/fragments/admin/users/{id}`  | 仅管理员，抽屉展示资料，不含密码或认证信息                                                                 |

首页/搜索首次读取 21 条判断是否还有下一批，返回最多 20 条。游标绑定关键词、首批 ID 上界、最后发布时间与 ID；前端追加按 ID 去重。新发布内容刷新后进入列表。主页和后台使用 `page=0` 起始的页码，每页 20 条；后台保留 username、name、status 查询条件。

### JSON 与文件

| 方法与地址                             | 输入                             | 成功输出                              |
| -------------------------------------- | -------------------------------- | ------------------------------------- |
| PUT `/actions/notes/{id}/like`         | `{liked: boolean}`，设置目标状态 | `{liked, likes}`，重复请求幂等        |
| POST `/actions/notes/{id}/comments`    | `{text, target?: commentId}`     | `Comment` 展示 DTO，回复平铺归根      |
| DELETE `/actions/comments/{id}`        | 无                               | `{success: true}`，根评论级联删除回复 |
| DELETE `/actions/notes/{id}`           | 无                               | `{success: true}`                     |
| POST `/actions/files`                  | multipart 单文件字段 `file`      | `{id, url}`，逐张上传                 |
| PUT `/actions/admin/users/{id}/status` | `{status: "正常" 或 "禁用"}`     | 后台 `User` DTO                       |
| GET `/preview/files/{key}`             | 预览上传图片标识                 | 图片流、实际 MIME、no-store、nosniff  |

错误结构为 `{code, message, fields?}`。400 输入错误、401 认证失效、403 权限或 CSRF、404 内容不可用、409 状态/编辑版本冲突、413 文件超限、415 图片类型/解码失败。未预期服务错误由 Spring 返回 500，客户端展示失败，不假报成功。未来真实业务按架构补齐请求追踪 ID。

图片支持 JPEG、PNG、WebP，10 MiB 单文件、11 MiB multipart 请求上限。使用 ImageIO 与 TwelveMonkeys WebP SPI 检查可解码性。缩略图只保存服务端返回的图片 ID，blob URL 只用于上传过程预览。上传失败逐张重试，上传中或失败项未移除时不能提交。

## 接入真实后端

1. 为 `RednoteApplicationPort` 提供真实 Service 适配器，沿用 `Views`、`Forms`、路径和响应契约；Controller 不查询 Repository，不向模板传 Entity。
2. 替换 `PreviewSecurity`，使用 `SessionUser` 的 ID、角色、认证版本；登录后擦除密码摘要，生产适配器继续负责账号状态与跨会话版本检查。
3. 将 Controller 的 preview profile 限制调整为生产功能配置，同时提供真实适配器。不要直接打开预览存储。文件读取需替换预览地址，并由受控服务提供。
4. 按 PRD 实现 MySQL 事务、行锁/唯一约束、图片引用与 24 小时回收、安全解码资源预算、持久化和备份；内存 synchronized 仅用于演示一致性。
5. 继续完成架构规定的真实 MySQL、安全并发、性能和恢复验收。本次测试不能代替这些验收。

## 验证

```sh
mvn test
```

自动集成测试启动真实嵌入式 HTTP 服务，验证模板渲染、角色访问、CSRF、游标、转义、字段失败回显、点赞幂等及禁用后的旧会话失效。

浏览器脚本不增加应用运行依赖。外部提供 Playwright 和 Chromium 后执行：

```sh
NODE_PATH=/path/to/node_modules \
REDNOTE_URL=http://localhost:8080 \
REDNOTE_BROWSER_PATH="/path/to/chromium" \
node tests/browser/smoke.cjs
```

可设置 `REDNOTE_SCREENSHOTS` 为已存在目录，保存截图。脚本覆盖 11 页 × 1440/768/375px、分页、上传发布编辑删除、评论回复、登录返回、资料、注册改密与后台操作，并检查页面溢出、脚本错误和外部请求。

实际执行环境与结果见 [frontend-verification.md](frontend-verification.md)。

### 评论分页与布局回归

`Views.CommentPage` 返回 `items`、`hasMore` 与全笔记评论总量 `total`。主评论和回复均读取 21 条判断后续页，只展示 20 条；空态由详情页评论容器持有，分页片段不输出空态。刷新、发表与删除后统一更新计数、空态和加载按钮。

专项浏览器回归：`node tests/browser/comments-layout.cjs`，沿用上文的 `NODE_PATH`、`REDNOTE_URL`、`REDNOTE_BROWSER_PATH` 和 `REDNOTE_SCREENSHOTS` 环境变量。覆盖导航对齐、输入区间距、零评论、删除最后一条、20/21 条主评论及回复、末页重复点击和三种视口。
