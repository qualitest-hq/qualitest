/**
 * patchHttpNodeNormalize 单元测试。
 */
import { describe, expect, it } from 'vitest';

import {
  convertLegacyExtractExpr,
  normalizeFlowDesignPatchHttpNodes,
  normalizeHttpNodeData,
} from '@/views/project/testFlow/utils/patchHttpNodeNormalize';

describe('patchHttpNodeNormalize', () => {
  it('requestBody 写入 requestValueOverrides.bodyExample，不落 requestConfig', () => {
    const data = normalizeHttpNodeData(
      {
        callMode: 'project',
        testProjectApiId: '2001',
        apiPath: '/api/login',
        requestBody: '{"mobile":"13800000001","password":"Test@123456"}',
      },
      {
        apiPath: '/api/login',
        requestConfig: {
          method: 'POST',
          queryParams: [],
          pathParams: [],
          body: { mode: 'json', json: { example: { mobile: '', password: '' } } },
        },
      },
    );

    expect(data.requestBody).toBeUndefined();
    expect(data.requestConfig).toBeUndefined();
    expect(data.apiPath).toBeUndefined();
    expect(data.httpMethod).toBe('POST');
    const overrides = data.requestValueOverrides as {
      bodyExample: Record<string, string>;
    };
    expect(overrides.bodyExample).toEqual({
      mobile: '13800000001',
      password: 'Test@123456',
    });
  });

  it('与资产默认相同的测值不写入 overrides', () => {
    const data = normalizeHttpNodeData(
      {
        callMode: 'project',
        requestConfig: {
          queryParams: [{ name: 'q', value: 'hello' }],
        },
      },
      {
        requestConfig: {
          method: 'GET',
          queryParams: [{ name: 'q', value: 'hello' }],
          pathParams: [],
          body: { mode: 'none' },
        },
      },
    );
    expect(data.requestConfig).toBeUndefined();
    expect(data.requestValueOverrides).toBeUndefined();
  });

  it('extracts.value 转为 expr/from/scope', () => {
    const data = normalizeHttpNodeData({
      callMode: 'project',
      extracts: [{ name: 'mobile', value: 'responses.mobile' }],
    });

    const extracts = data.extracts as Array<Record<string, unknown>>;
    expect(extracts).toHaveLength(1);
    expect(extracts[0].expr).toBe('$.data.mobile');
    expect(extracts[0].from).toBe('body');
    expect(extracts[0].scope).toBe('flow');
    expect(extracts[0].value).toBeUndefined();
  });

  it('缺失 successCheck 时按 callMode 补默认 mode', () => {
    const project = normalizeHttpNodeData({ callMode: 'project' });
    expect((project.successCheck as { mode: string }).mode).toBe('inherit');

    const external = normalizeHttpNodeData({ callMode: 'external', externalUrl: 'https://x' });
    expect((external.successCheck as { mode: string }).mode).toBe('off');
  });

  it('normalizeFlowDesignPatchHttpNodes 处理 addNodes', () => {
    const patch = normalizeFlowDesignPatchHttpNodes({
      addNodes: [
        {
          id: '9001',
          type: 'http',
          position: { x: 0, y: 0 },
          data: {
            callMode: 'project',
            extracts: [{ name: 'token', value: 'response.data.token' }],
          },
        },
      ],
    });

    const extracts = patch.addNodes?.[0]?.data?.extracts as Array<Record<string, unknown>>;
    expect(extracts[0].expr).toBe('$.data.token');
  });

  it('convertLegacyExtractExpr', () => {
    expect(convertLegacyExtractExpr('responses.mobile')).toBe('$.mobile');
    expect(convertLegacyExtractExpr('http.body.code')).toBe('$.code');
  });
});
