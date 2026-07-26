<template>
  <el-row :gutter="16" class="stat-cards">
    <el-col v-for="item in cards" :key="item.key" :xs="12" :sm="12" :lg="6">
      <el-card
        class="stat-card"
        shadow="hover"
        :class="`stat-card--${item.key}`"
      >
        <div class="stat-card__body">
          <div class="stat-card__icon">
            <el-icon :size="28"><component :is="item.icon" /></el-icon>
          </div>
          <div class="stat-card__info">
            <div class="stat-card__value">{{ item.value }}</div>
            <div class="stat-card__label">
              <span>{{ item.label }}</span>
              <span
                v-if="item.passRate != null"
                class="stat-card__rate"
                :class="`stat-card__rate--${passRateLevel(item.passRate)}`"
              >
                7日 {{ item.passRate }}%
              </span>
            </div>
          </div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { FolderOpened, Connection, Link, VideoPlay } from '@element-plus/icons-vue'

const props = defineProps({
  projectCount: { type: Number, default: 0 },
  flowCount: { type: Number, default: 0 },
  apiCount: { type: Number, default: 0 },
  runCount: { type: Number, default: 0 },
  recentPassRate: { type: Number, default: 0 },
})

function passRateLevel(rate) {
  if (rate >= 80) return 'high'
  if (rate >= 50) return 'mid'
  return 'low'
}

const cards = computed(() => [
  { key: 'project', label: '测试项目', value: props.projectCount, icon: FolderOpened },
  { key: 'flow', label: '测试流', value: props.flowCount, icon: Connection },
  { key: 'api', label: 'API 接口', value: props.apiCount, icon: Link },
  {
    key: 'run',
    label: '运行记录',
    value: props.runCount,
    icon: VideoPlay,
    passRate: props.runCount > 0 ? props.recentPassRate : null,
  },
])
</script>

<style scoped lang="scss">
.stat-cards {
  margin-bottom: 16px;
}

.stat-card {
  margin-bottom: 16px;
  border-top: 3px solid var(--el-color-primary);
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }

  &--flow {
    border-top-color: #67c23a;
  }

  &--api {
    border-top-color: #e6a23c;
  }

  &--run {
    border-top-color: #f56c6c;
  }
}

.stat-card__body {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-card__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.stat-card--flow .stat-card__icon {
  color: #67c23a;
}

.stat-card--api .stat-card__icon {
  color: #e6a23c;
}

.stat-card--run .stat-card__icon {
  color: #f56c6c;
}

.stat-card__value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.stat-card__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.stat-card__rate {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 20px;
  font-weight: 500;

  &--high {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }

  &--mid {
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
  }

  &--low {
    color: var(--el-color-danger);
    background: var(--el-color-danger-light-9);
  }
}
</style>
