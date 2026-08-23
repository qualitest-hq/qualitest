<template>
  <div
    ref="rootRef"
    class="script-source-editor"
    :class="{ 'script-source-editor--invalid': invalid }"
    :style="rootStyle"
  />
</template>

<script setup>
/**
 * 脚本源码编辑器：基于 CodeMirror 6，支持 JavaScript / Python 语法高亮。
 * 通过 v-model 双向绑定源码字符串；语言切换时自动更换高亮扩展。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  /** 脚本源码 */
  modelValue: { type: String, default: '' },
  /** 脚本语言：javascript | python | json */
  language: { type: String, default: 'javascript' },
  /** 只读模式 */
  readOnly: { type: Boolean, default: false },
  /** 编辑器最小高度（像素） */
  minHeight: { type: Number, default: 160 },
  /** 校验失败时高亮边框 */
  invalid: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const rootRef = ref(null)
let editorView = null
let cmModules = null

const rootStyle = computed(() => {
  if (props.minHeight <= 0) {
    return {
      minHeight: '0',
      height: '100%',
    }
  }
  return {
    minHeight: `${props.minHeight}px`,
  }
})

/** 按 language 加载 CodeMirror 语言扩展 */
async function loadLanguageExtension(language) {
  if (!cmModules) {
    cmModules = await import('./scriptEditorCore')
  }
  return cmModules.languageExtension(language)
}

/** 创建或重建编辑器实例 */
async function mountEditor() {
  if (!rootRef.value) return
  if (!cmModules) {
    cmModules = await import('./scriptEditorCore')
  }
  destroyEditor()
  const langExt = await loadLanguageExtension(props.language)
  editorView = cmModules.createEditor({
    parent: rootRef.value,
    doc: props.modelValue ?? '',
    readOnly: props.readOnly,
    languageExtension: langExt,
    onChange: (value) => emit('update:modelValue', value),
  })
}

function destroyEditor() {
  if (editorView) {
    editorView.destroy()
    editorView = null
  }
}

onMounted(() => {
  mountEditor()
})

onBeforeUnmount(() => {
  destroyEditor()
})

watch(
  () => props.modelValue,
  (val) => {
    if (!editorView || !cmModules) return
    cmModules.syncDoc(editorView, val ?? '')
  },
)

watch(
  () => [props.language, props.readOnly],
  () => {
    mountEditor()
  },
)

/** 在编辑器光标处插入文本（供常用脚本面板调用） */
function insertSnippet(text) {
  if (!editorView || !cmModules || !text) return
  cmModules.insertTextAtCursor(editorView, text)
}

defineExpose({ insertSnippet })
</script>

<style scoped lang="scss">
.script-source-editor {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--pd-border-subtle, #e2e8f0);
  border-radius: 8px;
  overflow: hidden;
  font-size: 12px;
  background: #f8fafc;
  box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: border-color 0.15s, box-shadow 0.15s;

  &:focus-within {
    border-color: #67e8f9;
    box-shadow:
      inset 0 1px 2px rgba(15, 23, 42, 0.04),
      0 0 0 2px rgba(8, 145, 178, 0.12);
  }

  &--invalid {
    border-color: var(--el-color-danger);

    &:focus-within {
      border-color: var(--el-color-danger);
      box-shadow:
        inset 0 1px 2px rgba(15, 23, 42, 0.04),
        0 0 0 2px rgba(245, 108, 108, 0.2);
    }
  }

  :deep(.cm-editor) {
    flex: 1;
    min-height: 0;
    outline: none;
  }

  :deep(.cm-scroller) {
    min-height: 0;
  }

  :deep(.cm-content) {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
}
</style>
