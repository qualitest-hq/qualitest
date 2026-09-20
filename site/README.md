# Qualitest 落地页（GitHub Pages）

Astro 静态站，部署到 `https://qualitest-hq.github.io/qualitest/`。

- 中文：`/qualitest/`
- English：`/qualitest/en/`（导航栏 **中文 / EN** 切换）

## 本地

```bash
cd site
pnpm install
pnpm dev      # http://localhost:4321/qualitest/ 与 /qualitest/en/
pnpm build
pnpm preview
```

镜像源由本目录 `.npmrc` 统一配置（默认 npmmirror）。

## CI 行为

[`Deploy Pages`](../.github/workflows/deploy-pages.yml) 在改 `site/` 时会**始终 build**（校验能编过）。  
真正 deploy 仅当仓库变量 **`ENABLE_PAGES_DEPLOY=true`**（公开日前不要设，避免 Private 下 deploy 404）。

## 公开日上线

> 与路线图「公开日安全动作」、[`docs/deploy.md`](../docs/deploy.md)「GitHub Pages 落地页」、[`docs/v1.0-首发文草稿.md`](../docs/v1.0-首发文草稿.md) 发文前核对一致。**Private 时站点不可对外访问，勿以为忘了部署。**

1. 仓库改为 Public  
2. Settings → Pages → Source = **GitHub Actions**  
3. Settings → Secrets and variables → Actions → **Variables** → 新增 `ENABLE_PAGES_DEPLOY` = `true`  
4. Actions → **Deploy Pages** → Run workflow（或再 push 一次 `site/`）  
5. About → Website 填上述 URL  
6. 浏览器打开确认；发文 CTA 可同时链官网 + GitHub  

GIF 放入 `public/images/`（与 `docs/images/` 同名即可）：

- `overview.gif` — Hero；缺失时用链路 SVG
- `demo-mcp-survey.gif` / `demo-mcp-autowrite.gif` — MCP 展示区；缺失时显示占位文案

