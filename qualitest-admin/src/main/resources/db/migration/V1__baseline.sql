-- Flyway V1：空库结构 + 平台种子（非业务数据）
-- 保留：菜单/字典/参数、演示账号与角色、内置项目模板、内置 AI 厂商与快捷提示词
-- 不包含：Flyway 历史表、测试项目/测试流/会话/日志等运行时数据
-- 勿从 mysqldump/Navicat 带入 LOCK TABLES 或 flyway_schema_history

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message`
(
    `ai_chat_message_id` bigint                                                       NOT NULL COMMENT '消息ID',
    `ai_chat_session_id` bigint                                                       NOT NULL COMMENT '会话ID',
    `message_role`       varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息角色（system系统 user用户 assistant模型）',
    `ai_llm_model_id`    bigint                                                       NULL DEFAULT NULL COMMENT '模型ID',
    `message_content`    mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '消息内容',
    `thinking_content`   mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL COMMENT '思考内容',
    `result_meta_json`   json                                                         NULL COMMENT '结果摘要',
    `create_time`        datetime                                                     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`ai_chat_message_id`) USING BTREE,
    INDEX `idx_session_time` (`ai_chat_session_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 会话消息'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_chat_message
-- ----------------------------

-- ----------------------------
-- Table structure for ai_chat_session
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_session`;
CREATE TABLE `ai_chat_session`
(
    `ai_chat_session_id`    bigint                                                        NOT NULL COMMENT '会话ID',
    `test_project_id`       bigint                                                        NOT NULL COMMENT '项目ID',
    `user_id`               bigint                                                        NOT NULL COMMENT '用户ID',
    `session_scene`         varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '会话场景',
    `biz_ref_json`          json                                                          NOT NULL COMMENT '业务锚点',
    `current_model_id`      bigint                                                        NOT NULL COMMENT '当前模型ID',
    `thinking_enabled`      tinyint                                                       NULL     DEFAULT NULL COMMENT '思考开关（0关 1开 NULL跟随模型默认）',
    `session_title`         varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '会话标题',
    `context_summary`       text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci         NULL COMMENT '滚动会话摘要（Checkpoint）',
    `summary_message_count` int                                                           NULL     DEFAULT NULL COMMENT '摘要已覆盖的消息条数',
    `summary_updated_at`    datetime                                                      NULL     DEFAULT NULL COMMENT '摘要最近更新时间',
    `del_status`            tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`           datetime                                                      NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`ai_chat_session_id`) USING BTREE,
    INDEX `idx_scene_user` (`session_scene` ASC, `user_id` ASC) USING BTREE,
    INDEX `idx_project_scene` (`test_project_id` ASC, `session_scene` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 会话'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_chat_session
-- ----------------------------

-- ----------------------------
-- Table structure for ai_llm_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_llm_model`;
CREATE TABLE `ai_llm_model`
(
    `ai_llm_model_id`        bigint                                                        NOT NULL COMMENT '模型ID',
    `ai_llm_vendor_id`       bigint                                                        NOT NULL COMMENT '厂商ID',
    `model_name`             varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '上游模型ID',
    `display_name`           varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '展示名',
    `builtin_status`         tinyint                                                       NOT NULL DEFAULT 0 COMMENT '内置状态（0自定义 1内置）',
    `enable_status`          tinyint                                                       NOT NULL DEFAULT 1 COMMENT '启用状态（0禁用 1启用）',
    `thinking_capable`       tinyint                                                       NOT NULL DEFAULT 0 COMMENT '是否支持思考（0否 1是）',
    `thinking_default`       tinyint                                                       NOT NULL DEFAULT 0 COMMENT '默认是否开启思考（0关 1开）',
    `thinking_budget_tokens` int                                                           NULL     DEFAULT NULL COMMENT '思考 token 预算，NULL 用全局默认',
    `sort_num`               int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `remark`                 varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '备注',
    `del_status`             tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`            datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`            datetime                                                      NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`ai_llm_model_id`) USING BTREE,
    UNIQUE INDEX `uk_vendor_model_name` (`ai_llm_vendor_id` ASC, `model_name` ASC, `del_status` ASC) USING BTREE,
    INDEX `idx_vendor_enable_sort` (`ai_llm_vendor_id` ASC, `enable_status` ASC, `sort_num` ASC) USING BTREE,
    INDEX `idx_vendor_builtin` (`ai_llm_vendor_id` ASC, `builtin_status` ASC, `enable_status` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 模型'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_llm_model
-- ----------------------------

-- ----------------------------
-- Table structure for ai_llm_vendor
-- ----------------------------
DROP TABLE IF EXISTS `ai_llm_vendor`;
CREATE TABLE `ai_llm_vendor`
(
    `ai_llm_vendor_id` bigint                                                        NOT NULL COMMENT '厂商ID',
    `vendor_name`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '厂商名',
    `builtin_status`   tinyint                                                       NOT NULL DEFAULT 0 COMMENT '内置状态（0自定义 1内置）',
    `template_id`      varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '模板ID',
    `provider`         varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL DEFAULT 'openai_compatible' COMMENT '协议标识',
    `discovery_type`   varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '发现类型',
    `base_url`         varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API Base URL',
    `api_key`          varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '密钥明文',
    `enable_status`    tinyint                                                       NOT NULL DEFAULT 1 COMMENT '启用状态（0禁用 1启用）',
    `sort_num`         int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `remark`           varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '备注',
    `del_status`       tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`      datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`      datetime                                                      NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`ai_llm_vendor_id`) USING BTREE,
    UNIQUE INDEX `uk_vendor_name` (`vendor_name` ASC, `del_status` ASC) USING BTREE,
    INDEX `idx_enable_sort` (`enable_status` ASC, `sort_num` ASC) USING BTREE,
    INDEX `idx_builtin_sort` (`builtin_status` ASC, `enable_status` ASC, `sort_num` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 厂商'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_llm_vendor
-- ----------------------------
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000001, 'Deepseek', 1, 'deepseek', 'openai_compatible', 'openai_models', 'https://api.deepseek.com', NULL, 0, 10, NULL, 0, '2026-06-23 11:42:53', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000002, 'OpenAI', 1, 'openai', 'openai_compatible', 'openai_models', 'https://api.openai.com/v1', NULL, 0, 20, NULL, 0, '2026-06-23 11:42:53', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000003, '硅基流动', 1, 'siliconflow', 'openai_compatible', 'openai_models', 'https://api.siliconflow.cn/v1', NULL, 0, 30, NULL, 0, '2026-06-23 11:42:54', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000004, 'Anthropic', 1, 'anthropic', 'anthropic_compatible', 'anthropic_models', 'https://api.anthropic.com', NULL, 0, 40, NULL, 0, '2026-06-23 11:42:54', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000005, 'Ollama', 1, 'ollama', 'openai_compatible', 'ollama_tags', 'http://127.0.0.1:11434/v1', NULL, 0, 50, NULL, 0, '2026-06-23 11:42:55', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000006, 'Moonshot（Kimi）', 1, 'moonshot', 'openai_compatible', 'openai_models', 'https://api.moonshot.cn/v1', NULL, 0, 60, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000007, '智谱 AI', 1, 'zhipu', 'openai_compatible', 'openai_models', 'https://open.bigmodel.cn/api/paas/v4', NULL, 0, 70, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000008, '通义千问', 1, 'qwen', 'openai_compatible', 'openai_models', 'https://dashscope.aliyuncs.com/compatible-mode/v1', NULL, 0, 80, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000009, 'MiniMax', 1, 'minimax', 'openai_compatible', 'openai_models', 'https://api.minimax.chat/v1', NULL, 0, 90, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000010, '火山引擎（豆包）', 1, 'volcengine', 'openai_compatible', 'openai_models', 'https://ark.cn-beijing.volces.com/api/v3', NULL, 0, 100, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000011, '百川智能', 1, 'baichuan', 'openai_compatible', 'openai_models', 'https://api.baichuan-ai.com/v1', NULL, 0, 110, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000012, '零一万物', 1, 'yi', 'openai_compatible', 'openai_models', 'https://api.lingyiwanwu.com/v1', NULL, 0, 120, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000013, '阶跃星辰', 1, 'stepfun', 'openai_compatible', 'openai_models', 'https://api.stepfun.com/v1', NULL, 0, 130, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000014, '腾讯混元', 1, 'hunyuan', 'openai_compatible', 'openai_models', 'https://api.hunyuan.cloud.tencent.com/v1', NULL, 0, 140, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000015, 'Google Gemini', 1, 'gemini', 'openai_compatible', 'openai_models', 'https://generativelanguage.googleapis.com/v1beta/openai', NULL, 0, 150, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000016, 'Groq', 1, 'groq', 'openai_compatible', 'openai_models', 'https://api.groq.com/openai/v1', NULL, 0, 160, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000017, 'Mistral AI', 1, 'mistral', 'openai_compatible', 'openai_models', 'https://api.mistral.ai/v1', NULL, 0, 170, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000018, 'OpenRouter', 1, 'openrouter', 'openai_compatible', 'openai_models', 'https://openrouter.ai/api/v1', NULL, 0, 180, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000019, 'Together AI', 1, 'together', 'openai_compatible', 'openai_models', 'https://api.together.xyz/v1', NULL, 0, 190, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000020, 'Fireworks AI', 1, 'fireworks', 'openai_compatible', 'openai_models', 'https://api.fireworks.ai/inference/v1', NULL, 0, 200, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000021, 'Azure OpenAI', 1, 'azure', 'openai_compatible', 'openai_models', '', NULL, 0, 210, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000022, 'Perplexity', 1, 'perplexity', 'openai_compatible', 'openai_models', 'https://api.perplexity.ai', NULL, 0, 220, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000023, 'xAI', 1, 'xai', 'openai_compatible', 'openai_models', 'https://api.x.ai/v1', NULL, 0, 230, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000024, '自定义 OpenAI 兼容', 1, 'custom_openai', 'openai_compatible', 'openai_models', '', NULL, 0, 240, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000025, '自定义（Anthropic 兼容）', 1, 'custom_anthropic', 'anthropic_compatible', 'anthropic_models', '', NULL, 0, 250, NULL, 0, '2026-06-23 11:42:56', NULL);
INSERT INTO `ai_llm_vendor` VALUES (2067000000000000026, '自定义（无列表接口）', 1, 'custom_static', 'openai_compatible', 'none', '', NULL, 0, 260, NULL, 0, '2026-06-23 11:42:56', NULL);

-- ----------------------------
-- Table structure for ai_prompt_template
-- ----------------------------
DROP TABLE IF EXISTS `ai_prompt_template`;
CREATE TABLE `ai_prompt_template`
(
    `ai_prompt_template_id` bigint                                                         NOT NULL COMMENT '提示词模板ID',
    `template_scope`        varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '模板范围（platform平台 project项目）',
    `test_project_id`       bigint                                                         NULL     DEFAULT NULL COMMENT '测试项目ID',
    `session_scene`         varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL DEFAULT 'test_flow_design' COMMENT '会话场景',
    `template_title`        varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '模板标题',
    `template_description`  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '模板说明',
    `template_content`      varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板正文',
    `builtin_status`        tinyint                                                        NOT NULL DEFAULT 0 COMMENT '内置状态（0自定义 1内置）',
    `enable_status`         tinyint                                                        NOT NULL DEFAULT 1 COMMENT '启用状态（0禁用 1启用）',
    `sort_num`              int                                                            NOT NULL DEFAULT 0 COMMENT '排序',
    `remark`                varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '备注',
    `del_status`            tinyint                                                        NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`           datetime                                                       NOT NULL COMMENT '创建时间',
    `update_time`           datetime                                                       NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`ai_prompt_template_id`) USING BTREE,
    INDEX `idx_project_scene` (`test_project_id` ASC, `session_scene` ASC, `enable_status` ASC, `del_status`
                               ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI提示词模板'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_prompt_template
-- ----------------------------
INSERT INTO `ai_prompt_template` VALUES (2070000000000000101, 'platform', NULL, 'test_flow_design', '从零搭建', '空画布或仅有 start', '搭建「{流程名称}」：\n1. {步骤描述}\n2. {步骤描述}\n\n需登录：Start 后挂登录子流，或开头只登录一次；关键步骤后加断言。', 1, 1, 101, '从零搭建', 0, '2026-07-04 21:37:19', NULL);
INSERT INTO `ai_prompt_template` VALUES (2070000000000000103, 'platform', NULL, 'test_flow_design', '预期业务拒绝', '失败路径，关业务 Code', '搭建「{流程名称}」失败路径：\n1. 绑定「{接口}」\n2. successCheck.mode=off\n3. 断言 http.body.code ≠ 成功码，或 msg 含「{拒绝文案}」\n\n测值用场景给定账号，勿用成功登录占位。', 1, 1, 102, '从零搭建', 0, '2026-07-04 21:37:19', NULL);
INSERT INTO `ai_prompt_template` VALUES (2070000000000000201, 'platform', NULL, 'test_flow_design', '末尾追加步骤', '画布已有节点', '在流末尾追加：{新步骤描述}。不要删除或修改现有节点。', 1, 1, 201, '扩展现有流', 0, '2026-07-04 21:37:19', NULL);
INSERT INTO `ai_prompt_template` VALUES (2070000000000000501, 'platform', NULL, 'test_flow_design', 'Run 失败修复', '@Run 或步骤名', '@{Run:最近一次失败Run} 或步骤「{nodeName}」失败。分析参数、断言、extract 并修复，勿动无关节点。', 1, 1, 301, 'Run 失败修复', 0, '2026-07-04 21:37:19', NULL);
INSERT INTO `ai_prompt_template` VALUES (2070000000000000601, 'platform', NULL, 'test_flow_design', '插入登录子流', 'Start 后挂子流', '在 Start 后插入登录子流「{子流名}」，outputs 映射 flow.token 或 flow.adminToken。后续 HTTP 勿再登录。', 1, 1, 401, '子流编排', 0, '2026-07-04 21:37:19', NULL);
INSERT INTO `ai_prompt_template` VALUES (2070000000000002103, 'platform', NULL, 'test_api_design', '测值用素材占位符', '{{asset.*}} 默认测值', '为缺省测值的参数补测值：优先 {{asset.key.field}}，环境相关用 {{env.*}}。勿写死密码或 Token。', 1, 1, 101, '补测值', 0, '2026-07-22 13:51:11', NULL);


-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table`
(
    `table_id`          bigint                                                         NOT NULL AUTO_INCREMENT COMMENT '编号',
    `table_name`        varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '表名称',
    `table_comment`     varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '表描述',
    `sub_table_name`    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '关联子表的表名',
    `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '子表关联的外键名',
    `class_name`        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '实体类名称',
    `tpl_category`      varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
    `tpl_web_type`      varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
    `package_name`      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT NULL COMMENT '生成包路径',
    `module_name`       varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '生成模块名',
    `business_name`     varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '生成业务名',
    `function_name`     varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '生成功能名',
    `function_author`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '生成功能作者',
    `gen_type`          char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci       NULL DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
    `gen_path`          varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
    `options`           varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '其它生成选项',
    `create_by`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '创建者',
    `create_time`       datetime                                                       NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`         varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '更新者',
    `update_time`       datetime                                                       NULL DEFAULT NULL COMMENT '更新时间',
    `remark`            varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of gen_table
-- ----------------------------

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column`
(
    `column_id`      bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `table_id`       bigint                                                        NULL DEFAULT NULL COMMENT '归属表编号',
    `column_name`    varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
    `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列描述',
    `column_type`    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列类型',
    `java_type`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
    `java_field`     varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA字段名',
    `is_pk`          char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否主键（1是）',
    `is_increment`   char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否自增（1是）',
    `is_required`    char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否必填（1是）',
    `is_insert`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否为插入字段（1是）',
    `is_edit`        char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否编辑字段（1是）',
    `is_list`        char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否列表字段（1是）',
    `is_query`       char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT NULL COMMENT '是否查询字段（1是）',
    `query_type`     varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
    `html_type`      varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
    `dict_type`      varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
    `sort`           int                                                           NULL DEFAULT NULL COMMENT '排序',
    `create_by`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time`    datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time`    datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`column_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表字段'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of gen_table_column
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_blob_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_blob_triggers`;
CREATE TABLE `qrtz_blob_triggers`
(
    `sched_name`    varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `trigger_name`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
    `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
    `blob_data`     blob                                                          NULL COMMENT '存放持久化Trigger对象',
    PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
    CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Blob类型的触发器表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_blob_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_calendars
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_calendars`;
CREATE TABLE `qrtz_calendars`
(
    `sched_name`    varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
    `calendar`      blob                                                          NOT NULL COMMENT '存放持久化calendar对象',
    PRIMARY KEY (`sched_name`, `calendar_name`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '日历信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_calendars
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_cron_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_cron_triggers`;
CREATE TABLE `qrtz_cron_triggers`
(
    `sched_name`      varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `trigger_name`    varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
    `trigger_group`   varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
    `cron_expression` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
    `time_zone_id`    varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT NULL COMMENT '时区',
    PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
    CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Cron类型的触发器表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_cron_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_fired_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_fired_triggers`;
CREATE TABLE `qrtz_fired_triggers`
(
    `sched_name`        varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `entry_id`          varchar(95) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '调度器实例id',
    `trigger_name`      varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
    `trigger_group`     varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
    `instance_name`     varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
    `fired_time`        bigint                                                        NOT NULL COMMENT '触发的时间',
    `sched_time`        bigint                                                        NOT NULL COMMENT '定时器制定的时间',
    `priority`          int                                                           NOT NULL COMMENT '优先级',
    `state`             varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '状态',
    `job_name`          varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名称',
    `job_group`         varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务组名',
    `is_nonconcurrent`  varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '是否并发',
    `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT '是否接受恢复执行',
    PRIMARY KEY (`sched_name`, `entry_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '已触发的触发器表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_fired_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_job_details
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_job_details`;
CREATE TABLE `qrtz_job_details`
(
    `sched_name`        varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `job_name`          varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
    `job_group`         varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
    `description`       varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相关介绍',
    `job_class_name`    varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
    `is_durable`        varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '是否持久化',
    `is_nonconcurrent`  varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '是否并发',
    `is_update_data`    varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '是否更新数据',
    `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '是否接受恢复执行',
    `job_data`          blob                                                          NULL COMMENT '存放持久化job对象',
    PRIMARY KEY (`sched_name`, `job_name`, `job_group`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '任务详细信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_job_details
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_locks
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_locks`;
CREATE TABLE `qrtz_locks`
(
    `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `lock_name`  varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '悲观锁名称',
    PRIMARY KEY (`sched_name`, `lock_name`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '存储的悲观锁信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_locks
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_paused_trigger_grps
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
CREATE TABLE `qrtz_paused_trigger_grps`
(
    `sched_name`    varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
    PRIMARY KEY (`sched_name`, `trigger_group`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '暂停的触发器表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_paused_trigger_grps
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_scheduler_state
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_scheduler_state`;
CREATE TABLE `qrtz_scheduler_state`
(
    `sched_name`        varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `instance_name`     varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
    `last_checkin_time` bigint                                                        NOT NULL COMMENT '上次检查时间',
    `checkin_interval`  bigint                                                        NOT NULL COMMENT '检查间隔时间',
    PRIMARY KEY (`sched_name`, `instance_name`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '调度器状态表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_scheduler_state
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_simple_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_simple_triggers`;
CREATE TABLE `qrtz_simple_triggers`
(
    `sched_name`      varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `trigger_name`    varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
    `trigger_group`   varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
    `repeat_count`    bigint                                                        NOT NULL COMMENT '重复的次数统计',
    `repeat_interval` bigint                                                        NOT NULL COMMENT '重复的间隔时间',
    `times_triggered` bigint                                                        NOT NULL COMMENT '已经触发的次数',
    PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
    CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '简单触发器的信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_simple_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_simprop_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
CREATE TABLE `qrtz_simprop_triggers`
(
    `sched_name`    varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `trigger_name`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
    `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
    `str_prop_1`    varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
    `str_prop_2`    varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
    `str_prop_3`    varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
    `int_prop_1`    int                                                           NULL DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
    `int_prop_2`    int                                                           NULL DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
    `long_prop_1`   bigint                                                        NULL DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
    `long_prop_2`   bigint                                                        NULL DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
    `dec_prop_1`    decimal(13, 4)                                                NULL DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
    `dec_prop_2`    decimal(13, 4)                                                NULL DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
    `bool_prop_1`   varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
    `bool_prop_2`   varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
    PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
    CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '同步机制的行锁表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_simprop_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_triggers`;
CREATE TABLE `qrtz_triggers`
(
    `sched_name`     varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
    `trigger_name`   varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
    `trigger_group`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
    `job_name`       varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
    `job_group`      varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
    `description`    varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相关介绍',
    `next_fire_time` bigint                                                        NULL DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
    `prev_fire_time` bigint                                                        NULL DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
    `priority`       int                                                           NULL DEFAULT NULL COMMENT '优先级',
    `trigger_state`  varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '触发器状态',
    `trigger_type`   varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '触发器的类型',
    `start_time`     bigint                                                        NOT NULL COMMENT '开始时间',
    `end_time`       bigint                                                        NULL DEFAULT NULL COMMENT '结束时间',
    `calendar_name`  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日程表名称',
    `misfire_instr`  smallint                                                      NULL DEFAULT NULL COMMENT '补偿执行的策略',
    `job_data`       blob                                                          NULL COMMENT '存放持久化job对象',
    PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
    INDEX `sched_name` (`sched_name` ASC, `job_name` ASC, `job_group` ASC) USING BTREE,
    CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '触发器详细信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of qrtz_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`
(
    `config_id`    int                                                           NOT NULL AUTO_INCREMENT COMMENT '参数主键',
    `config_name`  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数名称',
    `config_key`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数键名',
    `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数键值',
    `config_type`  char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
    `create_by`    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time`  datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time`  datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`config_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 9
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '参数配置表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2026-01-20 15:38:28', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT INTO `sys_config` VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2026-01-20 15:38:28', '', NULL, '初始化密码 123456');
INSERT INTO `sys_config` VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-light', 'Y', 'admin', '2026-01-20 15:38:28', 'admin', '2026-02-05 16:48:12', '深色主题theme-dark，浅色主题theme-light');
INSERT INTO `sys_config` VALUES (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'false', 'Y', 'admin', '2026-01-20 15:38:28', 'admin', '2026-04-07 15:11:45', '是否开启验证码功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2026-01-20 15:38:28', '', NULL, '是否开启注册用户功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2026-01-20 15:38:28', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
INSERT INTO `sys_config` VALUES (7, '用户管理-初始密码修改策略', 'sys.account.initPasswordModify', '0', 'Y', 'admin', '2026-01-20 15:38:28', 'admin', '2026-04-07 15:11:27', '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
INSERT INTO `sys_config` VALUES (8, '用户管理-账号密码更新周期', 'sys.account.passwordValidateDays', '0', 'Y', 'admin', '2026-01-20 15:38:28', '', NULL, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`
(
    `dept_id`     bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '部门id',
    `parent_id`   bigint                                                       NULL DEFAULT 0 COMMENT '父部门id',
    `ancestors`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '祖级列表',
    `dept_name`   varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '部门名称',
    `order_num`   int                                                          NULL DEFAULT 0 COMMENT '显示顺序',
    `leader`      varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '负责人',
    `phone`       varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
    `email`       varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
    `status`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci     NULL DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
    `del_flag`    char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci     NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    `create_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime                                                     NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime                                                     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 102
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '部门表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (100, 0, '0', '质衡科技', 0, '质衡', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-01-20 15:38:20', '', NULL);
INSERT INTO `sys_dept` VALUES (101, 100, '0,100', '测试部门', 1, '质衡', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-01-20 15:38:20', '', NULL);

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`
(
    `dict_code`   bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    `dict_sort`   int                                                           NULL DEFAULT 0 COMMENT '字典排序',
    `dict_label`  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典标签',
    `dict_value`  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典键值',
    `dict_type`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
    `css_class`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
    `list_class`  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '表格回显样式',
    `is_default`  char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
    `status`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 30
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '字典数据表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '性别男');
INSERT INTO `sys_dict_data` VALUES (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '性别女');
INSERT INTO `sys_dict_data` VALUES (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '性别未知');
INSERT INTO `sys_dict_data` VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '显示菜单');
INSERT INTO `sys_dict_data` VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '隐藏菜单');
INSERT INTO `sys_dict_data` VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '默认分组');
INSERT INTO `sys_dict_data` VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '系统分组');
INSERT INTO `sys_dict_data` VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '系统默认是');
INSERT INTO `sys_dict_data` VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '系统默认否');
INSERT INTO `sys_dict_data` VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '通知');
INSERT INTO `sys_dict_data` VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '公告');
INSERT INTO `sys_dict_data` VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '关闭状态');
INSERT INTO `sys_dict_data` VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '其他操作');
INSERT INTO `sys_dict_data` VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '新增操作');
INSERT INTO `sys_dict_data` VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '修改操作');
INSERT INTO `sys_dict_data` VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '删除操作');
INSERT INTO `sys_dict_data` VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '授权操作');
INSERT INTO `sys_dict_data` VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '导出操作');
INSERT INTO `sys_dict_data` VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '导入操作');
INSERT INTO `sys_dict_data` VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '强退操作');
INSERT INTO `sys_dict_data` VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '生成操作');
INSERT INTO `sys_dict_data` VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '清空操作');
INSERT INTO `sys_dict_data` VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2026-01-20 15:38:28', '', NULL, '停用状态');

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`
(
    `dict_id`     bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    `dict_name`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典名称',
    `dict_type`   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
    `status`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`dict_id`) USING BTREE,
    UNIQUE INDEX `dict_type` (`dict_type` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 11
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '字典类型表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '用户性别列表');
INSERT INTO `sys_dict_type` VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '菜单状态列表');
INSERT INTO `sys_dict_type` VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '系统开关列表');
INSERT INTO `sys_dict_type` VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '任务状态列表');
INSERT INTO `sys_dict_type` VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '任务分组列表');
INSERT INTO `sys_dict_type` VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '系统是否列表');
INSERT INTO `sys_dict_type` VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '通知类型列表');
INSERT INTO `sys_dict_type` VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '通知状态列表');
INSERT INTO `sys_dict_type` VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '操作类型列表');
INSERT INTO `sys_dict_type` VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2026-01-20 15:38:27', '', NULL, '登录状态列表');

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job`
(
    `job_id`          bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `job_name`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL DEFAULT '' COMMENT '任务名称',
    `job_group`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
    `invoke_target`   varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
    `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT '' COMMENT 'cron执行表达式',
    `misfire_policy`  varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
    `concurrent`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL     DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
    `status`          char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL     DEFAULT '0' COMMENT '状态（0正常 1暂停）',
    `create_by`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT '' COMMENT '创建者',
    `create_time`     datetime                                                      NULL     DEFAULT NULL COMMENT '创建时间',
    `update_by`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT '' COMMENT '更新者',
    `update_time`     datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    `remark`          varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT '' COMMENT '备注信息',
    PRIMARY KEY (`job_id`, `job_name`, `job_group`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '定时任务调度表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO `sys_job` VALUES (1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams', '0/10 * * * * ?', '3', '1', '1', 'admin', '2026-01-20 15:38:28', '', NULL, '');
INSERT INTO `sys_job` VALUES (2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')', '0/15 * * * * ?', '3', '1', '1', 'admin', '2026-01-20 15:38:28', '', NULL, '');
INSERT INTO `sys_job` VALUES (3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)', '0/20 * * * * ?', '3', '1', '1', 'admin', '2026-01-20 15:38:28', '', NULL, '');

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log`
(
    `job_log_id`     bigint                                                         NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
    `job_name`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '任务名称',
    `job_group`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NOT NULL COMMENT '任务组名',
    `invoke_target`  varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '调用目标字符串',
    `job_message`    varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT NULL COMMENT '日志信息',
    `status`         char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci       NULL DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
    `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '异常信息',
    `create_time`    datetime                                                       NULL DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '定时任务调度日志表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_job_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_logininfor
-- ----------------------------
DROP TABLE IF EXISTS `sys_logininfor`;
CREATE TABLE `sys_logininfor`
(
    `info_id`        bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    `user_name`      varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '用户账号',
    `ipaddr`         varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '登录IP地址',
    `login_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '登录地点',
    `browser`        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '浏览器类型',
    `os`             varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '操作系统',
    `status`         char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
    `msg`            varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '提示消息',
    `login_time`     datetime                                                      NULL DEFAULT NULL COMMENT '访问时间',
    PRIMARY KEY (`info_id`) USING BTREE,
    INDEX `idx_sys_logininfor_s` (`status` ASC) USING BTREE,
    INDEX `idx_sys_logininfor_lt` (`login_time` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '系统访问记录'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_logininfor
-- ----------------------------

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`
(
    `menu_id`     bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `menu_name`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '菜单名称',
    `parent_id`   bigint                                                        NULL DEFAULT 0 COMMENT '父菜单ID',
    `order_num`   int                                                           NULL DEFAULT 0 COMMENT '显示顺序',
    `path`        varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '路由地址',
    `component`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件路径',
    `query`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '路由参数',
    `route_name`  varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '路由名称',
    `is_frame`    int                                                           NULL DEFAULT 1 COMMENT '是否为外链（0是 1否）',
    `is_cache`    int                                                           NULL DEFAULT 0 COMMENT '是否缓存（0缓存 1不缓存）',
    `menu_type`   char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
    `visible`     char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
    `status`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
    `perms`       varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '权限标识',
    `icon`        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '#' COMMENT '菜单图标',
    `create_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 2062
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '菜单权限表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, '系统管理', 0, 21, 'system', NULL, '', '', 1, 0, 'M', '0', '0', '', 'system', 'admin', '2026-01-20 15:38:23', 'admin', '2026-02-05 19:16:52', '系统管理目录');
INSERT INTO `sys_menu` VALUES (2, '系统监控', 0, 22, 'monitor', NULL, '', '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', '2026-01-20 15:38:23', 'admin', '2026-02-05 19:16:47', '系统监控目录');
INSERT INTO `sys_menu` VALUES (3, '系统工具', 0, 23, 'tool', NULL, '', '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', '2026-01-20 15:38:23', 'admin', '2026-02-05 19:16:58', '系统工具目录');
INSERT INTO `sys_menu` VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2026-01-20 15:38:23', '', NULL, '用户管理菜单');
INSERT INTO `sys_menu` VALUES (101, '角色管理', 1, 2, 'role', 'system/role/index', '', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2026-01-20 15:38:23', '', NULL, '角色管理菜单');
INSERT INTO `sys_menu` VALUES (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2026-01-20 15:38:23', '', NULL, '菜单管理菜单');
INSERT INTO `sys_menu` VALUES (103, '部门管理', 1, 4, 'dept', 'system/dept/index', '', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2026-01-20 15:38:23', '', NULL, '部门管理菜单');
INSERT INTO `sys_menu` VALUES (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2026-01-20 15:38:23', '', NULL, '岗位管理菜单');
INSERT INTO `sys_menu` VALUES (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2026-01-20 15:38:23', '', NULL, '字典管理菜单');
INSERT INTO `sys_menu` VALUES (106, '参数设置', 1, 7, 'config', 'system/config/index', '', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2026-01-20 15:38:23', '', NULL, '参数设置菜单');
INSERT INTO `sys_menu` VALUES (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', '', 1, 0, 'C', '0', '0', 'system:notice:list', 'message', 'admin', '2026-01-20 15:38:23', '', NULL, '通知公告菜单');
INSERT INTO `sys_menu` VALUES (108, '日志管理', 1, 9, 'log', '', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2026-01-20 15:38:23', '', NULL, '日志管理菜单');
INSERT INTO `sys_menu` VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2026-01-20 15:38:23', '', NULL, '在线用户菜单');
INSERT INTO `sys_menu` VALUES (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2026-01-20 15:38:23', '', NULL, '定时任务菜单');
INSERT INTO `sys_menu` VALUES (111, '数据监控', 2, 3, 'druid', 'monitor/druid/index', '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list', 'druid', 'admin', '2026-01-20 15:38:23', '', NULL, '数据监控菜单');
INSERT INTO `sys_menu` VALUES (112, '服务监控', 2, 4, 'server', 'monitor/server/index', '', '', 1, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2026-01-20 15:38:23', '', NULL, '服务监控菜单');
INSERT INTO `sys_menu` VALUES (113, '缓存监控', 2, 5, 'cache', 'monitor/cache/index', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis', 'admin', '2026-01-20 15:38:23', '', NULL, '缓存监控菜单');
INSERT INTO `sys_menu` VALUES (114, '缓存列表', 2, 6, 'cacheList', 'monitor/cache/list', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis-list', 'admin', '2026-01-20 15:38:23', '', NULL, '缓存列表菜单');
INSERT INTO `sys_menu` VALUES (115, '表单构建', 3, 1, 'build', 'tool/build/index', '', '', 1, 0, 'C', '0', '0', 'tool:build:list', 'build', 'admin', '2026-01-20 15:38:23', '', NULL, '表单构建菜单');
INSERT INTO `sys_menu` VALUES (116, '代码生成', 3, 2, 'gen', 'tool/gen/index', '', '', 1, 0, 'C', '0', '0', 'tool:gen:list', 'code', 'admin', '2026-01-20 15:38:23', '', NULL, '代码生成菜单');
INSERT INTO `sys_menu` VALUES (117, '系统接口', 3, 3, 'swagger', 'tool/swagger/index', '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list', 'swagger', 'admin', '2026-01-20 15:38:23', '', NULL, '系统接口菜单');
INSERT INTO `sys_menu` VALUES (500, '操作日志', 108, 1, 'operlog', 'monitor/operlog/index', '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', '2026-01-20 15:38:23', '', NULL, '操作日志菜单');
INSERT INTO `sys_menu` VALUES (501, '登录日志', 108, 2, 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', '2026-01-20 15:38:23', '', NULL, '登录日志菜单');
INSERT INTO `sys_menu` VALUES (1000, '用户查询', 100, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1001, '用户新增', 100, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1002, '用户修改', 100, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1003, '用户删除', 100, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1004, '用户导出', 100, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1005, '用户导入', 100, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1006, '重置密码', 100, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1007, '角色查询', 101, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1008, '角色新增', 101, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2026-01-20 15:38:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1009, '角色修改', 101, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1010, '角色删除', 101, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1011, '角色导出', 101, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1012, '菜单查询', 102, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1013, '菜单新增', 102, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1014, '菜单修改', 102, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1015, '菜单删除', 102, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1016, '部门查询', 103, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1017, '部门新增', 103, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1018, '部门修改', 103, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1019, '部门删除', 103, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1020, '岗位查询', 104, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1021, '岗位新增', 104, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1022, '岗位修改', 104, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1023, '岗位删除', 104, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1024, '岗位导出', 104, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1025, '字典查询', 105, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1026, '字典新增', 105, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1027, '字典修改', 105, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1028, '字典删除', 105, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1029, '字典导出', 105, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1030, '参数查询', 106, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1031, '参数新增', 106, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1032, '参数修改', 106, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1033, '参数删除', 106, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1034, '参数导出', 106, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1035, '公告查询', 107, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1036, '公告新增', 107, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1037, '公告修改', 107, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1038, '公告删除', 107, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1039, '操作查询', 500, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1040, '操作删除', 500, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1041, '日志导出', 500, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1042, '登录查询', 501, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1043, '登录删除', 501, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1044, '日志导出', 501, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1045, '账户解锁', 501, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1046, '在线查询', 109, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1047, '批量强退', 109, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1048, '单条强退', 109, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1049, '任务查询', 110, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1050, '任务新增', 110, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1051, '任务修改', 110, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1052, '任务删除', 110, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1053, '状态修改', 110, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1054, '任务导出', 110, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1055, '生成查询', 116, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1056, '生成修改', 116, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1057, '生成删除', 116, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove', '#', 'admin', '2026-01-20 15:38:24', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1058, '导入代码', 116, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import', '#', 'admin', '2026-01-20 15:38:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1059, '预览代码', 116, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview', '#', 'admin', '2026-01-20 15:38:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1060, '生成代码', 116, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code', '#', 'admin', '2026-01-20 15:38:25', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2000, '测试管理', 0, 1, 'testManages', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'bug', 'admin', '2026-02-05 19:16:22', 'admin', '2026-07-04 21:17:34', '');
INSERT INTO `sys_menu` VALUES (2001, '测试项目', 2000, 1, 'testProject', 'project/testProject/index', NULL, '', 1, 0, 'C', '0', '0', 'project:testProject:list', 'test-project', 'admin', '2026-02-05 19:33:46', 'admin', '2026-02-09 19:58:19', '测试项目菜单');
INSERT INTO `sys_menu` VALUES (2002, '测试项目查询', 2001, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProject:query', '#', 'admin', '2026-02-05 19:33:46', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2003, '测试项目新增', 2001, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProject:add', '#', 'admin', '2026-02-05 19:33:46', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2004, '测试项目修改', 2001, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProject:edit', '#', 'admin', '2026-02-05 19:33:46', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2005, '测试项目删除', 2001, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProject:remove', '#', 'admin', '2026-02-05 19:33:46', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2006, '测试项目导出', 2001, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'project:testProject:export', '#', 'admin', '2026-02-05 19:33:46', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2020, '测试项目成员', 2001, 6, 'testProjectMember', 'project/testProjectMember/index', NULL, '', 1, 0, 'C', '1', '0', 'project:testProjectMember:list', '#', 'admin', '2026-03-26 19:39:58', '', NULL, '测试项目成员（隐藏）');
INSERT INTO `sys_menu` VALUES (2021, '测试项目成员查询', 2020, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMember:query', '#', 'admin', '2026-03-26 19:39:58', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2022, '测试项目成员新增', 2020, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMember:add', '#', 'admin', '2026-03-26 19:39:58', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2023, '测试项目成员修改', 2020, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMember:edit', '#', 'admin', '2026-03-26 19:39:58', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2024, '测试项目成员删除', 2020, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMember:remove', '#', 'admin', '2026-03-26 19:39:58', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2025, '测试项目成员导出', 2020, 5, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectMember:export', '#', 'admin', '2026-03-26 19:39:58', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2026, '项目模板', 2000, 2, 'testProjectTemplate', 'project/testProjectTemplate/index', NULL, '', 1, 0, 'C', '0', '0', 'project:testProjectTemplate:list', 'lock', 'admin', '2026-08-18 00:00:00', '', NULL, '项目模板菜单');
INSERT INTO `sys_menu` VALUES (2027, '项目模板查询', 2026, 1, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectTemplate:query', '#', 'admin', '2026-08-18 00:00:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2028, '项目模板新增', 2026, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectTemplate:add', '#', 'admin', '2026-08-18 00:00:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2029, '项目模板修改', 2026, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectTemplate:edit', '#', 'admin', '2026-08-18 00:00:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2030, 'AI 管理', 0, 2, 'aiManages', NULL, NULL, '', 1, 0, 'M', '0', '0', '', 'ai-manage', 'admin', '2026-06-15 10:35:16', 'admin', '2026-06-18 21:52:27', '');
INSERT INTO `sys_menu` VALUES (2031, 'AI 配置', 2030, 1, 'aiLlmVendor', 'ai/aiLlmVendor/index', NULL, '', 1, 0, 'C', '0', '0', 'ai:aiLlmVendor:list', 'ai-vendor', 'admin', '2026-06-15 10:36:55', '', NULL, 'AI 大模型厂商菜单');
INSERT INTO `sys_menu` VALUES (2032, 'AI 配置查询', 2031, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmVendor:query', '#', 'admin', '2026-06-15 10:36:55', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2033, 'AI 配置新增', 2031, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmVendor:add', '#', 'admin', '2026-06-15 10:36:55', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2034, 'AI 配置修改', 2031, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmVendor:edit', '#', 'admin', '2026-06-15 10:36:55', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2035, 'AI 配置删除', 2031, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmVendor:remove', '#', 'admin', '2026-06-15 10:36:55', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2036, 'AI 配置导出', 2031, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmVendor:export', '#', 'admin', '2026-06-15 10:36:55', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2037, 'AI 模型', 2030, 2, 'aiLlmModel', 'ai/aiLlmModel/index', NULL, '', 1, 0, 'C', '1', '0', 'ai:aiLlmModel:list', '#', 'admin', '2026-06-15 10:37:23', 'admin', '2026-06-16 21:59:55', 'AI 大模型菜单');
INSERT INTO `sys_menu` VALUES (2038, 'AI 模型查询', 2037, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmModel:query', '#', 'admin', '2026-06-15 10:37:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2039, 'AI 模型新增', 2037, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmModel:add', '#', 'admin', '2026-06-15 10:37:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2040, 'AI 模型修改', 2037, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmModel:edit', '#', 'admin', '2026-06-15 10:37:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2041, 'AI 模型删除', 2037, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmModel:remove', '#', 'admin', '2026-06-15 10:37:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2042, 'AI 模型导出', 2037, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiLlmModel:export', '#', 'admin', '2026-06-15 10:37:23', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2043, 'AI 会话', 2030, 3, 'aiChatSession', 'ai/aiChatSession/index', NULL, '', 1, 0, 'C', '0', '0', 'ai:aiChatSession:list', 'ai-session', 'admin', '2026-06-15 10:37:43', '', NULL, 'AI 会话菜单');
INSERT INTO `sys_menu` VALUES (2044, 'AI 会话查询', 2043, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatSession:query', '#', 'admin', '2026-06-15 10:37:43', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2045, 'AI 会话新增', 2043, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatSession:add', '#', 'admin', '2026-06-15 10:37:43', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2046, 'AI 会话修改', 2043, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatSession:edit', '#', 'admin', '2026-06-15 10:37:43', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2047, 'AI 会话删除', 2043, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatSession:remove', '#', 'admin', '2026-06-15 10:37:43', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2048, 'AI 会话导出', 2043, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatSession:export', '#', 'admin', '2026-06-15 10:37:43', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2049, 'AI 会话消息', 2030, 4, 'aiChatMessage', 'ai/aiChatMessage/index', NULL, '', 1, 0, 'C', '1', '0', 'ai:aiChatMessage:list', '#', 'admin', '2026-06-15 10:38:00', 'admin', '2026-06-16 22:16:06', 'AI 会话消息菜单');
INSERT INTO `sys_menu` VALUES (2050, 'AI 会话消息查询', 2049, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatMessage:query', '#', 'admin', '2026-06-15 10:38:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2051, 'AI 会话消息新增', 2049, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatMessage:add', '#', 'admin', '2026-06-15 10:38:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2052, 'AI 会话消息修改', 2049, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatMessage:edit', '#', 'admin', '2026-06-15 10:38:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2053, 'AI 会话消息删除', 2049, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatMessage:remove', '#', 'admin', '2026-06-15 10:38:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2054, 'AI 会话消息导出', 2049, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiChatMessage:export', '#', 'admin', '2026-06-15 10:38:00', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2055, 'AI 提示词', 2030, 3, 'aiPromptTemplate', 'ai/aiPromptTemplate/index', NULL, '', 1, 0, 'C', '0', '0', 'ai:aiPromptTemplate:list', 'ai-prompt', 'admin', '2026-07-04 21:17:56', 'admin', '2026-07-05 09:08:32', 'AI提示词模板菜单');
INSERT INTO `sys_menu` VALUES (2056, 'AI 提示词模板查询', 2055, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiPromptTemplate:query', '#', 'admin', '2026-07-04 21:17:56', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2057, 'AI 提示词模板新增', 2055, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiPromptTemplate:add', '#', 'admin', '2026-07-04 21:17:56', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2058, 'AI 提示词模板修改', 2055, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiPromptTemplate:edit', '#', 'admin', '2026-07-04 21:17:56', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2059, 'AI 提示词模板删除', 2055, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiPromptTemplate:remove', '#', 'admin', '2026-07-04 21:17:56', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2060, 'AI 提示词模板导出', 2055, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'ai:aiPromptTemplate:export', '#', 'admin', '2026-07-04 21:17:56', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2061, '项目模板删除', 2026, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'project:testProjectTemplate:remove', '#', 'admin', '2026-08-18 00:00:00', '', NULL, '');

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice`
(
    `notice_id`      int                                                           NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `notice_title`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '公告标题',
    `notice_type`    char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NOT NULL COMMENT '公告类型（1通知 2公告）',
    `notice_content` longblob                                                      NULL COMMENT '公告内容',
    `status`         char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
    `create_by`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time`    datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`      varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time`    datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`         varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`notice_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 3
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '通知公告表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO `sys_notice` VALUES (1, '温馨提醒：质衡系统已就绪', '2', 0xE696B0E78988E69CACE58685E5AEB9, '0', 'admin', '2026-01-20 15:38:28', '', NULL, '管理员');
INSERT INTO `sys_notice` VALUES (2, '维护通知：质衡系统计划维护', '1', 0xE7BBB4E68AA4E58685E5AEB9, '0', 'admin', '2026-01-20 15:38:28', '', NULL, '管理员');

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`
(
    `oper_id`        bigint                                                         NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    `title`          varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '模块标题',
    `business_type`  int                                                            NULL DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
    `method`         varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '方法名称',
    `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '请求方式',
    `operator_type`  int                                                            NULL DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
    `oper_name`      varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '操作人员',
    `dept_name`      varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '' COMMENT '部门名称',
    `oper_url`       varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '请求URL',
    `oper_ip`        varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '主机地址',
    `oper_location`  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '操作地点',
    `oper_param`     varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '请求参数',
    `json_result`    varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '返回参数',
    `status`         int                                                            NULL DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
    `error_msg`      varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '错误消息',
    `oper_time`      datetime                                                       NULL DEFAULT NULL COMMENT '操作时间',
    `cost_time`      bigint                                                         NULL DEFAULT 0 COMMENT '消耗时间',
    PRIMARY KEY (`oper_id`) USING BTREE,
    INDEX `idx_sys_oper_log_bt` (`business_type` ASC) USING BTREE,
    INDEX `idx_sys_oper_log_s` (`status` ASC) USING BTREE,
    INDEX `idx_sys_oper_log_ot` (`oper_time` ASC) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '操作日志记录'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post`
(
    `post_id`     bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    `post_code`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '岗位编码',
    `post_name`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '岗位名称',
    `post_sort`   int                                                           NOT NULL COMMENT '显示顺序',
    `status`      char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NOT NULL COMMENT '状态（0正常 1停用）',
    `create_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`post_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 5
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '岗位信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO `sys_post` VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2026-01-20 15:38:22', '', NULL, '');
INSERT INTO `sys_post` VALUES (2, 'se', '项目经理', 2, '0', 'admin', '2026-01-20 15:38:22', '', NULL, '');
INSERT INTO `sys_post` VALUES (3, 'hr', '人力资源', 3, '0', 'admin', '2026-01-20 15:38:22', '', NULL, '');
INSERT INTO `sys_post` VALUES (4, 'user', '普通员工', 4, '0', 'admin', '2026-01-20 15:38:22', '', NULL, '');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`
(
    `role_id`             bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`           varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '角色名称',
    `role_key`            varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
    `role_sort`           int                                                           NOT NULL COMMENT '显示顺序',
    `data_scope`          char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
    `menu_check_strictly` tinyint(1)                                                    NULL DEFAULT 1 COMMENT '菜单树选择项是否关联显示',
    `dept_check_strictly` tinyint(1)                                                    NULL DEFAULT 1 COMMENT '部门树选择项是否关联显示',
    `status`              char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NOT NULL COMMENT '角色状态（0正常 1停用）',
    `del_flag`            char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    `create_by`           varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time`         datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`           varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time`         datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`              varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`role_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 4
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '角色信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2026-01-20 15:38:23', '', NULL, '超级管理员');
INSERT INTO `sys_role` VALUES (2, '测试', 'test', 2, '2', 1, 1, '0', '0', 'admin', '2026-01-20 15:38:23', 'admin', '2026-03-31 11:11:29', '普通角色');
INSERT INTO `sys_role` VALUES (3, '项目管理员', 'projectAdmin', 3, '1', 1, 1, '0', '2', 'admin', '2026-03-31 10:59:59', 'admin', '2026-03-31 11:00:05', NULL);

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept`
(
    `role_id` bigint NOT NULL COMMENT '角色ID',
    `dept_id` bigint NOT NULL COMMENT '部门ID',
    PRIMARY KEY (`role_id`, `dept_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '角色和部门关联表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
INSERT INTO `sys_role_dept` VALUES (2, 100);
INSERT INTO `sys_role_dept` VALUES (2, 101);
INSERT INTO `sys_role_dept` VALUES (2, 105);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`
(
    `role_id` bigint NOT NULL COMMENT '角色ID',
    `menu_id` bigint NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '角色和菜单关联表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (2, 2000);
INSERT INTO `sys_role_menu` VALUES (2, 2001);
INSERT INTO `sys_role_menu` VALUES (2, 2002);
INSERT INTO `sys_role_menu` VALUES (2, 2003);
INSERT INTO `sys_role_menu` VALUES (2, 2004);
INSERT INTO `sys_role_menu` VALUES (2, 2005);
INSERT INTO `sys_role_menu` VALUES (2, 2006);
INSERT INTO `sys_role_menu` VALUES (2, 2020);
INSERT INTO `sys_role_menu` VALUES (2, 2021);
INSERT INTO `sys_role_menu` VALUES (2, 2022);
INSERT INTO `sys_role_menu` VALUES (2, 2023);
INSERT INTO `sys_role_menu` VALUES (2, 2024);
INSERT INTO `sys_role_menu` VALUES (2, 2025);
INSERT INTO `sys_role_menu` VALUES (2, 2026);
INSERT INTO `sys_role_menu` VALUES (2, 2027);
INSERT INTO `sys_role_menu` VALUES (2, 2028);
INSERT INTO `sys_role_menu` VALUES (2, 2029);
INSERT INTO `sys_role_menu` VALUES (2, 2061);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`
(
    `user_id`         bigint                                                        NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `dept_id`         bigint                                                        NULL DEFAULT NULL COMMENT '部门ID',
    `user_name`       varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '用户账号',
    `nick_name`       varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '用户昵称',
    `user_type`       varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci   NULL DEFAULT '00' COMMENT '用户类型（00系统用户）',
    `email`           varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '用户邮箱',
    `phonenumber`     varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '手机号码',
    `sex`             char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
    `avatar`          varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '头像地址',
    `password`        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '密码',
    `status`          char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
    `del_flag`        char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci      NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    `login_ip`        varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '最后登录IP',
    `login_date`      datetime                                                      NULL DEFAULT NULL COMMENT '最后登录时间',
    `pwd_update_date` datetime                                                      NULL DEFAULT NULL COMMENT '密码最后更新时间',
    `create_by`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '创建者',
    `create_time`     datetime                                                      NULL DEFAULT NULL COMMENT '创建时间',
    `update_by`       varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL DEFAULT '' COMMENT '更新者',
    `update_time`     datetime                                                      NULL DEFAULT NULL COMMENT '更新时间',
    `remark`          varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 6
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户信息表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 100, 'admin', '质衡', '00', 'qualitest@qq.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2026-07-19 21:40:21', '2026-01-20 15:38:21', 'admin', '2026-01-20 15:38:21', '', NULL, '管理员');
INSERT INTO `sys_user` VALUES (2, 101, 'projectOwner', '项目所有者', '00', 'qualitest@168.com', '15666666666', '0', '', '$2a$10$8UPHu.laiE54ve4UF5S5N.Sa/PoRnBoepkq/leJc66oCmmy4F5G6a', '0', '0', '127.0.0.1', '2026-05-17 12:58:34', '2026-04-05 21:37:46', 'admin', '2026-01-20 15:38:22', 'admin', '2026-04-05 21:37:46', '测试员');
INSERT INTO `sys_user` VALUES (3, 101, 'projectAdmin', '项目管理员', '00', '', '', '0', '', '$2a$10$pvJHoahgvDFy0VKnJZTyYe5kQZtBAq1UcKTPMbAKll2t.CdKKRFji', '0', '0', '127.0.0.1', '2026-04-07 16:48:35', '2026-04-05 21:42:33', 'admin', '2026-04-05 21:38:28', '', '2026-04-05 21:42:33', NULL);
INSERT INTO `sys_user` VALUES (4, 101, 'projectDeveloper', '项目开发者', '00', '', '', '0', '', '$2a$10$5LlcFL/bNXSqnDjcC0MhWum4DTdQjP2zUJR8Dwbs7Wy7zXC/4BdfC', '0', '0', '127.0.0.1', '2026-04-06 11:02:28', NULL, 'admin', '2026-04-05 21:39:20', '', NULL, NULL);
INSERT INTO `sys_user` VALUES (5, 101, 'projectTester', '项目测试员', '00', '', '', '0', '', '$2a$10$vOCx/EGDKdHRuDdbh7cEbe/5.LxrNNhKdo0h3ccKMw6IEDPoy0pGu', '0', '0', '127.0.0.1', '2026-04-06 11:02:38', '2026-04-05 21:45:03', 'admin', '2026-04-05 21:40:13', '', '2026-04-05 21:45:03', NULL);

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post`
(
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `post_id` bigint NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (`user_id`, `post_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户与岗位关联表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------
INSERT INTO `sys_user_post` VALUES (1, 1);
INSERT INTO `sys_user_post` VALUES (2, 2);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`
(
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `role_id` bigint NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户和角色关联表'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (2, 2);
INSERT INTO `sys_user_role` VALUES (3, 2);
INSERT INTO `sys_user_role` VALUES (4, 2);
INSERT INTO `sys_user_role` VALUES (5, 2);

-- ----------------------------
-- Table structure for test_flow
-- ----------------------------
DROP TABLE IF EXISTS `test_flow`;
CREATE TABLE `test_flow`
(
    `test_flow_id`             bigint                                                        NOT NULL COMMENT '测试流ID',
    `test_project_id`          bigint                                                        NOT NULL COMMENT '测试项目ID',
    `flow_name`                varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '测试流名称',
    `flow_description`         varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '测试流说明',
    `graph_json`               json                                                          NOT NULL COMMENT '流程图JSON',
    `api_health_warning_count` int                                                           NOT NULL DEFAULT 0 COMMENT 'API语义健康告警条数',
    `api_health_checked_at`    datetime                                                      NULL     DEFAULT NULL COMMENT '最近一次API语义健康检查时间',
    `api_health_warning_codes` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '告警类型摘要',
    `del_status`               tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`              datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`              datetime                                                      NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_flow_id`) USING BTREE,
    INDEX `idx_test_project_id` (`test_project_id` ASC) USING BTREE,
    INDEX `idx_api_health_warning` (`del_status` ASC, `api_health_warning_count` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试流'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_flow
-- ----------------------------

-- ----------------------------
-- Table structure for test_flow_run
-- ----------------------------
DROP TABLE IF EXISTS `test_flow_run`;
CREATE TABLE `test_flow_run`
(
    `test_flow_run_id`    bigint                                                       NOT NULL COMMENT '运行ID',
    `test_flow_id`        bigint                                                       NOT NULL COMMENT '测试流ID',
    `test_project_env_id` bigint                                                       NOT NULL COMMENT '测试项目环境ID',
    `run_scenario_id`     varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '运行场景ID',
    `status`              varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行状态（running执行中 passed成功 failed失败 cancelled取消）',
    `graph_json_snapshot` json                                                         NOT NULL COMMENT '流程图快照',
    `flow_snapshot`       json                                                         NULL COMMENT '流程变量快照',
    `run_execution_state` json                                                         NULL COMMENT '运行时状态',
    `paused_at`           datetime                                                     NULL     DEFAULT NULL COMMENT '暂停时间',
    `graph_fingerprint`   varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '流程图指纹哈希',
    `started_at`          datetime                                                     NOT NULL COMMENT '开始时间',
    `finished_at`         datetime                                                     NULL     DEFAULT NULL COMMENT '结束时间',
    `duration_ms`         bigint                                                       NULL     DEFAULT NULL COMMENT '耗时',
    `error_code`          varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '失败错误码',
    `error_message`       text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci        NULL COMMENT '失败错误信息',
    `trigger_type`        varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '触发方式（manual手动 ci持续集成 schedule定时）',
    `del_status`          tinyint                                                      NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`         datetime                                                     NOT NULL COMMENT '创建时间',
    `update_time`         datetime                                                     NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_flow_run_id`) USING BTREE,
    INDEX `idx_test_flow_id` (`test_flow_id` ASC, `started_at` ASC) USING BTREE,
    INDEX `idx_project_env` (`test_project_env_id` ASC, `started_at` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试流运行'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_flow_run
-- ----------------------------

-- ----------------------------
-- Table structure for test_flow_run_step
-- ----------------------------
DROP TABLE IF EXISTS `test_flow_run_step`;
CREATE TABLE `test_flow_run_step`
(
    `test_flow_run_step_id` bigint                                                        NOT NULL COMMENT '步骤ID',
    `test_flow_run_id`      bigint                                                        NOT NULL COMMENT '运行ID',
    `step_index`            int                                                           NOT NULL COMMENT '步骤序号',
    `node_id`               varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL DEFAULT '' COMMENT '节点ID',
    `node_type`             varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '节点类型',
    `node_name`             varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '节点名称',
    `status`                varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '步骤状态（passed成功 failed失败 skipped未执行）',
    `duration_ms`           bigint                                                        NULL     DEFAULT NULL COMMENT '步骤耗时（毫秒）',
    `edge_id`               varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '入边ID',
    `step_details`          json                                                          NOT NULL COMMENT '步骤详情JSON',
    `del_status`            tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`           datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`           datetime                                                      NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_flow_run_step_id`) USING BTREE,
    INDEX `idx_run_id` (`test_flow_run_id` ASC, `step_index` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试流运行步骤'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_flow_run_step
-- ----------------------------

-- ----------------------------
-- Table structure for test_project
-- ----------------------------
DROP TABLE IF EXISTS `test_project`;
CREATE TABLE `test_project`
(
    `test_project_id`     bigint                                                       NOT NULL COMMENT '测试项目ID',
    `project_name`        varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目名',
    `asset_variables`     json                                                         NOT NULL COMMENT '素材变量',
    `response_convention` json                                                         NULL COMMENT '响应约定',
    `auth_config`         json                                                         NULL COMMENT '项目鉴权配置：多套 Bearer 等',
    `last_api_sync_time`  datetime                                                     NULL     DEFAULT NULL COMMENT '最新API同步时间',
    `api_count`           int                                                          NOT NULL DEFAULT 0 COMMENT 'API数量',
    `owner_id`            bigint                                                       NOT NULL COMMENT '所有者ID',
    `del_status`          tinyint                                                      NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`         datetime                                                     NOT NULL COMMENT '创建时间',
    `update_time`         datetime                                                     NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_project_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project
-- ----------------------------

-- ----------------------------
-- Table structure for test_project_api
-- ----------------------------
DROP TABLE IF EXISTS `test_project_api`;
CREATE TABLE `test_project_api`
(
    `test_project_api_id` bigint                                                                        NOT NULL COMMENT '测试项目API ID',
    `test_project_id`     bigint                                                                        NOT NULL COMMENT '测试项目ID',
    `api_group_id`        bigint                                                                        NOT NULL COMMENT 'API分组ID',
    `api_status`          enum ('normal','deprecated') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API状态',
    `api_group`           varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                 NOT NULL COMMENT 'API分组',
    `api_name`            varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                 NOT NULL COMMENT 'API名称',
    `api_description`     text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                         NULL COMMENT 'API详细描述',
    `api_path`            varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                 NOT NULL COMMENT 'API路径',
    `protocol_type`       enum ('http') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                NOT NULL COMMENT '协议类型',
    `request_config`      json                                                                          NOT NULL COMMENT '请求结构（method/参数定义/schema，不含调试测值）',
    `headers`             json                                                                          NOT NULL COMMENT '调试发出的请求头 KV',
    `cookies`             json                                                                          NOT NULL COMMENT '调试发出的 Cookie KV',
    `response_config`     json                                                                          NOT NULL COMMENT '响应结构（条目/schema，不含示例正文）',
    `test_value_config`   json                                                                          NULL COMMENT '测值（参数默认值、body/响应示例）',
    `biz_code_config`     json                                                                          NULL COMMENT '业务code白名单',
    `auth_config`         json                                                                          NULL COMMENT '接口鉴权标签：是否免登录等',
    `sync_protected`      tinyint                                                                       NOT NULL DEFAULT 0 COMMENT '上传保护：1=插件/批量导入跳过本接口',
    `pre_request_script`  text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                         NULL COMMENT '前置操作脚本',
    `post_request_script` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci                         NULL COMMENT '后置操作脚本',
    `last_sync_time`      datetime                                                                      NULL     DEFAULT NULL COMMENT '最新同步时间',
    `del_status`          tinyint                                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`         datetime                                                                      NOT NULL COMMENT '创建时间',
    `update_time`         datetime                                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    `design_hints`        json                                                                          NULL COMMENT '造流设计提示（人机可维护，导入不覆盖）',
    PRIMARY KEY (`test_project_api_id`) USING BTREE,
    INDEX `idx_api_path` (`api_path` ASC) USING BTREE COMMENT '按路径检索',
    INDEX `idx_project_id` (`test_project_id` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目API'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project_api
-- ----------------------------

-- ----------------------------
-- Table structure for test_project_api_group
-- ----------------------------
DROP TABLE IF EXISTS `test_project_api_group`;
CREATE TABLE `test_project_api_group`
(
    `api_group_id`    bigint                                                        NOT NULL AUTO_INCREMENT COMMENT 'API分组ID',
    `test_project_id` bigint                                                        NOT NULL COMMENT '测试项目ID',
    `parent_id`       bigint                                                        NOT NULL DEFAULT 0 COMMENT '父分组ID',
    `ancestors`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '祖级列表',
    `group_name`      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称',
    `sort_num`        int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `del_status`      tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`     datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`     datetime                                                      NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`api_group_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目API分组'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project_api_group
-- ----------------------------

-- ----------------------------
-- Table structure for test_project_env
-- ----------------------------
DROP TABLE IF EXISTS `test_project_env`;
CREATE TABLE `test_project_env`
(
    `test_project_env_id`     bigint                                                        NOT NULL COMMENT '测试项目环境ID',
    `test_project_id`         bigint                                                        NOT NULL COMMENT '测试项目ID',
    `user_id`                 bigint                                                        NOT NULL COMMENT '用户ID',
    `share_status`            varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL DEFAULT 'private' COMMENT '共享状态（share共享 private私有）',
    `env_color`               varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NULL     DEFAULT NULL COMMENT '环境标识颜色',
    `env_name`                varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci  NOT NULL COMMENT '环境名称',
    `env_url`                 varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '环境URL',
    `env_variables`           json                                                          NOT NULL COMMENT '环境变量',
    `allow_destructive_reset` tinyint                                                       NOT NULL DEFAULT 0 COMMENT '是否允许还原被测数据（0否 1是）',
    `sort_num`                int                                                           NOT NULL DEFAULT 0 COMMENT '排序号',
    `del_status`              tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`             datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`             datetime                                                      NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_project_env_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目环境'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project_env
-- ----------------------------

-- ----------------------------
-- Table structure for test_project_member
-- ----------------------------
DROP TABLE IF EXISTS `test_project_member`;
CREATE TABLE `test_project_member`
(
    `test_project_member_id` bigint                                                       NOT NULL COMMENT '项目成员ID',
    `test_project_id`        bigint                                                       NOT NULL COMMENT '测试项目ID',
    `user_id`                bigint                                                       NOT NULL COMMENT '用户ID',
    `member_role`            varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'developer' COMMENT '成员角色（owner所有者 admin管理员 developer开发人员 tester测试人员）',
    `del_status`             tinyint                                                      NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`            datetime                                                     NOT NULL COMMENT '创建时间',
    `update_time`            datetime                                                     NULL     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`test_project_member_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目成员'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project_member
-- ----------------------------

-- ----------------------------
-- Table structure for test_project_template
-- ----------------------------
DROP TABLE IF EXISTS `test_project_template`;
CREATE TABLE `test_project_template`
(
    `test_project_template_id` bigint                                                        NOT NULL COMMENT '项目模板ID',
    `template_name`            varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
    `match_config`             json                                                          NULL COMMENT '路径匹配JSON，如pathPrefix',
    `template_apis`            json                                                          NOT NULL COMMENT '预制接口JSON数组，勾选进项目时种子接口',
    `template_params`          json                                                          NULL COMMENT '预制参数JSON数组：flow/env/asset',
    `template_flows`           json                                                          NULL COMMENT '预制测试流JSON数组；勾选时种子流并从extracts派生托管头',
    `builtin_status`           tinyint                                                       NOT NULL DEFAULT 0 COMMENT '内置状态（0自定义 1内置）',
    `enable_status`            tinyint                                                       NOT NULL DEFAULT 1 COMMENT '启用状态（0禁用 1启用）',
    `sort_num`                 int                                                           NOT NULL DEFAULT 0 COMMENT '排序',
    `remark`                   varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL     DEFAULT NULL COMMENT '备注',
    `del_status`               tinyint                                                       NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`              datetime                                                      NOT NULL COMMENT '创建时间',
    `update_time`              datetime                                                      NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_project_template_id`) USING BTREE,
    INDEX `idx_name_del` (`template_name` ASC, `del_status` ASC) USING BTREE,
    INDEX `idx_builtin_sort` (`builtin_status` ASC, `enable_status` ASC, `sort_num` ASC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目模板'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project_template
-- ----------------------------

INSERT INTO `test_project_template` VALUES (2100000000000000001, 'RuoYi Bearer', NULL, '[{"testProjectApiId":"2100000000000001101","apiName":"登录","apiPath":"/login","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"designHints":{"hints":["token 在 $.token，不要写成 $.data.token"]},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"code":{"type":"string"},"uuid":{"type":"string"},"password":{"type":"string"},"username":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-login","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"token":{"type":"string"}}},"httpStatus":200,"contentType":"json"}],"configVersion":1},"testValueConfig":{"request":{"bodyExample":{"password":"admin123","username":"admin"}},"response":{"examplesById":{"resp-login":{"msg":"操作成功","code":200,"token":"..."}}}}},{"testProjectApiId":"2100000000000001103","apiName":"注册","apiPath":"/register","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"password":{"type":"string"},"username":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"testValueConfig":{"request":{"bodyExample":{"password":"","username":""}},"response":{"examplesById":{"resp-register":{"msg":"操作成功","code":200}}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-register","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"}}}}]}},{"testProjectApiId":"2100000000000001102","apiName":"验证码","apiPath":"/captchaImage","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-captcha","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"captchaEnabled":{"type":"boolean"},"uuid":{"type":"string"},"img":{"type":"string"}}}}]},"testValueConfig":{"response":{"examplesById":{"resp-captcha":{"msg":"操作成功","code":200,"captchaEnabled":true,"uuid":"...","img":"..."}}}}},{"testProjectApiId":"2100000000000001104","apiName":"获取用户信息","apiPath":"/getInfo","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"designHints":{"hints":["探活：校验 asset 凭证是否仍有效"]},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-getInfo","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"user":{"type":"object","properties":{"userId":{"type":"integer"},"userName":{"type":"string"},"nickName":{"type":"string"},"email":{"type":"string"},"phonenumber":{"type":"string"},"sex":{"type":"string"},"avatar":{"type":"string"},"status":{"type":"string"},"deptId":{"type":"integer"}}},"roles":{"type":"array","items":{"type":"string"}},"permissions":{"type":"array","items":{"type":"string"}},"pwdChrtype":{"type":"string"},"isDefaultModifyPwd":{"type":"boolean"},"isPasswordExpired":{"type":"boolean"}}},"httpStatus":200,"contentType":"json"}],"configVersion":1}}]', '[]', '[{"flowName":"RuoYi Bearer 登录","graphJson":{"meta":{"layout":"manual","viewport":{"x":0,"y":0,"zoom":0.85},"scenarios":[{"id":"sc_builtin_login","name":"默认（冒烟）","remark":"","flowSeed":{},"testProjectEnvId":""}],"flowOutputs":[{"name":"adminAuth.token"}],"schemaVersion":1,"activeScenarioId":"sc_builtin_login"},"edges":[{"id":"e_token_if","source":"cond_token","target":"probe_http"},{"id":"e_token_else","source":"cond_token","target":"login_http"},{"id":"e_probe","source":"probe_http","target":"cond_alive"},{"id":"e_alive_else","source":"cond_alive","target":"login_http"}],"nodes":[{"id":"cond_token","data":{"name":"凭证是否存在","summary":"凭证是否存在","branches":[{"id":"b_token_if","kind":"if","target":"probe_http","conditions":[{"left":"asset.adminAuth.token","right":"","operator":"exists"}]},{"id":"b_token_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":80,"y":260}},{"id":"probe_http","data":{"name":"探活","apiPath":"/getInfo","summary":"GET 获取用户信息","callMode":"project","extracts":[],"timeoutMs":30000,"httpMethod":"GET","statusCheck":{"mode":"whitelist","values":[200,401]},"successCheck":{"mode":"off"},"testProjectApiId":"2100000000000001104","apiName":"获取用户信息"},"type":"http","position":{"x":480,"y":60}},{"id":"cond_alive","data":{"name":"凭证是否有效","summary":"凭证是否有效","branches":[{"id":"b_alive_if","kind":"if","terminal":true,"conditions":[{"left":"http.status","right":"200","operator":"eq"}]},{"id":"b_alive_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":960,"y":60}},{"id":"login_http","data":{"name":"登录","apiPath":"/login","summary":"POST 登录","callMode":"project","extracts":[{"expr":"$.token","from":"body","name":"","scope":"asset","entryKey":"adminAuth","fieldPath":"token"}],"timeoutMs":30000,"httpMethod":"POST","successCheck":{"mode":"inherit"},"testProjectApiId":"2100000000000001101","apiName":"登录"},"type":"http","position":{"x":680,"y":440}}]},"description":"探活复用或登录，抽出 asset.adminAuth.token"}]', 1, 1, 10, 'RuoYi Bearer', 0, '2026-08-19 10:54:45', NULL);
INSERT INTO `test_project_template` VALUES (2100000000000000002, 'RuoYi Session', NULL, '[{"testProjectApiId":"2100000000000002101","apiName":"登录","apiPath":"/login","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"designHints":{"hints":["Session 登录从 Set-Cookie 取 JSESSIONID"]},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"code":{"type":"string"},"uuid":{"type":"string"},"password":{"type":"string"},"username":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-login","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"}}},"httpStatus":200,"contentType":"json"}],"configVersion":1},"testValueConfig":{"request":{"bodyExample":{"password":"admin123","username":"admin"}},"response":{"examplesById":{"resp-login":{"msg":"操作成功","code":200}}}}},{"testProjectApiId":"2100000000000002102","apiName":"验证码","apiPath":"/captchaImage","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-captcha","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"captchaEnabled":{"type":"boolean"},"uuid":{"type":"string"},"img":{"type":"string"}}}}]},"testValueConfig":{"response":{"examplesById":{"resp-captcha":{"msg":"操作成功","code":200,"captchaEnabled":true,"uuid":"...","img":"..."}}}}},{"testProjectApiId":"2100000000000002103","apiName":"验证码(旧)","apiPath":"/captcha/captchaImage","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-captcha","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"captchaEnabled":{"type":"boolean"},"uuid":{"type":"string"},"img":{"type":"string"}}}}]},"testValueConfig":{"response":{"examplesById":{"resp-captcha":{"msg":"操作成功","code":200,"captchaEnabled":true,"uuid":"...","img":"..."}}}}},{"testProjectApiId":"2100000000000002104","apiName":"获取用户信息","apiPath":"/getInfo","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"designHints":{"hints":["探活：校验 asset 凭证是否仍有效"]},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-getInfo","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"user":{"type":"object","properties":{"userId":{"type":"integer"},"userName":{"type":"string"},"nickName":{"type":"string"},"email":{"type":"string"},"phonenumber":{"type":"string"},"sex":{"type":"string"},"avatar":{"type":"string"},"status":{"type":"string"},"deptId":{"type":"integer"}}},"roles":{"type":"array","items":{"type":"string"}},"permissions":{"type":"array","items":{"type":"string"}},"pwdChrtype":{"type":"string"},"isDefaultModifyPwd":{"type":"boolean"},"isPasswordExpired":{"type":"boolean"}}},"httpStatus":200,"contentType":"json"}],"configVersion":1}}]', '[]', '[{"flowName":"RuoYi Session 登录","graphJson":{"meta":{"layout":"manual","viewport":{"x":0,"y":0,"zoom":0.85},"scenarios":[{"id":"sc_builtin_login","name":"默认（冒烟）","remark":"","flowSeed":{},"testProjectEnvId":""}],"flowOutputs":[{"name":"adminAuth.jsessionId"}],"schemaVersion":1,"activeScenarioId":"sc_builtin_login"},"edges":[{"id":"e_token_if","source":"cond_token","target":"probe_http"},{"id":"e_token_else","source":"cond_token","target":"login_http"},{"id":"e_probe","source":"probe_http","target":"cond_alive"},{"id":"e_alive_else","source":"cond_alive","target":"login_http"}],"nodes":[{"id":"cond_token","data":{"name":"凭证是否存在","summary":"凭证是否存在","branches":[{"id":"b_token_if","kind":"if","target":"probe_http","conditions":[{"left":"asset.adminAuth.jsessionId","right":"","operator":"exists"}]},{"id":"b_token_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":80,"y":260}},{"id":"probe_http","data":{"name":"探活","apiPath":"/getInfo","summary":"GET 获取用户信息","callMode":"project","extracts":[],"timeoutMs":30000,"httpMethod":"GET","statusCheck":{"mode":"whitelist","values":[200,401]},"successCheck":{"mode":"off"},"testProjectApiId":"2100000000000002104","apiName":"获取用户信息"},"type":"http","position":{"x":480,"y":60}},{"id":"cond_alive","data":{"name":"凭证是否有效","summary":"凭证是否有效","branches":[{"id":"b_alive_if","kind":"if","terminal":true,"conditions":[{"left":"http.status","right":"200","operator":"eq"}]},{"id":"b_alive_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":960,"y":60}},{"id":"login_http","data":{"name":"登录","apiPath":"/login","summary":"POST 登录","callMode":"project","extracts":[{"expr":"JSESSIONID","from":"setCookie","name":"","scope":"asset","entryKey":"adminAuth","fieldPath":"jsessionId"}],"timeoutMs":30000,"httpMethod":"POST","successCheck":{"mode":"inherit"},"testProjectApiId":"2100000000000002101","apiName":"登录"},"type":"http","position":{"x":680,"y":440}}]},"description":"探活复用或登录，抽出 asset.adminAuth.jsessionId"}]', 1, 1, 20, 'RuoYi 传统 Session', 0, '2026-08-19 10:54:45', NULL);
INSERT INTO `test_project_template` VALUES (2100000000000000003, '客户端 Bearer', '{"pathPrefix":["/api/"]}', '[{"testProjectApiId":"2100000000000003101","apiName":"登录","apiPath":"/api/account/auth/login","apiGroup":"客户端.账号","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"designHints":{"hints":["客户端 token 在 $.data.token，不要写成 $.token"]},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"mobile":{"type":"string"},"password":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-login","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"data":{"type":"object","properties":{"accountId":{"type":"string"},"token":{"type":"string"},"nickName":{"type":"string"}}}}},"httpStatus":200,"contentType":"json"}],"configVersion":1},"testValueConfig":{"request":{"bodyExample":{"mobile":"13800000001","password":"Test@123456"}},"response":{"examplesById":{"resp-login":{"msg":"操作成功","code":200,"data":{"accountId":"1","token":"...","nickName":"演示用户"}}}}}},{"testProjectApiId":"2100000000000003102","apiName":"注册","apiPath":"/api/account/auth/register","apiGroup":"客户端.账号","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"mobile":{"type":"string"},"password":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"testValueConfig":{"request":{"bodyExample":{"mobile":"","password":""}},"response":{"examplesById":{"resp-register":{"msg":"操作成功","code":200,"data":{"accountId":"1","token":"...","nickName":"演示用户"}}}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-register","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"data":{"type":"object","properties":{"accountId":{"type":"string"},"token":{"type":"string"},"nickName":{"type":"string"}}}}}}]}},{"testProjectApiId":"2100000000000003103","apiName":"当前用户信息","apiPath":"/api/account/auth/profile","apiGroup":"客户端.账号","apiStatus":"normal","syncProtected":1,"designHints":{"hints":["探活：校验 asset.clientAuth.token 是否仍有效"]},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-profile","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"data":{"type":"object","properties":{"accountId":{"type":"string"},"nickName":{"type":"string"},"mobile":{"type":"string"},"balance":{"type":"number"},"gender":{"type":"integer"},"registerSource":{"type":"string"},"status":{"type":"integer"},"lastLoginIp":{"type":"string"},"lastLoginTime":{"type":"string","format":"date-time"},"remark":{"type":"string"}}}}},"httpStatus":200,"contentType":"json"}],"configVersion":1}}]', '[]', '[{"flowName":"客户端 Bearer 登录","graphJson":{"meta":{"layout":"manual","viewport":{"x":0,"y":0,"zoom":0.85},"scenarios":[{"id":"sc_builtin_login","name":"默认（冒烟）","remark":"","flowSeed":{},"testProjectEnvId":""}],"flowOutputs":[{"name":"clientAuth.token"}],"schemaVersion":1,"activeScenarioId":"sc_builtin_login"},"edges":[{"id":"e_token_if","source":"cond_token","target":"probe_http"},{"id":"e_token_else","source":"cond_token","target":"login_http"},{"id":"e_probe","source":"probe_http","target":"cond_alive"},{"id":"e_alive_else","source":"cond_alive","target":"login_http"}],"nodes":[{"id":"cond_token","data":{"name":"凭证是否存在","summary":"凭证是否存在","branches":[{"id":"b_token_if","kind":"if","target":"probe_http","conditions":[{"left":"asset.clientAuth.token","right":"","operator":"exists"}]},{"id":"b_token_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":80,"y":260}},{"id":"probe_http","data":{"name":"探活","apiPath":"/api/account/auth/profile","summary":"GET 当前用户信息","callMode":"project","extracts":[],"timeoutMs":30000,"httpMethod":"GET","statusCheck":{"mode":"whitelist","values":[200,401]},"successCheck":{"mode":"off"},"testProjectApiId":"2100000000000003103","apiName":"当前用户信息"},"type":"http","position":{"x":480,"y":60}},{"id":"cond_alive","data":{"name":"凭证是否有效","summary":"凭证是否有效","branches":[{"id":"b_alive_if","kind":"if","terminal":true,"conditions":[{"left":"http.status","right":"200","operator":"eq"}]},{"id":"b_alive_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":960,"y":60}},{"id":"login_http","data":{"name":"登录","apiPath":"/api/account/auth/login","summary":"POST 登录","callMode":"project","extracts":[{"expr":"$.data.token","from":"body","name":"","scope":"asset","entryKey":"clientAuth","fieldPath":"token"}],"timeoutMs":30000,"httpMethod":"POST","successCheck":{"mode":"inherit"},"testProjectApiId":"2100000000000003101","apiName":"登录"},"type":"http","position":{"x":680,"y":440}}]},"description":"探活复用或登录，抽出 asset.clientAuth.token"}]', 1, 1, 30, '商城 / 客户端 API', 0, '2026-08-19 10:54:45', NULL);
INSERT INTO `test_project_template` VALUES (2100000000000000004, '管理端 Bearer', '{"pathPrefix":["/system/","/monitor/","/tool/","/web/"]}', '[{"testProjectApiId":"2100000000000004101","apiName":"登录","apiPath":"/login","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"designHints":{"hints":["管理端 token 在 $.token → asset.adminAuth.token，不要写成 $.data.token"]},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"code":{"type":"string"},"uuid":{"type":"string"},"password":{"type":"string"},"username":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-login","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"token":{"type":"string"}}},"httpStatus":200,"contentType":"json"}],"configVersion":1},"testValueConfig":{"request":{"bodyExample":{"password":"admin123","username":"admin"}},"response":{"examplesById":{"resp-login":{"msg":"操作成功","code":200,"token":"..."}}}}},{"testProjectApiId":"2100000000000004103","apiName":"注册","apiPath":"/register","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"json":{"schema":{"type":"object","properties":{"password":{"type":"string"},"username":{"type":"string"}}}},"mode":"json"},"method":"POST","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"testValueConfig":{"request":{"bodyExample":{"password":"","username":""}},"response":{"examplesById":{"resp-register":{"msg":"操作成功","code":200}}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-register","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"}}}}]}},{"testProjectApiId":"2100000000000004102","apiName":"验证码","apiPath":"/captchaImage","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"authConfig":{"mode":"none"},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-captcha","name":"成功","httpStatus":200,"contentType":"json","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"captchaEnabled":{"type":"boolean"},"uuid":{"type":"string"},"img":{"type":"string"}}}}]},"testValueConfig":{"response":{"examplesById":{"resp-captcha":{"msg":"操作成功","code":200,"captchaEnabled":true,"uuid":"...","img":"..."}}}}},{"testProjectApiId":"2100000000000004104","apiName":"获取用户信息","apiPath":"/getInfo","apiGroup":"管理端.系统.登录","apiStatus":"normal","syncProtected":1,"designHints":{"hints":["探活：校验 asset 凭证是否仍有效"]},"protocolType":"http","requestConfig":{"body":{"mode":"none"},"method":"GET","pathParams":[],"queryParams":[],"configVersion":1,"declaredHeaders":[]},"responseConfig":{"responses":[{"id":"resp-getInfo","name":"成功","schema":{"type":"object","properties":{"msg":{"type":"string"},"code":{"type":"integer"},"user":{"type":"object","properties":{"userId":{"type":"integer"},"userName":{"type":"string"},"nickName":{"type":"string"},"email":{"type":"string"},"phonenumber":{"type":"string"},"sex":{"type":"string"},"avatar":{"type":"string"},"status":{"type":"string"},"deptId":{"type":"integer"}}},"roles":{"type":"array","items":{"type":"string"}},"permissions":{"type":"array","items":{"type":"string"}},"pwdChrtype":{"type":"string"},"isDefaultModifyPwd":{"type":"boolean"},"isPasswordExpired":{"type":"boolean"}}},"httpStatus":200,"contentType":"json"}],"configVersion":1}}]', '[]', '[{"flowName":"管理端 Bearer 登录","graphJson":{"meta":{"layout":"manual","viewport":{"x":0,"y":0,"zoom":0.85},"scenarios":[{"id":"sc_builtin_login","name":"默认（冒烟）","remark":"","flowSeed":{},"testProjectEnvId":""}],"flowOutputs":[{"name":"adminAuth.token"}],"schemaVersion":1,"activeScenarioId":"sc_builtin_login"},"edges":[{"id":"e_token_if","source":"cond_token","target":"probe_http"},{"id":"e_token_else","source":"cond_token","target":"login_http"},{"id":"e_probe","source":"probe_http","target":"cond_alive"},{"id":"e_alive_else","source":"cond_alive","target":"login_http"}],"nodes":[{"id":"cond_token","data":{"name":"凭证是否存在","summary":"凭证是否存在","branches":[{"id":"b_token_if","kind":"if","target":"probe_http","conditions":[{"left":"asset.adminAuth.token","right":"","operator":"exists"}]},{"id":"b_token_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":80,"y":260}},{"id":"probe_http","data":{"name":"探活","apiPath":"/getInfo","summary":"GET 获取用户信息","callMode":"project","extracts":[],"timeoutMs":30000,"httpMethod":"GET","statusCheck":{"mode":"whitelist","values":[200,401]},"successCheck":{"mode":"off"},"testProjectApiId":"2100000000000004104","apiName":"获取用户信息"},"type":"http","position":{"x":480,"y":60}},{"id":"cond_alive","data":{"name":"凭证是否有效","summary":"凭证是否有效","branches":[{"id":"b_alive_if","kind":"if","terminal":true,"conditions":[{"left":"http.status","right":"200","operator":"eq"}]},{"id":"b_alive_else","kind":"else","target":"login_http","conditions":[]}]},"type":"condition","position":{"x":960,"y":60}},{"id":"login_http","data":{"name":"登录","apiPath":"/login","summary":"POST 登录","callMode":"project","extracts":[{"expr":"$.token","from":"body","name":"","scope":"asset","entryKey":"adminAuth","fieldPath":"token"}],"timeoutMs":30000,"httpMethod":"POST","successCheck":{"mode":"inherit"},"testProjectApiId":"2100000000000004101","apiName":"登录"},"type":"http","position":{"x":680,"y":440}}]},"description":"探活复用或登录，抽出 asset.adminAuth.token"}]', 1, 1, 40, 'RuoYi 管理端', 0, '2026-08-19 10:54:45', NULL);

-- ----------------------------
-- Table structure for test_project_user_setting
-- ----------------------------
DROP TABLE IF EXISTS `test_project_user_setting`;
CREATE TABLE `test_project_user_setting`
(
    `test_project_user_setting_id` bigint                                                       NOT NULL COMMENT '测试项目用户设置ID',
    `test_project_id`              bigint                                                       NOT NULL COMMENT '测试项目ID',
    `user_id`                      bigint                                                       NOT NULL COMMENT '用户ID',
    `project_token`                varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '项目Token',
    `test_project_env_id`          bigint                                                       NOT NULL DEFAULT 0 COMMENT '测试项目环境ID',
    `del_status`                   tinyint                                                      NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
    `create_time`                  datetime                                                     NOT NULL COMMENT '创建时间',
    `update_time`                  datetime                                                     NULL     DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`test_project_user_setting_id` DESC) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '测试项目用户设置'
  ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of test_project_user_setting
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
