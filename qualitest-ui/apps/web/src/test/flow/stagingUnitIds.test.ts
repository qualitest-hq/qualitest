/**
 * stagingUnitIds 单元测试。
 *
 * 运行（apps/web 目录）：yarn test stagingUnitIds
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
    expect(objectIdFromUnitId('addNode:n1')).toBe('n1');
    expect(objectIdFromUnitId('updateEdge:e:sub')).toBe('e:sub');
    expect(objectIdFromUnitId('scenario:activeScenarioId')).toBe('');
  });

  it('isGraphStagingKind / isScenarioStagingKind 分类', () => {
    expect(isGraphStagingKind('addNode')).toBe(true);
    expect(isGraphStagingKind('setActiveScenario')).toBe(false);
    expect(isScenarioStagingKind('addScenario')).toBe(true);
    expect(isScenarioStagingKind('addEdge')).toBe(false);
  });

  it('stagingKindToCanvasMode 映射 add/update/delete', () => {
    expect(stagingKindToCanvasMode('addNode')).toBe('add');
    expect(stagingKindToCanvasMode('updateEdge')).toBe('update');
    expect(stagingKindToCanvasMode('deleteScenario')).toBe('delete');
  });

  it('graphObjectIdFromUnit 按 kind 返回 node/edge/scenarioId', () => {
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
