<template>
  <div>
    <svg-icon icon-class="skill" @click="openDialog" />
    <el-dialog
        v-model="visible"
        append-to-body
        title="MCP 造流 Agent 规程"
        width="640px"
        class="cursor-skill-dialog"
        @opened="ensureGuides"
    >
      <div v-loading="loading" class="cursor-skill-dialog__wrap">
        <el-tabs v-if="guides.length" v-model="activeId" class="cursor-skill-dialog__tabs">
          <el-tab-pane
              v-for="g in guides"
              :key="g.id"
              :label="g.title"
              :name="g.id"
          >
            <div class="cursor-skill-dialog__body">
              <p>{{ g.intro }}</p>
              <ol>
                <li v-for="(step, idx) in g.steps" :key="idx">{{ step }}</li>
              </ol>
              <p class="cursor-skill-dialog__hint">
                规程与具体测试项目无关；Token、MCP 配置和写流开关仍在各项目的「项目设置」里。
                建议保存位置：
                <code class="cursor-skill-dialog__path">{{ g.saveHint }}</code>
              </p>
            </div>
          </el-tab-pane>
        </el-tabs>
        <p v-else-if="!loading" class="cursor-skill-dialog__hint">暂无可用规程</p>
      </div>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button
            :disabled="!activeGuide"
            :loading="copying"
            type="primary"
            @click="copyActive"
        >
          复制规程
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 顶栏图标：弹出多编辑器 MCP 造流规程说明（Tabs），并可复制当前 Tab 全文。
 */
import { ElMessage } from 'element-plus'
import { getMcpAgentGuides } from '@/api/common/mcpCursorSkill'
import { copyTextSync } from '@/utils/clipboard'

const visible = ref(false)
const loading = ref(false)
const copying = ref(false)
/** @type {import('vue').Ref<Array<{ id: string, title: string, intro: string, steps: string[], saveHint: string, content: string }>>} */
const guides = ref([])
const activeId = ref('')

const activeGuide = computed(() => guides.value.find((g) => g.id === activeId.value) || null)

function openDialog() {
  visible.value = true
}

function ensureGuides() {
  if (guides.value.length || loading.value) {
    return
  }
  loading.value = true
  getMcpAgentGuides()
    .then((res) => {
      const list = Array.isArray(res?.data) ? res.data : []
      guides.value = list
      if (!activeId.value && list.length) {
        activeId.value = list[0].id
      }
    })
    .catch(() => {
      ElMessage.error('获取规程失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function copyActive() {
  const g = activeGuide.value
  if (!g) {
    return
  }
  const text = String(g.content || '').trim()
  if (!text) {
    ElMessage.error('规程为空')
    return
  }
  copying.value = true
  try {
    if (copyTextSync(text)) {
      ElMessage.success(`已复制，请保存为 ${g.saveHint}`)
    } else {
      ElMessage.error('复制失败')
    }
  } finally {
    copying.value = false
  }
}
</script>

<style scoped>
.cursor-skill-dialog__wrap {
  min-height: 160px;
}

.cursor-skill-dialog__tabs :deep(.el-tabs__nav-wrap) {
  overflow-x: auto;
}

.cursor-skill-dialog__body {
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-regular);
}

.cursor-skill-dialog__body p {
  margin: 0 0 12px;
}

.cursor-skill-dialog__body ol {
  margin: 0 0 12px;
  padding-left: 1.25em;
}

.cursor-skill-dialog__body li {
  margin-bottom: 10px;
}

.cursor-skill-dialog__body code,
.cursor-skill-dialog__path {
  padding: 1px 5px;
  border-radius: 4px;
  font-family: ui-monospace, 'Cascadia Code', 'Courier New', monospace;
  font-size: 12px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  word-break: break-all;
}

.cursor-skill-dialog__hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
