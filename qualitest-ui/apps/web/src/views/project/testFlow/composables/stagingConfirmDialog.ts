/**
 * Staging 单元 confirm 前的删除确认对话框。
 */
import { ElMessageBox } from 'element-plus';

import type { AiStagingUnit } from '../types/aiStagingTypes';

export async function confirmDeleteStagingUnit(unit: AiStagingUnit): Promise<boolean> {
  if (unit.kind === 'deleteNode') {
    try {
      await ElMessageBox.confirm('确认后将删除该节点及关联连线。', '确认删除节点', {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      });
      return true;
    } catch {
      return false;
    }
  }

  if (unit.kind === 'deleteEdge') {
    try {
      await ElMessageBox.confirm('确认后将删除此连线。', '确认删除连线', {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      });
      return true;
    } catch {
      return false;
    }
  }

  if (unit.kind === 'deleteScenario') {
    try {
      await ElMessageBox.confirm('确认后将删除此运行场景。', '确认删除场景', {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      });
      return true;
    } catch {
      return false;
    }
  }

  return true;
}
