import {updateTestProjectApi} from '@/api/project/testProjectApi'
import {sanitizeBodyJsonSchemaForPersist} from '@/views/project/testProject/utils/jsonSchemaTree'
import {sanitizeParamRowForPersist} from '@/views/project/testProject/utils/fieldTypeConstraints'
import {REQUEST_CONFIG_VERSION} from '@/views/project/testProject/utils/apiConfigConstants'
import {peelTestValuesFromStructure} from '@/views/project/testProject/utils/peelTestValueConfig'

/** 保存前剥离 KV 行上的 UI 专用字段（_enabled、exampleValue 等），不入库 */
export function stripRowsInternal(rows) {
  if (!Array.isArray(rows)) return rows
  return rows.map((r) => {
    if (!r || typeof r !== 'object') return r
    const {_enabled, _file, exampleValue, examples, ...rest} = r
    return rest
  })
}

export function sanitizeParamRowsForSave(rows) {
  if (!Array.isArray(rows)) return rows
  return rows.map((r) => {
    if (!r || typeof r !== 'object') return r
    return sanitizeParamRowForPersist(r)
  })
}

/** 深拷贝 requestConfig 并写入 configVersion=1，规范化各参数数组与 json schema */
export function cloneRequestConfigForSave(rc) {
  const o = JSON.parse(JSON.stringify(rc))
  o.configVersion = REQUEST_CONFIG_VERSION
  o.queryParams = sanitizeParamRowsForSave(stripRowsInternal(o.queryParams))
  o.pathParams = sanitizeParamRowsForSave(stripRowsInternal(o.pathParams))
  o.declaredHeaders = sanitizeParamRowsForSave(stripRowsInternal(o.declaredHeaders || []))
  if (o.body) {
    o.body.formData = sanitizeParamRowsForSave(stripRowsInternal(o.body.formData))
    o.body.urlencoded = sanitizeParamRowsForSave(stripRowsInternal(o.body.urlencoded))
    if (Array.isArray(o.body.urlencoded)) {
      o.body.urlencoded = o.body.urlencoded.map((r) => {
        if (!r || typeof r !== 'object') return r
        if (String(r.type || '').toLowerCase() === 'file') {
          return {...r, type: 'string'}
        }
        return r
      })
    }
    if (o.body.mode === 'json' && o.body.json && typeof o.body.json === 'object') {
      o.body.json = {
        ...o.body.json,
        schema: sanitizeBodyJsonSchemaForPersist(o.body.json.schema)
      }
    }
  }
  return o
}

export function rowsToKeyValueObject(rows) {
  const out = {}
  for (const r of rows || []) {
    if (r._enabled === false) continue
    const k = (r.name || '').trim()
    if (!k) continue
    out[k] = r.value ?? ''
  }
  return out
}

/**
 * API 调试持久化：组装保存载荷并调用更新接口。
 *
 * @param {object} props
 * @param {(event: string, ...args: any[]) => void} emit
 * @param {ReturnType<typeof import('./useApiDebugDraft').useApiDebugDraft>} draft
 * @param {object} proxy
 */
export function useApiDebugPersist(props, emit, draft, proxy) {
  const {
    draftApiPath,
    draftRequestConfig,
    draftHeaderRows,
    draftCookieRows,
    bodyJsonText,
    draftPreRequestScript,
    draftPostRequestScript,
    apiDebugSaving,
    initDraftFromApiDetail
  } = draft

  function applyBodyJsonToDraftBeforeSave() {
    if (draftRequestConfig.value.body.mode !== 'json') return
    const t = bodyJsonText.value.trim()
    draftRequestConfig.value.body.json = draftRequestConfig.value.body.json || {schema: null, example: null}
    if (!t) {
      draftRequestConfig.value.body.json.example = {}
      return
    }
    try {
      draftRequestConfig.value.body.json.example = JSON.parse(t)
    } catch {
      throw new Error('invalid-json')
    }
  }

  /**
   * 收集当前调试草稿并拆测值：结构只留定义，测值进 testValueConfig。
   * @param responseConfigOverride 可选；设计页/预制口传入当前响应稿，否则用详情里的 responseConfig
   */
  function buildPersistPayload(responseConfigOverride) {
    try {
      applyBodyJsonToDraftBeforeSave()
    } catch {
      return {error: '请求体 JSON 格式无效'}
    }
    const rc = cloneRequestConfigForSave(draftRequestConfig.value)
    const peeled = peelTestValuesFromStructure(
        rc,
        responseConfigOverride !== undefined ? responseConfigOverride : props.apiDetail?.responseConfig,
        props.apiDetail?.testValueConfig,
    )
    return {
      apiPath: draftApiPath.value,
      requestConfig: JSON.stringify(peeled.requestConfig),
      responseConfig: JSON.stringify(peeled.responseConfig),
      testValueConfig: JSON.stringify(peeled.testValueConfig),
      headers: JSON.stringify(rowsToKeyValueObject(draftHeaderRows.value)),
      cookies: JSON.stringify(rowsToKeyValueObject(draftCookieRows.value)),
      preRequestScript: draftPreRequestScript.value ?? '',
      postRequestScript: draftPostRequestScript.value ?? ''
    }
  }

  function handleSaveApiDebug() {
    if (!props.apiDetail?.testProjectApiId) {
      proxy.$modal.msgError('缺少 API 信息')
      return
    }
    const part = buildPersistPayload()
    if (part.error) {
      proxy.$modal.msgError(part.error)
      return
    }
    apiDebugSaving.value = true
    const payload = {
      testProjectApiId: props.apiDetail.testProjectApiId,
      testProjectId: props.apiDetail.testProjectId,
      apiGroupId: props.apiDetail.apiGroupId,
      apiStatus: props.apiDetail.apiStatus,
      apiGroup: props.apiDetail.apiGroup,
      apiName: props.apiDetail.apiName,
      apiDescription: props.apiDetail.apiDescription,
      apiPath: part.apiPath,
      protocolType: props.apiDetail.protocolType,
      requestConfig: part.requestConfig,
      headers: part.headers,
      cookies: part.cookies,
      responseConfig: part.responseConfig ?? props.apiDetail.responseConfig,
      testValueConfig: part.testValueConfig,
      preRequestScript:
          part.preRequestScript !== undefined ? part.preRequestScript : props.apiDetail.preRequestScript,
      postRequestScript:
          part.postRequestScript !== undefined ? part.postRequestScript : props.apiDetail.postRequestScript
    }
    updateTestProjectApi(payload)
        .then((res) => {
          if (res.code === 200) {
            proxy.$modal.msgSuccess('保存成功')
            const merged = {...props.apiDetail, ...payload}
            initDraftFromApiDetail(merged)
            emit('saved', merged)
          } else {
            proxy.$modal.msgError(res.msg || '保存失败')
          }
        })
        .catch(() => {})
        .finally(() => {
          apiDebugSaving.value = false
        })
  }

  return {
    stripRowsInternal,
    sanitizeParamRowsForSave,
    cloneRequestConfigForSave,
    rowsToKeyValueObject,
    applyBodyJsonToDraftBeforeSave,
    buildPersistPayload,
    handleSaveApiDebug
  }
}
