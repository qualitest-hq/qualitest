/**
 * 测 stagingAuthHints：托管头识别与 Diff 标签。
 * 边界：无 headers / 显式头 / profileManaged。
 * 单跑：pnpm test stagingAuthHints   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  hasProfileManagedHeaders,
  stagingHeadersFieldLabel,
} from '@/views/project/testFlow/utils/stagingAuthHints';

describe('stagingAuthHints', () => {
  it('无 headers 时不算托管', () => {
    expect(hasProfileManagedHeaders({ data: { name: 'x' } })).toBe(false);
    expect(stagingHeadersFieldLabel({ data: {} })).toBe('请求头');
  });

  it('显式头无 profileManaged 不算托管', () => {
    const draft = {
      data: {
        headers: [{ name: 'Authorization', value: 'Bearer {{flow.token}}' }],
      },
    };
    expect(hasProfileManagedHeaders(draft)).toBe(false);
    expect(stagingHeadersFieldLabel(draft)).toBe('请求头');
  });

  it('profileManaged 头影响 Diff 标签', () => {
    const draft = {
      data: {
        headers: [
          {
            name: 'Authorization',
            value: 'Bearer {{asset.adminAuth.token}}',
            profileManaged: true,
          },
        ],
      },
    };
    expect(hasProfileManagedHeaders(draft)).toBe(true);
    expect(stagingHeadersFieldLabel(draft)).toBe('请求头（按项目鉴权补全）');
  });
});
