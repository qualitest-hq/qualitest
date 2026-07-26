<template>
  <Teleport to="body">
    <div v-if="visible" class="export-drawer-overlay" @click.self="close">
      <div class="export-drawer" role="dialog">
        <div class="export-drawer__head">
          <span class="export-drawer__title">graph_json 预览</span>
          <div class="export-drawer__actions">
            <button class="btn btn--ghost" type="button" @click="copyJson">复制</button>
            <button class="btn btn--ghost" type="button" @click="downloadJson">下载 JSON</button>
            <button
                :disabled="exportingImage"
                class="btn btn--ghost"
                type="button"
                @click="exportImage"
            >
              {{ exportingImage ? '生成中…' : '导出图片' }}
            </button>
            <button class="btn" type="button" @click="close">关闭</button>
          </div>
        </div>
        <pre class="export-drawer__preview">{{ exportText }}</pre>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
/**
 * 导出 graph_json 抽屉：展示当前画布完整 JSON，支持复制、下载 JSON、导出 PNG 图片。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { toGraphJson } from '../graphAdapter'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { exportGraphAsPng } from '../utils/graphExportImage'

const EXPORT_BASENAME = 'test-flow'

const props = defineProps({
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible'])

const store = useFlowCanvasStore()
const exportingImage = ref(false)

const exportBasename = computed(() => {
  const name = String(store.flowName || '').trim()
  return name || EXPORT_BASENAME
})

const exportText = computed(() => {
  const graph = toGraphJson({
    nodes: store.nodes,
    edges: store.edges,
    viewport: store.viewport,
    runConfig: store.runConfig,
    flowOutputs: store.flowOutputs,
  })
  return JSON.stringify(graph, null, 2)
})

function close() {
  emit('update:visible', false)
}

async function copyJson() {
  const text = exportText.value
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('JSON 已复制到剪贴板')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    ta.remove()
    ElMessage.success('JSON 已复制到剪贴板')
  }
}

function downloadJson() {
  const blob = new Blob([exportText.value], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${exportBasename.value}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('JSON 文件已下载')
}

async function exportImage() {
  if (exportingImage.value) return
  exportingImage.value = true
  ElMessage.info('正在生成图片…')
  try {
    await exportGraphAsPng(store.nodes, store.edges, exportBasename.value)
    ElMessage.success('图片已下载')
  } catch (e) {
    ElMessage.error(`图片导出失败：${e?.message || String(e) || '未知错误'}`)
  } finally {
    exportingImage.value = false
  }
}
</script>

<style lang="scss">
/* 抽屉挂载在 body，底部滑出；样式对齐原型 json-drawer */
.export-drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: flex-end;
  justify-content: stretch;

  .btn {
    height: 28px;
    padding: 0 10px;
    border-radius: 6px;
    border: 1px solid #475569;
    background: #1e293b;
    color: #e2e8f0;
    font-size: 11px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;

    &:hover:not(:disabled) {
      background: #334155;
      border-color: #64748b;
      color: #fff;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .btn--ghost {
    background: transparent;
    border-color: #475569;
    color: #cbd5e1;

    &:hover:not(:disabled) {
      background: #334155;
      border-color: #64748b;
      color: #fff;
    }
  }
}

.export-drawer {
  width: 100%;
  height: 40vh;
  display: flex;
  flex-direction: column;
  background: #0f172a;
  color: #e2e8f0;
  border-top: 2px solid #0b6edc;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.2);
}

.export-drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #1e293b;
  flex-shrink: 0;
}

.export-drawer__title {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  flex: 1;
  min-width: 0;
}

.export-drawer__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.export-drawer__preview {
  flex: 1;
  margin: 0;
  padding: 16px;
  overflow: auto;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 11px;
  line-height: 1.6;
  background: transparent;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
