import { ref, watch } from 'vue';
import type { RouteLocationNormalizedLoaded } from 'vue-router';

import { getTestProject } from '@/api/project/testProject';
import useTagsViewStore from '@/store/modules/tagsView';
import { buildProjectTabTitle } from '@/views/project/testProject/utils/projectTabTitle';

/**
 * 同步 TagsView 标题为「前缀·功能名」。
 * @param route 当前路由
 * @param featureLabel 功能后缀（如「测试流」「预制测试流」或画布 flowName）
 * @param prefixLabel 可选前缀；缺省用 loadProjectName 写入的项目名
 */
export function useProjectTabTitle(
  route: RouteLocationNormalizedLoaded,
  featureLabel: () => string,
  prefixLabel?: () => string,
) {
  const tagsViewStore = useTagsViewStore();
  const projectName = ref('');

  function currentPrefix() {
    return prefixLabel ? prefixLabel() : projectName.value;
  }

  function updateTabTitle() {
    const prefix = currentPrefix();
    const feature = featureLabel();
    if (!prefix || !feature) return;
    tagsViewStore.updateVisitedView(
      Object.assign({}, route, { title: buildProjectTabTitle(prefix, feature) }),
    );
  }

  async function loadProjectName(testProjectId: string) {
    if (!testProjectId) {
      projectName.value = '';
      return;
    }
    const res = await getTestProject(testProjectId);
    projectName.value = res.data?.projectName ?? '';
    updateTabTitle();
  }

  watch(() => [currentPrefix(), featureLabel()] as const, () => updateTabTitle());

  return {
    projectName,
    loadProjectName,
    updateTabTitle,
  };
}
