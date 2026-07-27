/**
 * 测 stagingUnitIds：unitId 解析与 kind 分类映射。
 * 边界：纯函数，fixture 单元对象。
 * 单跑：yarn test stagingUnitIds   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import type { AiStagingUnit } from '@/views/project/testFlow/types/aiStagingTypes';
import {
  graphObjectIdFromUnit,
  isGraphStagingKind,
  isScenarioStagingKind,
  objectIdFromUnitId,
  stagingKindToCanvasMode,
} from '@/views/project/testFlow/utils/stagingUnitIds';

function unit(partial: Partial<AiStagingUnit> & Pick<AiStagingUnit, 'unitId' | 'kind'>): AiStagingUnit {
  return {
    messageId: 'msg-1',
    status: 'pending',
    patchSlice: {},
    ...partial,
  };
}

describe('stagingUnitIds', () => {
  it('objectIdFromUnitId 解析各类 unitId', () => {
    // 前提：各类 unitId 格式
    // 期望：正确解析对象 id，scenario 特殊 id 为空
    expect(objectIdFromUnitId('addNode:n1')).toBe('n1');
    expect(objectIdFromUnitId('updateEdge:e:sub')).toBe('e:sub');
    expect(objectIdFromUnitId('scenario:activeScenarioId')).toBe('');
  });

  it('isGraphStagingKind / isScenarioStagingKind 分类', () => {
    // 前提：图/场景各类 kind
    // 期望：分类符合约定
    expect(isGraphStagingKind('addNode')).toBe(true);
    expect(isGraphStagingKind('setActiveScenario')).toBe(false);
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

  it('graphObjectIdFromUnit 按 kind 返回 node/edge/scenarioId', () => {
    // 前提：addNode/deleteEdge/setActiveScenario 单元
    // 期望：分别返回 nodeId/edgeId/scenarioId
    expect(graphObjectIdFromUnit(unit({ unitId: 'addNode:n1', kind: 'addNode' }))).toEqual({
      nodeId: 'n1',
    });
    expect(graphObjectIdFromUnit(unit({ unitId: 'deleteEdge:e1', kind: 'deleteEdge' }))).toEqual({
      edgeId: 'e1',
    });
    expect(
      graphObjectIdFromUnit(
        unit({
          unitId: 'setActiveScenario',
          kind: 'setActiveScenario',
          draft: { activeScenarioId: 'sc9' },
        }),
      ),
    ).toEqual({ scenarioId: 'sc9' });
  });
});
