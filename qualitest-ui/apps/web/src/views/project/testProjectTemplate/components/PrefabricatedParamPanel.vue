<template>
  <div class="tpl-prefab-section prefab-param-panel">
    <div class="tpl-prefab-section__head">
      <span class="tpl-prefab-section__title">预制参数</span>
    </div>
    <div class="tpl-prefab-section__body">
      <p class="tpl-prefab-section__hint">
        勾选模板时写入项目素材库；项目已有同 key 则跳过，不冲掉你改过的口令等。登录成功后的 token
        仍会写入同一 key（如 adminAuth），不会另开多条。环境变量请在下方「预制环境」编辑。
      </p>
      <div v-if="!readOnly" class="prefab-param-panel__toolbar">
        <el-button link type="primary" @click="addAssetRow">＋ 添加素材</el-button>
      </div>
      <VariableEntrySheetSection
        :rows="assetSheetRows"
        :read-only="readOnly"
        :enable-file-upload="false"
        :persist-file-upload="false"
        value-placeholder="value"
        name-placeholder="如 clientAuth"
        wrap-class="prefab-param-sheet"
        type-popper-class="prefab-param-type-select-popper"
        :show-empty="!assetSheetRows.length"
        :empty-text="readOnly ? '暂无素材变量' : '暂无素材，点击「添加素材」'"
        @add-child="onAssetAddChild"
        @remove="onAssetRemove"
        @type-change="onAssetTypeChange"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 预制参数面板：编辑 templateParams 素材（kind=asset）。
 * 存量 kind=flow 保存时保留；kind=env 由表单迁入 templateEnvs。
 */
import { nextTick, watch } from 'vue'
import VariableEntrySheetSection from '@/views/project/testProject/components/VariableEntrySheetSection.vue'
import { useVariableEntrySheet } from '@/views/project/testProject/composables/useVariableEntrySheet'
import {
  rebuildTemplateParamsFromVariableEntries,
  templateParamsToVariableEntries,
} from '../utils/templateParamUtils'

const props = defineProps({
  /** 内置模板查看时只读。 */
  readOnly: { type: Boolean, default: false },
})

const list = defineModel({ type: Array, default: () => [] })

const {
  sheetRows: assetSheetRows,
  loadFromEntryList: loadAssetEntries,
  addEntryRow: addAssetEntryRow,
  onAddChildRowAt: assetAddChildAt,
  onRemoveRowAt: assetRemoveAt,
  onRowTypeChange: assetTypeChange,
  buildEntriesForSave: buildAssetEntries,
} = useVariableEntrySheet()

/** 避免 list ↔ sheet 双向同步形成环。 */
let syncingFromList = false
let syncingFromSheet = false

function loadSheetsFromList() {
  syncingFromList = true
  loadAssetEntries(templateParamsToVariableEntries(list.value, 'asset'))
  nextTick(() => {
    syncingFromList = false
  })
}

function commitSheetsToList() {
  if (props.readOnly || syncingFromList) return
  const next = rebuildTemplateParamsFromVariableEntries(list.value, {
    assetEntries: buildAssetEntries(),
  })
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
    loadSheetsFromList()
  },
  { immediate: true, deep: true },
)

watch(
  assetSheetRows,
  () => {
    if (syncingFromList || props.readOnly) return
    commitSheetsToList()
  },
  { deep: true },
)

function addAssetRow() {
  addAssetEntryRow()
  commitSheetsToList()
}

function onAssetAddChild(idx) {
  assetAddChildAt(idx)
  commitSheetsToList()
}

function onAssetRemove(idx) {
  assetRemoveAt(idx)
  commitSheetsToList()
}

function onAssetTypeChange(row) {
  assetTypeChange(row)
  commitSheetsToList()
}
</script>

<style lang="scss">
@use '../styles/templatePrefabPanel.scss';

.prefab-param-panel__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.prefab-param-sheet {
  margin-top: 0;
}
</style>
