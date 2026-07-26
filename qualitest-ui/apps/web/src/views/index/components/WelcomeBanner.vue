<template>
  <div class="welcome-banner">
    <div class="welcome-banner__main">
      <h2 class="welcome-banner__title">
        你好，{{ nickName || '用户' }}，欢迎使用{{ appTitle }}
      </h2>
      <p class="welcome-banner__subtitle">自动化 API 测试与可视化流程编排平台</p>
    </div>
    <div class="welcome-banner__date">{{ todayText }}</div>
  </div>
</template>

<script setup>
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const nickName = computed(() => userStore.nickName)
const appTitle = import.meta.env.VITE_APP_TITLE || '质衡'

const todayText = computed(() => {
  const now = new Date()
  return now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })
})
</script>

<style scoped lang="scss">
.welcome-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  margin-bottom: 16px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.welcome-banner__title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.welcome-banner__subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.welcome-banner__date {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .welcome-banner {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
