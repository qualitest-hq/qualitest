/**
 * 测 renderMarkdown：GFM 渲染与 XSS 防护。
 * 边界：纯函数，Node 环境字符串处理。
 * 单跑：yarn test markdown   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { renderMarkdown } from '@/utils/markdown';

describe('renderMarkdown', () => {
  it('渲染 GFM 表格与加粗文本', () => {
    // 前提：输入含 GFM 表格与 **加粗**
    // 期望：HTML 含 table 与 strong 标签
    const html = renderMarkdown('| 名称 | 方法 |\n| --- | --- |\n| 登录 | POST |\n\n共 **10 个接口**');
    expect(html).toContain('<table>');
    expect(html).toContain('<strong>10 个接口</strong>');
    expect(html).toContain('登录');
  });

  it('过滤 script 标签防止 XSS', () => {
    // 前提：输入含 script 标签
    // 期望：输出无 script，保留普通文本
    const html = renderMarkdown('hello<script>alert(1)</script>');
    expect(html).not.toContain('<script');
    expect(html).toContain('hello');
  });

  it('外链自动补上安全属性（target/rel）', () => {
    // 前提：Markdown 含外链
    // 期望：a 标签含 target=_blank 与 rel=noopener noreferrer
    const html = renderMarkdown('[文档](https://example.com/doc)');
    expect(html).toContain('href="https://example.com/doc"');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
  });
});
