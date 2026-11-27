/**
 * 测 patchHttpNodeNormalize：仅 extracts 路径轻量规范化（非 Staging 权威）。
 * 边界：纯函数；HTTP 落盘形状由服务端 Normalizer 负责。
 * 单跑：pnpm test patchHttpNodeNormalize   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  normalizeExtractExprPath,
  normalizeHttpNodeExtracts,
} from '@/views/project/testFlow/utils/patchHttpNodeNormalize';

describe('patchHttpNodeNormalize', () => {
  it('normalizeExtractExprPath 识别常见前缀', () => {
    // 前提：responses. / http.body. 前缀
    // 期望：转为 $. 路径
    expect(normalizeExtractExprPath('responses.mobile')).toBe('$.mobile');
    expect(normalizeExtractExprPath('http.body.code')).toBe('$.code');
    expect(normalizeExtractExprPath('$.already')).toBe('$.already');
  });

  it('normalizeHttpNodeExtracts 规范化 expr', () => {
    // 前提：extracts.expr 为 responses.mobile
    // 期望：转为 $.mobile，保留 name
    const extracts = normalizeHttpNodeExtracts([
      { name: 'mobile', expr: 'responses.mobile' },
    ]);
    expect(extracts).toHaveLength(1);
    expect(extracts[0].expr).toBe('$.mobile');
    expect(extracts[0].name).toBe('mobile');
  });
});
