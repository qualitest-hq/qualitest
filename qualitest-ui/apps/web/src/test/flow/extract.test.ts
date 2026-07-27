/**
 * 测 applyExtracts：HTTP 响应变量提取（与后端 ExtractApplicator 对齐）。
 * 边界：纯函数；用例来自 fixtures/compare-extract-cases.json。
 * 单跑：yarn test extract   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { applyExtracts } from '@/utils/flow/extract';
import type { ExtractTarget } from '@/utils/flow/extract';
import type { FlowRunContext, HttpResponseSnapshot } from '@/utils/flow/types';

import fixture from './fixtures/compare-extract-cases.json';

const baseCtx = fixture.mockContext as FlowRunContext;

/** 各用例共用的 HTTP 响应（status / headers / body） */
const response = baseCtx.lastResponse as HttpResponseSnapshot;

/** 深拷贝上下文，避免用例间 flow/env/asset 互相污染 */
function cloneCtx(): FlowRunContext {
  return JSON.parse(JSON.stringify(baseCtx)) as FlowRunContext;
}

function caseById(id: string) {
  const c = fixture.extractCases.find((x) => x.id === id);
  if (!c) throw new Error(`missing extract case: ${id}`);
  return c;
}

describe('applyExtracts', () => {
  it('从 body 用 JsonPath 提取 token 写入 flow 作用域', () => {
    // 前提：extracts 从 body $.data.token 提取
    // 期望：applied 含 token，flow.token 为 body-token
    const c = caseById('flow-token-body');
    const ctx = cloneCtx();
    const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
    expect(applied).toEqual(c.expected);
    if (c.flowAfter) {
      for (const [k, v] of Object.entries(c.flowAfter)) {
        expect(ctx.flow[k]).toEqual(v);
      }
    }
  });

  it('从 body 提取数字字段 code=0 写入 flow 作用域', () => {
    // 前提：extracts 从 body $.data.code 提取
    // 期望：flow.code 为 0
    const c = caseById('flow-code-body');
    const ctx = cloneCtx();
    const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
    expect(applied).toEqual(c.expected);
    if (c.flowAfter) {
      for (const [k, v] of Object.entries(c.flowAfter)) {
        expect(ctx.flow[k]).toEqual(v);
      }
    }
  });

  it('从响应头提取 X-Request-Id 写入 env 作用域', () => {
    // 前提：extracts 从 header X-Request-Id 提取
    // 期望：env.lastReqId 为 req-99
    const c = caseById('env-header');
    const ctx = cloneCtx();
    const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
    expect(applied).toEqual(c.expected);
    if (c.envAfter) {
      for (const [k, v] of Object.entries(c.envAfter)) {
        expect(ctx.env[k]).toEqual(v);
      }
    }
  });

  it('从响应状态码提取写入 flow 作用域', () => {
    // 前提：extracts 从 status 提取
    // 期望：flow.lastStatus 为 200
    const c = caseById('status-extract');
    const ctx = cloneCtx();
    const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
    expect(applied).toEqual(c.expected);
    if (c.flowAfter) {
      for (const [k, v] of Object.entries(c.flowAfter)) {
        expect(ctx.flow[k]).toEqual(v);
      }
    }
  });

  it('从 body 提取值写入 asset 指定条目字段', () => {
    // 前提：extracts 写入 asset.defaults.lastToken
    // 期望：asset 树含 lastToken 且不破坏原有字段
    const c = caseById('asset-scope');
    const ctx = cloneCtx();
    const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
    expect(applied).toEqual(c.expected);
    if (c.assetAfter) {
      expect(ctx.asset).toEqual(c.assetAfter);
    }
  });

  it('regex 提取方式未实现时返回 null', () => {
    // 前提：extracts 使用 regex 方式
    // 期望：applied 为 null
    const c = caseById('regex-unsupported');
    const ctx = cloneCtx();
    const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
    expect(applied).toEqual(c.expected);
  });
});
