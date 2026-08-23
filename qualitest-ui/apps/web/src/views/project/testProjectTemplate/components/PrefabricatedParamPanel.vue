<template>
  <div class="prefab-param-panel">
    <div class="prefab-param-panel__toolbar">
      <span class="prefab-param-panel__title">预制参数</span>
      <div v-if="!readOnly" class="prefab-param-panel__actions">
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
      <el-table-column label="类型" prop="kindLabel" width="88" />
      <el-table-column label="名称" min-width="100" prop="name" show-overflow-tooltip />
      <el-table-column label="绑定" min-width="140" prop="bindLabel" show-overflow-tooltip />
      <el-table-column label="说明" min-width="160" prop="detail" show-overflow-tooltip />
      <el-table-column v-if="!readOnly" align="center" label="操作" width="72">
        <template #default="scope">
          <el-button link type="primary" @click.stop="openEdit(scope.row._index)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else :image-size="48" description="可选：测值或凭证抽取参数" />

    <el-dialog
      v-model="dialogVisible"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
      title="编辑预制参数"
      width="520px"
    >
      <el-form v-if="draft" label-width="96px" size="small">
        <el-form-item label="类型">
          <el-radio-group v-model="draft.kind">
            <el-radio value="value">测值</el-radio>
            <el-radio value="extract">抽取</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="draft.name" :placeholder="draft.kind === 'extract' ? 'flow 变量名，如 token' : '参数名'" />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="draft.bind.method" style="width: 120px">
            <el-option v-for="m in HTTP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="draft.bind.path" placeholder="/login" />
        </el-form-item>
        <el-form-item v-if="draft.kind === 'value'" label="值">
          <el-input v-model="draft.value" placeholder="默认测值" />
        </el-form-item>
        <template v-else>
          <el-form-item label="from">
            <el-select v-model="draft.from" style="width: 140px">
              <el-option label="body" value="body" />
              <el-option label="setCookie" value="setCookie" />
              <el-option label="header" value="header" />
            </el-select>
          </el-form-item>
          <el-form-item label="expr">
            <el-input v-model="draft.expr" placeholder="$.token 或 Cookie 名" />
          </el-form-item>
          <el-form-item label="凭证">
            <el-switch v-model="draft.credential" />
            <span class="prefab-param-panel__hint">开启后勾选模板时会生成凭证规则与托管请求头</span>
          </el-form-item>
        </template>
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
 * 预制参数面板：编辑模板的 templateParams。
 * kind=value 叠测值；kind=extract 且「凭证」开启时，勾选模板可用来生成凭证规则与托管头。
 */
import { computed, ref, watch } from 'vue'
import { emptyPrefabParam, HTTP_METHODS, parseParams } from '../utils/templateForm'

const props = defineProps({
  /** 预制参数数组（v-model）。 */
  modelValue: { type: Array, default: () => [] },
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

/** 当前选中行下标，-1 表示未选。 */
const selectedIndex = ref(-1)
const dialogVisible = ref(false)
/** 编辑弹窗草稿。 */
const draft = ref(null)
/** 正在编辑的行下标。 */
const editIndex = ref(-1)

const list = computed(() => parseParams(props.modelValue))

/** 表格展示行。 */
const rows = computed(() =>
  list.value.map((row, index) => ({
    _index: index,
    kindLabel: row.kind === 'extract' ? '抽取' : '测值',
    name: row.name || '—',
    bindLabel: `${row.bind?.method || 'POST'} ${row.bind?.path || ''}`.trim(),
    detail:
      row.kind === 'extract'
        ? `${row.from || 'body'} ${row.expr || ''}${row.credential ? ' · 凭证' : ''}`
        : String(row.value ?? ''),
  })),
)

watch(
  () => props.modelValue,
  () => {
    if (selectedIndex.value >= list.value.length) selectedIndex.value = -1
  },
)

/** 回写 v-model。 */
function commit(next) {
  emit('update:modelValue', next)
}

/** 新增一行并打开编辑。 */
function handleAdd() {
  const next = [...list.value, emptyPrefabParam()]
  commit(next)
  openEdit(next.length - 1)
}

/** 删除当前选中行。 */
function handleRemove() {
  if (selectedIndex.value < 0) return
  const next = list.value.filter((_, i) => i !== selectedIndex.value)
  selectedIndex.value = -1
  commit(next)
}

function onCurrentChange(row) {
  selectedIndex.value = row?._index ?? -1
}

/** 打开编辑弹窗并拷贝草稿。 */
function openEdit(index) {
  editIndex.value = index
  draft.value = JSON.parse(JSON.stringify(list.value[index] || emptyPrefabParam()))
  if (!draft.value.bind) draft.value.bind = { method: 'POST', path: '' }
  dialogVisible.value = true
}

/** 保存草稿回列表。 */
function saveDraft() {
  if (!draft.value) return
  const next = [...list.value]
  next[editIndex.value] = JSON.parse(JSON.stringify(draft.value))
  commit(next)
  dialogVisible.value = false
}
</script>

<style scoped>
.prefab-param-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.prefab-param-panel__title {
  font-weight: 600;
}
.prefab-param-panel__hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
