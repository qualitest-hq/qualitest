/**
 * 运行前就绪检查。
 * 开跑硬拦：POST /project/testFlow/validate?stage=run。
 * 画布鉴权/必填图 error：走 stage=canvas（与拓扑等同条 graph 刷新链）。
 */
import { validateTestFlowGraph, type FlowIssueRecord } from '@/api/project/testFlow'

/** 运行就绪检查结果 */
export type RunBlockingResult = {
  /** 全部阻断文案 */
  errors: string[]
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
    const errorIssues = issues.filter(
      (i) => String(i?.severity || 'ERROR').toUpperCase() === 'ERROR',
    )
    const errors = errorIssues.map((i) => String(i?.message || i?.code || '').trim()).filter(Boolean)
    return { errors }
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : '运行前校验失败'
    return { errors: [message] }
  }
}

/** 鉴权 / 登录抽取 / HTTP 必填类（优先看 FlowIssue.code，文案前缀兜底） */
export function isAuthReadinessIssue(
  issue: Pick<FlowIssueRecord, 'code' | 'message'> | { code?: string; message?: string } | string,
): boolean {
  if (typeof issue === 'string') {
    return isAuthRiskMessage(issue)
  }
  const code = String(issue?.code || '')
  if (code === 'RUN_READINESS_AUTH') return true
  return isAuthRiskMessage(String(issue?.message || ''))
}

/** 文案是否属鉴权/必填风险（按 CODE 前缀；无 structured code 时用） */
export function isAuthRiskMessage(message: string): boolean {
  const text = String(message || '')
  return (
    text.startsWith('AUTH_TOKEN_MISSING:')
    || text.startsWith('LOGIN_EXTRACT_MISSING:')
    || text.startsWith('HTTP_REQUIRED_PARAM_MISSING:')
    || text.startsWith('AUTH_LOGIN_FLOWKEY_COLLISION:')
  )
}
