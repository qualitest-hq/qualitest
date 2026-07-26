import { computed, ref, watch } from 'vue'
import {
  collectEntryBlocks,
  createEmptyEntryRow,
  entriesToSheetRows,
  findParentSheetRow,
  indexOfSheetRow,
  insertChildRowAfter,
  parseVariableEntries,
  pruneChildrenAfterTypeChange,
  removeSheetRowAt,
  sheetRowsToEntries,
  syncSheetRowKeyFlags
} from '@/views/project/testProject/utils/variableEntryUtils'

const KEY_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_]*$/

/**
 * 变量条目 DebugKvSheet 共用逻辑（素材库、环境变量）
 */
export function useVariableEntrySheet() {
  const sheetRows = ref([])

  const entryBlocks = computed(() => collectEntryBlocks(sheetRows.value))

  function syncEntryKeysInBlock(start) {
    const rows = sheetRows.value
    const entryRow = rows[start]
    if (!entryRow) return
    const key = (entryRow.key || '').trim()
    entryRow._entryKey = key
    let j = start + 1
    while (j < rows.length && (rows[j]._depth ?? 0) > 0) {
      rows[j]._entryKey = key
      rows[j]._entryId = entryRow._entryId ?? null
      j++
    }
  }

  function syncAllEntryKeys() {
    for (const { start } of entryBlocks.value) {
      syncEntryKeysInBlock(start)
    }
  }

  watch(
    () =>
      sheetRows.value
        .map((r) => `${r._rowKind}:${r._depth}:${r.key}:${r.remark}`)
        .join('|'),
    () => syncAllEntryKeys()
  )

  function loadFromEntriesJson(raw) {
    const entries = parseVariableEntries(raw)
    sheetRows.value = entries.length ? entriesToSheetRows(entries) : []
  }

  function loadFromEntryList(entries) {
    sheetRows.value = entries?.length ? entriesToSheetRows(entries) : []
  }

  function addEntryRow() {
    sheetRows.value.push(createEmptyEntryRow())
  }

  function findEntryBlockStart(index) {
    const rows = sheetRows.value
    let i = index
    while (i > 0 && (rows[i]._depth ?? 0) > 0) i--
    return i
  }

  function onRemoveRowAt(realIndex) {
    if (realIndex < 0) return
    removeSheetRowAt(sheetRows.value, realIndex)
  }

  function onAddChildRowAt(realIndex) {
    if (realIndex < 0) return
    insertChildRowAfter(sheetRows.value, realIndex)
  }

  function onRowTypeChange(row) {
    const idx = indexOfSheetRow(sheetRows.value, row)
    if (idx < 0) return
    pruneChildrenAfterTypeChange(sheetRows.value, idx)
    syncEntryKeysInBlock(findEntryBlockStart(idx))
  }

  function isBlankEntryBlock(block) {
    const entryRow = block[0]
    if ((entryRow?.key || '').trim()) return false
    for (let k = 1; k < block.length; k++) {
      const r = block[k]
      if ((r?.key || '').trim() || (r?.value || '').trim()) return false
    }
    return true
  }

  function getSavableBlocks() {
    const rows = sheetRows.value
    return entryBlocks.value
      .map(({ start, end }) => rows.slice(start, end))
      .filter((block) => !isBlankEntryBlock(block))
  }

  function validateChildRows(block) {
    for (let k = 1; k < block.length; k++) {
      const r = block[k]
      if ((r._depth ?? 0) <= 0) continue
      const parent = findParentSheetRow(block, k)
      const parentType = String(parent?.type || 'object').toLowerCase()
      const fieldKey = (r.key || '').trim()
      if (parentType === 'object' && !fieldKey && !r._hideKey) {
        return { ok: false, message: '对象子行 key 不能为空' }
      }
    }
    return { ok: true }
  }

  /**
   * @param {{ keyLabel?: string }} [options]
   */
  function validateBeforeSave(options = {}) {
    const keyLabel = options.keyLabel || '变量'
    const blocks = getSavableBlocks()
    if (!blocks.length) {
      return { ok: true, blocks: [] }
    }
    const keys = new Set()
    for (const block of blocks) {
      const key = (block[0]?.key || '').trim()
      if (!key) {
        return { ok: false, message: `请填写${keyLabel} key` }
      }
      if (!KEY_PATTERN.test(key)) {
        return { ok: false, message: `${keyLabel} key 无效：${key}` }
      }
      if (keys.has(key)) {
        return { ok: false, message: `${keyLabel} key 重复：${key}` }
      }
      keys.add(key)
      const childCheck = validateChildRows(block)
      if (!childCheck.ok) return childCheck
    }
    return { ok: true, blocks }
  }

  function buildEntriesForSave() {
    syncAllEntryKeys()
    syncSheetRowKeyFlags(sheetRows.value)
    const blocks = getSavableBlocks()
    const savableKeySet = new Set(blocks.map((b) => (b[0]?.key || '').trim()))
    return sheetRowsToEntries(sheetRows.value).filter((e) =>
      savableKeySet.has((e.key || '').trim())
    )
  }

  function resolveSheetIndexFromDisplay(displayRows, visibleIndex) {
    const row = displayRows[visibleIndex]
    if (!row) return -1
    return indexOfSheetRow(sheetRows.value, row)
  }

  function resetSheet() {
    sheetRows.value = []
  }

  return {
    sheetRows,
    entryBlocks,
    loadFromEntriesJson,
    loadFromEntryList,
    addEntryRow,
    onRemoveRowAt,
    onAddChildRowAt,
    onRowTypeChange,
    getSavableBlocks,
    validateBeforeSave,
    buildEntriesForSave,
    resolveSheetIndexFromDisplay,
    resetSheet,
    syncAllEntryKeys,
    syncSheetRowKeyFlags: () => syncSheetRowKeyFlags(sheetRows.value)
  }
}
