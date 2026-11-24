<template>
  <div class="subflow-prop">
    <div class="field">
      <label>引用测试流</label>
      <select :value="subflowIdVal" @change="onSubflowChange">
        <option value="">— 请选择 —</option>
        <optgroup
            v-for="group in projectSubflowGroups"
            :key="group.label + '-' + (group.items[0]?.flowGroupId ?? 'ungrouped')"
            :label="group.label"
        >
          <option v-for="sf in group.items" :key="sf.testFlowId" :value="String(sf.testFlowId)">
            {{ sf.flowName }}
          </option>
        </optgroup>
      </select>
      <div class="field__hint">引用同项目下任意测试流（不含当前正在编辑的流）；每次 Run 取该流当前图</div>
    </div>

    <div v-if="platformTemplates.length" class="field">
      <label>从平台模板创建</label>
      <div class="subflow-tpl-list">
        <button
            v-for="tpl in platformTemplates"
            :key="tpl.templateId"
            :disabled="creating"
            class="btn btn--ghost subflow-tpl-btn"
            type="button"
            @click="createFromTemplate(tpl)"
        >
          {{ tpl.name }}
        </button>
      </div>
    </div>

    <div class="field">
      <label>输入映射（inputs）</label>
      <div v-for="(row, idx) in inputRows" :key="'in-' + idx" class="subflow-io-row">
        <input
            :value="row.name"
            class="subflow-io-name"
            placeholder="name"
            type="text"
            @input="updateInput(idx, 'name', $event.target.value)"
        />
        <input
            :value="row.value"
            class="subflow-io-value"
            placeholder="{{flow.xxx}} 或 {{asset.xxx}}"
            type="text"
            @input="updateInput(idx, 'value', $event.target.value)"
        />
        <button class="subflow-io-del" type="button" @click="removeInput(idx)">×</button>
      </div>
      <button class="btn btn--ghost btn--sm" type="button" @click="addInput">+ 添加输入</button>
    </div>

    <div class="field">
      <label>子流定稿出参</label>
      <div class="field__hint">
        子流 return（或入口 fallbackOut）后挂在
        <code v-pre>{{nodes.&lt;本节点id&gt;.out}}</code>，父流用 Assign / 断言读取。
      </div>
    </div>

    <!-- 失败后仍执行本子流节点（封装多步清理） -->
    <AlwaysRunPropertySection :node="node" />
  </div>
</template>

<script setup>
/**
 * 子流节点属性面板：选择被引用的子流、入参映射、失败后是否仍执行。
 * 每次 Run 取被引用流当前图；定稿出参挂在 nodes.<本节点id>.out。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { unwrapGraphPayload } from '@/utils/flow/graphValidate'

import {
  createFromSubflowTemplate,
  getTestFlow,
  listSubflowTemplates,
  listTestFlow,
} from '@/api/project/testFlow'

import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowCanvasStore } from '../../stores/flowCanvasStore'
import AlwaysRunPropertySection from './AlwaysRunPropertySection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const store = useFlowCanvasStore()
const { patchNodeData } = useFlowNodes()

const projectSubflows = ref([])
/** 按目录主键分段，供下拉 optgroup 展示（无目录归入「未分组」） */
const projectSubflowGroups = computed(() => {
  const map = new Map()
  for (const sf of projectSubflows.value) {
    const key = sf.flowGroupId != null ? String(sf.flowGroupId) : 'ungrouped'
    const label = sf.flowGroupName?.trim() ? sf.flowGroupName : '未分组'
    if (!map.has(key)) {
      map.set(key, { label, items: [] })
    }
    map.get(key).items.push(sf)
  }
  return Array.from(map.values())
})
const platformTemplates = ref([])
const creating = ref(false)

const subflowIdVal = computed(() => String(props.node?.data?.subflowId || ''))
/** 入参映射行列表：每行为子流变量名与传入值 */
const inputRows = computed(() => props.node?.data?.inputs || [])

/** 拉取同项目其它测试流（排除当前流），供引用选择 */
async function loadSubflows() {
  if (!store.testProjectId) return
  try {
    const res = await listTestFlow({ testProjectId: store.testProjectId, pageNum: 1, pageSize: 200 })
    const rows = res.rows || []
    const currentId = String(store.testFlowId || '')
    projectSubflows.value = currentId
      ? rows.filter((sf) => String(sf.testFlowId) !== currentId)
      : rows
  } catch {
    projectSubflows.value = []
  }
}

async function loadTemplates() {
  try {
    const res = await listSubflowTemplates()
    platformTemplates.value = res.data || []
  } catch {
    platformTemplates.value = []
  }
}

/** 把补丁合并写回当前子流节点数据，卡片摘要随之刷新 */
function refreshSummary(patch) {
  patchNodeData(props.node.id, patch)
}

/**
 * 根据子流图的第一个场景入口的入参种子，生成入参映射建议：
 * 每个种子键对应一行，值默认引用同名流变量；解析失败或无场景时返回空数组。
 */
function suggestInputsFromGraphJson(graphJsonRaw) {
  if (!graphJsonRaw) return []
  try {
    const raw = typeof graphJsonRaw === 'string' ? JSON.parse(graphJsonRaw) : graphJsonRaw
    const graph = unwrapGraphPayload(raw)
    const nodes = Array.isArray(graph.nodes) ? graph.nodes : []
    const entry = nodes.find((n) => n && n.type === 'scenario-entry')
    const rawScenarios = entry?.data?.scenarios
    if (!Array.isArray(rawScenarios) || rawScenarios.length === 0) return []
    const scenario = rawScenarios[0]
    const seed = scenario?.inSeed && typeof scenario.inSeed === 'object' ? scenario.inSeed : {}
    return Object.keys(seed).map((key) => ({
      name: key,
      value: `{{flow.${key}}}`,
    }))
  } catch {
    return []
  }
}

/** 已有入参映射时只提交基础补丁；否则按子流图生成入参建议并一并写入节点 */
function applyIoSuggestions(graphJsonRaw, basePatch = {}) {
  const hasInputs = Array.isArray(props.node?.data?.inputs) && props.node.data.inputs.length > 0
  if (hasInputs) {
    refreshSummary(basePatch)
    return
  }
  const suggestedInputs = suggestInputsFromGraphJson(graphJsonRaw)
  const patch = { ...basePatch }
  if (suggestedInputs.length) patch.inputs = suggestedInputs
  refreshSummary(patch)
}

function onSubflowChange(e) {
  const id = e.target.value
  const sf = projectSubflows.value.find((s) => String(s.testFlowId) === id)
  const patch = {
    subflowId: id,
    subflowName: sf?.flowName || '',
  }
  if (id) {
    loadAndSuggestIo(id, patch)
    return
  }
  refreshSummary(patch)
}

async function loadAndSuggestIo(subflowId, basePatch = {}) {
  try {
    const res = await getTestFlow(subflowId)
    const graphJson = res.data?.graphJson ?? res.data?.graph_json ?? ''
    applyIoSuggestions(graphJson, basePatch)
  } catch {
    refreshSummary(basePatch)
  }
}

function addInput() {
  const rows = [...inputRows.value, { name: '', value: '' }]
  refreshSummary({ inputs: rows })
}

function updateInput(idx, key, val) {
  const rows = inputRows.value.map((r, i) => (i === idx ? { ...r, [key]: val } : r))
  refreshSummary({ inputs: rows })
}

function removeInput(idx) {
  refreshSummary({ inputs: inputRows.value.filter((_, i) => i !== idx) })
}

async function createFromTemplate(tpl) {
  if (!store.testProjectId || creating.value) return
  creating.value = true
  try {
    const res = await createFromSubflowTemplate({
      testProjectId: store.testProjectId,
      templateId: tpl.templateId,
      flowName: tpl.name,
    })
    const created = res.data
    await loadSubflows()
    const inputs = (tpl.inputs || []).map((i) => ({
      name: i.name,
      value: `{{flow.${i.name}}}`,
    }))
    const patch = {
      subflowId: String(created?.testFlowId || ''),
      subflowName: created?.flowName || tpl.name,
      templateId: tpl.templateId,
      inputs,
    }
    refreshSummary(patch)
    if (created?.testFlowId && !inputs.length) {
      await loadAndSuggestIo(String(created.testFlowId), patch)
    }
    ElMessage.success(`已创建子流「${tpl.name}」并绑定`)
  } catch (e) {
    ElMessage.error(e?.message || '创建子流失败')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadSubflows()
  loadTemplates()
})

watch(
  () => store.testProjectId,
  () => {
    loadSubflows()
  },
)
</script>

<style scoped lang="scss">
.subflow-prop {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.subflow-tpl-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.subflow-tpl-btn {
  font-size: 11px;
  padding: 4px 8px;
}

.subflow-io-row {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
  align-items: center;
}

.subflow-io-name {
  width: 28%;
  min-width: 60px;
  padding: 4px 6px;
  font-size: 11px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 4px;
}

.subflow-io-value {
  flex: 1;
  min-width: 0;
  padding: 4px 6px;
  font-size: 11px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 4px;
}

.subflow-io-del {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--pd-text-muted);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.btn--sm {
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
}
</style>
