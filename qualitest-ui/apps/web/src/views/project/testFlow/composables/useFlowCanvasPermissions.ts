import { computed } from 'vue';

import { checkPermi } from '@/utils/permission';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';

/**
 * 测试流画布能力开关：编辑、造流 AI、执行、导出。
 *
 * 真实项目流：先校验系统菜单权限，再校验项目成员权限（编辑流、造流 AI、执行流、查看流）；
 * 项目权限尚未加载完成时暂时放行，减轻顶栏按钮闪断。
 * 模板预制流只读，上述能力均为不可用。
 */
export function useFlowCanvasPermissions() {
  const store = useFlowCanvasStore();
  /** 是否处于模板预制流画布 */
  const isTemplate = computed(() => store.canvasMode === 'template');

  const caps = computed(() => new Set(store.capabilities || []));

  /** 是否允许打开造流 AI；模板画布不可用 */
  const canUseAiDesign = computed(() => {
    if (isTemplate.value) return false;
    if (!checkPermi(['project:testProject:query'])) return false;
    // 项目权限未加载完时先放行，加载后要求具备造流 AI 权限
    if (!store.capabilitiesLoaded) return true;
    return caps.value.has('ai:flow:design');
  });

  /** 是否允许改图与保存；模板画布不可用 */
  const canEditFlow = computed(() => {
    if (isTemplate.value) return false;
    if (!checkPermi(['project:testProject:edit'])) return false;
    if (!store.capabilitiesLoaded) return true;
    return caps.value.has('flow:edit');
  });

  /** 是否允许执行或停止场景 */
  const canRunFlow = computed(() => {
    if (isTemplate.value) return false;
    if (!store.capabilitiesLoaded) return true;
    return caps.value.has('flow:run');
  });

  /** 是否允许导出图（按查看测试流权限） */
  const canExportFlow = computed(() => {
    if (isTemplate.value) return false;
    if (!store.capabilitiesLoaded) return true;
    return caps.value.has('flow:view');
  });

  return {
    canUseAiDesign,
    canEditFlow,
    canRunFlow,
    canExportFlow,
    isTemplateCanvas: isTemplate,
  };
}
