<template>
  <div class="tpl-prefab-section prefab-param-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制参数</span>
    </div>
    <div class="tpl-prefab-section__body">
      <el-tabs v-model="activeTab" class="tpl-prefab-section__tabs">
        <el-tab-pane label="流程变量" name="flow">
          <p class="tpl-prefab-section__hint">
            勾选模板时写入种子流默认场景的 flowSeed；适合调试预置 token，勿塞口令。
          </p>
          <FlowKeyValueEditor
            v-if="!readOnly"
            :add-label="FLOW_SEED_ADD_LABEL"
            :columns="FLOW_SEED_COLUMNS"
            :min-rows="0"
            :rows="flowRows"
            @update:rows="onFlowRowsChange"
          />
          <el-table
            v-else-if="flowRows.some((r) => r.key)"
            :data="flowRows.filter((r) => r.key)"
            border
            class="tpl-prefab-section__table"
            size="small"
          >
            <el-table-column label="变量名" min-width="120" prop="key" />
            <el-table-column label="初值" min-width="160" prop="value" show-overflow-tooltip />
          </el-table>
          <el-empty
            v-else
            :class="readOnly ? 'tpl-prefab-section__empty--compact' : 'tpl-prefab-section__empty'"
            :image-size="40"
            description="暂无流程变量"
          />
        </el-tab-pane>

        <el-tab-pane label="环境变量" name="env">
          <p class="tpl-prefab-section__hint">
            勾选模板时合并进项目默认环境的 envVariables（同名不覆盖）；baseUrl 可写入 envUrl。
          </p>
          <FlowKeyValueEditor
            v-if="!readOnly"
            add-label="＋ 添加变量"
            :columns="ENV_COLUMNS"
            :min-rows="0"
            :rows="envRows"
            @update:rows="onEnvRowsChange"
          />
          <el-table
            v-else-if="envRows.some((r) => r.key)"
            :data="envRows.filter((r) => r.key)"
            border
            class="tpl-prefab-section__table"
            size="small"
          >
            <el-table-column label="变量名" min-width="120" prop="key" />
            <el-table-column label="值" min-width="160" prop="value" show-overflow-tooltip />
          </el-table>
          <el-empty
            v-else
            :class="readOnly ? 'tpl-prefab-section__empty--compact' : 'tpl-prefab-section__empty'"
            :image-size="40"
            description="暂无环境变量"
          />
        </el-tab-pane>

        <el-tab-pane label="素材变量" name="asset">
          <p class="tpl-prefab-section__hint">
            勾选模板时合并进项目素材库 asset_variables（同 key 不覆盖）。对象可用 JSON，如
            <code>{{ assetJsonExample }}</code>，引用写法 <code>{{ assetPlaceholderHint }}</code>。
          </p>
          <FlowKeyValueEditor
            v-if="!readOnly"
            add-label="＋ 添加素材"
            :columns="ASSET_COLUMNS"
            :min-rows="0"
            :rows="assetRows"
            @update:rows="onAssetRowsChange"
          />
          <el-table
            v-else-if="assetRows.some((r) => r.key)"
            :data="assetRows.filter((r) => r.key)"
            border
            class="tpl-prefab-section__table"
            size="small"
          >
            <el-table-column label="key" min-width="120" prop="key" />
            <el-table-column label="值" min-width="200" prop="value" show-overflow-tooltip />
          </el-table>
          <el-empty
            v-else
            :class="readOnly ? 'tpl-prefab-section__empty--compact' : 'tpl-prefab-section__empty'"
            :image-size="40"
            description="暂无素材变量"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
/**
 * 预制参数面板：编辑 templateParams（flow / env / asset）。
 * 控件对齐测试流场景 flowSeed、环境变量、项目素材库。
 */
import { computed, ref } from 'vue'
import FlowKeyValueEditor from '@/views/project/testFlow/components/FlowKeyValueEditor.vue'
import {
  FLOW_SEED_ADD_LABEL,
  FLOW_SEED_COLUMNS,
} from '@/views/project/testFlow/constants/runConfigEditors'
import { parseParams } from '../utils/templateForm'

const ENV_COLUMNS = [
  { field: 'key', label: '变量名', placeholder: '如 timeout' },
  { field: 'value', label: '值', placeholder: '如 5000' },
]

const ASSET_COLUMNS = [
  { field: 'key', label: 'key', placeholder: '如 clientAuth' },
  { field: 'value', label: '值 / JSON', placeholder: '标量或 {"mobile":"..."}' },
]

/** 提示文案放脚本里，避免模板把 {{…}} / JSON 花括号当表达式。 */
const assetJsonExample = '{"mobile":"13800000001","password":"Test@123456"}'
const assetPlaceholderHint = '{{asset.key.field}}'

defineProps({
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})

const list = defineModel({ type: Array, default: () => [] })

const activeTab = ref('flow')

const parsed = computed(() => parseParams(list.value))

function toKvRows(kind) {
  const rows = parsed.value
    .filter((p) => p.kind === kind)
    .map((p) => ({
      key: String(p.name || ''),
      value: formatValue(p.value),
    }))
  return rows.length ? rows : [{ key: '', value: '' }]
}

function formatValue(value) {
  if (value == null) return ''
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

const flowRows = computed(() => toKvRows('flow'))
const envRows = computed(() => toKvRows('env'))
const assetRows = computed(() => toKvRows('asset'))

function parseStoredValue(raw) {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  if ((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'))) {
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }
  return text
}

function rebuildList({ flow, env, asset }) {
  const next = []
  for (const row of flow || []) {
    const name = String(row.key || '').trim()
    if (!name) continue
    next.push({ kind: 'flow', name, value: row.value ?? '', remark: '' })
  }
  for (const row of env || []) {
    const name = String(row.key || '').trim()
    if (!name) continue
    next.push({ kind: 'env', name, value: row.value ?? '', remark: '' })
  }
  for (const row of asset || []) {
    const name = String(row.key || '').trim()
    if (!name) continue
    next.push({ kind: 'asset', name, value: parseStoredValue(row.value), remark: '' })
  }
  list.value = next
}

function onFlowRowsChange(rows) {
  rebuildList({ flow: rows, env: envRows.value, asset: assetRows.value })
}

function onEnvRowsChange(rows) {
  rebuildList({ flow: flowRows.value, env: rows, asset: assetRows.value })
}

function onAssetRowsChange(rows) {
  rebuildList({ flow: flowRows.value, env: envRows.value, asset: rows })
}
</script>

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';
</style>
