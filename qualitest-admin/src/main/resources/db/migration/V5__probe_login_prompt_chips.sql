-- 探活再登录 AI 芯片：白名单含 403；活着判定增加「实际响应符合接口期望」
UPDATE `ai_prompt_template`
SET `template_content` = REPLACE(
        REPLACE(
                `template_content`,
                'values=[200,401]',
                'values=[200,401,403]'
        ),
        'IF left=http.status operator=eq right=200（此 IF 为结束分支，无出边）',
        'IF left=http.status operator=eq right=200 且 left=http.expectedMatch operator=eq right=true（此 IF 为结束分支，无出边；对照接口 expectedResponseKind）'
                         ),
    `update_time` = NOW()
WHERE `ai_prompt_template_id` IN (2070000000000000603, 2070000000000000604)
  AND `del_status` = 0;
