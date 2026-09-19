<template>
  <div>
    <svg-icon icon-class="skill" @click="openDialog" />
    <el-dialog
        v-model="visible"
        append-to-body
        title="MCP 造流 Agent 规程"
        width="720px"
        class="cursor-skill-dialog"
        @opened="ensureGuides"
    >
      <div v-loading="loading" class="cursor-skill-dialog__wrap">
        <template v-if="payload">
          <section class="cursor-skill-dialog__howto">
            <h4 class="cursor-skill-dialog__section-title">怎么用</h4>
            <p>{{ payload.howToUse }}</p>
            <ul v-if="payload.tips?.length" class="cursor-skill-dialog__tips">
              <li v-for="(tip, idx) in payload.tips" :key="idx">{{ tip }}</li>
            </ul>
            <div v-if="payload.examples?.length" class="cursor-skill-dialog__examples">
              <h4 class="cursor-skill-dialog__section-title">示例提问（可直接复制）</h4>
              <div
                  v-for="(ex, idx) in payload.examples"
                  :key="idx"
                  class="cursor-skill-dialog__example"
              >
                <div class="cursor-skill-dialog__example-head">
                  <span>{{ ex.label }}</span>
                  <el-button link type="primary" @click="copyText(ex.text, '示例已复制')">
                    复制
                  </el-button>
                </div>
                <pre class="cursor-skill-dialog__example-text">{{ ex.text }}</pre>
              </div>
            </div>
          </section>

          <el-tabs
              v-if="guides.length"
              v-model="activeId"
              class="cursor-skill-dialog__tabs"
          >
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
                  规程正文给 Agent 看；Token 只绑项目，读写看「允许 MCP 全自动写流」开关（均在各项目的「项目设置」里）。
                  建议保存位置：
                  <code class="cursor-skill-dialog__path">{{ g.saveHint }}</code>
                </p>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>
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
 * 顶栏图标：弹出 MCP 人话用法、示例提问，以及多编辑器规程 Tabs，可复制当前 Tab 全文。
 * 当前路由若带 testProjectId，则按该项目写流/导入开关拉取已裁剪规程；否则拉取只读规程。
 * 每次打开弹框都会重新请求，以便换项目或改开关后拿到最新正文。
 */
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { getMcpAgentGuides } from '@/api/common/mcpAgentGuides'
import { copyTextSync } from '@/utils/clipboard'

const route = useRoute()
const visible = ref(false)
const loading = ref(false)
const copying = ref(false)
/** @type {import('vue').Ref<{ howToUse?: string, tips?: string[], examples?: Array<{ label: string, text: string }>, guides?: Array<{ id: string, title: string, intro: string, steps: string[], saveHint: string, content: string }> } | null>} */
const payload = ref(null)
const activeId = ref('')

const guides = computed(() => payload.value?.guides || [])
const activeGuide = computed(() => guides.value.find((g) => g.id === activeId.value) || null)

/** 从当前路由 params 取出 testProjectId；没有则返回 undefined */
function resolveTestProjectId() {
  const id = route.params?.testProjectId
  return id != null && String(id).trim() !== '' ? String(id) : undefined
}

function openDialog() {
  // 清空缓存，打开时按当前路由项目重新拉取规程
  payload.value = null
  visible.value = true
}

function ensureGuides() {
  if (payload.value || loading.value) {
    return
  }
  loading.value = true
  getMcpAgentGuides(resolveTestProjectId())
    .then((res) => {
      const data = res?.data
      // 旧接口若仍返回数组，只当作 guides 使用
      if (Array.isArray(data)) {
        payload.value = { guides: data }
      } else if (data && typeof data === 'object') {
        payload.value = data
      } else {
        payload.value = null
      }
      const list = payload.value?.guides || []
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

function copyText(text, successMsg) {
  const value = String(text || '').trim()
  if (!value) {
    ElMessage.error('内容为空')
    return false
  }
  if (copyTextSync(value)) {
    ElMessage.success(successMsg || '已复制')
    return true
  }
  ElMessage.error('复制失败')
  return false
}

function copyActive() {
  const g = activeGuide.value
  if (!g) {
    return
  }
  copying.value = true
  try {
    copyText(g.content, `已复制，请保存为 ${g.saveHint}`)
  } finally {
    copying.value = false
  }
}
</script>

<style scoped>
.cursor-skill-dialog__wrap {
  min-height: 160px;
  max-height: min(70vh, 640px);
  overflow-y: auto;
  padding-right: 4px;
}

.cursor-skill-dialog__howto {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.cursor-skill-dialog__section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cursor-skill-dialog__howto > p {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-regular);
}

.cursor-skill-dialog__tips {
  margin: 0 0 14px;
  padding-left: 1.2em;
  font-size: 12px;
  line-height: 1.65;
  color: var(--el-text-color-secondary);
}

.cursor-skill-dialog__tips li {
  margin-bottom: 6px;
}

.cursor-skill-dialog__examples {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cursor-skill-dialog__example {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.cursor-skill-dialog__example-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cursor-skill-dialog__example-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, 'Cascadia Code', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.55;
  color: var(--el-text-color-regular);
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
