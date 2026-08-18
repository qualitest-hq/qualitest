-- 测试项目模板：一行模板 = 一条鉴权 Profile；勾选后拷贝进 test_project.auth_config.authProfiles
CREATE TABLE test_project_template (
  test_project_template_id bigint NOT NULL COMMENT '项目模板ID',
  template_name            varchar(100) NOT NULL COMMENT '模板名称',
  header_name              varchar(64)  NOT NULL COMMENT '鉴权头名称',
  header_value_template    varchar(512) NOT NULL COMMENT '鉴权头值模板',
  match_config             json DEFAULT NULL COMMENT '路径匹配',
  apis                     json NOT NULL COMMENT '预制接口',
  builtin_status           tinyint NOT NULL DEFAULT 0 COMMENT '内置状态（0自定义 1内置）',
  enable_status            tinyint NOT NULL DEFAULT 1 COMMENT '启用状态（0禁用 1启用）',
  sort_num                 int NOT NULL DEFAULT 0 COMMENT '排序',
  remark                   varchar(256) DEFAULT NULL COMMENT '备注',
  del_status               tinyint NOT NULL DEFAULT 0 COMMENT '删除状态（0正常 1删除）',
  create_time              datetime NOT NULL COMMENT '创建时间',
  update_time              datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (test_project_template_id),
  KEY idx_name_del (template_name, del_status),
  KEY idx_builtin_sort (builtin_status, enable_status, sort_num)
) COMMENT='测试项目模板';

-- 默认 Bearer：POST /login $.token→token + register + captchaImage；不含客户端 path
INSERT INTO test_project_template (
  test_project_template_id, template_name, header_name, header_value_template, match_config, apis,
  builtin_status, enable_status, sort_num, remark, del_status, create_time, update_time
) VALUES (
  2100000000000000001,
  '默认 Bearer',
  'Authorization',
  'Bearer {{flow.token}}',
  NULL,
  '[{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":"","code":"","uuid":""}}}},"authConfig":{"mode":"none","loginHint":{"flowKey":"token","from":"body","expr":"$.token"}},"designHints":{"hints":["token 在 $.token，不要写成 $.data.token"]}},{"apiName":"注册","apiPath":"/register","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":""}}}},"authConfig":{"mode":"none"}},{"apiName":"验证码","apiPath":"/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}}]',
  1, 1, 10, 'RuoYi-Vue / 通用 Bearer', 0, NOW(), NULL
);

-- RuoYi Session
INSERT INTO test_project_template (
  test_project_template_id, template_name, header_name, header_value_template, match_config, apis,
  builtin_status, enable_status, sort_num, remark, del_status, create_time, update_time
) VALUES (
  2100000000000000002,
  'RuoYi Session',
  'Cookie',
  'JSESSIONID={{flow.jsessionId}}',
  NULL,
  '[{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":"","code":"","uuid":""}}}},"authConfig":{"mode":"none","loginHint":{"flowKey":"jsessionId","from":"setCookie","expr":"JSESSIONID"}},"designHints":{"hints":["Session 登录从 Set-Cookie 取 JSESSIONID"]}},{"apiName":"验证码","apiPath":"/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}},{"apiName":"验证码(旧)","apiPath":"/captcha/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}}]',
  1, 1, 20, 'RuoYi 传统 Session', 0, NOW(), NULL
);

-- 客户端 Bearer：pathPrefix /api/
INSERT INTO test_project_template (
  test_project_template_id, template_name, header_name, header_value_template, match_config, apis,
  builtin_status, enable_status, sort_num, remark, del_status, create_time, update_time
) VALUES (
  2100000000000000003,
  '客户端 Bearer',
  'Authorization',
  'Bearer {{flow.token}}',
  '{"pathPrefix":["/api/"]}',
  '[{"apiName":"登录","apiPath":"/api/account/auth/login","apiGroup":"客户端.账号","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"mobile":"","password":""}}}},"authConfig":{"mode":"none","loginHint":{"flowKey":"token","from":"body","expr":"$.data.token"}},"designHints":{"hints":["客户端 token 在 $.data.token，不要写成 $.token"]}},{"apiName":"注册","apiPath":"/api/account/auth/register","apiGroup":"客户端.账号","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"mobile":"","password":""}}}},"authConfig":{"mode":"none"}}]',
  1, 1, 30, '商城 / 客户端 API', 0, NOW(), NULL
);

-- 管理端 Bearer：与默认相同三口，flowKey=adminToken
INSERT INTO test_project_template (
  test_project_template_id, template_name, header_name, header_value_template, match_config, apis,
  builtin_status, enable_status, sort_num, remark, del_status, create_time, update_time
) VALUES (
  2100000000000000004,
  '管理端 Bearer',
  'Authorization',
  'Bearer {{flow.adminToken}}',
  '{"pathPrefix":["/system/","/monitor/","/tool/","/web/"]}',
  '[{"apiName":"登录","apiPath":"/login","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":"","code":"","uuid":""}}}},"authConfig":{"mode":"none","loginHint":{"flowKey":"adminToken","from":"body","expr":"$.token"}},"designHints":{"hints":["管理端 token 在 $.token → adminToken，不要写成 $.data.token"]}},{"apiName":"注册","apiPath":"/register","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"json","json":{"example":{"username":"","password":""}}}},"authConfig":{"mode":"none"}},{"apiName":"验证码","apiPath":"/captchaImage","apiGroup":"系统.登录","protocolType":"http","apiStatus":"normal","requestConfig":{"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}},"authConfig":{"mode":"none"}}]',
  1, 1, 40, 'RuoYi 管理端', 0, NOW(), NULL
);
