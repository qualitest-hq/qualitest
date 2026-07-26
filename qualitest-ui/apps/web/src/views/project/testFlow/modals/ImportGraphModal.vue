<template>
  <Teleport to="body">
    <div v-if="visible" class="import-modal-overlay" @click.self="close">
      <div aria-labelledby="importModalTitle" class="import-modal" role="dialog">
        <div class="import-modal__head">
          <div>
            <div id="importModalTitle" class="import-modal__title">导入 graph_json</div>
            <div class="import-modal__subtitle">
              支持粘贴 JSON、选择 .json 文件或从剪贴板读取；导入前自动校验结构与引用关系
            </div>
          </div>
          <button class="btn btn--ghost" type="button" @click="close">×</button>
        </div>

        <div class="import-modal__toolbar">
          <button class="btn btn--ghost" type="button" @click="pickFile">选择 JSON 文件</button>
          <button class="btn btn--ghost" type="button" @click="pasteFromClipboard">从剪贴板粘贴</button>
          <input ref="fileInputRef" accept=".json,application/json,text/json" hidden type="file" @change="onFileChange" />
        </div>

        <textarea
            v-model="importText"
            class="import-modal__textarea"
            placeholder='粘贴 graph_json，或支持 { "graph_json": { ... } } 包装'
            spellcheck="false"
            @input="refreshPreview"
        />

        <div class="import-modal__preview">
          <template v-if="!preview">
            <div class="import-preview__hint">输入或粘贴 JSON 后将显示校验结果</div>
          </template>
          <template v-else-if="preview.parseError">
            <div class="import-preview__item--error">JSON 解析失败：{{ preview.parseError }}</div>
            <div class="import-preview__hint">请检查括号、引号是否完整，或是否粘贴了多余文本</div>
          </template>
          <template v-else>
            <div v-if="preview.stat" class="import-preview__stat">{{ preview.stat }}</div>
            <div v-if="preview.typeSummary" class="import-preview__hint">{{ preview.typeSummary }}</div>
            <div v-if="preview.items.length" class="import-preview__list">
              <div
                  v-for="(item, i) in preview.items"
                  :key="i"
                  :class="item.class"
              >
                {{ item.text }}
              </div>
            </div>
          </template>
        </div>

        <div v-if="showOverwriteConfirm" class="import-modal__overwrite" role="alert">
          <div class="import-modal__overwrite-title">确认覆盖导入</div>
          <p class="import-modal__overwrite-text">
            当前画布有 {{ store.nodes.length }} 个节点、{{ store.edges.length }} 条边，导入将
            <strong>完全覆盖</strong> 现有内容，此操作不可通过导入撤销。
          </p>
        </div>

        <div class="import-modal__foot">
          <template v-if="showOverwriteConfirm">
            <button class="btn" type="button" @click="showOverwriteConfirm = false">返回编辑</button>
            <button class="btn btn--primary" type="button" @click="applyImport">确认覆盖</button>
          </template>
          <template v-else>
            <button class="btn" type="button" @click="close">取消</button>
            <button
                :disabled="!canConfirm"
                class="btn btn--primary"
                type="button"
                @click="confirmImport"
            >
              导入
            </button>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
/**
 * 导入 graph_json 弹窗：粘贴/选文件 → 结构校验预览 → 确认后覆盖画布。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { unwrapGraphPayload, validateGraphJson } from '@/utils/flow/graphValidate'

import { useFlowGraph } from '../composables/useFlowGraph'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

const props = defineProps({
  visible: { type: Boolean, default: false },
})

const emit = defineEmits(['update:visible'])

const store = useFlowCanvasStore()
const { importGraph } = useFlowGraph()

const importText = ref('')
const fileInputRef = ref(null)
const validationResult = ref(null)
const parseError = ref(null)
/** 画布非空时，在弹窗内二次确认覆盖，避免 MessageBox 被全屏层遮挡 */
const showOverwriteConfirm = ref(false)

const preview = computed(() => {
  if (!importText.value.trim() && !parseError.value && !validationResult.value) return null
  if (parseError.value) return { parseError: parseError.value }
  if (!validationResult.value) return null

  const { ok, errors, warnings, graph } = validationResult.value
  const nodes = graph?.nodes || []
  const edges = graph?.edges || []
  const typeCount = {}
  nodes.forEach((n) => {
    typeCount[n.type] = (typeCount[n.type] || 0) + 1
  })
  const typeSummary = Object.entries(typeCount).map(([t, c]) => `${c} ${t}`).join(' · ') || '无'

  const items = []
  if (ok) items.push({ class: 'import-preview__item--ok', text: '结构校验通过，可以导入' })
  errors.forEach((msg) => items.push({ class: 'import-preview__item--error', text: `✗ ${msg}` }))
  warnings.slice(0, 8).forEach((msg) => items.push({ class: 'import-preview__item--warn', text: `⚠ ${msg}` }))
  if (warnings.length > 8) {
    items.push({ class: 'import-preview__item--warn', text: `… 另有 ${warnings.length - 8} 条警告` })
  }

  return {
    stat: `${nodes.length} 个节点 · ${edges.length} 条边`,
    typeSummary,
    items,
  }
})

const canConfirm = computed(() => validationResult.value?.ok === true)

/** 解析文本并更新校验预览 */
function refreshPreview() {
  parseError.value = null
  validationResult.value = null
  const text = importText.value.trim()
  if (!text) return
  try {
    let data = JSON.parse(text)
    if (typeof data === 'string') data = JSON.parse(data)
    const unwrapped = unwrapGraphPayload(data)
    const result = validateGraphJson(unwrapped, { forImport: true })
    validationResult.value = {
      ...result,
      graph: unwrapped,
      raw: data,
    }
  } catch (e) {
    parseError.value = e.message || String(e)
  }
}

function close() {
  showOverwriteConfirm.value = false
  emit('update:visible', false)
}

function confirmImport() {
  if (!validationResult.value?.ok) {
    ElMessage.warning('请先修正 JSON 错误')
    return
  }
  const hasContent = store.nodes.length > 0 || store.edges.length > 0
  if (hasContent) {
    showOverwriteConfirm.value = true
    return
  }
  applyImport()
}

async function applyImport() {
  if (!validationResult.value?.ok) return
  const ok = await importGraph(validationResult.value.raw)
  if (ok) {
    const w = validationResult.value.warnings?.length || 0
    ElMessage.success(w ? `导入成功（${w} 条警告）` : '导入成功')
    close()
  }
}

function pickFile() {
  fileInputRef.value?.click()
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    importText.value = String(reader.result || '')
    refreshPreview()
    ElMessage.success(`已读取 ${file.name}`)
  }
  reader.onerror = () => ElMessage.error('文件读取失败')
  reader.readAsText(file, 'UTF-8')
  event.target.value = ''
}

async function pasteFromClipboard() {
  try {
    const text = await navigator.clipboard.readText()
    if (!text.trim()) {
      ElMessage.warning('剪贴板为空')
      return
    }
    importText.value = text
    refreshPreview()
    ElMessage.success('已从剪贴板粘贴')
  } catch {
    ElMessage.warning('无法读取剪贴板，请手动粘贴')
  }
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      importText.value = ''
      parseError.value = null
      validationResult.value = null
      showOverwriteConfirm.value = false
    }
  },
)
</script>

<style lang="scss">
@use '../styles/flowCanvasTokens.scss' as flow;

/* 弹窗挂载在 body，需在此定义变量与 .btn */
.import-modal-overlay {
  @include flow.flow-pd-import-vars;

  position: fixed;
  inset: 0;
  z-index: 10000;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;

  @include flow.flow-pd-buttons-nested;
}

.import-modal {
  width: min(720px, 100%);
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  background: var(--pd-surface-elevated, #fff);
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(20, 60, 120, 0.18);
  overflow: hidden;
}

.import-modal__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--pd-divider, #dbe8f4);
}

.import-modal__title {
  font-weight: 600;
  font-size: 15px;
}

.import-modal__subtitle {
  font-size: 11px;
  color: var(--pd-text-muted, #5a6b86);
  margin-top: 4px;
  line-height: 1.45;
}

.import-modal__toolbar {
  display: flex;
  gap: 8px;
  padding: 10px 18px 0;
}

.import-modal__textarea {
  margin: 10px 18px 0;
  height: 180px;
  padding: 10px 12px;
  border: 1px solid var(--pd-border-subtle, #c5d8ec);
  border-radius: 8px;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 11px;
  line-height: 1.5;
  resize: vertical;
  background: #f8fafc;
}

.import-modal__preview {
  margin: 10px 18px;
  padding: 10px 12px;
  border: 1px solid var(--pd-divider, #dbe8f4);
  border-radius: 8px;
  background: var(--pd-bg-sunken, #e9f2fc);
  min-height: 80px;
  max-height: 160px;
  overflow-y: auto;
  font-size: 11px;
}

.import-preview__stat {
  font-weight: 600;
  margin-bottom: 4px;
}

.import-preview__hint {
  color: var(--pd-text-muted, #5a6b86);
  margin-bottom: 6px;
}

.import-preview__list {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.import-preview__item--error {
  color: #b91c1c;
}

.import-preview__item--warn {
  color: #a16207;
}

.import-preview__item--ok {
  color: #166534;
}

.import-modal__overwrite {
  margin: 0 18px 10px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid #fcd34d;
  background: #fffbeb;
  color: #92400e;
}

.import-modal__overwrite-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
}

.import-modal__overwrite-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.import-modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--pd-divider, #dbe8f4);
}
</style>
