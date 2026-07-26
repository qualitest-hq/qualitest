<template>
  <el-card shadow="never" class="recent-projects">
    <template #header>
      <div class="recent-projects__header">
        <span class="recent-projects__title">最近项目</span>
        <el-button link type="primary" @click="goList">查看全部</el-button>
      </div>
    </template>
    <el-table
      v-if="projects.length"
      :data="projects"
      stripe
      style="width: 100%"
      @row-click="handleRowClick"
    >
      <el-table-column label="项目名" prop="projectName" min-width="140" show-overflow-tooltip />
      <el-table-column label="所有者" prop="ownerName" width="100" show-overflow-tooltip />
      <el-table-column label="API数量" prop="apiCount" width="90" align="center" />
      <el-table-column label="最近 API 同步" prop="lastApiSyncTime" width="170" />
    </el-table>
    <el-empty v-else description="暂无项目" :image-size="80" />
  </el-card>
</template>

<script setup>
defineProps({
  projects: { type: Array, default: () => [] },
})

const router = useRouter()

function goList() {
  router.push('/testManages/testProject')
}

function handleRowClick(row) {
  if (!row?.testProjectId) return
  router.push(`/project/testProject/detail/${row.testProjectId}`)
}
</script>

<style scoped lang="scss">
.recent-projects__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recent-projects__title {
  font-weight: 600;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
