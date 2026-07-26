/**
 * 单个 Staging 单元在画布上的视觉回滚。
 *
 * 按单元类型撤销 add/update/delete 在节点、边、运行场景上的临时展示。
 * 只改画布 store，不修改 Staging 单元的状态字段。
 */
import type { AiStagingUnit } from '../types/aiStagingTypes';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { isScenarioKind, objectIdFromUnitId } from './stagingUnitIds';
import {
  removeStagingEdgeFromCanvas,
  removeStagingNodeFromCanvas,
  restoreEdgeFromBaseline,
  restoreNodeFromBaseline,
} from '../composables/useAiStagingCanvas';
import { rejectScenarioStagingUnit } from '../composables/useAiStagingScenario';

/**
 * 根据单元类型撤销其在画布上的 Staging 效果：
 * - add 类：从画布移除临时节点/边
 * - update 类：用 baseline 还原节点/边
 * - delete 类：画布上无额外操作（待删标记由 Staging 状态驱动）
 * - 场景类：回滚 runConfig 上的场景 Staging
 */
export function revertStagingUnitOnCanvas(unit: AiStagingUnit) {
  const objectId = objectIdFromUnitId(unit.unitId);

  switch (unit.kind) {
    case 'addNode':
      removeStagingNodeFromCanvas(objectId);
      break;
    case 'addEdge':
      removeStagingEdgeFromCanvas(objectId);
      break;
    case 'updateNode':
      restoreNodeFromBaseline(objectId, unit.baseline);
      break;
    case 'updateEdge':
      restoreEdgeFromBaseline(objectId, unit.baseline);
      break;
    case 'deleteNode':
    case 'deleteEdge':
      // 待删高亮由 Staging 标记控制，reject 或清 store 后自然消失
      break;
    default:
      if (isScenarioKind(unit.kind)) {
        rejectScenarioStagingUnit(unit);
      }
      break;
  }

  useFlowCanvasStore().markDirty();
}
