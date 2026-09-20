import {ref, computed, watch, nextTick} from 'vue'
import {
  buildExampleFromSchemaDefaults,
  mergeJsonFillEmpty,
  mergeJsonPreferFiller
} from '@/views/project/testProject/utils/jsonSchemaTree'
import {
  coerceUrlencodedRowTypes,
  ensureTrailingEmptyRow
} from '@/views/project/testProject/utils/apiDetailRequestWorkbench'

/**
 * Body JSON / binary 同步与组装。依赖 useApiDebugDraft 返回的 draft 对象。
 *
 * @param {ReturnType<typeof import('./useApiDebugDraft').useApiDebugDraft>} draft
 */
export function useApiDebugBodyJson(draft) {
  const {
    draftRequestConfig,
    bodyJsonText,
    binaryBodyFile,
    binaryFileInputRef,
    activeBodyJsonSubTab,
    syncBodyJsonTextFromDraftRef
  } = draft

  const bodyJsonSchemaTreeRef = ref(null)

  const binaryFileLabel = computed(() => {
    const f = binaryBodyFile.value
    if (f instanceof File) return f.name
    return '未选择文件'
  })

  function triggerBinaryFilePick() {
    binaryFileInputRef.value?.click()
  }

  function onBinaryFileInputChange(e) {
    const input = e.target
    const f = input?.files?.[0]
    if (!f) return
    binaryBodyFile.value = f
    input.value = ''
  }

  function clearBinaryBodyFile() {
    binaryBodyFile.value = null
  }

  function syncBodyJsonTextFromDraft() {
    const ex = draftRequestConfig.value.body?.json?.example
    if (ex == null || ex === '') {
      bodyJsonText.value = ''
    } else if (typeof ex === 'string') {
      bodyJsonText.value = ex
    } else {
      try {
        bodyJsonText.value = JSON.stringify(ex, null, 2)
      } catch {
        bodyJsonText.value = String(ex)
      }
    }
  }

  syncBodyJsonTextFromDraftRef.fn = syncBodyJsonTextFromDraft
  // draft 的 apiDetail immediate watch 可能早于本 composable 执行，补一次同步
  syncBodyJsonTextFromDraft()

  /** 避免 schema→example 回写与用户编辑原始 JSON 互相打断 */
  let syncingBodyJsonFromSchema = false

  function parseBodyJsonTextOrEmpty() {
    const t = bodyJsonText.value.trim()
    if (!t) return {}
    try {
      const parsed = JSON.parse(t)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
    } catch {
      return null
    }
  }

  function ensureBodyJsonContainer() {
    const body = draftRequestConfig.value.body
    if (!body) return null
    if (!body.json || typeof body.json !== 'object') {
      body.json = {schema: null, example: null}
    }
    return body.json
  }

  /** 回写「请求示例」文本与 body.json.example（带同步锁，避免与 watch 互打断） */
  function writeBodyJsonExample(value) {
    const json = ensureBodyJsonContainer()
    if (!json) return
    syncingBodyJsonFromSchema = true
    try {
      bodyJsonText.value = JSON.stringify(value, null, 2)
      json.example = value
    } finally {
      syncingBodyJsonFromSchema = false
    }
  }

  /**
   * 将「数据结构」里的 default（参数值）合并进「请求示例」。
   * @param {'preferSchema'|'fillEmpty'} mode preferSchema=编辑后回写；fillEmpty=补空串
   * @param {object} [fillerOverride] 若传入则优先用（发送时直接取自树，避免 v-model 未写回）
   */
  function mergeSchemaDefaultsIntoBodyJson(mode, fillerOverride) {
    if (draftRequestConfig.value.body?.mode !== 'json') return
    if (!ensureBodyJsonContainer()) return
    const filler =
        fillerOverride !== undefined
            ? fillerOverride
            : buildExampleFromSchemaDefaults(draftRequestConfig.value.body?.json?.schema, {
              onlyExplicitDefaults: true
            })
    if (filler === undefined) return

    const current = parseBodyJsonTextOrEmpty()
    if (current === null) {
      // 原始 JSON 非法：仅在数据结构页时用 schema 覆盖，避免打字中途被冲掉
      if (activeBodyJsonSubTab.value === 'raw' && mode !== 'fillEmpty') return
      writeBodyJsonExample(filler)
      return
    }

    const merged =
        mode === 'preferSchema'
            ? mergeJsonPreferFiller(current, filler)
            : mergeJsonFillEmpty(current, filler)
    try {
      if (JSON.stringify(merged) === JSON.stringify(current) && bodyJsonText.value.trim()) {
        return
      }
    } catch {
      /* ignore */
    }
    writeBodyJsonExample(merged)
  }

  /** 从当前数据结构树读取显式 default（树未挂载则 undefined） */
  function readJsonBodyFillerFromTree() {
    try {
      return bodyJsonSchemaTreeRef.value?.buildDebugExampleFromTree?.()
    } catch {
      return undefined
    }
  }

  /** flush 树后从 draft schema 取显式 default（不含读树） */
  function readJsonBodyFillerFromDraftSchema() {
    try {
      bodyJsonSchemaTreeRef.value?.emitSchema?.()
    } catch {
      /* ignore */
    }
    return buildExampleFromSchemaDefaults(draftRequestConfig.value.body?.json?.schema, {
      onlyExplicitDefaults: true
    })
  }

  /**
   * 组装 JSON 发送体：
   * - 数据结构树已挂载：以树参数值为准（preferFiller），避免示例里旧非空值盖住刚改的参数值
   * - 仅请求示例页（树未挂载）：示例文本为主，schema default 只补空串
   * - 文本非法时若能从数据结构得到值则仍可发送（并回写示例）
   */
  function buildJsonBodyDataForSend() {
    const fromTree = readJsonBodyFillerFromTree()
    const preferTree = fromTree !== undefined
    const filler = preferTree ? fromTree : readJsonBodyFillerFromDraftSchema()

    const current = parseBodyJsonTextOrEmpty()
    const textInvalid = current === null
    if (textInvalid && filler === undefined) {
      return {error: '请求体 JSON 格式无效'}
    }

    const base = textInvalid ? {} : current
    const data =
        filler !== undefined
            ? preferTree
                ? mergeJsonPreferFiller(base, filler)
                : mergeJsonFillEmpty(base, filler)
            : base

    try {
      writeBodyJsonExample(data)
    } catch {
      /* 回写失败不挡发送 */
    }

    return {data}
  }

  watch(
      () => draftRequestConfig.value.body?.mode,
      (mode, prevMode) => {
        if (prevMode === 'binary' && mode !== 'binary') {
          binaryBodyFile.value = null
        }
        syncBodyJsonTextFromDraft()
        if (mode === 'x-www-form-urlencoded') {
          coerceUrlencodedRowTypes(draftRequestConfig.value.body?.urlencoded || [])
        }
        nextTick(() => {
          const b = draftRequestConfig.value.body
          if (!b) return
          if (mode === 'form-data') ensureTrailingEmptyRow(b.formData)
          if (mode === 'x-www-form-urlencoded') ensureTrailingEmptyRow(b.urlencoded)
        })
      }
  )

  watch(
      () => draftRequestConfig.value.body?.json?.schema,
      () => {
        if (syncingBodyJsonFromSchema) return
        if (draftRequestConfig.value.body?.mode !== 'json') return
        mergeSchemaDefaultsIntoBodyJson('preferSchema')
      },
      {deep: true}
  )

  return {
    bodyJsonSchemaTreeRef,
    binaryFileLabel,
    triggerBinaryFilePick,
    onBinaryFileInputChange,
    clearBinaryBodyFile,
    syncBodyJsonTextFromDraft,
    writeBodyJsonExample,
    mergeSchemaDefaultsIntoBodyJson,
    buildJsonBodyDataForSend,
    parseBodyJsonTextOrEmpty,
    ensureBodyJsonContainer,
    readJsonBodyFillerFromTree,
    readJsonBodyFillerFromDraftSchema
  }
}
