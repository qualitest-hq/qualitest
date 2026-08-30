/**
 * 按画布模式解析接口详情：
 * - template：读 templateApiCatalog（作者期 id，禁止打项目 Long 详情）
 * - project：请求 `/project/testProjectApi/{id}`
 * 失败或未找到返回 null，由调用方决定是否提示。
 */
import { getTestProjectApi } from '@/api/project/testProjectApi';
import { findTemplateApiDetail } from '@/views/project/testProjectTemplate/utils/synthesizeTemplateApiTree';

import { useFlowCanvasStore } from '../stores/flowCanvasStore';

export async function resolveCanvasApiDetail(apiId: string | number | null | undefined): Promise<Record<string, unknown> | null> {
  const id = String(apiId ?? '').trim();
  if (!id) return null;
  const store = useFlowCanvasStore();
  try {
    if (store.canvasMode === 'template') {
      const detail = findTemplateApiDetail(store.templateApiCatalog, id);
      return detail && typeof detail === 'object' ? (detail as Record<string, unknown>) : null;
    }
    const res = await getTestProjectApi(id);
    const detail = res?.data ?? res;
    return detail && typeof detail === 'object' ? (detail as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
