<template>
  <el-dialog
      v-model="visible"
      class="asset-manage-dialog asset-manage-dialog--wide"
      title="项目素材库"
      width="1040px"
      top="5vh"
      append-to-body
      :z-index="ASSET_DIALOG_Z_INDEX"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="onDialogClosed"
      @opened="onDialogOpened"
  >
    <div v-loading="listLoading" class="asset-manage-shell">
      <header class="asset-manage-toolbar">
        <div class="asset-manage-search-wrap">
          <el-input
              v-model="listFilter"
              class="asset-manage-search"
              clearable
              placeholder="搜索 key、备注、value（支持空格分词）"
              prefix-icon="Search"
              @keydown="onSearchKeydown"
          />
          <span
              v-if="searchTokens.length"
              class="asset-manage-search-meta"
              :class="{ 'is-empty': matchedAssetCount === 0 }"
          >
            匹配 {{ matchedAssetCount }} / {{ totalAssetCount }} 条素材
          </span>
        </div>
        <el-button class="asset-manage-add-btn" type="primary" @click="addEntryRow">
          <el-icon>
            <Plus/>
          </el-icon>
          新建素材
        </el-button>
      </header>

      <div class="asset-manage-scroll">
        <VariableEntrySheetSection
            v-if="displayRows.length"
            :rows="displayRows"
            name-placeholder="key"
            type-popper-class="asset-param-type-select-popper"
            @add-child="onAddChildRow"
            @remove="onRemoveRow"
            @type-change="onRowTypeChange"
        />

        <div v-if="!listLoading && !displayRows.length" class="asset-manage-empty">
          <template v-if="searchTokens.length">
            <p class="asset-manage-empty-title">无匹配结果</p>
            <p class="asset-manage-empty-desc">试试缩短关键词，或<el-button link type="primary" @click="clearSearch">清空搜索</el-button></p>
          </template>
          <template v-else>
            暂无素材，点击「新建素材」添加
          </template>
        </div>
      </div>

      <footer class="asset-manage-footer">
        <el-button @click="visible = false">取 消</el-button>
        <el-button :loading="saveLoading" @click="handleSaveAll(true)">
          保存并关闭
        </el-button>
        <el-button :loading="saveLoading" type="primary" @click="handleSaveAll(false)">
          保 存
        </el-button>
      </footer>
    </div>
  </el-dialog>
</template>

<script setup>
import {Plus} from '@element-plus/icons-vue'
import {
  addTestProjectAsset,
  listTestProjectAsset,
  updateTestProjectAsset
} from '@/api/project/testProjectAsset'
import VariableEntrySheetSection from './VariableEntrySheetSection.vue'
import {useVariableEntrySheet} from '@/views/project/testProject/composables/useVariableEntrySheet'
import {entriesToSheetRows} from '@/views/project/testProject/utils/variableEntryUtils'

const visible = defineModel('visible', {type: Boolean, default: false})

const props = defineProps({
  testProjectId: {
    type: [String, Number],
    required: true
  }
})

const emit = defineEmits(['saved'])

const ASSET_DIALOG_Z_INDEX = 10000

const {proxy} = getCurrentInstance()

const listLoading = ref(false)
const saveLoading = ref(false)
const listFilter = ref('')

const {
  sheetRows,
  entryBlocks,
  addEntryRow,
  onRemoveRowAt,
  onAddChildRowAt,
  onRowTypeChange,
  validateBeforeSave,
  buildEntriesForSave,
  resolveSheetIndexFromDisplay,
  resetSheet
} = useVariableEntrySheet()

const searchTokens = computed(() => parseSearchTokens(listFilter.value))

function parseSearchTokens(raw) {
  return (raw || '')
      .trim()
      .toLowerCase()
      .split(/\s+/)
      .filter(Boolean)
}

function entryBlockMatchesSearch(block, tokens) {
  const parts = []
  const entryRow = block[0]
  const remark = (entryRow?.remark || '').trim().toLowerCase()
  if (remark) parts.push(remark)
  for (const r of block) {
    const key = (r?.key || '').trim().toLowerCase()
    const value = (r?.value == null ? '' : String(r.value).trim().toLowerCase())
    if (key) parts.push(key)
    if (value) parts.push(value)
  }
  const text = parts.join('\n')
  return tokens.every((t) => text.includes(t))
}

const totalAssetCount = computed(() => entryBlocks.value.length)

const matchedAssetCount = computed(() => {
  const tokens = searchTokens.value
  if (!tokens.length) return totalAssetCount.value
  const rows = sheetRows.value
  return entryBlocks.value.filter(({start, end}) =>
      entryBlockMatchesSearch(rows.slice(start, end), tokens)
  ).length
})

const displayRows = computed(() => {
  const rows = sheetRows.value
  const tokens = searchTokens.value
  if (!tokens.length) return rows
  const out = []
  for (const {start, end} of entryBlocks.value) {
    const block = rows.slice(start, end)
    if (entryBlockMatchesSearch(block, tokens)) {
      out.push(...block)
    }
  }
  return out
})

function onSearchKeydown(e) {
  if (e.key === 'Escape') {
    listFilter.value = ''
  }
}

function clearSearch() {
  listFilter.value = ''
}

function onRemoveRow(visibleIndex) {
  onRemoveRowAt(resolveSheetIndexFromDisplay(displayRows.value, visibleIndex))
}

function onAddChildRow(visibleIndex) {
  onAddChildRowAt(resolveSheetIndexFromDisplay(displayRows.value, visibleIndex))
}

function fetchAndLoadDrafts() {
  listLoading.value = true
  return listTestProjectAsset({testProjectId: props.testProjectId})
      .then((res) => {
        const rows = res.rows || []
        sheetRows.value = entriesToSheetRows(rows)
        return rows
      })
      .catch(() => {
        resetSheet()
        return []
      })
      .finally(() => {
        listLoading.value = false
      })
}

function onDialogOpened() {
  listFilter.value = ''
  fetchAndLoadDrafts()
}

function onDialogClosed() {
  resetSheet()
  listFilter.value = ''
}

async function saveEntry(entry) {
  const body = {
    testProjectId: props.testProjectId,
    key: entry.key,
    remark: entry.remark || '',
    assets: entry.assets
  }
  const isNew = entry.id == null
  if (!isNew) body.id = entry.id
  const response = isNew
      ? await addTestProjectAsset(body)
      : await updateTestProjectAsset(body)
  if (response.code !== 200) {
    throw new Error(response.msg || '保存失败')
  }
  return response.data
}

async function handleSaveAll(closeAfter) {
  const blocks = validateBeforeSave({keyLabel: '素材'})
  if (!blocks.ok) {
    proxy.$modal.msgError(blocks.message)
    return
  }
  const savableBlocks = blocks.blocks || []
  if (!savableBlocks.length) {
    proxy.$modal.msgWarning('请先新建素材并填写素材 key')
    return
  }

  const entries = buildEntriesForSave()

  saveLoading.value = true
  try {
    for (const entry of entries) {
      await saveEntry(entry)
    }
    proxy.$modal.msgSuccess('保存成功')
    emit('saved', {})
    await fetchAndLoadDrafts()
    if (closeAfter) {
      visible.value = false
    }
  } catch (e) {
    proxy.$modal.msgError(e.message || '保存失败')
  } finally {
    saveLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.asset-manage-shell {
  --pd-bg-sunken: #e9f2fc;
  --pd-border-subtle: #c5d8ec;
  --pd-border-muted: #d6e6f5;
  --pd-divider: #dbe8f4;
  --pd-text: #0f172a;
  --pd-text-muted: #5a6b86;
  --pd-primary: #0b6edc;
  --pd-primary-soft: rgba(11, 110, 220, 0.12);
  --pd-radius: 10px;
  --pd-shadow-card: 0 1px 2px rgba(20, 60, 120, 0.05), 0 4px 14px rgba(30, 80, 160, 0.07);
  --pd-gradient-panel-head: linear-gradient(180deg, #fafcff 0%, #ffffff 100%);
  --pd-surface-elevated: #ffffff;

  display: flex;
  flex-direction: column;
  min-height: 460px;
  max-height: min(76vh, 720px);
  margin: -10px -16px -8px;
}

.asset-manage-toolbar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 4px 12px;
  border-bottom: 1px solid var(--pd-border-muted, var(--el-border-color-lighter));
  background: var(--pd-surface-elevated, var(--el-bg-color));
  flex-shrink: 0;
}

.asset-manage-search-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.asset-manage-search {
  width: 100%;

  :deep(.el-input__wrapper) {
    border-radius: 6px;
    box-shadow: 0 0 0 1px var(--pd-border-subtle, #dcdfe6) inset;
    background: var(--pd-surface-elevated, #fff);
    transition: box-shadow 0.15s ease;

    &:hover {
      box-shadow: 0 0 0 1px color-mix(in srgb, var(--pd-primary, #0b6edc) 35%, #dcdfe6) inset;
    }

    &.is-focus {
      box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
    }
  }

  :deep(.el-input__inner) {
    font-size: 13px;
  }

  :deep(.el-input__prefix) {
    color: var(--pd-text-muted, var(--el-text-color-placeholder));
  }
}

.asset-manage-search-meta {
  font-size: 12px;
  color: var(--pd-text-muted, var(--el-text-color-secondary));
  padding-left: 2px;

  &.is-empty {
    color: var(--el-color-warning);
  }
}

.asset-manage-add-btn {
  flex-shrink: 0;
  margin-top: 1px;
}

.asset-manage-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 4px 12px;
}

.asset-manage-empty {
  padding: 48px 16px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-secondary);

  .asset-manage-empty-title {
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--pd-text, var(--el-text-color-primary));
  }

  .asset-manage-empty-desc {
    margin: 0;
    font-size: 13px;
    line-height: 1.6;
  }
}

.asset-manage-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 4px 4px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}
</style>

<style lang="scss">
.asset-manage-dialog--wide.el-dialog {
  max-width: min(88vw, 1040px);
}

.asset-manage-dialog.el-dialog .el-dialog__body {
  padding: 0 20px 20px;
  overflow: visible;
}

.asset-param-type-select-popper.el-popper,
.debug-kv-remark-popper.el-popper {
  z-index: 10010 !important;
}

.asset-manage-dialog ~ .el-message {
  z-index: 10100 !important;
}

.el-overlay.is-message-box {
  z-index: 10100 !important;
}
</style>
