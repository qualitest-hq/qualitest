# Qualitest 落地页（GitHub Pages）

Astro 静态站，部署到 `https://qualitest-hq.github.io/qualitest/`。

## 本地

```bash
cd site
npm ci
npm run dev      # http://localhost:4321/qualitest/
npm run build
npm run preview
```

## 公开日上线

> 与路线图「公开日安全动作」、[`docs/deploy.md`](../docs/deploy.md)「GitHub Pages 落地页」、[`docs/v1.0-首发文草稿.md`](../docs/v1.0-首发文草稿.md) 发文前核对一致。**Private 时站点不可对外访问，勿以为忘了部署。**

1. 仓库改为 Public  
2. Settings → Pages → Source = **GitHub Actions**  
3. Actions → **Deploy Pages** → Run workflow  
4. About → Website 填上述 URL  
5. 浏览器打开确认；发文 CTA 可同时链官网 + GitHub

`overview.gif` 放入 `public/images/`（与 `docs/images/overview.gif` 同内容即可）；缺失时 Hero 使用链路 SVG。
