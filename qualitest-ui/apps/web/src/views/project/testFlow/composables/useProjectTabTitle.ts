import { ref, watch } from 'vue';
import type { RouteLocationNormalizedLoaded } from 'vue-router';

import { getTestProject } from '@/api/project/testProject';
import useTagsViewStore from '@/store/modules/tagsView';
import { buildProjectTabTitle } from '@/views/project/testProject/utils/projectTabTitle';

/**
 * 同步 TagsView 标题为「项目名·功能名」。
 * @param route 当前路由
 * @param featureLabel 功能后缀（如「测试流」或画布 flowName）
 */
export function useProjectTabTitle(
  route: RouteLocationNormalizedLoaded,
  featureLabel: () => string,
) {
  const tagsViewStore = useTagsViewStore();
  const projectName = ref('');

  function updateTabTitle() {
    const feature = featureLabel();
    if (!projectName.value || !feature) return;
    tagsViewStore.updateVisitedView(
      Object.assign({}, route, { title: buildProjectTabTitle(projectName.value, feature) }),
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

  watch(() => featureLabel(), () => updateTabTitle());

  return {
    projectName,
    loadProjectName,
    updateTabTitle,
  };
}
