/**
 * 测 patchHttpNodeNormalize：HTTP 节点 patch 数据规范化。
 * 边界：纯函数，fixture 节点与 API 资产。
 * 单跑：yarn test patchHttpNodeNormalize   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import {
  convertLegacyExtractExpr,
  normalizeFlowDesignPatchHttpNodes,
  normalizeHttpNodeData,
} from '@/views/project/testFlow/utils/patchHttpNodeNormalize';

describe('patchHttpNodeNormalize', () => {
  it('requestBody 写入 requestValueOverrides.bodyExample，不落 requestConfig', () => {
    // 前提：AI patch 含 requestBody 与 API 默认配置
    // 期望：body 进 overrides，requestBody/requestConfig/apiPath 清除
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
    // 前提：query 值与 API 默认一致
    // 期望：不生成 requestConfig 与 overrides
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
    // 前提：extracts 使用 legacy value 字段
    // 期望：转为 expr/from/scope，value 清除
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
    // 前提：project / external 节点无 successCheck
    // 期望：project 为 inherit，external 为 off
    const project = normalizeHttpNodeData({ callMode: 'project' });
    expect((project.successCheck as { mode: string }).mode).toBe('inherit');

    const external = normalizeHttpNodeData({ callMode: 'external', externalUrl: 'https://x' });
    expect((external.successCheck as { mode: string }).mode).toBe('off');
  });

  it('normalizeFlowDesignPatchHttpNodes 处理 addNodes', () => {
    // 前提：patch addNodes 含 legacy extracts
    // 期望：addNodes 内 extracts expr 已规范化
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

  it('旧式提取路径统一转换为 $. 前缀写法', () => {
    // 前提：legacy responses.* / http.body.* 路径
    // 期望：转为 $. 前缀 JsonPath
    expect(convertLegacyExtractExpr('responses.mobile')).toBe('$.mobile');
    expect(convertLegacyExtractExpr('http.body.code')).toBe('$.code');
  });
});
