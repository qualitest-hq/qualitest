<template>
  <div v-loading="loading" class="app-container dashboard">
    <WelcomeBanner />

    <ApiHealthAttentionCard
      :items="attention.list"
      :total="attention.total"
    />

    <StatCards
      :project-count="summary.projectCount"
      :flow-count="summary.flowCount"
      :api-count="summary.apiCount"
      :run-count="summary.runCount"
      :recent-pass-rate="summary.recentPassRate"
    />

    <el-row :gutter="16" class="dashboard__middle">
      <el-col :xs="24" :lg="16">
        <RunTrendChart :run-trend="summary.runTrend" />
      </el-col>
      <el-col :xs="24" :lg="8">
        <QuickLinks />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dashboard__bottom">
      <el-col :xs="24" :lg="12">
        <RecentProjects :projects="summary.recentProjects" />
      </el-col>
      <el-col :xs="24" :lg="12">
        <RecentRuns :runs="summary.recentRuns" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Index">
/**
 * 工作台首页：欢迎区、接口变更待关注、统计卡、运行趋势、快捷入口、最近项目/运行。
 */
import { ElMessage } from 'element-plus'
import { getApiHealthAttention, getDashboardSummary } from '@/api/dashboard'
import WelcomeBanner from './components/WelcomeBanner.vue'
import ApiHealthAttentionCard from './components/ApiHealthAttentionCard.vue'
import StatCards from './components/StatCards.vue'
import QuickLinks from './components/QuickLinks.vue'
import RunTrendChart from './components/RunTrendChart.vue'
import RecentProjects from './components/RecentProjects.vue'
import RecentRuns from './components/RecentRuns.vue'

const loading = ref(false)
/** 首页汇总数据 */
const summary = ref({
  projectCount: 0,
  flowCount: 0,
  apiCount: 0,
  runCount: 0,
  recentPassRate: 0,
  recentProjects: [],
  recentRuns: [],
  runTrend: [],
})
/** 接口变更待关注：总数 + 列表 */
const attention = ref({
  total: 0,
  list: [],
})

/** 并行拉取汇总与待关注列表 */
function loadDashboard() {
  loading.value = true
  Promise.all([getDashboardSummary(), getApiHealthAttention()])
    .then(([summaryRes, attentionRes]) => {
      const data = summaryRes.data || {}
      summary.value = {
        projectCount: data.projectCount ?? 0,
        flowCount: data.flowCount ?? 0,
        apiCount: data.apiCount ?? 0,
        runCount: data.runCount ?? 0,
        recentPassRate: data.recentPassRate ?? 0,
        recentProjects: data.recentProjects || [],
        recentRuns: data.recentRuns || [],
        runTrend: data.runTrend || [],
      }
      const att = attentionRes.data || {}
      attention.value = {
        total: att.total ?? 0,
        list: att.list || [],
      }
    })
    .catch(() => {
      ElMessage.error('加载首页数据失败')
    })
    .finally(() => {
      loading.value = false
    })
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped lang="scss">
.dashboard {
  min-height: calc(100vh - 120px);
}

.dashboard__middle,
.dashboard__bottom {
  margin-bottom: 0;
}

.dashboard__middle .el-col,
.dashboard__bottom .el-col {
  margin-bottom: 16px;
}
</style>
