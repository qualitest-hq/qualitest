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
      <div class="field__hint">引用同项目下任意测试流（不含当前正在编辑的流）</div>
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
      <label>版本策略</label>
      <select :value="versionPolicy" @change="onPolicyChange">
        <option value="pinned">pinned（固化当前图）</option>
        <option value="latest">latest（每次 Run 取最新）</option>
      </select>
      <div v-if="versionPolicy === 'pinned' && hasPinnedSnapshot" class="field__hint">
        已固化子流图快照（pinnedGraphJson）
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
      <label>输出映射（outputs）</label>
      <div v-for="(row, idx) in outputRows" :key="'out-' + idx" class="subflow-io-row">
        <input
            :value="row.name"
            class="subflow-io-name"
            placeholder="子流变量名"
            type="text"
            @input="updateOutput(idx, 'name', $event.target.value)"
        />
        <input
            :value="row.flowKey"
            class="subflow-io-value"
            placeholder="flowKey"
            type="text"
            @input="updateOutput(idx, 'flowKey', $event.target.value)"
        />
        <button class="subflow-io-del" type="button" @click="removeOutput(idx)">×</button>
      </div>
      <button class="btn btn--ghost btn--sm" type="button" @click="addOutput">+ 添加输出</button>
    </div>
  </div>
</template>

<script setup>
/** 子流节点属性：选择子流、版本策略、inputs/outputs 映射 */
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
import { updateSummary } from '../../utils/nodeDataUtils'

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
const versionPolicy = computed(() => props.node?.data?.versionPolicy || 'pinned')
const inputRows = computed(() => props.node?.data?.inputs || [])
const outputRows = computed(() => props.node?.data?.outputs || [])
const hasPinnedSnapshot = computed(() => {
  const raw = props.node?.data?.pinnedGraphJson
  return raw != null && String(raw).trim() !== ''
})

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

function refreshSummary(patch) {
  const data = { ...props.node.data, ...patch }
  updateSummary('subflow', data)
  patchNodeData(props.node.id, { ...patch, summary: data.summary })
}

function suggestIoFromGraphJson(graphJsonRaw) {
  if (!graphJsonRaw) return { inputs: [], outputs: [] }
  try {
    const raw = typeof graphJsonRaw === 'string' ? JSON.parse(graphJsonRaw) : graphJsonRaw
    const graph = unwrapGraphPayload(raw)
    const meta = graph.meta
    if (!meta?.scenarios?.length) return { inputs: [], outputs: [] }
    const activeId = meta.activeScenarioId ?? meta.scenarios[0]?.id
    const scenario = meta.scenarios.find((s) => s.id === activeId) ?? meta.scenarios[0]
    const inputs = Object.keys(scenario?.flowSeed ?? {}).map((key) => ({
      name: key,
      value: `{{flow.${key}}}`,
    }))
    const outputs = (meta.flowOutputs ?? [])
      .filter((o) => o?.name?.trim())
      .map((o) => ({ name: String(o.name).trim(), flowKey: String(o.name).trim() }))
    return { inputs, outputs }
  } catch {
    return { inputs: [], outputs: [] }
  }
}

function applyIoSuggestions(graphJsonRaw, basePatch = {}) {
  const hasInputs = Array.isArray(props.node?.data?.inputs) && props.node.data.inputs.length > 0
  const hasOutputs = Array.isArray(props.node?.data?.outputs) && props.node.data.outputs.length > 0
  if (hasInputs && hasOutputs) {
    refreshSummary(basePatch)
    return
  }
  const suggested = suggestIoFromGraphJson(graphJsonRaw)
  const patch = { ...basePatch }
  if (!hasInputs && suggested.inputs.length) patch.inputs = suggested.inputs
  if (!hasOutputs && suggested.outputs.length) patch.outputs = suggested.outputs
  refreshSummary(patch)
}

function onSubflowChange(e) {
  const id = e.target.value
  const sf = projectSubflows.value.find((s) => String(s.testFlowId) === id)
  const patch = {
    subflowId: id,
    subflowName: sf?.flowName || '',
  }
  if (versionPolicy.value === 'pinned' && id) {
    snapshotPinnedGraph(id, patch)
    return
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

async function snapshotPinnedGraph(subflowId, basePatch = {}) {
  if (!subflowId) return
  try {
    const res = await getTestFlow(subflowId)
    const graphJson = res.data?.graphJson ?? res.data?.graph_json ?? ''
    applyIoSuggestions(graphJson, {
      ...basePatch,
      pinnedGraphJson: graphJson || undefined,
    })
  } catch {
    refreshSummary(basePatch)
    ElMessage.warning('无法读取子流图，pinned 快照未更新')
  }
}

function onPolicyChange(e) {
  const policy = e.target.value
  if (policy === 'pinned' && subflowIdVal.value) {
    snapshotPinnedGraph(subflowIdVal.value, { versionPolicy: policy })
    return
  }
  patchNodeData(props.node.id, { versionPolicy: policy })
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

function addOutput() {
  const rows = [...outputRows.value, { name: '', flowKey: '' }]
  refreshSummary({ outputs: rows })
}

function updateOutput(idx, key, val) {
  const rows = outputRows.value.map((r, i) => (i === idx ? { ...r, [key]: val } : r))
  refreshSummary({ outputs: rows })
}

function removeOutput(idx) {
  refreshSummary({ outputs: outputRows.value.filter((_, i) => i !== idx) })
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
    const outputs = (tpl.outputs || []).map((o) => ({
      name: o.name,
      flowKey: o.name,
    }))
    refreshSummary({
      subflowId: String(created?.testFlowId || ''),
      subflowName: created?.flowName || tpl.name,
      templateId: tpl.templateId,
      inputs,
      outputs,
      versionPolicy: 'pinned',
    })
    if (created?.testFlowId) {
      await snapshotPinnedGraph(String(created.testFlowId))
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
