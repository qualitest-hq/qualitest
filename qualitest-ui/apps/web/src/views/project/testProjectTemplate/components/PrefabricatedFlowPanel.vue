<template>
  <div class="prefab-flow-panel">
    <div class="prefab-flow-panel__toolbar">
      <span class="prefab-flow-panel__title">预制测试流</span>
      <div v-if="!readOnly" class="prefab-flow-panel__actions">
        <el-button icon="Plus" size="small" type="primary" @click="handleAdd">新增</el-button>
        <el-button :disabled="selectedIndex < 0" icon="Delete" size="small" @click="handleRemove">删除</el-button>
      </div>
    </div>
    <el-table
      v-if="rows.length"
      :data="rows"
      border
      highlight-current-row
      row-key="_index"
      size="small"
      @current-change="onCurrentChange"
    >
      <el-table-column label="流名称" min-width="140" prop="flowName" show-overflow-tooltip />
      <el-table-column label="方法" prop="method" width="72" />
      <el-table-column label="路径" min-width="140" prop="apiPath" show-overflow-tooltip />
      <el-table-column label="抽取" min-width="160" prop="extractLabel" show-overflow-tooltip />
      <el-table-column align="center" label="操作" width="72">
        <template #default="scope">
          <el-button link type="primary" @click.stop="openEdit(scope.row._index)">
            {{ readOnly ? '查看' : '编辑' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else :image-size="48" description="可选：登录等测试流，勾选模板时种子到项目" />

    <el-dialog
      v-model="dialogVisible"
      :close-on-click-modal="false"
      append-to-body
      class="prefab-flow-detail-dialog"
      destroy-on-close
      :title="readOnly ? '查看预制测试流' : '编辑预制测试流'"
      top="4vh"
      width="72vw"
    >
      <el-form v-if="draft" label-width="108px" size="small">
        <el-form-item label="流名称">
          <el-input
            v-model="draft.flowName"
            :disabled="readOnly"
            placeholder="如 管理端 Bearer 登录"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="draft.description" :disabled="readOnly" placeholder="可选" />
        </el-form-item>
        <el-form-item label="绑定接口">
          <el-select
            v-model="boundApiKey"
            :disabled="readOnly"
            clearable
            filterable
            placeholder="从预制接口选择"
            style="width: 100%"
            @change="onBindApiChange"
          >
            <el-option
              v-for="opt in apiOptions"
              :key="opt.key"
              :label="opt.label"
              :value="opt.key"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="方法">
              <el-select v-model="http.method" :disabled="readOnly" style="width: 100%">
                <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="路径">
              <el-input v-model="http.apiPath" :disabled="readOnly" placeholder="/login" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="超时(ms)">
              <el-input-number
                v-model="http.timeoutMs"
                :disabled="readOnly"
                :min="0"
                :step="1000"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务 Code">
              <el-select v-model="http.successCheckMode" :disabled="readOnly" style="width: 100%">
                <el-option label="继承项目响应约定" value="inherit" />
                <el-option label="关闭（仅校验 HTTP 状态码）" value="off" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="节点名称">
          <el-input v-model="http.nodeName" :disabled="readOnly" placeholder="登录" />
        </el-form-item>
        <el-form-item label="响应提取" label-position="top">
          <div class="prefab-flow-panel__extracts" :class="{ 'is-readonly': readOnly }">
            <DebugExtractEditor v-model="http.extracts" :auto-seed-row="true" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="!readOnly" type="primary" @click="saveDraft">确定</el-button>
        <el-button @click="dialogVisible = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 预制测试流面板：编辑 templateFlows。
 * 单 HTTP 登录节点；字段与 extracts 对齐真实测试流；保留 apiPath 供种子绑定。
 */
import { computed, reactive, ref, watch } from 'vue'
import DebugExtractEditor from '@/views/project/testProject/components/DebugExtractEditor.vue'
import { emptyExtractTarget } from '@/utils/flow/extract'
import {
  buildLoginGraphJson,
  emptyPrefabFlow,
  HTTP_METHODS,
  parseFlows,
  parseJsonMaybe,
  resolveApiMethod,
  summarizePrefabFlow,
} from '../utils/templateForm'

const props = defineProps({
  /** 同模板预制接口，供绑定下拉。 */
  templateApis: { type: Array, default: () => [] },
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})

const list = defineModel({ type: Array, default: () => [] })

const selectedIndex = ref(-1)
const dialogVisible = ref(false)
const draft = ref(null)
const editIndex = ref(-1)
const boundApiKey = ref('')

const http = reactive({
  method: 'POST',
  apiPath: '',
  nodeName: '登录',
  timeoutMs: 30000,
  successCheckMode: 'inherit',
  extracts: [emptyExtractTarget()],
})

const flows = computed(() => parseFlows(list.value))

const rows = computed(() =>
  flows.value.map((flow, index) => ({
    _index: index,
    ...summarizePrefabFlow(flow),
  })),
)

const apiOptions = computed(() =>
  (props.templateApis || [])
    .filter((api) => api && typeof api === 'object')
    .map((api) => {
      const method = resolveApiMethod(api)
      const path = String(api.apiPath || '').trim()
      const name = String(api.apiName || '').trim()
      return {
        key: `${method} ${path}`,
        method,
        path,
        name,
        label: `${method} ${path}${name ? ` · ${name}` : ''}`,
      }
    })
    .filter((o) => o.path),
)

watch(
  () => list.value,
  () => {
    if (selectedIndex.value >= flows.value.length) selectedIndex.value = -1
  },
)

function commit(next) {
  list.value = next
}

function handleAdd() {
  const next = [...flows.value, emptyPrefabFlow()]
  commit(next)
  openEdit(next.length - 1)
}

function handleRemove() {
  if (selectedIndex.value < 0) return
  const next = flows.value.filter((_, i) => i !== selectedIndex.value)
  selectedIndex.value = -1
  commit(next)
}

function onCurrentChange(row) {
  selectedIndex.value = row?._index ?? -1
}

function openEdit(index) {
  editIndex.value = index
  draft.value = JSON.parse(JSON.stringify(flows.value[index] || emptyPrefabFlow()))
  const graph = parseJsonMaybe(draft.value.graphJson) || draft.value.graphJson || {}
  const node = Array.isArray(graph.nodes) ? graph.nodes.find((n) => n?.type === 'http') : null
  const data = node?.data || {}
  http.method = String(data.httpMethod || 'POST').toUpperCase()
  http.apiPath = String(data.apiPath || '')
  http.nodeName = String(data.name || '登录')
  http.timeoutMs = Number(data.timeoutMs) > 0 ? Number(data.timeoutMs) : 30000
  http.successCheckMode = data.successCheck?.mode === 'off' ? 'off' : 'inherit'
  const extracts = Array.isArray(data.extracts) && data.extracts.length
    ? data.extracts.map((e) => ({
        from: e.from || 'body',
        expr: e.expr || '',
        scope: e.scope || 'flow',
        name: e.name || '',
        entryKey: e.entryKey || '',
        fieldPath: e.fieldPath || '',
      }))
    : [emptyExtractTarget()]
  http.extracts = extracts
  boundApiKey.value = http.apiPath ? `${http.method} ${http.apiPath}` : ''
  dialogVisible.value = true
}

function onBindApiChange(key) {
  const opt = apiOptions.value.find((o) => o.key === key)
  if (!opt) return
  http.method = opt.method
  http.apiPath = opt.path
  if (opt.name) http.nodeName = opt.name
}

function saveDraft() {
  if (!draft.value || props.readOnly) return
  const filledExtracts = (http.extracts || []).filter(
    (e) => String(e.expr || '').trim() && (String(e.name || '').trim() || String(e.entryKey || '').trim()),
  )
  draft.value.graphJson = buildLoginGraphJson({
    method: http.method,
    apiPath: http.apiPath,
    extracts: filledExtracts.length ? filledExtracts : http.extracts,
    timeoutMs: http.timeoutMs,
    successCheckMode: http.successCheckMode,
    nodeName: http.nodeName,
  })
  const next = [...flows.value]
  next[editIndex.value] = JSON.parse(JSON.stringify(draft.value))
  commit(next)
  dialogVisible.value = false
}
</script>

<style scoped>
.prefab-flow-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.prefab-flow-panel__title {
  font-weight: 600;
}
.prefab-flow-panel__extracts {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
}
.prefab-flow-panel__extracts.is-readonly {
  pointer-events: none;
  opacity: 0.85;
}
</style>
