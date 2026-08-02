-- 结算预览 / 创建订单资产默认 body 同时含 cartIds 与 items，违反靶场「二选一」约束。
-- 默认改为购物车结算路径（S01：addressId + cartIds），去掉 items。
-- bodyExample 在库中以 JSON 字符串形式存放（与导入资产一致）。
UPDATE test_project_api
SET test_value_config = JSON_SET(
        test_value_config,
        '$.request.bodyExample',
        '{"addressId":4001,"cartIds":[5001,5002]}'
    ),
    update_time = NOW()
WHERE api_path IN ('/api/mall/mallOrder/preview', '/api/mall/mallOrder/create')
  AND del_status = 0
  AND JSON_UNQUOTE(JSON_EXTRACT(test_value_config, '$.request.bodyExample')) LIKE '%"items"%'
  AND JSON_UNQUOTE(JSON_EXTRACT(test_value_config, '$.request.bodyExample')) LIKE '%"cartIds"%';
