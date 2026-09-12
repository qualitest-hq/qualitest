/**
 * 测 stagingLocate：摘要定位辅助（首个 pending 单元与场景 id 解析）。
 * 边界：纯函数，fixture 单元列表。
 * 单跑：pnpm test stagingLocate   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';
import {
  findFirstPendingGraphUnit,
  findFirstPendingScenarioUnit,
  isGraphStagingKind,
  isScenarioStagingKind,
  resolveScenarioIdFromUnit,
} from '@/views/project/testFlow/utils/stagingLocate';

function unit(partial: Partial<AiStagingUnit> & Pick<AiStagingUnit, 'unitId' | 'kind' | 'status'>): AiStagingUnit {
  return {
    messageId: 'msg-1',
    patchSlice: {},
    ...partial,
  };
}

describe('stagingLocate', () => {
  const units: AiStagingUnit[] = [
    unit({ unitId: 'addNode:n1', kind: 'addNode', status: 'confirmed' }),
    unit({ unitId: 'updateNode:n2', kind: 'updateNode', status: 'pending' }),
    unit({ unitId: 'addScenario:sc2', kind: 'addScenario', status: 'pending' }),
  ];

  it('isGraphStagingKind / isScenarioStagingKind 分类正确', () => {
    // 前提：各类 kind 输入
    // 期望：图/场景 kind 判定符合约定
    expect(isGraphStagingKind('addNode')).toBe(true);
    expect(isScenarioStagingKind('addScenario')).toBe(true);
    expect(isGraphStagingKind('updateScenario')).toBe(false);
  });

  it('findFirstPendingGraphUnit 跳过已确认与非图单元', () => {
    // 前提：列表含 confirmed 图单元与 pending 场景单元
    // 期望：返回首个 pending 图单元 updateNode:n2
    expect(findFirstPendingGraphUnit(units)?.unitId).toBe('updateNode:n2');
  });

  it('findFirstPendingScenarioUnit 返回首个 pending 场景单元', () => {
    // 前提：含 pending addScenario
    // 期望：返回 addScenario:sc2
    expect(findFirstPendingScenarioUnit(units)?.unitId).toBe('addScenario:sc2');
  });

  it('resolveScenarioIdFromUnit 从 unitId 解析场景 id', () => {
    // 前提：addScenario 单元 unitId 含场景 id
    // 期望：返回 sc2
    expect(resolveScenarioIdFromUnit(units[2])).toBe('sc2');
  });
});
