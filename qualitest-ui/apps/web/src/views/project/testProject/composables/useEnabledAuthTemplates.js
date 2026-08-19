import { ref } from 'vue'
import { listEnabledTestProjectTemplate } from '@/api/project/testProjectTemplate'

/**
 * 已启用鉴权模板列表：新建项目勾选、设置页追加共用。
 */
export function useEnabledAuthTemplates() {
  const templateLoading = ref(false)
  const enabledTemplates = ref([])

  function loadEnabledTemplates() {
    templateLoading.value = true
    return listEnabledTestProjectTemplate()
      .then((res) => {
        enabledTemplates.value = Array.isArray(res.data) ? res.data : []
        return enabledTemplates.value
      })
      .catch(() => {
        enabledTemplates.value = []
        return enabledTemplates.value
      })
      .finally(() => {
        templateLoading.value = false
      })
  }

  return {
    templateLoading,
    enabledTemplates,
    loadEnabledTemplates,
  }
}

/** checkbox 选中值 → 后端 templateIds（保留长整型字符串，避免 JS Number 精度丢失） */
export function toTemplateIds(ids) {
  return (ids || [])
    .map((id) => String(id ?? '').trim())
    .filter((id) => /^\d+$/.test(id) && id !== '0')
}
