<template>
  <el-card shadow="never" class="quick-links">
    <template #header>
      <span class="quick-links__title">快捷入口</span>
    </template>
    <div class="quick-links__grid">
      <div
        v-for="item in visibleLinks"
        :key="item.path"
        class="quick-link-item"
        @click="go(item.path)"
      >
        <svg-icon
          v-if="item.svgIcon"
          :icon-class="item.svgIcon"
          class="quick-link-item__icon quick-link-item__svg-icon"
        />
        <el-icon v-else :size="22" class="quick-link-item__icon">
          <component :is="item.icon" />
        </el-icon>
        <span class="quick-link-item__label">{{ item.label }}</span>
      </div>
      <div v-if="!visibleLinks.length" class="quick-links__empty">暂无可用入口</div>
    </div>
  </el-card>
</template>

<script setup>
import { FolderOpened, Cpu, Monitor, Setting } from '@element-plus/icons-vue'
import { checkPermi } from '@/utils/permission'

const router = useRouter()

const allLinks = [
  {
    label: '测试项目',
    path: '/testManages/testProject',
    icon: FolderOpened,
    permi: ['project:testProject:list'],
  },
  {
    label: 'AI 配置',
    path: '/aiManages/aiLlmVendor',
    icon: Setting,
    permi: ['ai:aiLlmVendor:list'],
  },
  {
    label: 'AI 提示词',
    path: '/aiManages/aiPromptTemplate',
    svgIcon: 'ai-prompt',
    permi: ['ai:aiPromptTemplate:list'],
  },
  {
    label: 'AI 模型',
    path: '/aiManages/aiLlmModel',
    icon: Cpu,
    permi: ['ai:aiLlmModel:list'],
  },
  {
    label: '服务监控',
    path: '/monitor/server',
    icon: Monitor,
    permi: ['monitor:server:list'],
  },
]

const visibleLinks = computed(() =>
  allLinks.filter((item) => !item.permi?.length || checkPermi(item.permi)),
)

function go(path) {
  router.push(path)
}
</script>

<style scoped lang="scss">
.quick-links {
  height: 100%;
}

.quick-links__title {
  font-weight: 600;
}

.quick-links__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quick-link-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 8px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;

  &:hover {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
}

.quick-link-item__icon {
  flex-shrink: 0;
}

.quick-link-item__svg-icon {
  width: 22px;
  height: 22px;
}

.quick-link-item__label {
  font-size: 13px;
  text-align: center;
}

.quick-links__empty {
  grid-column: 1 / -1;
  padding: 24px;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
