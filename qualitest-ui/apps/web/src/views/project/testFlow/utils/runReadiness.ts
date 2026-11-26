/**
 * 运行前就绪检查。
 * 开跑硬拦：POST /project/testFlow/validate?stage=run。
 * 风险条预览：仍走 savePrecheck（只取鉴权/必填，不跑整份开跑门禁）。
 */
import { validateTestFlowGraph, type FlowIssueRecord } from '@/api/project/testFlow'
import { savePrecheckFlowDesign } from '@/api/project/testFlowAi'

/** 运行就绪检查结果 */
export type RunBlockingResult = {
  /** 全部阻断文案 */
  errors: string[]
  /** 鉴权 / 登录抽取 / HTTP 必填子集，供校验条「运行风险」段 */
  precheckErrors: string[]
}

/**
 * 收集阻止开跑的错误（服务端权威，与 FlowRunReadinessGate 同口径）。
 */
export async function collectRunBlockingErrors(opts: {
  graph: unknown
  testProjectId?: string | null
  testFlowId?: string | null
}): Promise<RunBlockingResult> {
  const graphJson = typeof opts.graph === 'string' ? opts.graph : JSON.stringify(opts.graph ?? {})
  const projectId = opts.testProjectId != null ? String(opts.testProjectId).trim() : ''
  const flowId = opts.testFlowId != null ? String(opts.testFlowId).trim() : ''
  try {
    const res = await validateTestFlowGraph('run', {
      graphJson,
      testProjectId: projectId || undefined,
      testFlowId: flowId || undefined,
    })
    const issues = Array.isArray(res?.data?.issues) ? res.data!.issues! : []
    const errorIssues = issues.filter((i) => String(i?.severity || '').toUpperCase() !== 'WARNING')
    const errors = errorIssues.map((i) => String(i?.message || i?.code || '').trim()).filter(Boolean)
    const precheckErrors = errorIssues
      .filter((i) => isAuthReadinessIssue(i))
      .map((i) => String(i?.message || '').trim())
      .filter(Boolean)
    return { errors, precheckErrors }
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '运行前校验失败'
    return { errors: [message], precheckErrors: [message] }
  }
}

/**
 * 仅取鉴权 / 登录抽取 / HTTP 必填风险文案（画布风险条预览用，不跑整份开跑门禁）。
 */
export async function fetchSavePrecheckErrors(
  testProjectId: string,
  graph: unknown,
): Promise<string[]> {
  const projectId = String(testProjectId || '').trim()
  if (!projectId) return []
  try {
    const precheck = await savePrecheckFlowDesign({
      testProjectId: projectId,
      graphJson: typeof graph === 'string' ? graph : JSON.stringify(graph),
    })
    return Array.isArray(precheck?.errors) ? precheck.errors : []
  } catch (e: unknown) {
    return [e instanceof Error ? e.message : '运行前预检失败']
  }
}

function isAuthReadinessIssue(issue: FlowIssueRecord): boolean {
  const code = String(issue?.code || '')
  if (code === 'RUN_READINESS_AUTH') return true
  const message = String(issue?.message || '')
  return (
    message.startsWith('AUTH_TOKEN_MISSING:')
    || message.startsWith('LOGIN_EXTRACT_MISSING:')
    || message.startsWith('HTTP_REQUIRED_PARAM_MISSING:')
  )
}
