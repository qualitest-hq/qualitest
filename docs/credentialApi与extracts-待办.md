# 鉴权抽凭证模型

登录口由节点 `extracts` 写入目标与 Profile `headerValueTemplate` 对齐推断；完全没 extracts 不硬拦，由人/AI 后补。

```text
extracts（抽） + headerValueTemplate（带）
```

- 有 extracts 且目标命中托管头 → 视为登录/发凭证口（UI 角标、键碰撞门禁）
- 无 extracts → 不当登录口、不拦保存
- AI 按响应 schema + Profile `headerValueTemplate` 自定 extracts
