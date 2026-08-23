/**
 * 项目模板 / 预制接口测试夹具 builder。
 * 边界：仅构造内存对象，不发 HTTP。
 */

/** 最小登录口 PrefabricatedApi */
export function buildLoginApi(overrides: Record<string, unknown> = {}) {
  return {
    apiName: '登录',
    apiPath: '/login',
    apiGroup: '系统.登录',
    requestConfig: { method: 'POST', configVersion: 1 },
    authConfig: { mode: 'none' },
    ...overrides,
  }
}

/** 库行 → templateToForm 输入（apis 为 JSON 字符串） */
export function buildTemplateRow(overrides: Record<string, unknown> = {}) {
  const apis = overrides.apis ?? [buildLoginApi()]
  const apisValue = typeof apis === 'string' ? apis : JSON.stringify(apis)
  return {
    testProjectTemplateId: 1,
    templateName: '测试模板',
    headerName: 'Authorization',
    headerValueTemplate: 'Bearer {{flow.token}}',
    matchConfig: '{"pathPrefix":["/api/"]}',
    enableStatus: 1,
    sortNum: 10,
    remark: '',
    builtinStatus: 0,
    ...overrides,
    apis: apisValue,
  }
}

/** 管理端 Bearer 风格 Profile（供 projectAuthConfig 复用） */
export function buildAdminBearerProfile(overrides: Record<string, unknown> = {}) {
  return {
    id: 'adminBearer',
    name: '管理端 Bearer',
    headerName: 'Authorization',
    headerValueTemplate: 'Bearer {{flow.adminToken}}',
    match: { pathPrefix: ['/system/'] },
    credentialApi: { method: 'POST', path: '/login' },
    loginHint: { flowKey: 'adminToken', from: 'body', expr: '$.token' },
    apis: [buildLoginApi()],
    ...overrides,
  }
}
