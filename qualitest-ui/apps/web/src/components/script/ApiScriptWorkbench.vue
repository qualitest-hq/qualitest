<template>
  <div class="api-script-workbench" :class="{ 'is-aside-collapsed': !snippetPanelOpen }">
    <div class="api-script-workbench__editor-col">
      <div class="api-script-workbench__toolbar">
        <div class="api-script-workbench__toolbar-left">
          <span class="api-script-workbench__badge">JS</span>
          <span class="api-script-workbench__phase-tag">{{ phaseLabel }}</span>
          <span class="api-script-workbench__toolbar-desc">{{ phaseDesc }}</span>
        </div>
        <div v-if="$slots['toolbar-actions']" class="api-script-workbench__toolbar-right">
          <!-- 扩展插槽：父页面可注入「AI 助手」等工具栏按钮 -->
          <slot name="toolbar-actions" />
        </div>
      </div>
      <div class="api-script-workbench__editor-wrap">
        <ScriptSourceEditor
            ref="editorRef"
            v-model="model"
            :min-height="editorMinHeight"
            language="javascript"
        />
      </div>
    </div>

    <aside :class="{ 'is-collapsed': !snippetPanelOpen }" class="api-script-workbench__aside">
      <button
          v-if="!snippetPanelOpen"
          :title="'展开常用脚本'"
          class="api-script-workbench__aside-expand"
          type="button"
          @click="snippetPanelOpen = true"
      >
        <span class="api-script-workbench__aside-expand-text">常用脚本</span>
        <span aria-hidden="true" class="api-script-workbench__chevron">‹</span>
      </button>

      <div v-else class="api-script-workbench__aside-body">
        <div class="api-script-workbench__aside-head">
          <span class="api-script-workbench__aside-title">常用脚本</span>
          <button
              :title="'收起常用脚本'"
              class="api-script-workbench__aside-collapse"
              type="button"
              @click="snippetPanelOpen = false"
          >
            <span aria-hidden="true" class="api-script-workbench__chevron">›</span>
          </button>
        </div>

        <div class="api-script-workbench__aside-scroll">
          <div class="api-script-workbench__aside-meta">
            <p class="api-script-workbench__aside-hint">点击条目插入到左侧编辑器</p>
            <label class="api-script-workbench__comment-switch">
              <span class="api-script-workbench__comment-label">注释</span>
              <el-switch v-model="withComment" size="small" />
            </label>
          </div>

          <ul class="api-script-workbench__snippet-list">
            <li v-for="item in snippets" :key="item.id">
              <button
                  class="api-script-workbench__snippet-btn"
                  type="button"
                  @click="insertSnippet(item)"
              >
                {{ item.label }}
              </button>
            </li>
          </ul>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ScriptSourceEditor from './ScriptSourceEditor.vue'
import {
  formatApiScriptSnippet,
  getApiScriptSnippets,
} from './apiScriptSnippets'

const COMMENT_PREF_KEY = 'qualitest-api-script-with-comment'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** pre | post */
  phase: { type: String, default: 'pre' },
  /** 0 表示随父容器 flex 撑满，不设固定最小高度 */
  minHeight: { type: Number, default: 0 },
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
const snippetPanelOpen = ref(true)
const withComment = ref(readCommentPref())

const model = computed({
  get: () => props.modelValue ?? '',
  set: (val) => emit('update:modelValue', val ?? ''),
})

const snippets = computed(() => getApiScriptSnippets(props.phase === 'post' ? 'post' : 'pre'))

const phaseLabel = computed(() => (props.phase === 'post' ? '后置' : '前置'))

const phaseDesc = computed(() =>
  props.phase === 'post'
    ? '请求完成后执行，可用于断言与提取变量'
    : '请求发送前执行，可用于签名、改 Header 等',
)

const editorMinHeight = computed(() => (props.minHeight > 0 ? props.minHeight : 0))

watch(withComment, (val) => {
  try {
    localStorage.setItem(COMMENT_PREF_KEY, val ? '1' : '0')
  } catch {
    /* ignore */
  }
})

function readCommentPref() {
  try {
    return localStorage.getItem(COMMENT_PREF_KEY) !== '0'
  } catch {
    return true
  }
}

function insertSnippet(item) {
  const text = formatApiScriptSnippet(item, withComment.value)
  editorRef.value?.insertSnippet(text)
}
</script>

<style scoped lang="scss">
.api-script-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  grid-template-rows: minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  flex: 1;
  border: 1px solid var(--pd-border-subtle, #d6e6f5);
  border-radius: var(--pd-radius-sm, 8px);
  overflow: hidden;
  background: transparent;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.03);

  &.is-aside-collapsed {
    grid-template-columns: minmax(0, 1fr) 36px;
  }
}

.api-script-workbench__editor-col {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--pd-surface-elevated, #fff);
  border-right: 1px solid var(--pd-border-subtle, #d6e6f5);
}

.api-script-workbench__toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.55));
  border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted, #d6e6f5));
}

.api-script-workbench__toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.api-script-workbench__toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.api-script-workbench__badge {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #0e7490;
  background: #ecfeff;
  border: 1px solid #a5f3fc;
  flex-shrink: 0;
}

.api-script-workbench__phase-tag {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-primary, #0b6edc);
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--pd-primary-soft, rgba(11, 110, 220, 0.08));
}

.api-script-workbench__toolbar-desc {
  font-size: 12px;
  color: var(--pd-text-muted, #64748b);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.api-script-workbench__editor-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 6px;

  :deep(.script-source-editor) {
    flex: 1;
    min-height: 0;
    border-radius: 6px;
    border-color: var(--pd-border-muted, #d6e6f5);
    box-shadow: none;
  }
}

.api-script-workbench__aside {
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--pd-bg-sunken, #e9f2fc);
  overflow: hidden;

  &.is-collapsed {
    border-left: none;
  }
}

.api-script-workbench__aside-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.api-script-workbench__aside-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.api-script-workbench__aside-expand {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  width: 100%;
  height: 100%;
  padding: 10px 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--pd-text-muted, #64748b);
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--pd-primary, #0b6edc);
    background: rgba(255, 255, 255, 0.65);
  }
}

.api-script-workbench__aside-expand-text {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.api-script-workbench__aside-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px 6px;
  border-bottom: 1px solid var(--pd-divider, rgba(214, 230, 245, 0.9));
}

.api-script-workbench__aside-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--pd-text, #1e293b);
}

.api-script-workbench__aside-collapse,
.api-script-workbench__chevron {
  line-height: 1;
}

.api-script-workbench__aside-collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--pd-text-muted, #94a3b8);
  cursor: pointer;
  font-size: 16px;
  transition: color 0.15s ease, background 0.15s ease, border-color 0.15s ease;

  &:hover {
    color: var(--pd-primary, #0b6edc);
    background: rgba(255, 255, 255, 0.85);
    border-color: var(--pd-border-muted, #d6e6f5);
  }
}

.api-script-workbench__aside-meta {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--pd-divider, rgba(214, 230, 245, 0.6));
}

.api-script-workbench__aside-hint {
  margin: 0;
  font-size: 11px;
  color: var(--pd-text-muted, #94a3b8);
  line-height: 1.45;
}

.api-script-workbench__comment-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--pd-border-muted, #d6e6f5);
  cursor: pointer;
}

.api-script-workbench__comment-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--pd-text-tab, #334c6e);
}

.api-script-workbench__snippet-list {
  list-style: none;
  margin: 0;
  padding: 4px 8px 8px;
}

.api-script-workbench__snippet-btn {
  display: block;
  width: 100%;
  margin: 0 0 2px;
  padding: 7px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pd-primary, #0b6edc);
  cursor: pointer;
  text-decoration: none;
  transition: background 0.12s ease, color 0.12s ease;

  &:hover {
    background: rgba(11, 110, 220, 0.08);
    color: #0958b8;
  }

  &:active {
    background: rgba(11, 110, 220, 0.14);
  }

  &:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 40%, transparent);
    outline-offset: 0;
  }
}
</style>
