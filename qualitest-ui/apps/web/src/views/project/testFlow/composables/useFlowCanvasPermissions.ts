import { computed } from 'vue';

import { checkPermi } from '@/utils/permission';

/**
 * 测试流画布页权限：AI 设计需 query，保存/运行需 edit。
 */
export function useFlowCanvasPermissions() {
  const canUseAiDesign = computed(() => checkPermi(['project:testProject:query']));
  const canEditFlow = computed(() => checkPermi(['project:testProject:edit']));

  return { canUseAiDesign, canEditFlow };
}
