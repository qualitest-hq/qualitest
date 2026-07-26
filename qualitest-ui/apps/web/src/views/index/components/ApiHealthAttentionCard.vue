<template>
  <!-- 有告警流时才渲染；无数据整块不占版 -->
  <el-card
    v-if="items.length"
    shadow="never"
    class="api-health-attention"
  >
    <template #header>
      <div class="api-health-attention__header">
        <span class="api-health-attention__title">接口变更待关注</span>
        <span class="api-health-attention__total">共 {{ total }} 条</span>
      </div>
    </template>
    <el-table
      :data="items"
      stripe
      style="width: 100%"
      @row-click="handleRowClick"
    >
      <el-table-column label="测试流" prop="flowName" min-width="140" show-overflow-tooltip />
      <el-table-column label="项目" prop="projectName" min-width="110" show-overflow-tooltip />
      <el-table-column label="告警数" prop="warningCount" width="80" align="center">
        <template #default="{ row }">
          <el-tag type="warning" size="small">{{ row.warningCount ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="告警类型" prop="warningCodes" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ formatCodes(row.warningCodes) }}
        </template>
      </el-table-column>
      <el-table-column label="检查时间" prop="checkedAt" width="170" />
    </el-table>
  </el-card>
</template>

<script setup>
/**
 * 首页「接口变更待关注」卡片。
 * 展示仍有 API 语义告警的测试流；点击行跳转到该流画布。
 */
defineProps({
  /** 待关注流列表（截断后） */
  items: { type: Array, default: () => [] },
  /** 告警流总数（可大于 items.length） */
  total: { type: Number, default: 0 },
})

const router = useRouter()

/** 告警 code → 中文展示名 */
const CODE_LABEL = {
  API_MISSING: 'API缺失',
  ORPHAN_PARAM: '孤儿参数',
  EXTRACT_PATH_MISSING: '抽取路径',
}

/** 把逗号分隔的 code 转成中文文案，如「API缺失、孤儿参数」 */
function formatCodes(codes) {
  if (!codes) return '-'
  return String(codes)
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean)
    .map((c) => CODE_LABEL[c] || c)
    .join('、')
}

/** 打开对应测试流画布 */
function handleRowClick(row) {
  if (!row?.testProjectId || !row?.testFlowId) return
  router.push(`/project/testProject/flow/${row.testProjectId}/${row.testFlowId}`)
}
</script>

<style scoped lang="scss">
.api-health-attention {
  margin-bottom: 16px;
}

.api-health-attention__header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.api-health-attention__title {
  font-weight: 600;
}

.api-health-attention__total {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
