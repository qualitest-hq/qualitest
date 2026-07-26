/**
 * applyExtracts 单元测试：验证 HTTP 响应变量提取逻辑（设计态 / 与后端 ExtractApplicator 对齐）。
 *
 * 被测函数根据 extracts 配置（from=body/header/status、expr、scope、name），
 * 从上一步 HTTP 响应快照中提取值，写入 env / flow / asset 对应作用域，并返回 applied 列表。
 *
 * 数据驱动：用例来自 fixtures/compare-extract-cases.json 的 extractCases 数组，
 * 与后端 qualitest-system/src/test/resources/flow/compare-extract-cases.json 共享。
 * 覆盖 JsonPath body 提取、header 提取、status 码、asset 作用域、regex 未实现返回 null。
 *
 * 运行（apps/web 目录）：yarn test extract
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

describe('applyExtracts', () => {
  // eslint-disable-next-line no-console
  console.log(`\n=== applyExtracts fixture cases (${fixture.extractCases.length}) ===`);

  /** 数据驱动：遍历 extractCases，断言 applied 列表及 flowAfter/envAfter/assetAfter 副作用 */
  for (const c of fixture.extractCases) {
    it(c.id, () => {
      const ctx = cloneCtx();
      const applied = applyExtracts(c.extracts as ExtractTarget[], ctx, response);
      // eslint-disable-next-line no-console
      console.log(`  OK ${c.id.padEnd(22)}  applied=${JSON.stringify(applied)}`);

      expect(applied).toEqual(c.expected);

      if (c.flowAfter) {
        for (const [k, v] of Object.entries(c.flowAfter)) {
          expect(ctx.flow[k]).toEqual(v);
        }
      }
      if (c.envAfter) {
        for (const [k, v] of Object.entries(c.envAfter)) {
          expect(ctx.env[k]).toEqual(v);
        }
      }
      if (c.assetAfter) {
        expect(ctx.asset).toEqual(c.assetAfter);
      }
    });
  }
});
