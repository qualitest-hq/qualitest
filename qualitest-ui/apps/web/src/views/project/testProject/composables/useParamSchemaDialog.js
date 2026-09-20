import {ref, computed, nextTick} from 'vue'
import {SCHEMA_JSON_BODY_TYPES} from '@/views/project/testProject/utils/jsonSchemaTree'
import {
  bodyJsonSchemaNodeToParamRow,
  paramRowMergeIntoBodyJsonNode,
  paramRowMergeIntoFlatParamRow
} from '@/views/project/testProject/utils/bodyJsonSchemaParamBridge'
import {
  pruneConstraintsForType,
  supportsFormat
} from '@/views/project/testProject/utils/fieldTypeConstraints'
import {emptyKVRow} from '@/views/project/testProject/utils/apiDetailRequestWorkbench'
import {
  PARAM_TYPES_QUERY_PATH,
  PARAM_TYPES_URLENCODED,
  PARAM_TYPES_FORM_DATA,
  DISALLOWED_URLENC_TYPES,
  DISALLOWED_QUERY_PATH_TYPES
} from '@/views/project/testProject/composables/apiDebugParamConstants'

/**
 * 参数类型弹窗状态与打开/关闭、KV 行 required/type 变更。
 *
 * @param {object} proxy
 * @param {import('vue').Ref} bodyJsonSchemaTreeRef
 */
export function useParamSchemaDialog(proxy, bodyJsonSchemaTreeRef) {
  const paramSchemaDialogVisible = ref(false)
  const paramSchemaRow = ref(null)
  /** 类型弹窗：urlencoded 不包含 file，file 仅保留在 form-data */
  const paramSchemaSource = ref('default')
  /** KV 行弹窗关闭时写回目标（非 body JSON 树节点） */
  const paramSchemaKvTarget = ref(null)
  /** Body JSON 树标量节点 → 弹窗关闭后写回 */
  const bodyJsonSchemaEditTarget = ref(null)

  const paramSchemaTypeOptions = computed(() => {
    switch (paramSchemaSource.value) {
      case 'urlencoded':
        return PARAM_TYPES_URLENCODED
      case 'formData':
        return PARAM_TYPES_FORM_DATA
      case 'bodyJson':
        return SCHEMA_JSON_BODY_TYPES
      case 'query':
      case 'path':
        return PARAM_TYPES_QUERY_PATH
      default:
        return PARAM_TYPES_QUERY_PATH
    }
  })

  const paramSchemaStringConstraints = computed(() => {
    const r = paramSchemaRow.value
    if (!r) return false
    const t = String(r.type || '').toLowerCase()
    return t === 'string' || t === 'file' || t === 'any'
  })

  const paramSchemaNumberConstraints = computed(() => {
    const r = paramSchemaRow.value
    if (!r) return false
    const t = String(r.type || '').toLowerCase()
    return t === 'integer' || t === 'number'
  })

  const paramSchemaArrayConstraints = computed(() => {
    const r = paramSchemaRow.value
    if (!r) return false
    return String(r.type || '').toLowerCase() === 'array'
  })

  const paramSchemaFormatVisible = computed(() => {
    const r = paramSchemaRow.value
    if (!r) return false
    return supportsFormat(r.type)
  })

  /** body JSON schema 节点才展示数值高级项（flat KV 用 minValue/maxValue 即可） */
  const paramSchemaAdvancedNumberConstraints = computed(() => {
    return paramSchemaNumberConstraints.value && paramSchemaSource.value === 'bodyJson'
  })

  function ensureParamSchemaDefaults(row) {
    if (!row || typeof row !== 'object') return
    const d = emptyKVRow()
    for (const key of Object.keys(d)) {
      if (row[key] === undefined) row[key] = d[key]
    }
  }

  function toggleDebugParamRequired(row) {
    ensureParamSchemaDefaults(row)
    row.required = !row.required
  }

  function onKvParamTypeChange(row) {
    if (!row || typeof row !== 'object') return
    pruneConstraintsForType(row, row.type, {flatParam: true})
  }

  function openParamSchemaDialog(row, source = 'default') {
    ensureParamSchemaDefaults(row)
    paramSchemaSource.value = source
    const t = String(row.type || '').toLowerCase()
    if (source === 'urlencoded' && DISALLOWED_URLENC_TYPES.includes(t)) {
      row.type = 'string'
    } else if ((source === 'query' || source === 'path') && DISALLOWED_QUERY_PATH_TYPES.includes(t)) {
      row.type = 'string'
    }
    paramSchemaKvTarget.value = source === 'bodyJson' ? null : row
    paramSchemaRow.value = source === 'bodyJson' ? row : JSON.parse(JSON.stringify(row))
    paramSchemaDialogVisible.value = true
  }

  function onBodyJsonOpenSchema(node) {
    if (!node || typeof node !== 'object') return
    const t = String(node.type || '').toLowerCase()
    if (t === 'object') {
      proxy?.$modal?.msgInfo?.(
          'object 请在树中编辑子节点与类型列；标量 / array 字段可使用齿轮打开高级设置。'
      )
      return
    }
    bodyJsonSchemaEditTarget.value = node
    paramSchemaSource.value = 'bodyJson'
    paramSchemaKvTarget.value = null
    paramSchemaRow.value = bodyJsonSchemaNodeToParamRow(node)
    paramSchemaDialogVisible.value = true
  }

  function onParamSchemaDialogClosed() {
    if (bodyJsonSchemaEditTarget.value && paramSchemaRow.value) {
      paramRowMergeIntoBodyJsonNode(bodyJsonSchemaEditTarget.value, paramSchemaRow.value)
      bodyJsonSchemaEditTarget.value = null
      nextTick(() => bodyJsonSchemaTreeRef.value?.emitSchema?.())
    } else if (paramSchemaKvTarget.value && paramSchemaRow.value) {
      paramRowMergeIntoFlatParamRow(paramSchemaKvTarget.value, paramSchemaRow.value)
      paramSchemaKvTarget.value = null
    }
    paramSchemaRow.value = null
    paramSchemaSource.value = 'default'
  }

  return {
    paramSchemaDialogVisible,
    paramSchemaRow,
    paramSchemaSource,
    paramSchemaKvTarget,
    bodyJsonSchemaEditTarget,
    paramSchemaTypeOptions,
    paramSchemaStringConstraints,
    paramSchemaNumberConstraints,
    paramSchemaArrayConstraints,
    paramSchemaFormatVisible,
    paramSchemaAdvancedNumberConstraints,
    ensureParamSchemaDefaults,
    toggleDebugParamRequired,
    onKvParamTypeChange,
    openParamSchemaDialog,
    onBodyJsonOpenSchema,
    onParamSchemaDialogClosed
  }
}
