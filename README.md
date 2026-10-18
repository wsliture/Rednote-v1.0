# Rednote

Spring Boot 4 / Java 17 / Thymeleaf 全栈项目，使用 MySQL 8.4、JPA、Flyway 和本地持久化图片。

```sh
# 先启动本机 MySQL 8:3306 并创建 rednote_v1 数据库
# 本地密码配置见 docs/backend.md；默认开发账号 root
scripts/dev.sh
```

访问 [localhost:8080](http://localhost:8080/)。无预置演示账号，普通用户自行注册；管理员需显式初始化。

- [运行、配置、测试与备份恢复](docs/backend.md)
- [后端验收记录](docs/backend-verification.md)
- [产品需求](docs/prd.md)、[架构](docs/arch.md)、[数据库设计](docs/database-design.md)
- [页面与接口契约](docs/frontend.md)
