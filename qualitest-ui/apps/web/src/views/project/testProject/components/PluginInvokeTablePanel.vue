<template>
  <div v-loading="loading" class="plugin-invoke-table">
    <div class="plugin-invoke-table__head">
      <span class="plugin-invoke-table__title">{{ title }}</span>
      <el-button :disabled="loading" link type="primary" @click="loadRows">刷新</el-button>
    </div>
    <el-alert
        v-if="errorText"
        :closable="false"
        :title="errorText"
        class="plugin-invoke-table__alert"
        show-icon
        type="error"
    />
    <el-empty v-if="!errorText && !loading && !rows.length" description="暂无数据" :image-size="72" />
    <el-table v-if="rows.length" :data="rows" border size="small">
      <el-table-column
          v-for="col in columns"
          :key="col.prop"
          :label="col.label"
          :min-width="col.minWidth || 140"
          :prop="col.prop"
          show-overflow-tooltip
      />
      <el-table-column v-if="!readonly && deleteCapability" align="center" label="操作" width="88">
        <template #default="{ row }">
          <el-button
              :disabled="!row[idField]"
              :loading="deletingId === String(row[idField])"
              link
              type="danger"
              @click="removeRow(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
/**
 * 说明书 ui.env.actions.panel=invoke-table 的通用列表：invoke listCapability，可选 deleteCapability。
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { invokeEnvPlugin } from '@/api/project/pluginHost'

const props = defineProps({
  envId: { type: [String, Number], required: true },
  pluginId: { type: String, required: true },
  listCapability: { type: String, default: 'list' },
  deleteCapability: { type: String, default: '' },
  idField: { type: String, default: 'saveId' },
  listKey: { type: String, default: 'saves' },
  title: { type: String, default: '列表' },
  columns: {
    type: Array,
    default: () => [
      { prop: 'saveId', label: 'saveId', minWidth: 220 },
      { prop: 'createdAt', label: '创建时间', minWidth: 168 },
    ],
  },
  readonly: { type: Boolean, default: false },
})

const loading = ref(false)
const errorText = ref('')
const rows = ref([])
const deletingId = ref('')

function extractRows(res) {
  const data = res?.data
  if (Array.isArray(data?.[props.listKey])) return { rows: data[props.listKey], invalid: false }
  const stdout = typeof data?.stdout === 'string' ? data.stdout : ''
  if (!stdout.trim()) return { rows: [], invalid: false }
  try {
    const parsed = JSON.parse(stdout)
    return { rows: Array.isArray(parsed?.[props.listKey]) ? parsed[props.listKey] : [], invalid: false }
  } catch {
    return { rows: [], invalid: true }
  }
}

async function loadRows() {
  loading.value = true
  errorText.value = ''
  try {
    const res = await invokeEnvPlugin(props.envId, {
      pluginId: props.pluginId,
      capability: props.listCapability,
      args: {},
    })
    if (res?.code !== undefined && res.code !== 200) {
      errorText.value = res.msg || '加载失败'
      rows.value = []
      return
    }
    const extracted = extractRows(res)
    if (extracted.invalid) {
      errorText.value = '无法解析列表输出'
      rows.value = []
      return
    }
    rows.value = extracted.rows
  } catch (e) {
    errorText.value = e?.message || '加载失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function removeRow(row) {
  const id = row?.[props.idField]
  if (!id || !props.deleteCapability) return
  try {
    await ElMessageBox.confirm(`确认删除 ${id}？`, '删除', { type: 'warning' })
  } catch {
    return
  }
  deletingId.value = String(id)
  try {
    const res = await invokeEnvPlugin(props.envId, {
      pluginId: props.pluginId,
      capability: props.deleteCapability,
      args: { [props.idField]: id, saveId: id },
    })
    if (res?.code !== undefined && res.code !== 200) {
      ElMessage.error(res.msg || '删除失败')
      return
    }
    ElMessage.success('已删除')
    await loadRows()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    deletingId.value = ''
  }
}

watch(
  () => [props.envId, props.pluginId, props.listCapability],
  () => {
    if (props.envId && props.pluginId) loadRows()
  },
  { immediate: true },
)
</script>

<style scoped>
.plugin-invoke-table__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.plugin-invoke-table__title {
  font-weight: 600;
}
.plugin-invoke-table__alert {
  margin-bottom: 8px;
}
</style>
