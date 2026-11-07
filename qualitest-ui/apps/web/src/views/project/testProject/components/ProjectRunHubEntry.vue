<template>
  <!-- 工具条：测试流列表 / 套件列表 -->
  <el-button
      v-if="variant === 'toolbar'"
      v-hasPermi="['project:testProject:query']"
      plain
      @click="open"
  >
    <svg-icon class="project-run-hub-entry__btn-icon" icon-class="run-hub"/>
    运行中心
  </el-button>
  <!-- 画布 / 详情顶栏图标 -->
  <button
      v-else-if="variant === 'icon'"
      v-hasPermi="['project:testProject:query']"
      type="button"
      :class="['project-run-hub-entry__icon-btn', buttonClass]"
      title="运行中心"
      aria-label="运行中心"
      @click="open"
  >
    <svg-icon :class="['project-run-hub-entry__icon', iconClass]" icon-class="run-hub"/>
  </button>
  <!-- 表格行链接 -->
  <el-button
      v-else
      v-hasPermi="['project:testProject:query']"
      class="test-project-op-btn"
      link
      type="primary"
      @click="open"
  >
    <svg-icon icon-class="run-hub"/>
    运行中心
  </el-button>

  <ProjectRunHubDialog
      v-if="resolvedProjectId"
      v-model:visible="visible"
      :test-project-id="resolvedProjectId"
  />
</template>

<script setup>
/**
 * 项目运行中心入口：按钮 + 弹窗。
 * 图标用 run-hub（活动条 + 开跑三角），绘制风格对齐 test-flow。
 * variant=toolbar 列表顶栏；icon 画布/详情顶栏；link 表格操作列（默认）。
 */
import { computed, ref } from 'vue'
import ProjectRunHubDialog from './ProjectRunHubDialog.vue'

const props = defineProps({
  testProjectId: { type: [String, Number], required: true },
  /** toolbar | icon | link */
  variant: { type: String, default: 'link' },
  /** 图标按钮额外 class（如画布顶栏 flow-canvas-header__icon-btn） */
  buttonClass: { type: String, default: '' },
  /** 图标额外 class */
  iconClass: { type: String, default: '' },
})

const visible = ref(false)
const resolvedProjectId = computed(() => props.testProjectId)

function open() {
  if (props.testProjectId == null || props.testProjectId === '') return
  visible.value = true
}
</script>

<style scoped>
.project-run-hub-entry__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.project-run-hub-entry__icon-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.project-run-hub-entry__icon {
  width: 20px;
  height: 20px;
  font-size: 20px;
}

.project-run-hub-entry__btn-icon {
  margin-right: 6px;
  font-size: 16px;
  width: 1em;
  height: 1em;
  vertical-align: -0.15em;
}

.test-project-op-btn :deep(.svg-icon) {
  font-size: 15px;
  width: 1em;
  height: 1em;
}
</style>
