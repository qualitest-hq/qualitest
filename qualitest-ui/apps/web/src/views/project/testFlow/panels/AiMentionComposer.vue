<template>
  <div ref="anchorRef" class="ai-mention-wrap">
    <div
        v-if="empty"
        class="ai-mention-editor__placeholder"
    >
      {{ placeholder }}
    </div>

    <div
        ref="editorRef"
        :aria-disabled="disabled"
        :class="['ai-mention-editor', { 'is-disabled': disabled }]"
        contenteditable="true"
        role="textbox"
        aria-multiline="true"
        @compositionend="editor.handleCompositionEnd"
        @compositionstart="editor.handleCompositionStart"
        @input="onInput"
        @keydown="onKeydown"
        @paste="editor.handlePaste"
        @mousedown="editor.handleEditorMouseDown"
        @scroll="editor.updateMenuPosition"
    />

    <div
        v-if="menu.open"
        :style="{ top: `${menu.y}px`, left: `${menu.x}px` }"
        class="mention-dropdown"
        role="listbox"
        @mousedown.prevent
    >
      <div class="mention-dropdown__tabs">
        <button
            v-for="tab in MENTION_TABS"
            :key="tab.id"
            :class="{ 'is-active': menu.category === tab.id }"
            class="mention-dropdown__tab"
            type="button"
            @click="onCategoryChange(tab.id)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="menu.category === 'var'" class="mention-dropdown__subtabs">
        <button
            v-for="sub in VAR_SUBTYPES"
            :key="sub.id"
            :class="{ 'is-active': menu.varSubtype === sub.id }"
            class="mention-dropdown__subtab"
            type="button"
            @click="onVarSubtypeChange(sub.id)"
        >
          {{ sub.label }}
        </button>
      </div>

      <div v-if="loadingCandidates" class="mention-dropdown__hint">加载中…</div>
      <template v-else-if="candidates.length">
        <button
            v-for="(item, index) in candidates"
            :key="`${item.type}-${item.subtype ?? ''}-${item.id}`"
            :class="{
              'is-active': index === activeIndex,
              'is-pinned': item.pinned,
            }"
            class="mention-dropdown__option"
            type="button"
            @click="onSelectCandidate(item)"
            @mouseenter="activeIndex = index"
        >
          <span v-if="item.pinned" class="mention-dropdown__pin">★</span>
          <span class="mention-dropdown__label">{{ item.label }}</span>
          <span v-if="item.detail" class="mention-dropdown__detail">{{ item.detail }}</span>
        </button>
      </template>
      <div v-else class="mention-dropdown__hint">无匹配项</div>
    </div>
  </div>
</template>

<script setup lang="ts">
/** AI 设计输入框：contenteditable + 内嵌 @ chip + 下拉候选项 */
import { ref, watch } from 'vue';

import {
  buildComposerSendPayload,
  buildNodeMentionCandidate,
  MENTION_TABS,
  renderDocToDom,
  searchMentionCandidates,
  useMentionEditor,
  type ComposerDoc,
  type ComposerSendPayload,
  type MentionCandidate,
  type MentionCategory,
  type VarSubtype,
} from '../composables/mentionComposer';

const VAR_SUBTYPES: { id: VarSubtype; label: string }[] = [
  { id: 'flow', label: 'flow' },
  { id: 'env', label: 'env' },
  { id: 'asset', label: 'asset' },
];

const props = withDefaults(
  defineProps<{ disabled?: boolean; placeholder?: string }>(),
  {
    disabled: false,
    placeholder: '描述测试意图，输入 @ 引用接口、节点、Run 或变量',
  },
);

const emit = defineEmits<{
  submit: [payload: ComposerSendPayload];
  change: [doc: ComposerDoc];
}>();

const anchorRef = ref<HTMLElement | null>(null);
const editorRef = ref<HTMLElement | null>(null);
const empty = ref(true);
const candidates = ref<MentionCandidate[]>([]);
const loadingCandidates = ref(false);
const activeIndex = ref(0);

const editor = useMentionEditor({
  getRoot: () => editorRef.value,
  getAnchor: () => anchorRef.value,
  onDocChange: () => {
    empty.value = editor.isEmpty();
    emit('change', editor.getDoc());
  },
});

const menu = editor.menu;

function onInput() {
  editor.handleInput();
  if (menu.value.open) {
    editor.updateMenuPosition();
    loadCandidates();
  }
}

async function loadCandidates() {
  loadingCandidates.value = true;
  try {
    candidates.value = await searchMentionCandidates(
      menu.value.category,
      menu.value.query,
      menu.value.category === 'var' ? menu.value.varSubtype : undefined,
    );
    activeIndex.value = 0;
  } finally {
    loadingCandidates.value = false;
  }
}

function onCategoryChange(category: MentionCategory) {
  editor.setCategory(category);
  loadCandidates();
}

/** 左右方向键切换主 Tab */
function switchCategoryByDelta(delta: number) {
  const currentIndex = MENTION_TABS.findIndex((t) => t.id === menu.value.category);
  const nextIndex = (currentIndex + delta + MENTION_TABS.length) % MENTION_TABS.length;
  onCategoryChange(MENTION_TABS[nextIndex]!.id);
}

function onVarSubtypeChange(subtype: VarSubtype) {
  editor.setVarSubtype(subtype);
  loadCandidates();
}

function onSelectCandidate(item: MentionCandidate) {
  editor.insertMention(item);
  empty.value = editor.isEmpty();
}

function onKeydown(e: KeyboardEvent) {
  if (props.disabled) return;

  if (menu.value.open) {
    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      switchCategoryByDelta(-1);
      return;
    }
    if (e.key === 'ArrowRight') {
      e.preventDefault();
      switchCategoryByDelta(1);
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      activeIndex.value = Math.min(activeIndex.value + 1, Math.max(candidates.value.length - 1, 0));
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      activeIndex.value = Math.max(activeIndex.value - 1, 0);
      return;
    }
    if ((e.key === 'Enter' || e.key === 'Tab') && candidates.value.length) {
      e.preventDefault();
      onSelectCandidate(candidates.value[activeIndex.value]!);
      return;
    }
  }

  if (editor.handleKeydown(e) === 'send') trySubmit();
}

function trySubmit() {
  if (props.disabled) return;
  try {
    emit('submit', buildComposerSendPayload(editor.getDoc()));
  } catch {
    // 空内容时忽略
  }
}

function setDoc(doc: ComposerDoc) {
  if (!editorRef.value) return;
  renderDocToDom(doc, editorRef.value);
  empty.value = editor.isEmpty();
  emit('change', editor.getDoc());
}

function clear() {
  editor.clearEditor();
  empty.value = true;
}

function appendRunMention(runId: string, status = 'failed') {
  editor.appendMentionChip({
    type: 'run',
    id: runId,
    label: `Run#${runId} ${status === 'failed' ? '失败' : status}`,
  });
  empty.value = editor.isEmpty();
}

function appendNodeMention(node: { id: string; type?: string; data?: { name?: string } }) {
  editor.appendMentionChip(buildNodeMentionCandidate(node));
  empty.value = editor.isEmpty();
  emit('change', editor.getDoc());
}

function appendText(text: string) {
  editor.insertText(text);
  empty.value = editor.isEmpty();
}

watch(() => menu.value.open, (open) => {
  if (open) {
    editor.updateMenuPosition();
    loadCandidates();
  }
});

watch(
  () => props.disabled,
  (disabled) => {
    if (editorRef.value) editorRef.value.contentEditable = disabled ? 'false' : 'true';
  },
  { immediate: true },
);

defineExpose({
  setDoc,
  clear,
  appendRunMention,
  appendNodeMention,
  appendText,
  focus: () => editor.focusEditor(),
  trySubmit,
});
</script>

<style scoped lang="scss">
.ai-mention-wrap {
  position: relative;
  width: 100%;
}

.ai-mention-editor__placeholder {
  position: absolute;
  z-index: 1;
  top: 10px;
  left: 12px;
  right: 12px;
  color: #5a6b86;
  pointer-events: none;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.ai-mention-editor {
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
  min-height: 88px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #c5d8ec;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f8fafc;
  color: #0f172a;
  outline: none;

  &:focus {
    border-color: #0b6edc;
    box-shadow: 0 0 0 2px rgba(11, 110, 220, 0.18);
    background: #fff;
  }

  &.is-disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.mention-dropdown {
  position: absolute;
  z-index: 20;
  min-width: 260px;
  max-width: min(360px, calc(100vw - 24px));
  max-height: 280px;
  overflow: auto;
  background: #fff;
  border: 1px solid #d6e6f5;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.14);
  transform: translateY(calc(-100% - 6px));
}

.mention-dropdown__tabs {
  display: flex;
  gap: 4px;
  padding: 8px 8px 4px;
  border-bottom: 1px solid #e9f2fc;
  position: sticky;
  top: 0;
  background: #fff;
}

.mention-dropdown__tab {
  border: none;
  background: transparent;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  color: #5a6b86;

  &.is-active {
    background: rgba(11, 110, 220, 0.12);
    color: #0b6edc;
    font-weight: 600;
  }
}

.mention-dropdown__subtabs {
  display: flex;
  gap: 4px;
  padding: 4px 8px 6px;
}

.mention-dropdown__subtab {
  border: 1px solid #d6e6f5;
  background: #f8fafc;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  cursor: pointer;

  &.is-active {
    border-color: #0b6edc;
    color: #0b6edc;
  }
}

.mention-dropdown__option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 8px 12px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;

  &:hover,
  &.is-active {
    background: rgba(11, 110, 220, 0.08);
  }

  &.is-pinned {
    border-bottom: 1px dashed #dbe8f4;
  }
}

.mention-dropdown__pin {
  font-size: 10px;
  color: #f59e0b;
  margin-bottom: 2px;
}

.mention-dropdown__label {
  font-size: 13px;
  font-weight: 500;
  color: #0f172a;
}

.mention-dropdown__detail {
  font-size: 11px;
  color: #5a6b86;
  margin-top: 2px;
}

.mention-dropdown__hint {
  padding: 12px;
  font-size: 12px;
  color: #5a6b86;
}
</style>

<!-- chip 由 contenteditable 动态插入 DOM，需非 scoped 样式 -->
<style lang="scss">
@use '../styles/mentionChip.scss' as *;
</style>
