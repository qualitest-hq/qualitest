/**
 * 项目流检查目录与严重性配置接口。
 * ERROR：开跑硬拦；WARNING：仅提示；OFF：关闭该项。
 */
import request from '@/utils/request'

/** 可配置严重性 */
export type InspectionSeverity = 'ERROR' | 'WARNING' | 'OFF'

/** 目录接口返回的单条检查项 */
export interface FlowInspectionCatalogItem {
  /** 生效诊断码 */
  code?: string
  /** 包内短码 */
  shortCode?: string
  /** 展示名 */
  name?: string
  /** 分组名 */
  category?: string
  /** 所属插件 id；核心内置为空 */
  pluginId?: string | null
  /** 求值方式字符串 */
  evaluator?: string | null
  /** Invoke 时的能力名 */
  capability?: string | null
  /** 锁定后不可改级或关闭 */
  locked?: boolean
  /** 目录默认严重性 */
  defaultSeverity?: InspectionSeverity
  /** 本项目生效严重性 */
  effectiveSeverity?: InspectionSeverity
}

/** 拉取平台内置与已安装插件的检查目录（含本项目生效严重性） */
export function listFlowInspections(testProjectId: string | number) {
  return request({
    url: `/project/testProject/${testProjectId}/flowInspections`,
    method: 'get',
  })
}

/**
 * 保存本项目的严重性覆盖表。
 * 只应提交相对目录默认有变化的码；空对象表示恢复全部默认。
 */
export function saveFlowInspectionProfile(
  testProjectId: string | number,
  overrides: Record<string, InspectionSeverity>,
) {
  return request({
    url: `/project/testProject/${testProjectId}/flowInspections/profile`,
    method: 'put',
    data: { testProjectId, overrides },
  })
}
