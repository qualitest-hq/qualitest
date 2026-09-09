import { computed } from 'vue';

import { checkPermi } from '@/utils/permission';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';

/**
 * 测试流画布页的编辑 / AI 权限。
 *
 * - 普通项目画布：按 project:testProject:* 权限判断能否改图、用 AI
 * - 项目模板预制流画布（canvasMode=template）：一律不可编辑、不可用 AI（只能看）
 */
export function useFlowCanvasPermissions() {
  const store = useFlowCanvasStore();
  /** 是否处于模板预制流画布 */
  const isTemplate = computed(() => store.canvasMode === 'template');

  /** 是否允许打开 AI 设计：模板画布永远 false */
  const canUseAiDesign = computed(() => {
    if (isTemplate.value) return false;
    return checkPermi(['project:testProject:query']);
  });

  /** 是否允许改图 / 保存：模板画布永远 false */
  const canEditFlow = computed(() => {
    if (isTemplate.value) return false;
    return checkPermi(['project:testProject:edit']);
  });

  return { canUseAiDesign, canEditFlow, isTemplateCanvas: isTemplate };
}
