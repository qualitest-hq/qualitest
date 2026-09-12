/**
 * 测 stagingUnitIds：unitId 解析与 kind 分类映射。
 * 边界：纯函数，fixture 单元对象。
 * 单跑：pnpm test stagingUnitIds   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';
import {
  graphObjectIdFromUnit,
  isDeleteStagingUnit,
  isGraphStagingKind,
  isScenarioStagingKind,
  objectIdFromUnitId,
  stagingKindToCanvasMode,
} from '@/views/project/testFlow/utils/stagingUnitIds';

function unit(partial: Partial<AiStagingUnit> & Pick<AiStagingUnit, 'unitId' | 'kind'>): AiStagingUnit {
  return {
    messageId: 'msg-1',
    status: 'pending',
    label: partial.unitId,
    patchSlice: {},
    ...partial,
  };
}

describe('stagingUnitIds', () => {
  it('objectIdFromUnitId 解析各类 unitId', () => {
    // 前提：各类 unitId 格式
    // 期望：正确解析对象 id
    expect(objectIdFromUnitId('addNode:n1')).toBe('n1');
    expect(objectIdFromUnitId('updateEdge:e:sub')).toBe('e:sub');
    expect(objectIdFromUnitId('updateScenario:sc1')).toBe('sc1');
  });

  it('isGraphStagingKind / isScenarioStagingKind 分类', () => {
    // 前提：图/场景各类 kind
    // 期望：分类符合约定
    expect(isGraphStagingKind('addNode')).toBe(true);
    expect(isGraphStagingKind('addScenario')).toBe(false);
    expect(isScenarioStagingKind('addScenario')).toBe(true);
    expect(isScenarioStagingKind('addEdge')).toBe(false);
  });

  it('stagingKindToCanvasMode 映射 add/update/delete', () => {
    // 前提：add/update/delete 各类 kind
    // 期望：映射为 add/update/delete 模式
    expect(stagingKindToCanvasMode('addNode')).toBe('add');
    expect(stagingKindToCanvasMode('updateEdge')).toBe('update');
    expect(stagingKindToCanvasMode('deleteScenario')).toBe('delete');
  });

  it('isDeleteStagingUnit 仅识别删除类 kind', () => {
    // 前提：混合 add/update/delete kind
    // 期望：仅 delete* 为 true
    expect(isDeleteStagingUnit(unit({ unitId: 'deleteEdge:e1', kind: 'deleteEdge' }))).toBe(true);
    expect(isDeleteStagingUnit(unit({ unitId: 'deleteNode:n1', kind: 'deleteNode' }))).toBe(true);
    expect(isDeleteStagingUnit(unit({ unitId: 'deleteScenario:s1', kind: 'deleteScenario' }))).toBe(true);
    expect(isDeleteStagingUnit(unit({ unitId: 'addEdge:e1', kind: 'addEdge' }))).toBe(false);
    expect(isDeleteStagingUnit(unit({ unitId: 'updateNode:n1', kind: 'updateNode' }))).toBe(false);
    expect(isDeleteStagingUnit(null)).toBe(false);
  });

  it('graphObjectIdFromUnit 按 kind 返回 node/edge/scenarioId', () => {
    // 前提：addNode/deleteEdge/updateScenario 单元
    // 期望：分别返回 nodeId/edgeId/scenarioId
    expect(graphObjectIdFromUnit(unit({ unitId: 'addNode:n1', kind: 'addNode' }))).toEqual({
      nodeId: 'n1',
    });
    expect(graphObjectIdFromUnit(unit({ unitId: 'deleteEdge:e1', kind: 'deleteEdge' }))).toEqual({
      edgeId: 'e1',
    });
    expect(
      graphObjectIdFromUnit(unit({ unitId: 'updateScenario:sc9', kind: 'updateScenario' })),
    ).toEqual({ scenarioId: 'sc9' });
  });
});
