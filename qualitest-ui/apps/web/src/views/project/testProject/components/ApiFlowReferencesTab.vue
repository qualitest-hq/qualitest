<template>
  <div v-loading="loading" class="api-flow-refs">
    <div class="api-flow-refs__toolbar">
      <div class="api-flow-refs__summary">
        <span>引用节点 {{ references.length }}</span>
        <span v-if="diagnosed" class="api-flow-refs__warn-count">告警 {{ warnings.length }}</span>
      </div>
      <button class="btn btn--ghost" type="button" :disabled="loading || diagnosing" @click="runDiagnose">
        {{ diagnosing ? '诊断中…' : '诊断影响' }}
      </button>
    </div>

    <div v-if="!loading && references.length === 0" class="api-flow-refs__empty">
      暂无测试流引用此接口
    </div>

    <div v-else class="api-flow-refs__list">
      <div
          v-for="(row, idx) in references"
          :key="(row.testFlowId || '') + '-' + (row.nodeId || idx)"
          class="api-flow-refs__row"
      >
        <div class="api-flow-refs__main">
          <button class="api-flow-refs__link" type="button" @click="openFlow(row)">
            {{ row.flowName || ('流 #' + row.testFlowId) }}
          </button>
          <span class="api-flow-refs__node">{{ row.nodeName || row.nodeId || 'HTTP 节点' }}</span>
        </div>
        <ul v-if="warningsFor(row).length" class="api-flow-refs__warnings">
          <li v-for="(w, wi) in warningsFor(row)" :key="wi">{{ w.message || w.code }}</li>
        </ul>
      </div>
    </div>

    <div v-if="diagnosed && warnings.length === 0 && references.length > 0" class="api-flow-refs__ok">
      诊断完成：未发现孤儿测值或抽取路径告警
    </div>
  </div>
</template>

<script setup>
/**
 * API 详情「引用」页签。
 * 加载时列出绑定本接口的测试流 HTTP 节点；可点「诊断影响」检查孤儿测值、抽取路径等告警。
 * 点击流名跳转到对应测试流画布，并带上 focusNodeId 便于定位节点。
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { diagnoseApiImpacts, listApiFlowReferences } from '@/api/project/testProjectApi'

const props = defineProps({
  apiDetail: { type: Object, required: true },
  testProjectId: { type: [String, Number], required: true },
})

const router = useRouter()
const loading = ref(false)
const diagnosing = ref(false)
const diagnosed = ref(false)
const references = ref([])
const warnings = ref([])

const apiId = computed(() => props.apiDetail?.testProjectApiId)

/** 筛出挂在某一引用行（流+节点）上的诊断告警 */
function warningsFor(row) {
  const nodeId = row?.nodeId != null ? String(row.nodeId) : ''
  const flowId = row?.testFlowId != null ? String(row.testFlowId) : ''
  return warnings.value.filter((w) => {
    const sameFlow = w.testFlowId != null && String(w.testFlowId) === flowId
    const sameNode = !w.nodeId || String(w.nodeId) === nodeId
    return sameFlow && sameNode
  })
}

/** 加载反向引用列表 */
async function loadReferences() {
  if (!apiId.value) {
    references.value = []
    return
  }
  loading.value = true
  diagnosed.value = false
  warnings.value = []
  try {
    const res = await listApiFlowReferences(apiId.value)
    references.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    references.value = []
    ElMessage.error(e?.message || '加载引用失败')
  } finally {
    loading.value = false
  }
}

/** 重新扫描引用并跑语义健康检查 */
async function runDiagnose() {
  if (!apiId.value) return
  diagnosing.value = true
  try {
    const res = await diagnoseApiImpacts(apiId.value)
    const data = res?.data || {}
    if (Array.isArray(data.references)) {
      references.value = data.references
    }
    warnings.value = Array.isArray(data.warnings) ? data.warnings : []
    diagnosed.value = true
  } catch (e) {
    ElMessage.error(e?.message || '诊断失败')
  } finally {
    diagnosing.value = false
  }
}

/** 跳转到测试流画布；有 nodeId 时写入 query 便于定位 */
function openFlow(row) {
  if (!row?.testFlowId) return
  router.push({
    name: 'TestFlowCanvas',
    params: {
      testProjectId: String(props.testProjectId),
      testFlowId: String(row.testFlowId),
    },
    query: row.nodeId ? { focusNodeId: String(row.nodeId) } : undefined,
  })
}

watch(apiId, () => {
  loadReferences()
}, { immediate: true })
</script>

<style scoped lang="scss">
.api-flow-refs {
  padding: 12px 16px 24px;
  min-height: 160px;
}

.api-flow-refs__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.api-flow-refs__summary {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #64748b);
}

.api-flow-refs__warn-count {
  color: #a16207;
}

.btn {
  border: 1px solid var(--pd-divider, #e2e8f0);
  background: #fff;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn--ghost:hover:not(:disabled) {
  border-color: #94a3b8;
}

.api-flow-refs__empty,
.api-flow-refs__ok {
  font-size: 13px;
  color: var(--el-text-color-secondary, #64748b);
  padding: 24px 0;
  text-align: center;
}

.api-flow-refs__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.api-flow-refs__row {
  border: 1px solid var(--pd-divider, #e2e8f0);
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
}

.api-flow-refs__main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 16px;
}

.api-flow-refs__link {
  border: none;
  background: none;
  padding: 0;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.api-flow-refs__link:hover {
  text-decoration: underline;
}

.api-flow-refs__node {
  font-size: 12px;
  color: #64748b;
}

.api-flow-refs__warnings {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #a16207;
  line-height: 1.45;
}
</style>
