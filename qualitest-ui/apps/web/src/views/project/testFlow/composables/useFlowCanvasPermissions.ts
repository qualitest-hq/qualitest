import { computed } from 'vue';

import { checkPermi } from '@/utils/permission';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';

/**
 * 测试流画布页权限：项目画布用 testProject；模板画布用 testProjectTemplate。
 */
export function useFlowCanvasPermissions() {
  const store = useFlowCanvasStore();
  const isTemplate = computed(() => store.canvasMode === 'template');

  const canUseAiDesign = computed(() => {
    if (isTemplate.value) {
      return checkPermi([
        'project:testProjectTemplate:query',
        'project:testProjectTemplate:edit',
        'project:testProjectTemplate:list',
      ]);
    }
    return checkPermi(['project:testProject:query']);
  });

  const canEditFlow = computed(() => {
    if (isTemplate.value) {
      if (store.templateReadOnly) return false;
      return checkPermi(['project:testProjectTemplate:edit']);
    }
    return checkPermi(['project:testProject:edit']);
  });

  return { canUseAiDesign, canEditFlow, isTemplateCanvas: isTemplate };
}
