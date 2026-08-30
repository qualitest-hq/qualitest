/**
 * 项目模板 / 预制接口测试夹具。
 * 只构造内存对象，不发 HTTP。
 */

/** 最小登录口预制接口（免登）。 */
export function buildLoginApi(overrides: Record<string, unknown> = {}) {
  return {
    apiName: '登录',
    apiPath: '/login',
    apiGroup: '管理端.系统.登录',
    requestConfig: { method: 'POST', configVersion: 1 },
    authConfig: { mode: 'none' },
    ...overrides,
  }
}

/** 已是字符串则原样，否则 JSON.stringify（模拟库列形态）。 */
function asJsonField(value: unknown, fallback: unknown): string {
  const raw = value ?? fallback
  return typeof raw === 'string' ? raw : JSON.stringify(raw)
}

/**
 * 构造模板详情行（给 templateToForm 用）。
 * templateApis / templateParams / templateFlows / templatePrompts 写成 JSON 字符串。
 */
export function buildTemplateRow(overrides: Record<string, unknown> = {}) {
  return {
    testProjectTemplateId: 1,
    templateName: '测试模板',
    matchConfig: '{"pathPrefix":["/api/"]}',
    enableStatus: 1,
    sortNum: 10,
    remark: '',
    builtinStatus: 0,
    ...overrides,
    templateApis: asJsonField(overrides.templateApis, [buildLoginApi()]),
    templateParams: asJsonField(overrides.templateParams, []),
    templateFlows: asJsonField(overrides.templateFlows, []),
    templatePrompts: asJsonField(overrides.templatePrompts, []),
  }
}

/** 管理端 Bearer 风格的项目鉴权 Profile（含预制登录口）。 */
export function buildAdminBearerProfile(overrides: Record<string, unknown> = {}) {
  return {
    id: 'adminBearer',
    name: '管理端 Bearer',
    headerName: 'Authorization',
    headerValueTemplate: 'Bearer {{asset.adminAuth.token}}',
    match: { pathPrefix: ['/system/'] },
    credentialApi: { method: 'POST', path: '/login' },
    apis: [buildLoginApi()],
    ...overrides,
  }
}
