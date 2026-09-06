<template>
  <div class="tpl-prefab-section prefab-env-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制环境</span>
    </div>
    <div class="tpl-prefab-section__body">
      <p class="tpl-prefab-section__hint">
        勾选时填入项目第一条环境：占位 URL（<code>http://127.0.0.1</code>）才写入，已改过的地址不覆盖。
        变量同 key 跳过。不新增环境行，也不改「允许还原」。
      </p>
      <div class="prefab-env-panel__fields">
        <div class="prefab-env-panel__field">
          <span class="prefab-env-panel__label">环境名称</span>
          <el-input
            v-if="!readOnly"
            v-model="envName"
            maxlength="64"
            placeholder="如 默认环境"
          />
          <span v-else class="prefab-env-panel__text">{{ envName || '—' }}</span>
        </div>
        <div class="prefab-env-panel__field">
          <span class="prefab-env-panel__label">前置 URL</span>
          <el-input
            v-if="!readOnly"
            v-model="envUrl"
            placeholder="如 http://localhost:8801"
          />
          <span v-else class="prefab-env-panel__text prefab-env-panel__text--url">{{ envUrl || '—' }}</span>
        </div>
      </div>
      <div class="prefab-env-panel__vars">
        <div class="prefab-env-panel__vars-head">
          <span class="prefab-env-panel__vars-title">环境变量</span>
          <el-button v-if="!readOnly" link type="primary" @click="addEnvRow">＋ 添加变量</el-button>
        </div>
        <VariableEntrySheetSection
          :rows="envSheetRows"
          :read-only="readOnly"
          :enable-file-upload="false"
          :persist-file-upload="false"
          value-placeholder="value"
          name-placeholder="如 timeout"
          wrap-class="prefab-env-sheet"
          type-popper-class="prefab-param-type-select-popper"
          :show-empty="!envSheetRows.length"
          :empty-text="readOnly ? '暂无环境变量' : '暂无环境变量，点击「添加变量」'"
          @add-child="onEnvAddChild"
          @remove="onEnvRemove"
          @type-change="onEnvTypeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 预制环境面板：编辑 templateEnvs[0]（名称、baseUrl、环境变量扁平行表）。
 * 其余环境行原样保留。Apply 只修项目第一条环境。
 */
import { nextTick, ref, watch } from 'vue'
import VariableEntrySheetSection from '@/views/project/testProject/components/VariableEntrySheetSection.vue'
import { useVariableEntrySheet } from '@/views/project/testProject/composables/useVariableEntrySheet'
import { normalizeEnvVariableEntries } from '../utils/templateParamUtils'

const props = defineProps({
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})

const list = defineModel({ type: Array, default: () => [] })

const envName = ref('')
const envUrl = ref('')

const {
  sheetRows: envSheetRows,
  loadFromEntryList: loadEnvEntries,
  addEntryRow: addEnvEntryRow,
  onAddChildRowAt: envAddChildAt,
  onRemoveRowAt: envRemoveAt,
  onRowTypeChange: envTypeChange,
  buildEntriesForSave: buildEnvEntries,
} = useVariableEntrySheet()

let syncingFromList = false
let syncingFromSheet = false

function restEnvs() {
  const rows = Array.isArray(list.value) ? list.value : []
  return rows.slice(1).map((row) => (row && typeof row === 'object' ? { ...row } : {}))
}

function loadFromList() {
  syncingFromList = true
  const rows = Array.isArray(list.value) ? list.value : []
  const first = rows[0] && typeof rows[0] === 'object' ? rows[0] : null
  envName.value = String(first?.envName || '')
  envUrl.value = String(first?.envUrl || '')
  loadEnvEntries(normalizeEnvVariableEntries(first?.envVariables))
  nextTick(() => {
    syncingFromList = false
  })
}

function commitToList() {
  if (props.readOnly || syncingFromList) return
  const name = String(envName.value || '').trim()
  const url = String(envUrl.value || '').trim()
  const entries = buildEnvEntries()
  const rest = restEnvs()
  const next = !name && !url && !entries.length && !rest.length
    ? []
    : [{ envName: name, envUrl: url, envVariables: entries }, ...rest]
  if (JSON.stringify(next) === JSON.stringify(list.value || [])) return
  syncingFromSheet = true
  list.value = next
  nextTick(() => {
    syncingFromSheet = false
  })
}

watch(
  () => list.value,
  () => {
    if (syncingFromSheet) return
    loadFromList()
  },
  { immediate: true, deep: true },
)

watch([envName, envUrl], () => {
  if (syncingFromList || props.readOnly) return
  commitToList()
})

watch(
  envSheetRows,
  () => {
    if (syncingFromList || props.readOnly) return
    commitToList()
  },
  { deep: true },
)

function addEnvRow() {
  addEnvEntryRow()
  commitToList()
}

function onEnvAddChild(idx) {
  envAddChildAt(idx)
  commitToList()
}

function onEnvRemove(idx) {
  envRemoveAt(idx)
  commitToList()
}

function onEnvTypeChange(row) {
  envTypeChange(row)
  commitToList()
}
</script>

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';

.prefab-env-panel__fields {
  display: grid;
  grid-template-columns: minmax(140px, 0.9fr) minmax(220px, 1.4fr);
  gap: 10px 16px;
  margin-bottom: 14px;
}

.prefab-env-panel__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.prefab-env-panel__text {
  min-height: 22px;
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.prefab-env-panel__text--url {
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 12px;
}

.prefab-env-panel__label,
.prefab-env-panel__vars-title {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-regular);
}

.prefab-env-panel__vars-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 24px;
  margin-bottom: 8px;
}

.prefab-env-sheet {
  margin-top: 0;

  .variable-entry-sheet-empty {
    padding: 12px 16px;
  }
}

@media (max-width: 640px) {
  .prefab-env-panel__fields {
    grid-template-columns: 1fr;
  }
}
</style>
