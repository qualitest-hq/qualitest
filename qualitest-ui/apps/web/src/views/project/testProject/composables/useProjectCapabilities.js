/**
 * 按项目拉取当前用户的成员角色与权限列表，供列表页、工具栏做按钮门禁。
 * 未加载完成前 has / hasAny 返回 false，避免无权限按钮先显示再隐藏。
 */
import { computed, ref, unref, watch } from 'vue'

import { myProjectContext } from '@/api/project/testProject'

import {
  hasAnyProjectCapability,
  hasProjectCapability,
  parseProjectCapabilities,
} from '../utils/projectCapabilities'

/**
 * @param {import('vue').Ref|import('vue').ComputedRef|string|number|null|undefined} testProjectIdSource 项目 id 或其响应式来源
 * @returns {{capabilities: import('vue').Ref<string[]>, capabilitySet: import('vue').ComputedRef<Set<string>>, memberRole: import('vue').Ref<string|null>, loaded: import('vue').Ref<boolean>, loading: import('vue').Ref<boolean>, load: Function, has: Function, hasAny: Function}}
 */
export function useProjectCapabilities(testProjectIdSource) {
  /** 当前用户在该项目的权限 code 列表 */
  const capabilities = ref([])
  /** 当前用户在该项目的成员角色标识 */
  const memberRole = ref(null)
  /** 是否已完成一次加载（成功或失败均算完成） */
  const loaded = ref(false)
  /** 是否正在请求项目上下文 */
  const loading = ref(false)

  const capabilitySet = computed(() => new Set(capabilities.value))

  /** 解析当前项目 id；空则返回 null */
  function resolveProjectId() {
    const raw = unref(testProjectIdSource)
    if (raw == null || raw === '') return null
    return String(raw)
  }

  /**
   * 是否具备指定项目权限；未加载完视为不具备。
   * @param {string} code 权限编码
   */
  function has(code) {
    if (!loaded.value) return false
    return hasProjectCapability(capabilities.value, code)
  }

  /**
   * 是否具备任一指定项目权限；未加载完视为不具备。
   * @param {string[]} codes 权限编码列表
   */
  function hasAny(codes) {
    if (!loaded.value) return false
    return hasAnyProjectCapability(capabilities.value, codes)
  }

  /** 拉取当前用户在该项目的角色与权限；无项目 id 时清空并标记已加载 */
  function load() {
    const testProjectId = resolveProjectId()
    if (!testProjectId) {
      capabilities.value = []
      memberRole.value = null
      loaded.value = true
      return Promise.resolve()
    }
    loading.value = true
    return myProjectContext(testProjectId)
      .then((res) => {
        capabilities.value = parseProjectCapabilities(res.data)
        memberRole.value = res.data?.memberRole ?? null
        loaded.value = true
      })
      .catch(() => {
        capabilities.value = []
        memberRole.value = null
        loaded.value = true
      })
      .finally(() => {
        loading.value = false
      })
  }

  watch(
    () => resolveProjectId(),
    (id, prev) => {
      if (id !== prev) {
        loaded.value = false
        void load()
      }
    },
    { immediate: true },
  )

  return {
    capabilities,
    capabilitySet,
    memberRole,
    loaded,
    loading,
    load,
    has,
    hasAny,
  }
}
