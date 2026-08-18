-- Flyway V1 baseline from sql/qualitest_20260722_135127.sql
-- Connected DB is target; no CREATE DATABASE / USE in script
-- Empty DB: migrate; existing DB: baseline-on-migrate (see docs/deploy.md)

-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: qualitest
-- ------------------------------------------------------
-- Server version	8.0.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_chat_message`
--

DROP TABLE IF EXISTS `ai_chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_message` (
  `ai_chat_message_id` bigint NOT NULL COMMENT '消息ID',
  `ai_chat_session_id` bigint NOT NULL COMMENT '会话ID',
  `message_role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息角色（system系统 user用户 assistant模型）',
  `ai_llm_model_id` bigint DEFAULT NULL COMMENT '模型ID',
  `message_content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
  `thinking_content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '思考内容',
  `result_meta_json` json DEFAULT NULL COMMENT '结果摘要',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`ai_chat_message_id`),
  KEY `idx_session_time` (`ai_chat_session_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 会话消息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_message`
--

LOCK TABLES `ai_chat_message` WRITE;
/*!40000 ALTER TABLE `ai_chat_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_chat_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_session`
--

DROP TABLE IF EXISTS `ai_chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_session` (
  `ai_chat_session_id` bigint NOT NULL COMMENT '会话ID',
  `test_project_id` bigint NOT NULL COMMENT '项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_scene` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话场景',
  `biz_ref_json` json NOT NULL COMMENT '业务锚点',
  `current_model_id` bigint NOT NULL COMMENT '当前模型ID',
  `thinking_enabled` tinyint DEFAULT NULL COMMENT '思考开关（0关 1开 NULL跟随模型默认）',
  `session_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话标题',
  `context_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '滚动会话摘要（Checkpoint）',
  `summary_message_count` int DEFAULT NULL COMMENT '摘要已覆盖的消息条数',
  `summary_updated_at` datetime DEFAULT NULL COMMENT '摘要最近更新时间',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`ai_chat_session_id`),
  KEY `idx_scene_user` (`session_scene`,`user_id`),
  KEY `idx_project_scene` (`test_project_id`,`session_scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 会话';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_session`
--

LOCK TABLES `ai_chat_session` WRITE;
/*!40000 ALTER TABLE `ai_chat_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_chat_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_llm_model`
--

DROP TABLE IF EXISTS `ai_llm_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_llm_model` (
  `ai_llm_model_id` bigint NOT NULL COMMENT '模型ID',
  `ai_llm_vendor_id` bigint NOT NULL COMMENT '厂商ID',
  `model_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '上游模型ID',
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '展示名',
  `builtin_status` tinyint NOT NULL DEFAULT '0' COMMENT '内置状态（0自定义 1内置）',
  `enable_status` tinyint NOT NULL DEFAULT '1' COMMENT '启用状态（0禁用 1启用）',
  `thinking_capable` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持思考（0否 1是）',
  `thinking_default` tinyint NOT NULL DEFAULT '0' COMMENT '默认是否开启思考（0关 1开）',
  `thinking_budget_tokens` int DEFAULT NULL COMMENT '思考 token 预算，NULL 用全局默认',
  `sort_num` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`ai_llm_model_id`),
  UNIQUE KEY `uk_vendor_model_name` (`ai_llm_vendor_id`,`model_name`,`del_status`),
  KEY `idx_vendor_enable_sort` (`ai_llm_vendor_id`,`enable_status`,`sort_num`),
  KEY `idx_vendor_builtin` (`ai_llm_vendor_id`,`builtin_status`,`enable_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 模型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_llm_model`
--

LOCK TABLES `ai_llm_model` WRITE;
/*!40000 ALTER TABLE `ai_llm_model` DISABLE KEYS */;
INSERT INTO `ai_llm_model` VALUES (2066887079509553152,2066777753641959424,'deepseek-v4-flash','deepseek-v4-flash',0,1,1,1,NULL,1,NULL,0,'2026-06-16 22:14:15','2026-06-19 18:50:05'),(2066887156454060032,2066777753641959424,'deepseek-v4-pro','deepseek-v4-pro',0,1,1,1,NULL,2,NULL,0,'2026-06-16 22:14:34','2026-06-19 18:50:10'),(2067000000000000101,2066777753641959424,'deepseek-chat','DeepSeek Chat',1,0,0,0,NULL,10,NULL,0,'2026-06-23 11:43:02',NULL),(2067000000000000102,2066777753641959424,'deepseek-reasoner','DeepSeek Reasoner',1,0,1,1,NULL,20,NULL,0,'2026-06-23 11:43:03',NULL);
/*!40000 ALTER TABLE `ai_llm_model` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_llm_vendor`
--

DROP TABLE IF EXISTS `ai_llm_vendor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_llm_vendor` (
  `ai_llm_vendor_id` bigint NOT NULL COMMENT '厂商ID',
  `vendor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '厂商名',
  `builtin_status` tinyint NOT NULL DEFAULT '0' COMMENT '内置状态（0自定义 1内置）',
  `template_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模板ID',
  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'openai_compatible' COMMENT '协议标识',
  `discovery_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发现类型',
  `base_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API Base URL',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密钥明文',
  `enable_status` tinyint NOT NULL DEFAULT '1' COMMENT '启用状态（0禁用 1启用）',
  `sort_num` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`ai_llm_vendor_id`),
  UNIQUE KEY `uk_vendor_name` (`vendor_name`,`del_status`),
  KEY `idx_enable_sort` (`enable_status`,`sort_num`),
  KEY `idx_builtin_sort` (`builtin_status`,`enable_status`,`sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 厂商';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_llm_vendor`
--

LOCK TABLES `ai_llm_vendor` WRITE;
/*!40000 ALTER TABLE `ai_llm_vendor` DISABLE KEYS */;
INSERT INTO `ai_llm_vendor` VALUES (2066777753641959424,'Deepseek',0,'deepseek','openai_compatible',NULL,'https://api.deepseek.com','sk-28e0e2f5491f436594922f28d5b51cde',1,1,NULL,0,'2026-06-16 14:59:50',NULL),(2067000000000000002,'OpenAI',1,'openai','openai_compatible','openai_models','https://api.openai.com/v1',NULL,0,20,NULL,0,'2026-06-23 11:42:53',NULL),(2067000000000000003,'硅基流动',1,'siliconflow','openai_compatible','openai_models','https://api.siliconflow.cn/v1',NULL,0,30,NULL,0,'2026-06-23 11:42:54',NULL),(2067000000000000004,'Anthropic',1,'anthropic','anthropic_compatible','anthropic_models','https://api.anthropic.com',NULL,0,40,NULL,0,'2026-06-23 11:42:54',NULL),(2067000000000000005,'Ollama',1,'ollama','openai_compatible','ollama_tags','http://127.0.0.1:11434/v1',NULL,0,50,NULL,0,'2026-06-23 11:42:55',NULL),(2067000000000000006,'自定义 OpenAI 兼容',1,'custom_openai','openai_compatible','openai_models','',NULL,0,100,NULL,0,'2026-06-23 11:42:56',NULL);
/*!40000 ALTER TABLE `ai_llm_vendor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_prompt_template`
--

DROP TABLE IF EXISTS `ai_prompt_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_prompt_template` (
  `ai_prompt_template_id` bigint NOT NULL COMMENT '提示词模板ID',
  `template_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板范围（platform平台 project项目）',
  `test_project_id` bigint DEFAULT NULL COMMENT '测试项目ID',
  `session_scene` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'test_flow_design' COMMENT '会话场景',
  `template_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板标题',
  `template_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模板说明',
  `template_content` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板正文',
  `builtin_status` tinyint NOT NULL DEFAULT '0' COMMENT '内置状态（0自定义 1内置）',
  `enable_status` tinyint NOT NULL DEFAULT '1' COMMENT '启用状态（0禁用 1启用）',
  `sort_num` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`ai_prompt_template_id`) USING BTREE,
  KEY `idx_project_scene` (`test_project_id`,`session_scene`,`enable_status`,`del_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI提示词模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_prompt_template`
--

LOCK TABLES `ai_prompt_template` WRITE;
/*!40000 ALTER TABLE `ai_prompt_template` DISABLE KEYS */;
INSERT INTO `ai_prompt_template` VALUES (2070000000000000101,'platform',NULL,'test_flow_design','从零搭建','新建 test_flow，画布为空或仅有 start 节点','为本测试流从零搭建「{流程名称}」：\n1. {步骤 1 业务描述}\n2. {步骤 2 业务描述}\n3. {步骤 3 业务描述}\n\n要求：\n- 需登录时开头只登录一次（或挂登录子流），extract 按端写入 flow.token/adminToken（客户端 $.data.token，管理端 $.token），后续勿重复登录\n- 关键步骤后校验 {期望结果}\n- 线性串联即可，暂不加分支',1,1,101,'从零搭建',0,'2026-07-04 21:37:19',NULL),(2070000000000000102,'platform',NULL,'test_flow_design','带子流/外联搭建','登录子流或外联环境变量','搭建「{流程名称}」：\n- 登录：优先挂平台/项目登录子流（如 Bearer 登录），outputs 映射 flow.token 或 adminToken；主流程后续只引用 {{flow.*}}，勿逐步再登录\n- 后续业务：{描述}\n- OAuth / 验证码等：优先用平台子流模板；若无则外联 {{env.*}} 环境变量',1,1,102,'从零搭建',0,'2026-07-04 21:37:19',NULL),(2070000000000000103,'platform',NULL,'test_flow_design','预期业务拒绝','失败路径：关业务 Code + 失败断言','搭建「预期业务拒绝」流「{流程名称}」：\n1. 绑定接口「{失败接口，如停用账号登录}」\n2. HTTP successCheck.mode=off（禁止 inherit 业务成功码）\n3. assert：http.body.code 不等于 200（或项目成功码），和/或 http.body.msg 包含「{拒绝文案}」\n4. 测值用场景真实账号（优先 {{asset.*}}），勿写成成功登录占位\n\n要求：本流预期业务失败；登录口勿补 Bearer；勿 inherit 业务成功码。',1,1,103,'从零搭建',0,'2026-07-04 21:37:19',NULL),(2070000000000000104,'platform',NULL,'test_flow_design','确保登录态','有 token 则跳过登录，否则登录/子流','确保本流具备登录态后再执行「{业务步骤}」：\n1. 若 flow.token（或 adminToken）已有（含 flowSeed），跳过登录直接业务\n2. 否则走登录 HTTP 或登录子流，按端 extract 写入 flow.*\n3. 口令用 {{asset.*}}，禁止 flowSeed 塞密码；同流只登录一次\n\n可用 condition 判断 flow.token 非空后分支。',1,1,104,'从零搭建',0,'2026-07-04 21:37:19',NULL),(2070000000000000201,'platform',NULL,'test_flow_design','末尾追加步骤','画布已有节点，在末尾追加','在当前测试流末尾追加：\n{新业务步骤描述}\n\n不要删除或修改现有节点，除非与 {某冲突点} 冲突。\n新增节点与现有最后一步 HTTP 之间用 assert 校验 {期望状态}。',1,1,201,'扩展现有流',0,'2026-07-04 21:37:19',NULL),(2070000000000000202,'platform',NULL,'test_flow_design','插入中间步骤','在两节点之间插入新步骤','在 @{Node:某HTTP节点} 与 @{Node:下一节点} 之间插入：\n{新步骤描述}\n\n后续节点若依赖旧 extract 变量，请同步 update 占位符引用。',1,1,202,'扩展现有流',0,'2026-07-04 21:37:19',NULL),(2070000000000000301,'platform',NULL,'test_flow_design','复制/变异场景','同一流程测多种业务分支','基于当前主流拓扑，新增运行场景「{场景名}」：\n- 环境：{环境名或 testProjectEnvId}\n- flowSeed 初值：{键名=值，如 loginMobile=13800000002}\n- 默认场景保持不变\n\n并在 scenarioPatch 中 addScenarios；HTTP 节点用 {{flow.*}} 引用 seed，不要写死账号。',1,1,301,'运行场景',0,'2026-07-04 21:37:19',NULL),(2070000000000000401,'platform',NULL,'test_flow_design','API 变更-参数/路径','IDEA 同步后修复单接口','项目 API「{apiName}」已更新，变更如下：\n- {变更点 1，如 path /api/login → /api/v2/auth/login}\n- {变更点 2，如 请求体 password → credential}\n\n请扫描本测试流，仅修复引用该 API 的 HTTP 节点及相关 assert/extract，\n不要改动无关节点和场景配置。',1,1,401,'API 变更修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000402,'platform',NULL,'test_flow_design','API 变更-响应结构','响应 JSONPath 变更','接口「{apiName}」响应 JSON 已变：{旧 JSONPath} → {新 JSONPath}。\n请更新所有相关节点的 extracts 和 assertRules；\nrequestConfig 若无变化则不要修改。',1,1,402,'API 变更修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000403,'platform',NULL,'test_flow_design','API 变更-批量','多接口同步更新','以下接口已在 IDEA 同步更新：{apiName1}、{apiName2}、{apiName3}。\n请仅提交最小改动，优先 updateNode，避免 deleteNode 除非接口已删除。',1,1,403,'API 变更修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000404,'platform',NULL,'test_flow_design','重置为 API 资产','清空历史复制的 requestConfig','以下 HTTP 节点 @{Node:…} 的 requestConfig 仅为历史复制，无业务定制：\n请清空 requestConfig 与 apiPath 覆盖，仅保留接口绑定与 extracts/asserts。',1,1,404,'API 变更修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000501,'platform',NULL,'test_flow_design','Run 失败修复','结合 @Run 与 @Node 分析','@{Run:最近一次失败Run} 在 @{Node:失败节点名} 失败。\n请分析是请求参数、断言还是 extract 问题并修复。\n不要改动与本次失败无关的节点。',1,1,501,'Run 失败修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000502,'platform',NULL,'test_flow_design','Run 失败修复（无@Run）','按步骤名定位失败','上次运行在本测试流失败，步骤名「{nodeName}」。\n请定位原因并修复相关 HTTP / assert 节点。',1,1,502,'Run 失败修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000503,'platform',NULL,'test_flow_design','断言过严/过松','按实际业务调整断言','@{Run:…} 中 @{Node:…} 断言失败，但实际业务 {接受/拒绝} 是符合预期的。\n请根据失败 Run 的响应体，将断言调整为 {期望语义}，不要改 HTTP 请求参数。',1,1,503,'Run 失败修复',0,'2026-07-04 21:37:19',NULL),(2070000000000000601,'platform',NULL,'test_flow_design','插入登录子流','引用平台/项目子流','在本流开头插入登录子流「{模板名}」：\n- 若场景 flowSeed 已有对应 token/adminToken，可跳过登录子流\n- 否则 fork 子流：inputs {name=value}；outputs 映射到 flow.token 或 flow.adminToken（按端）\n- extract JsonPath 对齐该端（客户端 $.data.token，管理端 $.token）\n\n主流程后续 HTTP 只用 {{flow.*}}，勿重复登录。',1,1,601,'子流编排',0,'2026-07-04 21:37:19',NULL),(2070000000000000602,'platform',NULL,'test_flow_design','抽取子流','将节点链重构为子流','将 @{Node:A} 到 @{Node:B} 之间的节点抽成可复用子流「{子流名}」：\n1. 说明建议的 inputs/outputs 映射\n2. 主流程用 subflow 节点替换原节点链\n3. 不要改变对外 flow 变量名（或列出 rename 对照）',1,1,602,'子流编排',0,'2026-07-04 21:37:19',NULL),(2070000000000000701,'platform',NULL,'test_flow_design','新增运行场景','仅调整场景配置','为本测试流配置运行场景：\n- 新增场景「{场景名}」，绑定环境「{环境名}」\n- flowSeed：{键=值}\n- 设为默认运行场景\n\n不要修改 graph 节点，仅调整场景配置。',1,1,701,'运行场景',0,'2026-07-04 21:37:19',NULL),(2070000000000000702,'platform',NULL,'test_flow_design','检查场景配置','修正环境绑定与 flowSeed','检查当前所有场景的环境绑定与 flowSeed 是否合理；\n若有场景仍指向 {错误环境名}，请修正。',1,1,702,'运行场景',0,'2026-07-04 21:37:19',NULL),(2070000000000000801,'platform',NULL,'test_flow_design','加强断言','为 HTTP 及下游补 assert','为 @{Node:某HTTP} 及其下游补充 assert：\n- HTTP 状态码 2xx\n- 响应体 {业务字段} 符合 {条件}\n- 若已有 assert 节点，update 而非重复 add',1,1,801,'断言与变量',0,'2026-07-04 21:37:19',NULL),(2070000000000000802,'platform',NULL,'test_flow_design','补 extract','从响应提取 flow 变量','@{Node:某HTTP} 响应中需要提取 {变量名} 供后续使用，JSONPath {路径}，scope=flow。\n若为登录口：优先按项目 loginHint / get_api_detail.suggestedExtracts（客户端 $.data.token→token，管理端 $.token→adminToken），勿混端。\n请 updateNode 添加 extracts，并检查下游是否引用 {{flow.{变量名}}}。',1,1,802,'断言与变量',0,'2026-07-04 21:37:19',NULL),(2070000000000000803,'platform',NULL,'test_flow_design','补 condition 分支','按响应字段分支','在 @{Node:某HTTP} 之后按响应 {字段} 分支：\n- 等于 {值 A} → {目标步骤/节点语义}\n- 否则 → {另一路径}',1,1,803,'断言与变量',0,'2026-07-04 21:37:19',NULL),(2070000000000000901,'platform',NULL,'test_flow_design','重构整理','不改变业务语义整理拓扑','当前测试流拓扑较乱，请在不改变业务语义的前提下：\n- 合并重复 HTTP 调用\n- 统一节点命名\n- 删除明显冗余的断言\n不要 delete 仍在用的节点。',1,1,901,'重构整理',0,'2026-07-04 21:37:19',NULL),(2070000000000000902,'platform',NULL,'test_flow_design','外联改绑定 API','将外联 HTTP 改为项目接口','将所有外联 HTTP 改为绑定项目已登记接口（若存在对应 API）。',1,1,902,'重构整理',0,'2026-07-04 21:37:19',NULL),(2070000000000001001,'platform',NULL,'test_flow_design','仅答疑-流程说明','只读分析，不改图','说明当前测试流的主路径、关键 flow 变量和运行场景配置。本次仅解释，不要改图。',1,1,1001,'仅答疑',0,'2026-07-04 21:37:19',NULL),(2070000000000001002,'platform',NULL,'test_flow_design','仅答疑-接口引用','分析 Api 在流中的使用','@{Api:某接口} 在本测试流中被哪些节点引用？各节点主要传什么参数？只读分析，不要改图。',1,1,1002,'仅答疑',0,'2026-07-04 21:37:19',NULL),(2070000000000002101,'platform',NULL,'test_api_design','补手机号约束','string + pattern / maxLength','为当前接口 Query/Path/Body 中的 mobile（或手机号字段）补结构约束：\n- type=string\n- pattern 匹配国内 11 位手机号\n- maxLength=11\n- 可附带 description\n\n禁止 enum/const。请调用工具后提交 patch，不要自动保存。',1,1,101,'补约束',0,'2026-07-22 13:51:11',NULL),(2070000000000002102,'platform',NULL,'test_api_design','补必填与区间约束','required / min max','根据接口语义，为关键必填参数补 required；数值参数补合理 minValue/maxValue（schema 用 minimum/maximum）。\n改 type 时清理不兼容约束。禁止 enum/const。提交 patch，不落库。',1,1,102,'补约束',0,'2026-07-22 13:51:11',NULL),(2070000000000002103,'platform',NULL,'test_api_design','测值用素材占位符','{{asset.*}} 默认测值','为当前接口缺少默认测值的参数补测值：\n- 优先 list_asset_variables 选用 {{asset.key.field}}\n- 环境相关用 {{env.*}}\n- 禁止写死密码、Token、密钥\n\n仅改测值层，不要把约束写进 testValue。提交 patch。',1,1,201,'补测值与变量',0,'2026-07-22 13:51:11',NULL),(2070000000000002104,'platform',NULL,'test_api_design','HMAC 签名前置脚本','动态签名 Header','为当前接口生成前置脚本：\n- 使用环境变量 signSecret（api.environment.get(\'signSecret\')）\n- 对请求体原文做 HMAC-SHA256，结果写入 Header X-Sign\n- 同时添加 X-Timestamp（当前毫秒时间戳）\n\n仅改前置脚本（script:pre）。',1,1,301,'写前置/后置脚本',0,'2026-07-22 13:51:11',NULL),(2070000000000002105,'platform',NULL,'test_api_design','状态码与业务码断言','api.test 后置断言','为当前接口生成后置断言：\n- HTTP 状态码 2xx\n- 响应 JSON 中 code 等于 0\n- 使用 api.test 块\n\n仅改后置脚本（script:post）。',1,1,302,'写前置/后置脚本',0,'2026-07-22 13:51:11',NULL),(2070000000000002106,'platform',NULL,'test_api_design','提取 token 变量','JSON 提取到 variables','从响应 JSON 提取 accessToken 到 api.variables（键名 token）。\n路径：$.data.accessToken\n\n仅改后置脚本。',1,1,303,'写前置/后置脚本',0,'2026-07-22 13:51:11',NULL),(2070000000000002107,'platform',NULL,'test_api_design','辅助请求获取 Token','sendRequest 获取 token','前置脚本先调用辅助接口获取 token：\n- POST 路径 /api/auth/token（baseUrl 用 api.environment.get(\'baseUrl\')）\n- 成功后将 token 写入 api.variables，并添加 Authorization Header\n\n仅改前置脚本。',1,1,304,'写前置/后置脚本',0,'2026-07-22 13:51:11',NULL),(2070000000000002108,'platform',NULL,'test_api_design','补接口说明','apiDescription','根据当前接口 method/path/参数摘要，撰写简洁的 apiDescription（中文，一两段），说明用途与关键入参/出参。\n提交 meta:apiDescription patch，不改脚本与约束。',1,1,401,'补接口说明',0,'2026-07-22 13:51:11',NULL),(2070000000000002109,'platform',NULL,'test_api_design','修复脚本执行失败','根据错误日志修复','上次调试脚本失败，错误信息：\n{粘贴错误日志}\n\n请根据当前脚本与接口参数摘要修复，仅改出问题的阶段。',1,1,402,'写前置/后置脚本',0,'2026-07-22 13:51:11',NULL),(2070000000000002110,'platform',NULL,'test_api_design','仅答疑不改配置','解释结构/脚本，不提交 patch','解释当前接口参数、约束、测值或前置/后置脚本在做什么。本次仅说明，不要调用 submit_api_design_patch。',1,1,501,'答疑',0,'2026-07-22 13:51:11',NULL);
/*!40000 ALTER TABLE `ai_prompt_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table`
--

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`)
) ENGINE=InnoDB AUTO_INCREMENT=188 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table_column`
--

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_blob_triggers`
--

DROP TABLE IF EXISTS `qrtz_blob_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_blob_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Blob类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_blob_triggers`
--

LOCK TABLES `qrtz_blob_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_blob_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_blob_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_calendars`
--

DROP TABLE IF EXISTS `qrtz_calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_calendars` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日历信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_calendars`
--

LOCK TABLES `qrtz_calendars` WRITE;
/*!40000 ALTER TABLE `qrtz_calendars` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_calendars` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_cron_triggers`
--

DROP TABLE IF EXISTS `qrtz_cron_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_cron_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Cron类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_cron_triggers`
--

LOCK TABLES `qrtz_cron_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_cron_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_cron_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_fired_triggers`
--

DROP TABLE IF EXISTS `qrtz_fired_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_fired_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='已触发的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_fired_triggers`
--

LOCK TABLES `qrtz_fired_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_fired_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_fired_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_job_details`
--

DROP TABLE IF EXISTS `qrtz_job_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_job_details` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_job_details`
--

LOCK TABLES `qrtz_job_details` WRITE;
/*!40000 ALTER TABLE `qrtz_job_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_job_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_locks`
--

DROP TABLE IF EXISTS `qrtz_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_locks` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='存储的悲观锁信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_locks`
--

LOCK TABLES `qrtz_locks` WRITE;
/*!40000 ALTER TABLE `qrtz_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='暂停的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_paused_trigger_grps`
--

LOCK TABLES `qrtz_paused_trigger_grps` WRITE;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_scheduler_state`
--

DROP TABLE IF EXISTS `qrtz_scheduler_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_scheduler_state` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度器状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_scheduler_state`
--

LOCK TABLES `qrtz_scheduler_state` WRITE;
/*!40000 ALTER TABLE `qrtz_scheduler_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_scheduler_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simple_triggers`
--

DROP TABLE IF EXISTS `qrtz_simple_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simple_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='简单触发器的信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simple_triggers`
--

LOCK TABLES `qrtz_simple_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simple_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simple_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simprop_triggers`
--

DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simprop_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='同步机制的行锁表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simprop_triggers`
--

LOCK TABLES `qrtz_simprop_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_triggers`
--

DROP TABLE IF EXISTS `qrtz_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_triggers` (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触发器详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_triggers`
--

LOCK TABLES `qrtz_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'主框架页-默认皮肤样式名称','sys.index.skinName','skin-blue','Y','admin','2026-01-20 15:38:28','',NULL,'蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),(2,'用户管理-账号初始密码','sys.user.initPassword','123456','Y','admin','2026-01-20 15:38:28','',NULL,'初始化密码 123456'),(3,'主框架页-侧边栏主题','sys.index.sideTheme','theme-light','Y','admin','2026-01-20 15:38:28','admin','2026-02-05 16:48:12','深色主题theme-dark，浅色主题theme-light'),(4,'账号自助-验证码开关','sys.account.captchaEnabled','false','Y','admin','2026-01-20 15:38:28','admin','2026-04-07 15:11:45','是否开启验证码功能（true开启，false关闭）'),(5,'账号自助-是否开启用户注册功能','sys.account.registerUser','false','Y','admin','2026-01-20 15:38:28','',NULL,'是否开启注册用户功能（true开启，false关闭）'),(6,'用户登录-黑名单列表','sys.login.blackIPList','','Y','admin','2026-01-20 15:38:28','',NULL,'设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),(7,'用户管理-初始密码修改策略','sys.account.initPasswordModify','0','Y','admin','2026-01-20 15:38:28','admin','2026-04-07 15:11:27','0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框'),(8,'用户管理-账号密码更新周期','sys.account.passwordValidateDays','0','Y','admin','2026-01-20 15:38:28','',NULL,'密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (100,0,'0','质衡科技',0,'质衡','15888888888','ry@qq.com','0','0','admin','2026-01-20 15:38:20','',NULL),(101,100,'0,100','测试部门',1,'质衡','15888888888','ry@qq.com','0','0','admin','2026-01-20 15:38:20','',NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1,1,'男','0','sys_user_sex','','','Y','0','admin','2026-01-20 15:38:27','',NULL,'性别男'),(2,2,'女','1','sys_user_sex','','','N','0','admin','2026-01-20 15:38:27','',NULL,'性别女'),(3,3,'未知','2','sys_user_sex','','','N','0','admin','2026-01-20 15:38:27','',NULL,'性别未知'),(4,1,'显示','0','sys_show_hide','','primary','Y','0','admin','2026-01-20 15:38:27','',NULL,'显示菜单'),(5,2,'隐藏','1','sys_show_hide','','danger','N','0','admin','2026-01-20 15:38:27','',NULL,'隐藏菜单'),(6,1,'正常','0','sys_normal_disable','','primary','Y','0','admin','2026-01-20 15:38:27','',NULL,'正常状态'),(7,2,'停用','1','sys_normal_disable','','danger','N','0','admin','2026-01-20 15:38:27','',NULL,'停用状态'),(8,1,'正常','0','sys_job_status','','primary','Y','0','admin','2026-01-20 15:38:27','',NULL,'正常状态'),(9,2,'暂停','1','sys_job_status','','danger','N','0','admin','2026-01-20 15:38:27','',NULL,'停用状态'),(10,1,'默认','DEFAULT','sys_job_group','','','Y','0','admin','2026-01-20 15:38:27','',NULL,'默认分组'),(11,2,'系统','SYSTEM','sys_job_group','','','N','0','admin','2026-01-20 15:38:27','',NULL,'系统分组'),(12,1,'是','Y','sys_yes_no','','primary','Y','0','admin','2026-01-20 15:38:27','',NULL,'系统默认是'),(13,2,'否','N','sys_yes_no','','danger','N','0','admin','2026-01-20 15:38:27','',NULL,'系统默认否'),(14,1,'通知','1','sys_notice_type','','warning','Y','0','admin','2026-01-20 15:38:27','',NULL,'通知'),(15,2,'公告','2','sys_notice_type','','success','N','0','admin','2026-01-20 15:38:27','',NULL,'公告'),(16,1,'正常','0','sys_notice_status','','primary','Y','0','admin','2026-01-20 15:38:27','',NULL,'正常状态'),(17,2,'关闭','1','sys_notice_status','','danger','N','0','admin','2026-01-20 15:38:27','',NULL,'关闭状态'),(18,99,'其他','0','sys_oper_type','','info','N','0','admin','2026-01-20 15:38:27','',NULL,'其他操作'),(19,1,'新增','1','sys_oper_type','','info','N','0','admin','2026-01-20 15:38:27','',NULL,'新增操作'),(20,2,'修改','2','sys_oper_type','','info','N','0','admin','2026-01-20 15:38:27','',NULL,'修改操作'),(21,3,'删除','3','sys_oper_type','','danger','N','0','admin','2026-01-20 15:38:28','',NULL,'删除操作'),(22,4,'授权','4','sys_oper_type','','primary','N','0','admin','2026-01-20 15:38:28','',NULL,'授权操作'),(23,5,'导出','5','sys_oper_type','','warning','N','0','admin','2026-01-20 15:38:28','',NULL,'导出操作'),(24,6,'导入','6','sys_oper_type','','warning','N','0','admin','2026-01-20 15:38:28','',NULL,'导入操作'),(25,7,'强退','7','sys_oper_type','','danger','N','0','admin','2026-01-20 15:38:28','',NULL,'强退操作'),(26,8,'生成代码','8','sys_oper_type','','warning','N','0','admin','2026-01-20 15:38:28','',NULL,'生成操作'),(27,9,'清空数据','9','sys_oper_type','','danger','N','0','admin','2026-01-20 15:38:28','',NULL,'清空操作'),(28,1,'成功','0','sys_common_status','','primary','N','0','admin','2026-01-20 15:38:28','',NULL,'正常状态'),(29,2,'失败','1','sys_common_status','','danger','N','0','admin','2026-01-20 15:38:28','',NULL,'停用状态');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'用户性别','sys_user_sex','0','admin','2026-01-20 15:38:27','',NULL,'用户性别列表'),(2,'菜单状态','sys_show_hide','0','admin','2026-01-20 15:38:27','',NULL,'菜单状态列表'),(3,'系统开关','sys_normal_disable','0','admin','2026-01-20 15:38:27','',NULL,'系统开关列表'),(4,'任务状态','sys_job_status','0','admin','2026-01-20 15:38:27','',NULL,'任务状态列表'),(5,'任务分组','sys_job_group','0','admin','2026-01-20 15:38:27','',NULL,'任务分组列表'),(6,'系统是否','sys_yes_no','0','admin','2026-01-20 15:38:27','',NULL,'系统是否列表'),(7,'通知类型','sys_notice_type','0','admin','2026-01-20 15:38:27','',NULL,'通知类型列表'),(8,'通知状态','sys_notice_status','0','admin','2026-01-20 15:38:27','',NULL,'通知状态列表'),(9,'操作类型','sys_oper_type','0','admin','2026-01-20 15:38:27','',NULL,'操作类型列表'),(10,'系统状态','sys_common_status','0','admin','2026-01-20 15:38:27','',NULL,'登录状态列表');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'系统默认（无参）','DEFAULT','ryTask.ryNoParams','0/10 * * * * ?','3','1','1','admin','2026-01-20 15:38:28','',NULL,''),(2,'系统默认（有参）','DEFAULT','ryTask.ryParams(\'ry\')','0/15 * * * * ?','3','1','1','admin','2026-01-20 15:38:28','',NULL,''),(3,'系统默认（多参）','DEFAULT','ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)','0/20 * * * * ?','3','1','1','admin','2026-01-20 15:38:28','',NULL,'');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '异常信息',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_logininfor`
--

DROP TABLE IF EXISTS `sys_logininfor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_logininfor` (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作系统',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`),
  KEY `idx_sys_logininfor_s` (`status`),
  KEY `idx_sys_logininfor_lt` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=233 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_logininfor`
--

LOCK TABLES `sys_logininfor` WRITE;
/*!40000 ALTER TABLE `sys_logininfor` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_logininfor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由名称',
  `is_frame` int DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2061 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'系统管理',0,21,'system',NULL,'','',1,0,'M','0','0','','system','admin','2026-01-20 15:38:23','admin','2026-02-05 19:16:52','系统管理目录'),(2,'系统监控',0,22,'monitor',NULL,'','',1,0,'M','0','0','','monitor','admin','2026-01-20 15:38:23','admin','2026-02-05 19:16:47','系统监控目录'),(3,'系统工具',0,23,'tool',NULL,'','',1,0,'M','0','0','','tool','admin','2026-01-20 15:38:23','admin','2026-02-05 19:16:58','系统工具目录'),(100,'用户管理',1,1,'user','system/user/index','','',1,0,'C','0','0','system:user:list','user','admin','2026-01-20 15:38:23','',NULL,'用户管理菜单'),(101,'角色管理',1,2,'role','system/role/index','','',1,0,'C','0','0','system:role:list','peoples','admin','2026-01-20 15:38:23','',NULL,'角色管理菜单'),(102,'菜单管理',1,3,'menu','system/menu/index','','',1,0,'C','0','0','system:menu:list','tree-table','admin','2026-01-20 15:38:23','',NULL,'菜单管理菜单'),(103,'部门管理',1,4,'dept','system/dept/index','','',1,0,'C','0','0','system:dept:list','tree','admin','2026-01-20 15:38:23','',NULL,'部门管理菜单'),(104,'岗位管理',1,5,'post','system/post/index','','',1,0,'C','0','0','system:post:list','post','admin','2026-01-20 15:38:23','',NULL,'岗位管理菜单'),(105,'字典管理',1,6,'dict','system/dict/index','','',1,0,'C','0','0','system:dict:list','dict','admin','2026-01-20 15:38:23','',NULL,'字典管理菜单'),(106,'参数设置',1,7,'config','system/config/index','','',1,0,'C','0','0','system:config:list','edit','admin','2026-01-20 15:38:23','',NULL,'参数设置菜单'),(107,'通知公告',1,8,'notice','system/notice/index','','',1,0,'C','0','0','system:notice:list','message','admin','2026-01-20 15:38:23','',NULL,'通知公告菜单'),(108,'日志管理',1,9,'log','','','',1,0,'M','0','0','','log','admin','2026-01-20 15:38:23','',NULL,'日志管理菜单'),(109,'在线用户',2,1,'online','monitor/online/index','','',1,0,'C','0','0','monitor:online:list','online','admin','2026-01-20 15:38:23','',NULL,'在线用户菜单'),(110,'定时任务',2,2,'job','monitor/job/index','','',1,0,'C','0','0','monitor:job:list','job','admin','2026-01-20 15:38:23','',NULL,'定时任务菜单'),(111,'数据监控',2,3,'druid','monitor/druid/index','','',1,0,'C','0','0','monitor:druid:list','druid','admin','2026-01-20 15:38:23','',NULL,'数据监控菜单'),(112,'服务监控',2,4,'server','monitor/server/index','','',1,0,'C','0','0','monitor:server:list','server','admin','2026-01-20 15:38:23','',NULL,'服务监控菜单'),(113,'缓存监控',2,5,'cache','monitor/cache/index','','',1,0,'C','0','0','monitor:cache:list','redis','admin','2026-01-20 15:38:23','',NULL,'缓存监控菜单'),(114,'缓存列表',2,6,'cacheList','monitor/cache/list','','',1,0,'C','0','0','monitor:cache:list','redis-list','admin','2026-01-20 15:38:23','',NULL,'缓存列表菜单'),(115,'表单构建',3,1,'build','tool/build/index','','',1,0,'C','0','0','tool:build:list','build','admin','2026-01-20 15:38:23','',NULL,'表单构建菜单'),(116,'代码生成',3,2,'gen','tool/gen/index','','',1,0,'C','0','0','tool:gen:list','code','admin','2026-01-20 15:38:23','',NULL,'代码生成菜单'),(117,'系统接口',3,3,'swagger','tool/swagger/index','','',1,0,'C','0','0','tool:swagger:list','swagger','admin','2026-01-20 15:38:23','',NULL,'系统接口菜单'),(500,'操作日志',108,1,'operlog','monitor/operlog/index','','',1,0,'C','0','0','monitor:operlog:list','form','admin','2026-01-20 15:38:23','',NULL,'操作日志菜单'),(501,'登录日志',108,2,'logininfor','monitor/logininfor/index','','',1,0,'C','0','0','monitor:logininfor:list','logininfor','admin','2026-01-20 15:38:23','',NULL,'登录日志菜单'),(1000,'用户查询',100,1,'','','','',1,0,'F','0','0','system:user:query','#','admin','2026-01-20 15:38:23','',NULL,''),(1001,'用户新增',100,2,'','','','',1,0,'F','0','0','system:user:add','#','admin','2026-01-20 15:38:23','',NULL,''),(1002,'用户修改',100,3,'','','','',1,0,'F','0','0','system:user:edit','#','admin','2026-01-20 15:38:23','',NULL,''),(1003,'用户删除',100,4,'','','','',1,0,'F','0','0','system:user:remove','#','admin','2026-01-20 15:38:23','',NULL,''),(1004,'用户导出',100,5,'','','','',1,0,'F','0','0','system:user:export','#','admin','2026-01-20 15:38:23','',NULL,''),(1005,'用户导入',100,6,'','','','',1,0,'F','0','0','system:user:import','#','admin','2026-01-20 15:38:23','',NULL,''),(1006,'重置密码',100,7,'','','','',1,0,'F','0','0','system:user:resetPwd','#','admin','2026-01-20 15:38:23','',NULL,''),(1007,'角色查询',101,1,'','','','',1,0,'F','0','0','system:role:query','#','admin','2026-01-20 15:38:23','',NULL,''),(1008,'角色新增',101,2,'','','','',1,0,'F','0','0','system:role:add','#','admin','2026-01-20 15:38:23','',NULL,''),(1009,'角色修改',101,3,'','','','',1,0,'F','0','0','system:role:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1010,'角色删除',101,4,'','','','',1,0,'F','0','0','system:role:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1011,'角色导出',101,5,'','','','',1,0,'F','0','0','system:role:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1012,'菜单查询',102,1,'','','','',1,0,'F','0','0','system:menu:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1013,'菜单新增',102,2,'','','','',1,0,'F','0','0','system:menu:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1014,'菜单修改',102,3,'','','','',1,0,'F','0','0','system:menu:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1015,'菜单删除',102,4,'','','','',1,0,'F','0','0','system:menu:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1016,'部门查询',103,1,'','','','',1,0,'F','0','0','system:dept:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1017,'部门新增',103,2,'','','','',1,0,'F','0','0','system:dept:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1018,'部门修改',103,3,'','','','',1,0,'F','0','0','system:dept:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1019,'部门删除',103,4,'','','','',1,0,'F','0','0','system:dept:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1020,'岗位查询',104,1,'','','','',1,0,'F','0','0','system:post:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1021,'岗位新增',104,2,'','','','',1,0,'F','0','0','system:post:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1022,'岗位修改',104,3,'','','','',1,0,'F','0','0','system:post:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1023,'岗位删除',104,4,'','','','',1,0,'F','0','0','system:post:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1024,'岗位导出',104,5,'','','','',1,0,'F','0','0','system:post:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1025,'字典查询',105,1,'#','','','',1,0,'F','0','0','system:dict:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1026,'字典新增',105,2,'#','','','',1,0,'F','0','0','system:dict:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1027,'字典修改',105,3,'#','','','',1,0,'F','0','0','system:dict:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1028,'字典删除',105,4,'#','','','',1,0,'F','0','0','system:dict:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1029,'字典导出',105,5,'#','','','',1,0,'F','0','0','system:dict:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1030,'参数查询',106,1,'#','','','',1,0,'F','0','0','system:config:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1031,'参数新增',106,2,'#','','','',1,0,'F','0','0','system:config:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1032,'参数修改',106,3,'#','','','',1,0,'F','0','0','system:config:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1033,'参数删除',106,4,'#','','','',1,0,'F','0','0','system:config:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1034,'参数导出',106,5,'#','','','',1,0,'F','0','0','system:config:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1035,'公告查询',107,1,'#','','','',1,0,'F','0','0','system:notice:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1036,'公告新增',107,2,'#','','','',1,0,'F','0','0','system:notice:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1037,'公告修改',107,3,'#','','','',1,0,'F','0','0','system:notice:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1038,'公告删除',107,4,'#','','','',1,0,'F','0','0','system:notice:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1039,'操作查询',500,1,'#','','','',1,0,'F','0','0','monitor:operlog:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1040,'操作删除',500,2,'#','','','',1,0,'F','0','0','monitor:operlog:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1041,'日志导出',500,3,'#','','','',1,0,'F','0','0','monitor:operlog:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1042,'登录查询',501,1,'#','','','',1,0,'F','0','0','monitor:logininfor:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1043,'登录删除',501,2,'#','','','',1,0,'F','0','0','monitor:logininfor:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1044,'日志导出',501,3,'#','','','',1,0,'F','0','0','monitor:logininfor:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1045,'账户解锁',501,4,'#','','','',1,0,'F','0','0','monitor:logininfor:unlock','#','admin','2026-01-20 15:38:24','',NULL,''),(1046,'在线查询',109,1,'#','','','',1,0,'F','0','0','monitor:online:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1047,'批量强退',109,2,'#','','','',1,0,'F','0','0','monitor:online:batchLogout','#','admin','2026-01-20 15:38:24','',NULL,''),(1048,'单条强退',109,3,'#','','','',1,0,'F','0','0','monitor:online:forceLogout','#','admin','2026-01-20 15:38:24','',NULL,''),(1049,'任务查询',110,1,'#','','','',1,0,'F','0','0','monitor:job:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1050,'任务新增',110,2,'#','','','',1,0,'F','0','0','monitor:job:add','#','admin','2026-01-20 15:38:24','',NULL,''),(1051,'任务修改',110,3,'#','','','',1,0,'F','0','0','monitor:job:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1052,'任务删除',110,4,'#','','','',1,0,'F','0','0','monitor:job:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1053,'状态修改',110,5,'#','','','',1,0,'F','0','0','monitor:job:changeStatus','#','admin','2026-01-20 15:38:24','',NULL,''),(1054,'任务导出',110,6,'#','','','',1,0,'F','0','0','monitor:job:export','#','admin','2026-01-20 15:38:24','',NULL,''),(1055,'生成查询',116,1,'#','','','',1,0,'F','0','0','tool:gen:query','#','admin','2026-01-20 15:38:24','',NULL,''),(1056,'生成修改',116,2,'#','','','',1,0,'F','0','0','tool:gen:edit','#','admin','2026-01-20 15:38:24','',NULL,''),(1057,'生成删除',116,3,'#','','','',1,0,'F','0','0','tool:gen:remove','#','admin','2026-01-20 15:38:24','',NULL,''),(1058,'导入代码',116,4,'#','','','',1,0,'F','0','0','tool:gen:import','#','admin','2026-01-20 15:38:25','',NULL,''),(1059,'预览代码',116,5,'#','','','',1,0,'F','0','0','tool:gen:preview','#','admin','2026-01-20 15:38:25','',NULL,''),(1060,'生成代码',116,6,'#','','','',1,0,'F','0','0','tool:gen:code','#','admin','2026-01-20 15:38:25','',NULL,''),(2000,'测试管理',0,1,'testManages',NULL,NULL,'',1,0,'M','0','0','','bug','admin','2026-02-05 19:16:22','admin','2026-07-04 21:17:34',''),(2001,'测试项目',2000,1,'testProject','project/testProject/index',NULL,'',1,0,'C','0','0','project:testProject:list','test-project','admin','2026-02-05 19:33:46','admin','2026-02-09 19:58:19','测试项目菜单'),(2002,'测试项目查询',2001,1,'#','',NULL,'',1,0,'F','0','0','project:testProject:query','#','admin','2026-02-05 19:33:46','',NULL,''),(2003,'测试项目新增',2001,2,'#','',NULL,'',1,0,'F','0','0','project:testProject:add','#','admin','2026-02-05 19:33:46','',NULL,''),(2004,'测试项目修改',2001,3,'#','',NULL,'',1,0,'F','0','0','project:testProject:edit','#','admin','2026-02-05 19:33:46','',NULL,''),(2005,'测试项目删除',2001,4,'#','',NULL,'',1,0,'F','0','0','project:testProject:remove','#','admin','2026-02-05 19:33:46','',NULL,''),(2006,'测试项目导出',2001,5,'#','',NULL,'',1,0,'F','0','0','project:testProject:export','#','admin','2026-02-05 19:33:46','',NULL,''),(2020,'测试项目成员',2001,6,'testProjectMember','project/testProjectMember/index',NULL,'',1,0,'C','1','0','project:testProjectMember:list','#','admin','2026-03-26 19:39:58','',NULL,'测试项目成员（隐藏）'),(2021,'测试项目成员查询',2020,1,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectMember:query','#','admin','2026-03-26 19:39:58','',NULL,''),(2022,'测试项目成员新增',2020,2,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectMember:add','#','admin','2026-03-26 19:39:58','',NULL,''),(2023,'测试项目成员修改',2020,3,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectMember:edit','#','admin','2026-03-26 19:39:58','',NULL,''),(2024,'测试项目成员删除',2020,4,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectMember:remove','#','admin','2026-03-26 19:39:58','',NULL,''),(2025,'测试项目成员导出',2020,5,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectMember:export','#','admin','2026-03-26 19:39:58','',NULL,''),(2026,'鉴权模板库',2000,2,'testProjectTemplate','project/testProjectTemplate/index',NULL,'',1,0,'C','0','0','project:testProjectTemplate:list','lock','admin','2026-08-18 00:00:00','',NULL,'鉴权模板库菜单'),(2027,'鉴权模板查询',2026,1,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectTemplate:query','#','admin','2026-08-18 00:00:00','',NULL,''),(2028,'鉴权模板新增',2026,2,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectTemplate:add','#','admin','2026-08-18 00:00:00','',NULL,''),(2029,'鉴权模板修改',2026,3,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectTemplate:edit','#','admin','2026-08-18 00:00:00','',NULL,''),(2032,'鉴权模板删除',2026,4,'#',NULL,NULL,'',1,0,'F','0','0','project:testProjectTemplate:remove','#','admin','2026-08-18 00:00:00','',NULL,''),(2030,'AI 管理',0,2,'aiManages',NULL,NULL,'',1,0,'M','0','0','','ai-manage','admin','2026-06-15 10:35:16','admin','2026-06-18 21:52:27',''),(2031,'AI 配置',2030,1,'aiLlmVendor','ai/aiLlmVendor/index',NULL,'',1,0,'C','0','0','ai:aiLlmVendor:list','ai-vendor','admin','2026-06-15 10:36:55','',NULL,'AI 大模型厂商菜单'),(2032,'AI 配置查询',2031,1,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmVendor:query','#','admin','2026-06-15 10:36:55','',NULL,''),(2033,'AI 配置新增',2031,2,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmVendor:add','#','admin','2026-06-15 10:36:55','',NULL,''),(2034,'AI 配置修改',2031,3,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmVendor:edit','#','admin','2026-06-15 10:36:55','',NULL,''),(2035,'AI 配置删除',2031,4,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmVendor:remove','#','admin','2026-06-15 10:36:55','',NULL,''),(2036,'AI 配置导出',2031,5,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmVendor:export','#','admin','2026-06-15 10:36:55','',NULL,''),(2037,'AI 模型',2030,2,'aiLlmModel','ai/aiLlmModel/index',NULL,'',1,0,'C','1','0','ai:aiLlmModel:list','#','admin','2026-06-15 10:37:23','admin','2026-06-16 21:59:55','AI 大模型菜单'),(2038,'AI 模型查询',2037,1,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmModel:query','#','admin','2026-06-15 10:37:23','',NULL,''),(2039,'AI 模型新增',2037,2,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmModel:add','#','admin','2026-06-15 10:37:23','',NULL,''),(2040,'AI 模型修改',2037,3,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmModel:edit','#','admin','2026-06-15 10:37:23','',NULL,''),(2041,'AI 模型删除',2037,4,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmModel:remove','#','admin','2026-06-15 10:37:23','',NULL,''),(2042,'AI 模型导出',2037,5,'#','',NULL,'',1,0,'F','0','0','ai:aiLlmModel:export','#','admin','2026-06-15 10:37:23','',NULL,''),(2043,'AI 会话',2030,3,'aiChatSession','ai/aiChatSession/index',NULL,'',1,0,'C','0','0','ai:aiChatSession:list','ai-session','admin','2026-06-15 10:37:43','',NULL,'AI 会话菜单'),(2044,'AI 会话查询',2043,1,'#','',NULL,'',1,0,'F','0','0','ai:aiChatSession:query','#','admin','2026-06-15 10:37:43','',NULL,''),(2045,'AI 会话新增',2043,2,'#','',NULL,'',1,0,'F','0','0','ai:aiChatSession:add','#','admin','2026-06-15 10:37:43','',NULL,''),(2046,'AI 会话修改',2043,3,'#','',NULL,'',1,0,'F','0','0','ai:aiChatSession:edit','#','admin','2026-06-15 10:37:43','',NULL,''),(2047,'AI 会话删除',2043,4,'#','',NULL,'',1,0,'F','0','0','ai:aiChatSession:remove','#','admin','2026-06-15 10:37:43','',NULL,''),(2048,'AI 会话导出',2043,5,'#','',NULL,'',1,0,'F','0','0','ai:aiChatSession:export','#','admin','2026-06-15 10:37:43','',NULL,''),(2049,'AI 会话消息',2030,4,'aiChatMessage','ai/aiChatMessage/index',NULL,'',1,0,'C','1','0','ai:aiChatMessage:list','#','admin','2026-06-15 10:38:00','admin','2026-06-16 22:16:06','AI 会话消息菜单'),(2050,'AI 会话消息查询',2049,1,'#','',NULL,'',1,0,'F','0','0','ai:aiChatMessage:query','#','admin','2026-06-15 10:38:00','',NULL,''),(2051,'AI 会话消息新增',2049,2,'#','',NULL,'',1,0,'F','0','0','ai:aiChatMessage:add','#','admin','2026-06-15 10:38:00','',NULL,''),(2052,'AI 会话消息修改',2049,3,'#','',NULL,'',1,0,'F','0','0','ai:aiChatMessage:edit','#','admin','2026-06-15 10:38:00','',NULL,''),(2053,'AI 会话消息删除',2049,4,'#','',NULL,'',1,0,'F','0','0','ai:aiChatMessage:remove','#','admin','2026-06-15 10:38:00','',NULL,''),(2054,'AI 会话消息导出',2049,5,'#','',NULL,'',1,0,'F','0','0','ai:aiChatMessage:export','#','admin','2026-06-15 10:38:00','',NULL,''),(2055,'AI 提示词',2030,3,'aiPromptTemplate','ai/aiPromptTemplate/index',NULL,'',1,0,'C','0','0','ai:aiPromptTemplate:list','ai-prompt','admin','2026-07-04 21:17:56','admin','2026-07-05 09:08:32','AI提示词模板菜单'),(2056,'AI 提示词模板查询',2055,1,'#','',NULL,'',1,0,'F','0','0','ai:aiPromptTemplate:query','#','admin','2026-07-04 21:17:56','',NULL,''),(2057,'AI 提示词模板新增',2055,2,'#','',NULL,'',1,0,'F','0','0','ai:aiPromptTemplate:add','#','admin','2026-07-04 21:17:56','',NULL,''),(2058,'AI 提示词模板修改',2055,3,'#','',NULL,'',1,0,'F','0','0','ai:aiPromptTemplate:edit','#','admin','2026-07-04 21:17:56','',NULL,''),(2059,'AI 提示词模板删除',2055,4,'#','',NULL,'',1,0,'F','0','0','ai:aiPromptTemplate:remove','#','admin','2026-07-04 21:17:56','',NULL,''),(2060,'AI 提示词模板导出',2055,5,'#','',NULL,'',1,0,'F','0','0','ai:aiPromptTemplate:export','#','admin','2026-07-04 21:17:56','',NULL,'');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
INSERT INTO `sys_notice` VALUES (1,'温馨提醒：2018-07-01 若依新版本发布啦','2',_binary '新版本内容','0','admin','2026-01-20 15:38:28','',NULL,'管理员'),(2,'维护通知：2018-07-01 若依系统凌晨维护','1',_binary '维护内容','0','admin','2026-01-20 15:38:28','',NULL,'管理员');
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求方式',
  `operator_type` int DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB AUTO_INCREMENT=300 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1,'ceo','董事长',1,'0','admin','2026-01-20 15:38:22','',NULL,''),(2,'se','项目经理',2,'0','admin','2026-01-20 15:38:22','',NULL,''),(3,'hr','人力资源',3,'0','admin','2026-01-20 15:38:22','',NULL,''),(4,'user','普通员工',4,'0','admin','2026-01-20 15:38:22','',NULL,'');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'超级管理员','admin',1,'1',1,1,'0','0','admin','2026-01-20 15:38:23','',NULL,'超级管理员'),(2,'测试','test',2,'2',1,1,'0','0','admin','2026-01-20 15:38:23','admin','2026-03-31 11:11:29','普通角色'),(3,'项目管理员','projectAdmin',3,'1',1,1,'0','2','admin','2026-03-31 10:59:59','admin','2026-03-31 11:00:05',NULL);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_dept`
--

DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和部门关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_dept`
--

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
INSERT INTO `sys_role_dept` VALUES (2,100),(2,101),(2,105);
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (2,2000),(2,2001),(2,2002),(2,2003),(2,2004),(2,2005),(2,2006),(2,2020),(2,2021),(2,2022),(2,2023),(2,2024),(2,2025),(2,2026),(2,2027),(2,2028),(2,2029),(2,2032);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,103,'admin','质衡','00','qualitest@qq.com','15888888888','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','127.0.0.1','2026-07-19 21:40:21','2026-01-20 15:38:21','admin','2026-01-20 15:38:21','',NULL,'管理员'),(2,101,'projectOwner','项目所有者','00','qualitest@168.com','15666666666','0','','$2a$10$8UPHu.laiE54ve4UF5S5N.Sa/PoRnBoepkq/leJc66oCmmy4F5G6a','0','0','127.0.0.1','2026-05-17 12:58:34','2026-04-05 21:37:46','admin','2026-01-20 15:38:22','admin','2026-04-05 21:37:46','测试员'),(3,101,'projectAdmin','项目管理员','00','','','0','','$2a$10$pvJHoahgvDFy0VKnJZTyYe5kQZtBAq1UcKTPMbAKll2t.CdKKRFji','0','0','127.0.0.1','2026-04-07 16:48:35','2026-04-05 21:42:33','admin','2026-04-05 21:38:28','','2026-04-05 21:42:33',NULL),(4,101,'projectDeveloper','项目开发者','00','','','0','','$2a$10$5LlcFL/bNXSqnDjcC0MhWum4DTdQjP2zUJR8Dwbs7Wy7zXC/4BdfC','0','0','127.0.0.1','2026-04-06 11:02:28',NULL,'admin','2026-04-05 21:39:20','',NULL,NULL),(5,101,'projectTester','项目测试员','00','','','0','','$2a$10$vOCx/EGDKdHRuDdbh7cEbe/5.LxrNNhKdo0h3ccKMw6IEDPoy0pGu','0','0','127.0.0.1','2026-04-06 11:02:38','2026-04-05 21:45:03','admin','2026-04-05 21:40:13','','2026-04-05 21:45:03',NULL);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1,1),(2,2);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1),(2,2),(3,2),(4,2),(5,2);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_flow`
--

DROP TABLE IF EXISTS `test_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_flow` (
  `test_flow_id` bigint NOT NULL COMMENT '测试流ID',
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `flow_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '测试流名称',
  `flow_description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '测试流说明',
  `graph_json` json NOT NULL COMMENT '流程图JSON',
  `api_health_warning_count` int NOT NULL DEFAULT '0' COMMENT 'API语义健康告警条数',
  `api_health_checked_at` datetime DEFAULT NULL COMMENT '最近一次API语义健康检查时间',
  `api_health_warning_codes` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '告警类型摘要',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_flow_id`),
  KEY `idx_test_project_id` (`test_project_id`),
  KEY `idx_api_health_warning` (`del_status`,`api_health_warning_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试流';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_flow`
--

LOCK TABLES `test_flow` WRITE;
/*!40000 ALTER TABLE `test_flow` DISABLE KEYS */;
INSERT INTO `test_flow` VALUES (2073764216729636864,2043000000000000200,'未命名测试流',NULL,'{\"meta\": {\"layout\": \"manual\", \"viewport\": {\"x\": -24, \"y\": 174, \"zoom\": 1}, \"scenarios\": [{\"id\": \"2073764216648634368\", \"name\": \"默认（冒烟）\", \"remark\": \"\", \"flowSeed\": {}, \"onNodeFailure\": null, \"testProjectEnvId\": \"2043000000000000201\", \"onSnapshotFailure\": null}], \"flowOutputs\": [], \"startNodeId\": \"2074869393335414784\", \"schemaVersion\": 1, \"activeScenarioId\": \"2073764216648634368\"}, \"edges\": [{\"id\": \"2074869393335414787\", \"source\": \"2074869393335414784\", \"target\": \"2074869393335414785\"}, {\"id\": \"2074869393335414788\", \"source\": \"2074869393335414785\", \"target\": \"2074869393335414786\"}], \"nodes\": [{\"id\": \"2074869393335414784\", \"data\": {\"name\": \"客户端用户登录\", \"apiName\": \"客户端用户登录\", \"apiPath\": \"/api/account/auth/login\", \"summary\": \"POST /api/account/auth/login · 1 项参数\", \"callMode\": \"project\", \"httpMethod\": \"POST\", \"requestConfig\": {\"body\": {\"json\": {\"schema\": {\"type\": \"object\", \"properties\": {\"mobile\": {\"type\": \"string\", \"description\": \"手机号\"}, \"password\": {\"type\": \"string\", \"description\": \"密码\"}}}, \"example\": \"{\\\"mobile\\\":\\\"13800000001\\\",\\\"password\\\":\\\"Test@123456\\\"}\"}, \"mode\": \"json\", \"urlencoded\": [{\"name\": \"\", \"type\": \"string\", \"value\": \"\", \"_enabled\": true}]}, \"method\": \"POST\", \"params\": [], \"pathParams\": []}, \"useRunSession\": true, \"testProjectApiId\": \"2073436533309964288\"}, \"type\": \"http\", \"position\": {\"x\": 0, \"y\": 100}}, {\"id\": \"2074869393335414785\", \"data\": {\"name\": \"获取当前用户信息\", \"apiName\": \"获取当前用户信息\", \"apiPath\": \"/api/account/auth/profile\", \"summary\": \"GET /api/account/auth/profile · 提取 1 项\", \"callMode\": \"project\", \"extracts\": [{\"expr\": \"$.mobile\", \"from\": \"body\", \"name\": \"mobile\", \"scope\": \"flow\", \"entryKey\": \"\", \"fieldPath\": \"\"}], \"httpMethod\": \"GET\", \"requestConfig\": {\"body\": {\"mode\": \"none\"}, \"method\": \"GET\", \"params\": [], \"pathParams\": []}, \"useRunSession\": true, \"testProjectApiId\": \"2073436533582594048\"}, \"type\": \"http\", \"position\": {\"x\": 380, \"y\": 100}}, {\"id\": \"2074869393335414786\", \"data\": {\"name\": \"确认手机号\", \"rules\": [{\"left\": \"{{flow.mobile}}\", \"right\": \"13800000001\", \"operator\": \"equals\"}], \"summary\": \"{{flow.mobile}} equals 13800000001\"}, \"type\": \"assert\", \"position\": {\"x\": 760, \"y\": 100}}]}',0,NULL,NULL,0,'2026-07-05 21:41:33','2026-07-12 10:11:37');
/*!40000 ALTER TABLE `test_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_flow_run`
--

DROP TABLE IF EXISTS `test_flow_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_flow_run` (
  `test_flow_run_id` bigint NOT NULL COMMENT '运行ID',
  `test_flow_id` bigint NOT NULL COMMENT '测试流ID',
  `test_project_env_id` bigint NOT NULL COMMENT '测试项目环境ID',
  `run_scenario_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行场景ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '运行状态（running执行中 passed成功 failed失败 cancelled取消）',
  `graph_json_snapshot` json NOT NULL COMMENT '流程图快照',
  `flow_snapshot` json DEFAULT NULL COMMENT '流程变量快照',
  `run_execution_state` json DEFAULT NULL COMMENT '运行时状态',
  `paused_at` datetime DEFAULT NULL COMMENT '暂停时间',
  `graph_fingerprint` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程图指纹哈希',
  `started_at` datetime NOT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败错误码',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '失败错误信息',
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '触发方式（manual手动 ci持续集成 schedule定时）',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_flow_run_id`),
  KEY `idx_test_flow_id` (`test_flow_id`,`started_at`),
  KEY `idx_project_env` (`test_project_env_id`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试流运行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_flow_run`
--

LOCK TABLES `test_flow_run` WRITE;
/*!40000 ALTER TABLE `test_flow_run` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_flow_run` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_flow_run_step`
--

DROP TABLE IF EXISTS `test_flow_run_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_flow_run_step` (
  `test_flow_run_step_id` bigint NOT NULL COMMENT '步骤ID',
  `test_flow_run_id` bigint NOT NULL COMMENT '运行ID',
  `step_index` int NOT NULL COMMENT '步骤序号',
  `node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '节点ID',
  `node_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点类型',
  `node_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点名称',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '步骤状态（passed成功 failed失败 skipped未执行）',
  `duration_ms` bigint DEFAULT NULL COMMENT '步骤耗时（毫秒）',
  `edge_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '入边ID',
  `step_details` json NOT NULL COMMENT '步骤详情JSON',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_flow_run_step_id`),
  KEY `idx_run_id` (`test_flow_run_id`,`step_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试流运行步骤';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_flow_run_step`
--

LOCK TABLES `test_flow_run_step` WRITE;
/*!40000 ALTER TABLE `test_flow_run_step` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_flow_run_step` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project`
--

DROP TABLE IF EXISTS `test_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project` (
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `project_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '项目名',
  `asset_variables` json NOT NULL COMMENT '素材变量',
  `response_convention` json DEFAULT NULL COMMENT '响应约定',
  `auth_config` json DEFAULT NULL COMMENT '项目鉴权配置：多套 Bearer 等',
  `last_api_sync_time` datetime DEFAULT NULL COMMENT '最新API同步时间',
  `api_count` int NOT NULL DEFAULT '0' COMMENT 'API数量',
  `owner_id` bigint NOT NULL COMMENT '所有者ID',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_project_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project`
--

LOCK TABLES `test_project` WRITE;
/*!40000 ALTER TABLE `test_project` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project_api`
--

DROP TABLE IF EXISTS `test_project_api`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project_api` (
  `test_project_api_id` bigint NOT NULL COMMENT '测试项目API ID',
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `api_group_id` bigint NOT NULL COMMENT 'API分组ID',
  `api_status` enum('normal','deprecated') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API状态',
  `api_group` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API分组',
  `api_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API名称',
  `api_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'API详细描述',
  `api_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'API路径',
  `protocol_type` enum('http') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '协议类型',
  `request_config` json NOT NULL COMMENT '请求配置',
  `headers` json NOT NULL COMMENT '请求头配置',
  `cookies` json NOT NULL COMMENT 'Cookie配置',
  `response_config` json NOT NULL COMMENT '响应配置',
  `test_value_config` json DEFAULT NULL COMMENT '测试值层',
  `biz_code_config` json DEFAULT NULL COMMENT '业务code白名单',
  `auth_config` json DEFAULT NULL COMMENT '接口鉴权标签：是否免登录等',
  `pre_request_script` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '前置操作脚本',
  `post_request_script` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '后置操作脚本',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最新同步时间',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `design_hints` json DEFAULT NULL COMMENT '造流设计提示（人机可维护，导入不覆盖）',
  PRIMARY KEY (`test_project_api_id`) USING BTREE,
  KEY `idx_path_method` (`api_path`),
  KEY `idx_project_id` (`test_project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目API';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project_api`
--

LOCK TABLES `test_project_api` WRITE;
/*!40000 ALTER TABLE `test_project_api` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_project_api` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project_api_group`
--

DROP TABLE IF EXISTS `test_project_api_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project_api_group` (
  `api_group_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'API分组ID',
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父分组ID',
  `ancestors` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '祖级列表',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称',
  `sort_num` int NOT NULL DEFAULT '0' COMMENT '排序',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`api_group_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目API分组';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project_api_group`
--

LOCK TABLES `test_project_api_group` WRITE;
/*!40000 ALTER TABLE `test_project_api_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_project_api_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project_env`
--

DROP TABLE IF EXISTS `test_project_env`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project_env` (
  `test_project_env_id` bigint NOT NULL COMMENT '测试项目环境ID',
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `share_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'private' COMMENT '共享状态（share共享 private私有）',
  `env_color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '环境标识颜色',
  `env_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '环境名称',
  `env_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '环境URL',
  `env_variables` json NOT NULL COMMENT '环境变量',
  `allow_destructive_reset` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许还原被测数据（0否 1是）',
  `sort_num` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_project_env_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目环境';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project_env`
--

LOCK TABLES `test_project_env` WRITE;
/*!40000 ALTER TABLE `test_project_env` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_project_env` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project_member`
--

DROP TABLE IF EXISTS `test_project_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project_member` (
  `test_project_member_id` bigint NOT NULL COMMENT '项目成员ID',
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `member_role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'developer' COMMENT '成员角色（owner所有者 admin管理员 developer开发人员 tester测试人员）',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`test_project_member_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目成员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project_member`
--

LOCK TABLES `test_project_member` WRITE;
/*!40000 ALTER TABLE `test_project_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_project_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project_user_setting`
--

DROP TABLE IF EXISTS `test_project_user_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project_user_setting` (
  `test_project_user_setting_id` bigint NOT NULL COMMENT '测试项目用户设置ID',
  `test_project_id` bigint NOT NULL COMMENT '测试项目ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `project_token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '项目Token',
  `test_project_env_id` bigint NOT NULL DEFAULT '0' COMMENT '测试项目环境ID',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_project_user_setting_id` DESC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目用户设置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project_user_setting`
--

LOCK TABLES `test_project_user_setting` WRITE;
/*!40000 ALTER TABLE `test_project_user_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `test_project_user_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_project_template`
-- 一行模板 = 一条鉴权 Profile；勾选后拷贝进 test_project.auth_config.authProfiles
--

DROP TABLE IF EXISTS `test_project_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_project_template` (
  `test_project_template_id` bigint NOT NULL COMMENT '项目模板ID',
  `template_name` varchar(100) NOT NULL COMMENT '模板名称',
  `header_name` varchar(64) NOT NULL COMMENT '鉴权头名称',
  `header_value_template` varchar(512) NOT NULL COMMENT '鉴权头值模板',
  `match_config` json DEFAULT NULL COMMENT '路径匹配',
  `apis` json NOT NULL COMMENT '预制接口',
  `builtin_status` tinyint NOT NULL DEFAULT '0' COMMENT '内置状态（0自定义 1内置）',
  `enable_status` tinyint NOT NULL DEFAULT '1' COMMENT '启用状态（0禁用 1启用）',
  `sort_num` int NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(256) DEFAULT NULL COMMENT '备注',
  `del_status` tinyint NOT NULL DEFAULT '0' COMMENT '删除状态（0正常 1删除）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`test_project_template_id`),
  KEY `idx_name_del` (`template_name`,`del_status`),
  KEY `idx_builtin_sort` (`builtin_status`,`enable_status`,`sort_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试项目模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_project_template`
--

LOCK TABLES `test_project_template` WRITE;
/*!40000 ALTER TABLE `test_project_template` DISABLE KEYS */;
INSERT INTO `test_project_template` (
  `test_project_template_id`, `template_name`, `header_name`, `header_value_template`, `match_config`, `apis`,
  `builtin_status`, `enable_status`, `sort_num`, `remark`, `del_status`, `create_time`, `update_time`
) VALUES
(2100000000000000001,'RuoYi Bearer','Authorization','Bearer {{flow.token}}',NULL,'[{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":"","code":"","uuid":""}}}},"testValueConfig":{"request":{"bodyExample":{"username":"admin","password":"admin123"}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-login","name":"成功","httpStatus":200,"contentType":"json","example":{"code":200,"msg":"操作成功","token":"..."}}]},"authConfig":{"mode":"none","loginHint":{"flowKey":"token","from":"body","expr":"$.token"}},"designHints":{"hints":["token 在 $.token，不要写成 $.data.token"]}},{"apiName":"注册","apiPath":"/register","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":""}}}},"authConfig":{"mode":"none"}},{"apiName":"验证码","apiPath":"/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}}]',1,1,10,'RuoYi Bearer',0,NOW(),NULL),
(2100000000000000002,'RuoYi Session','Cookie','JSESSIONID={{flow.jsessionId}}',NULL,'[{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":"","code":"","uuid":""}}}},"testValueConfig":{"request":{"bodyExample":{"username":"admin","password":"admin123"}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-login","name":"成功","httpStatus":200,"contentType":"json","example":{"code":200,"msg":"操作成功"}}]},"authConfig":{"mode":"none","loginHint":{"flowKey":"jsessionId","from":"setCookie","expr":"JSESSIONID"}},"designHints":{"hints":["Session 登录从 Set-Cookie 取 JSESSIONID"]}},{"apiName":"验证码","apiPath":"/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}},{"apiName":"验证码(旧)","apiPath":"/captcha/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}}]',1,1,20,'RuoYi 传统 Session',0,NOW(),NULL),
(2100000000000000003,'客户端 Bearer','Authorization','Bearer {{flow.token}}','{"pathPrefix":["/api/"]}','[{"apiName":"登录","apiPath":"/api/account/auth/login","apiGroup":"客户端.账号","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"mobile":"","password":""}}}},"testValueConfig":{"request":{"bodyExample":{"mobile":"13800000001","password":"Test@123456"}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-login","name":"成功","httpStatus":200,"contentType":"json","example":{"code":200,"msg":"操作成功","data":{"token":"..."}}}]},"authConfig":{"mode":"none","loginHint":{"flowKey":"token","from":"body","expr":"$.data.token"}},"designHints":{"hints":["客户端 token 在 $.data.token，不要写成 $.token"]}},{"apiName":"注册","apiPath":"/api/account/auth/register","apiGroup":"客户端.账号","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"mobile":"","password":""}}}},"authConfig":{"mode":"none"}}]',1,1,30,'商城 / 客户端 API',0,NOW(),NULL),
(2100000000000000004,'管理端 Bearer','Authorization','Bearer {{flow.adminToken}}','{"pathPrefix":["/system/","/monitor/","/tool/","/web/"]}','[{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":"","code":"","uuid":""}}}},"testValueConfig":{"request":{"bodyExample":{"username":"admin","password":"admin123"}}},"responseConfig":{"configVersion":1,"responses":[{"id":"resp-login","name":"成功","httpStatus":200,"contentType":"json","example":{"code":200,"msg":"操作成功","token":"..."}}]},"authConfig":{"mode":"none","loginHint":{"flowKey":"adminToken","from":"body","expr":"$.token"}},"designHints":{"hints":["管理端 token 在 $.token → adminToken，不要写成 $.data.token"]}},{"apiName":"注册","apiPath":"/register","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":""}}}},"authConfig":{"mode":"none"}},{"apiName":"验证码","apiPath":"/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}}]',1,1,40,'RuoYi 管理端',0,NOW(),NULL);
/*!40000 ALTER TABLE `test_project_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-22 13:51:28
