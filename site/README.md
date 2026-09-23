# Qualitest 落地页（GitHub Pages）

Astro 静态站，已部署：`https://qualitest-hq.github.io/qualitest/`。

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

## 部署（维护者）

[`Deploy Pages`](../.github/workflows/deploy-pages.yml)：改 `site/` 或手动跑 workflow 时**始终 build**。  
真正发布到 Pages 需仓库变量 **`ENABLE_PAGES_DEPLOY=true`**，且 Settings → Pages → Source = **GitHub Actions**（公开仓已按此配置即可；关变量则只校验构建、不 deploy）。

改完落地页后：push `site/`，或 Actions → **Deploy Pages** → Run workflow。

对外文案以 README / 本站为准。演示 GIF 在 `public/images/`（与 `docs/images/` 同源副本）；Hero 为 SVG；MCP 区仍为文字步骤（短图待补）。
