/**
 * 测 useFlowValidation：Staging 单元确认后画布校验随之重算。
 * 边界：Pinia 内存态，无 API 依赖。
 * 单跑：pnpm test useFlowValidation   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { withFreshPinia } from '@/test/helpers/pinia';
import {
  resetFlowValidationForTests,
  useFlowValidation,
} from '@/views/project/testFlow/composables/useFlowValidation';
import { useAiStagingStore } from '@/views/project/testFlow/stores/aiStagingStore';
import { useFlowCanvasStore } from '@/views/project/testFlow/stores/flowCanvasStore';
import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';

describe('useFlowValidation', () => {
  withFreshPinia(() => {
    resetFlowValidationForTests();
  });

  it('最后一条 Staging 边确认后，不再误报多个开始节点', () => {
    // 前提：登录子流 → 查购物车 → 断言，第二条边仍是待确认的 Staging 新增
    // 期望：待确认与确认后都不出现开始节点类问题（半成品阶段不提示，确认后按完整规则判）
    const canvas = useFlowCanvasStore();
    const staging = useAiStagingStore();
    const runConfig = {
      activeScenarioId: 'sc1',
      scenarios: [{ id: 'sc1', name: '默认', envId: '', inSeed: {} }],
    };
    canvas.runConfig = runConfig;
    canvas.nodes = [
      { id: 'login', type: 'subflow', position: { x: 0, y: 0 }, data: { name: '客户端登录', subflowId: '1' } },
      { id: 'cart', type: 'http', position: { x: 300, y: 0 }, data: { name: '查询我的购物车', callMode: 'project' } },
      {
        id: 'check',
        type: 'assert',
        position: { x: 600, y: 0 },
        data: { name: '接口成功断言', rules: [{ left: 'http.status', operator: 'eq', right: '200' }] },
      },
    ];
    canvas.edges = [
      { id: 'e1', source: 'login', target: 'cart' },
      { id: 'e2', source: 'cart', target: 'check' },
    ];
    staging.unitsById = {
      'addEdge:e2': {
        unitId: 'addEdge:e2',
        messageId: 'msg-1',
        kind: 'addEdge',
        status: 'pending',
        label: '查询我的购物车 → 接口成功断言',
      } as AiStagingUnit,
    };

    const { issues } = useFlowValidation();
    const startIssues = () => issues.value.filter((i) => i.message.includes('开始节点'));

    expect(startIssues()).toEqual([]);

    staging.markConfirmed('addEdge:e2');

    expect(startIssues()).toEqual([]);
  });
});
