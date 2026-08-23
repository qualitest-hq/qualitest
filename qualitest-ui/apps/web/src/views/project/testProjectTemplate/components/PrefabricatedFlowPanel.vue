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
      <el-table-column v-if="!readOnly" align="center" label="操作" width="72">
        <template #default="scope">
          <el-button link type="primary" @click.stop="openEdit(scope.row._index)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else :image-size="48" description="可选：登录等测试流，勾选模板时种子到项目" />

    <el-dialog
      v-model="dialogVisible"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
      title="编辑预制测试流"
      width="560px"
    >
      <el-form v-if="draft" label-width="96px" size="small">
        <el-form-item label="流名称">
          <el-input v-model="draft.flowName" placeholder="如 管理端 Bearer 登录" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="draft.description" placeholder="可选" />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="http.method" style="width: 120px">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="http.apiPath" placeholder="/login" />
        </el-form-item>
        <el-form-item label="extract.from">
          <el-select v-model="http.from" style="width: 140px">
            <el-option label="body" value="body" />
            <el-option label="setCookie" value="setCookie" />
            <el-option label="header" value="header" />
          </el-select>
        </el-form-item>
        <el-form-item label="extract.expr">
          <el-input v-model="http.expr" placeholder="$.token" />
        </el-form-item>
        <el-form-item label="flowKey">
          <el-input v-model="http.flowKey" placeholder="token / adminToken" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDraft">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 预制测试流面板：编辑模板的 templateFlows。
 * 简化编辑单 HTTP 登录节点（方法 / 路径 / 抽取）；勾选模板时会种子到项目并可用于生成托管头。
 */
import { computed, reactive, ref, watch } from 'vue'
import {
  buildLoginGraphJson,
  emptyPrefabFlow,
  HTTP_METHODS,
  parseFlows,
  parseJsonMaybe,
  summarizePrefabFlow,
} from '../utils/templateForm'

const props = defineProps({
  /** 预制测试流数组（v-model）。 */
  modelValue: { type: Array, default: () => [] },
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const selectedIndex = ref(-1)
const dialogVisible = ref(false)
const draft = ref(null)
const editIndex = ref(-1)
/** 弹窗里编辑的登录 HTTP 节点字段（写回 graphJson）。 */
const http = reactive({
  method: 'POST',
  apiPath: '',
  from: 'body',
  expr: '$.token',
  flowKey: 'token',
})

const list = computed(() => parseFlows(props.modelValue))

const rows = computed(() =>
  list.value.map((flow, index) => ({
    _index: index,
    ...summarizePrefabFlow(flow),
  })),
)

watch(
  () => props.modelValue,
  () => {
    if (selectedIndex.value >= list.value.length) selectedIndex.value = -1
  },
)

function commit(next) {
  emit('update:modelValue', next)
}

/** 新增一条登录流骨架并打开编辑。 */
function handleAdd() {
  const next = [...list.value, emptyPrefabFlow()]
  commit(next)
  openEdit(next.length - 1)
}

function handleRemove() {
  if (selectedIndex.value < 0) return
  const next = list.value.filter((_, i) => i !== selectedIndex.value)
  selectedIndex.value = -1
  commit(next)
}

function onCurrentChange(row) {
  selectedIndex.value = row?._index ?? -1
}

/** 打开编辑：从 graphJson 首个 HTTP 节点回填表单。 */
function openEdit(index) {
  editIndex.value = index
  draft.value = JSON.parse(JSON.stringify(list.value[index] || emptyPrefabFlow()))
  const graph = parseJsonMaybe(draft.value.graphJson) || draft.value.graphJson || {}
  const node = Array.isArray(graph.nodes) ? graph.nodes.find((n) => n?.type === 'http') : null
  const data = node?.data || {}
  const extract = Array.isArray(data.extracts) && data.extracts[0] ? data.extracts[0] : {}
  http.method = String(data.httpMethod || 'POST').toUpperCase()
  http.apiPath = String(data.apiPath || '')
  http.from = String(extract.from || 'body')
  http.expr = String(extract.expr || '$.token')
  http.flowKey = String(extract.name || 'token')
  dialogVisible.value = true
}

/** 把表单字段写回单节点 graphJson 并保存。 */
function saveDraft() {
  if (!draft.value) return
  const flowKey = String(http.flowKey || 'token').trim() || 'token'
  draft.value.graphJson = buildLoginGraphJson({
    method: http.method,
    apiPath: http.apiPath,
    from: http.from,
    expr: http.expr,
    flowKey,
  })
  const next = [...list.value]
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
</style>
