import { describe, expect, it } from 'vitest';

import { renderMarkdown } from '@/utils/markdown';

describe('renderMarkdown', () => {
  it('renders GFM table and bold text', () => {
    const html = renderMarkdown('| 名称 | 方法 |\n| --- | --- |\n| 登录 | POST |\n\n共 **10 个接口**');
    expect(html).toContain('<table>');
    expect(html).toContain('<strong>10 个接口</strong>');
    expect(html).toContain('登录');
  });

  it('strips script tags', () => {
    const html = renderMarkdown('hello<script>alert(1)</script>');
    expect(html).not.toContain('<script');
    expect(html).toContain('hello');
  });

  it('adds safe link attributes', () => {
    const html = renderMarkdown('[文档](https://example.com/doc)');
    expect(html).toContain('href="https://example.com/doc"');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
  });
});
