import request from '@/utils/request';

export interface DashboardRunTrendItem {
  date: string;
  passed: number;
  failed: number;
  cancelled: number;
  total: number;
}

export interface DashboardRecentRun {
  testFlowRunId?: string | number;
  testFlowId?: string | number;
  testProjectId?: string | number;
  flowName?: string;
  projectName?: string;
  status?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: string | number;
  triggerType?: string;
}

export interface DashboardRecentProject {
  testProjectId?: string | number;
  projectName?: string;
  ownerName?: string;
  lastApiSyncTime?: string;
  updateTime?: string;
}

export interface DashboardSummary {
  projectCount: number;
  flowCount: number;
  apiCount: number;
  runCount: number;
  recentPassRate: number;
  recentProjects: DashboardRecentProject[];
  recentRuns: DashboardRecentRun[];
  runTrend: DashboardRunTrendItem[];
}

/** 接口变更待关注列表中的一条测试流 */
export interface DashboardApiHealthAttentionItem {
  testProjectId?: string | number;
  projectName?: string;
  testFlowId?: string | number;
  flowName?: string;
  /** API 语义健康告警条数 */
  warningCount?: number;
  /** 告警类型摘要，如 API_MISSING,ORPHAN_PARAM */
  warningCodes?: string;
  /** 最近一次健康检查落库时间 */
  checkedAt?: string;
}

/** 接口变更待关注接口返回体 */
export interface DashboardApiHealthAttention {
  /** 告警流总数 */
  total: number;
  /** 截断后的列表（最多约 20 条） */
  list: DashboardApiHealthAttentionItem[];
}

/** 拉取首页汇总（计数、通过率、最近项目/运行、趋势） */
export function getDashboardSummary() {
  return request({
    url: '/dashboard/summary',
    method: 'get',
  });
}

/** 拉取仍有 API 语义告警的测试流列表 */
export function getApiHealthAttention() {
  return request({
    url: '/dashboard/apiHealthAttention',
    method: 'get',
  });
}
