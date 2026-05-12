-- V9: PR A3 用例契约字段扩充
--
-- 注：本迁移在 PR #37 中原编号 V8，但 PR #36（B-ii 邮件 Inject）先于本 PR 合入 main
-- 占用了 V8__smtp_profiles.sql，故合并后顺延为 V9 以避免 Flyway 版本冲突。
-- 列名 / 表名不受影响，Java / 前端引用无需调整.
--
-- 为 injectors_contracts 表新增 6 个用例契约扩展列，承载 IPv6 安全验证系统招标
-- §3.4 / §5.1 / §6.1 / §6.2 所需的契约元数据：
--   - software_category      软件分类（web_component / security_product / application 等 8 类）
--   - defense_layer          防御层级（boundary / traffic / application / host / data 5 层）
--   - network_protocol_family 网络协议族（ipv4 / ipv6 / both）
--   - target_os              目标操作系统（linux / windows / both / none）
--   - network_dependent      是否依赖网络（落库非空，默认 false 回填存量行）
--   - rollback_steps         回滚步骤列表（删除文件 / 杀进程 / 还原注册表 / 清持久化）
--
-- 枚举校验交由 Java 层（DTO + Service）承担，DB 不加 CHECK 约束，便于未来扩展取值
-- 而无需新增迁移；rollback_steps 用 JSONB 承载步骤对象数组，与 Hibernate JsonType 配合.

ALTER TABLE injectors_contracts
    ADD COLUMN injector_contract_software_category VARCHAR(64);

ALTER TABLE injectors_contracts
    ADD COLUMN injector_contract_defense_layer VARCHAR(32);

ALTER TABLE injectors_contracts
    ADD COLUMN injector_contract_network_protocol_family VARCHAR(16);

ALTER TABLE injectors_contracts
    ADD COLUMN injector_contract_target_os VARCHAR(16);

ALTER TABLE injectors_contracts
    ADD COLUMN injector_contract_network_dependent BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE injectors_contracts
    ADD COLUMN injector_contract_rollback_steps JSONB;
