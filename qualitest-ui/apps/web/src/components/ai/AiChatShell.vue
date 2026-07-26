<script setup lang="ts">
/**
 * AI 助手侧栏壳层组件。
 *
 * 提供所有 AI 对话面板的公共 UI 骨架：
 * - 标题栏与关闭按钮
 * - 多会话 Tab（新建、切换、删除）
 * - 虚拟滚动消息列表（#message 插槽由业务面板填充）
 * - 空态、错误提示、工具调用提示
 * - 底部 Composer：模型选择、思考开关、取消/发送
 *
 * 插槽：
 * - #message：单条消息渲染
 * - #composer-prompt：输入区上方的提示条（如模板快捷选择）
 * - #composer-input：主输入控件（Mention 或 textarea）
 * - #composer-extra：Composer 底栏额外控件（如自动保存开关）
 */
import { nextTick, ref } from 'vue';

import type { AiChatSessionItem, AiModelVendorGroup } from '@/api/ai/chat';
import AiChatVirtualMessageList from '@/components/ai/AiChatVirtualMessageList.vue';
import { DRAFT_SESSION_ID } from '@/utils/ai/aiChatSession';

const props = withDefaults(
  defineProps<{
    titleId: string;
    title?: string;
    sessions: AiChatSessionItem[];
    activeSessionId: string;
    isDraftSession: boolean;
    loadingSessions: boolean;
    displayMessages: readonly unknown[];
    hasMoreOlderMessages?: boolean;
    loadingOlderMessages?: boolean;
    emptyHint: string;
    designError?: string;
    designing?: boolean;
    activeTool?: string | null;
    modelGroups: AiModelVendorGroup[];
    selectedModelId: string;
    loadingModels?: boolean;
    thinkingEnabled: boolean;
    thinkingCapable?: boolean;
    sendDisabled?: boolean;
    selectPopperClass?: string;
  }>(),
  {
    title: 'AI 助手',
    hasMoreOlderMessages: false,
    loadingOlderMessages: false,
    designError: '',
    designing: false,
    activeTool: null,
    loadingModels: false,
    thinkingCapable: false,
    sendDisabled: false,
    selectPopperClass: 'ai-chat-shell-select-popper',
  },
);

const emit = defineEmits<{
  close: [];
  'session-change': [sessionId: string];
  'session-remove': [sessionId: string];
  'new-conversation': [];
  'update:selectedModelId': [value: string];
  'update:thinkingEnabled': [value: boolean];
  'model-change': [modelId: string];
  'thinking-change': [enabled: boolean];
  cancel: [];
  send: [];
  'load-older': [];
  scroll: [];
}>();

const sessionScrollRef = ref<HTMLElement | null>(null);
/** 内部虚拟消息列表组件 ref */
const virtualListRef = ref<InstanceType<typeof AiChatVirtualMessageList> | null>(null);

function onSessionChange(sessionId: string) {
  emit('session-change', sessionId);
  void nextTick(() => scrollActiveSessionIntoView());
}

function onSessionRemove(sessionId: string) {
  emit('session-remove', sessionId);
  void nextTick(() => scrollActiveSessionIntoView());
}

function onModelChange(modelId: string | number) {
  const next = String(modelId);
  emit('update:selectedModelId', next);
  emit('model-change', next);
}

function onThinkingChange(enabled: boolean) {
  emit('update:thinkingEnabled', enabled);
  emit('thinking-change', enabled);
}

/** 横向会话 Tab 列表滚到当前选中项 */
function scrollActiveSessionIntoView() {
  const root = sessionScrollRef.value;
  if (!root) return;
  const active = root.querySelector('.ai-chat-shell__session-tab.is-active') as HTMLElement | null;
  active?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
}

/** 返回消息列表的可滚动 DOM，供贴底滚动和 patch 懒加载观察使用 */
function getMessageScrollElement(): HTMLElement | null {
  return virtualListRef.value?.getScrollElement() ?? null;
}

defineExpose({
  virtualListRef,
  sessionScrollRef,
  scrollActiveSessionIntoView,
  getMessageScrollElement,
});
</script>

<template>
  <aside class="ai-chat-shell" role="complementary" :aria-labelledby="titleId">
    <header class="ai-chat-shell__head">
      <div class="ai-chat-shell__head-main">
        <div class="ai-chat-shell__head-text">
          <h2 :id="titleId" class="ai-chat-shell__title">
            <svg
                aria-hidden="true"
                class="ai-chat-shell__title-icon"
                fill="none"
                viewBox="0 0 24 24"
            >
              <path
                  d="M21 12c0 4.418-4.03 8-9 8-1.01 0-1.98-.14-2.88-.4L3 21l1.4-4.2C3.56 15.55 3 13.85 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                  stroke="currentColor"
                  stroke-linejoin="round"
                  stroke-width="1.75"
              />
              <path
                  d="M8.5 11.5h7M8.5 14.5h4.5"
                  stroke="currentColor"
                  stroke-linecap="round"
                  stroke-width="1.75"
              />
            </svg>
            {{ title }}
          </h2>
        </div>
      </div>
      <button class="ai-chat-shell__close" type="button" title="关闭" @click="emit('close')">关闭</button>
    </header>

    <div class="ai-chat-shell__session-bar">
      <span class="ai-chat-shell__session-count">共 {{ sessions.length }} 个对话</span>
      <div ref="sessionScrollRef" class="ai-chat-shell__session-scroll">
        <div v-if="loadingSessions" class="ai-chat-shell__session-loading">加载中…</div>
        <template v-else>
          <button
              v-if="isDraftSession"
              :class="['ai-chat-shell__session-tab', 'is-active']"
              type="button"
              @click="onSessionChange(DRAFT_SESSION_ID)"
          >
            <span class="ai-chat-shell__session-tab-title">新对话</span>
            <span
                class="ai-chat-shell__session-tab-del"
                role="button"
                tabindex="-1"
                title="关闭草稿"
                @click.stop="onSessionRemove(DRAFT_SESSION_ID)"
            >×</span>
          </button>
          <button
              v-for="s in sessions"
              :key="s.aiChatSessionId"
              :class="[
                'ai-chat-shell__session-tab',
                { 'is-active': !isDraftSession && s.aiChatSessionId === activeSessionId },
              ]"
              type="button"
              @click="onSessionChange(s.aiChatSessionId)"
          >
            <span class="ai-chat-shell__session-tab-title" :title="s.sessionTitle || '未命名对话'">
              {{ s.sessionTitle || '未命名对话' }}
            </span>
            <span
                class="ai-chat-shell__session-tab-del"
                role="button"
                tabindex="-1"
                title="删除对话"
                @click.stop="onSessionRemove(s.aiChatSessionId)"
            >×</span>
          </button>
        </template>
      </div>
      <button
          class="ai-chat-shell__session-add"
          title="新建对话"
          type="button"
          @click="emit('new-conversation')"
      >+</button>
    </div>

    <AiChatVirtualMessageList
        v-if="displayMessages.length > 0"
        ref="virtualListRef"
        class="ai-chat-shell__messages"
        :messages="displayMessages"
        :has-more-older="hasMoreOlderMessages"
        :loading-older="loadingOlderMessages"
        @load-older="emit('load-older')"
        @scroll="emit('scroll')"
    >
      <template #default="{ message }">
        <slot name="message" :message="message" />
      </template>
    </AiChatVirtualMessageList>

    <div v-if="displayMessages.length === 0" class="ai-chat-shell__empty">
      {{ emptyHint }}
    </div>

    <div v-if="designError" class="ai-chat-shell__error" role="alert">
      {{ designError }}
    </div>

    <div v-if="designing && activeTool" class="ai-chat-shell__tool-hint">
      正在调用工具：{{ activeTool }}
    </div>

    <div class="ai-chat-shell__composer">
      <slot name="composer-prompt" />
      <slot name="composer-input" />
      <div class="ai-chat-shell__composer-footer">
        <div class="ai-chat-shell__model-wrap">
          <el-select
              :model-value="selectedModelId"
              :disabled="designing"
              :loading="loadingModels"
              class="ai-chat-shell__select"
              placeholder="选择模型"
              :popper-class="selectPopperClass"
              size="default"
              teleported
              @update:model-value="onModelChange"
          >
            <el-option-group
                v-for="group in modelGroups"
                :key="group.aiLlmVendorId"
                :label="group.vendorName"
            >
              <el-option
                  v-for="m in group.models"
                  :key="m.aiLlmModelId"
                  :label="m.modelName"
                  :value="String(m.aiLlmModelId)"
              />
            </el-option-group>
          </el-select>
        </div>
        <el-switch
            :model-value="thinkingEnabled"
            :disabled="!thinkingCapable || designing"
            active-text="思考"
            class="ai-chat-shell__thinking"
            inline-prompt
            size="small"
            @update:model-value="onThinkingChange"
        />
        <slot name="composer-extra" />
        <div class="ai-chat-shell__composer-actions">
          <button
              v-if="designing"
              class="btn btn--ghost ai-chat-shell__cancel"
              type="button"
              @click="emit('cancel')"
          >
            取消
          </button>
          <button
              :disabled="designing || sendDisabled"
              class="btn btn--primary ai-chat-shell__send"
              type="button"
              @click="emit('send')"
          >
            {{ designing ? '生成中…' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style lang="scss">
/* ========== 侧栏整体布局 ========== */
.ai-chat-shell {
  pointer-events: auto;
  width: var(--ai-chat-panel-width, min(560px, 42vw));
  max-width: 100vw;
  height: 100%;
  background: var(--pd-surface);
  border-left: 1px solid var(--pd-border-muted);
  box-shadow: -8px 0 32px rgba(20, 60, 120, 0.12);
  display: flex;
  flex-direction: column;
}

/* ========== 标题栏 ========== */
.ai-chat-shell__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: var(--header-h, 48px);
  box-sizing: border-box;
  padding: 0 14px;
  border-bottom: 1px solid var(--pd-divider);
  background: var(--pd-gradient-panel-head);
  flex-shrink: 0;
}

.ai-chat-shell__head-main {
  display: flex;
  align-items: center;
  min-width: 0;
}

.ai-chat-shell__head-text {
  min-width: 0;
}

.ai-chat-shell__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}

.ai-chat-shell__title-icon {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
  color: var(--pd-primary);
}

.ai-chat-shell__close {
  flex-shrink: 0;
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: #fff;
  color: var(--pd-text-muted);
  font-size: 11px;
  cursor: pointer;

  &:hover {
    color: var(--pd-text);
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }
}

/* ========== 会话 Tab 栏 ========== */
.ai-chat-shell__session-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px 12px;
  min-width: 0;
}

.ai-chat-shell__session-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--pd-text-muted);
  white-space: nowrap;
}

.ai-chat-shell__session-scroll {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 2px 0;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: var(--pd-border-muted) transparent;

  &::-webkit-scrollbar {
    height: 4px;
  }

  &::-webkit-scrollbar-thumb {
    border-radius: 4px;
    background: var(--pd-border-muted);
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

.ai-chat-shell__session-loading {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--pd-text-muted);
}

.ai-chat-shell__session-tab {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 148px;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  font-size: 12px;
  color: var(--pd-text-muted);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease;

  &:hover:not(.is-active) {
    border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-border-subtle));
    color: var(--pd-text);
    background: #fff;
  }

  &.is-active {
    background: var(--pd-primary);
    border-color: var(--pd-primary);
    color: #fff;

    .ai-chat-shell__session-tab-del {
      opacity: 0.85;

      &:hover {
        opacity: 1;
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }
}

.ai-chat-shell__session-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.ai-chat-shell__session-tab-del {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-right: -4px;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
  opacity: 0.55;
  transition: opacity 0.15s ease, background 0.15s ease;

  &:hover {
    opacity: 1;
    background: color-mix(in srgb, var(--pd-text) 8%, transparent);
  }
}

.ai-chat-shell__session-add {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--pd-primary);
  color: #fff;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: #095ec0;
  }
}

/* ========== 消息列表 / 空态 / 错误 / 工具提示 ========== */
.ai-chat-shell__messages {
  flex: 1;
  padding: 12px 14px;
  min-height: 0;
}

.ai-chat-shell__messages.ai-chat-virtual-list {
  overflow-y: auto;
}

.ai-chat-shell__empty {
  flex: 1;
  padding: 24px 8px;
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
  color: var(--pd-text-muted);
}

.ai-chat-shell__error {
  flex-shrink: 0;
  margin: 0 14px 8px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.45;
}

.ai-chat-shell__tool-hint {
  flex-shrink: 0;
  margin: 0 14px 6px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--pd-primary);
  background: var(--pd-primary-soft);
  border: 1px solid color-mix(in srgb, var(--pd-primary) 20%, transparent);
}

/* ========== 底部 Composer ========== */
.ai-chat-shell__composer {
  flex-shrink: 0;
  padding: 12px 14px;
  border-top: 1px solid var(--pd-divider);
  background: var(--pd-surface-elevated);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-chat-shell__composer-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.ai-chat-shell__thinking {
  flex-shrink: 0;
  align-self: flex-end;
  margin-bottom: 2px;
}

.ai-chat-shell__model-wrap {
  flex: 1;
  min-width: 0;
}

.ai-chat-shell__select {
  width: 100%;
  max-width: 280px;

  .el-select__wrapper {
    border: 1px solid var(--pd-border-subtle);
    border-radius: 8px;
    box-shadow: none;
    background: var(--pd-surface-elevated);
    font-size: 13px;
    min-height: 34px;

    &:hover {
      border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-border-subtle));
    }

    &.is-focused {
      border-color: var(--pd-primary);
      box-shadow: 0 0 0 2px color-mix(in srgb, var(--pd-primary) 18%, transparent);
    }
  }
}

.ai-chat-shell__composer-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ai-chat-shell__cancel {
  min-width: 64px;
}

.ai-chat-shell__send {
  min-width: 88px;
  height: 34px;
  font-weight: 600;
}

/*
 * 消息行公共样式（#message 插槽根节点由 AiChatMessageRow 提供 class）
 * user / assistant / system 三种对齐方式，以及用户气泡、系统提示、操作按钮
 */
.ai-chat-msg {
  display: flex;
  margin-bottom: 12px;
}

.ai-chat-msg--user {
  justify-content: flex-end;
}

.ai-chat-msg--assistant {
  justify-content: flex-start;
}

.ai-chat-msg--system {
  justify-content: center;
}

.ai-chat-msg__bubble--user {
  max-width: 96%;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.5;
  background: var(--pd-primary-soft);
  border: 1px solid color-mix(in srgb, var(--pd-primary) 22%, transparent);
}

.ai-chat-msg__text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-chat-msg__system {
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--pd-text-muted);
  background: var(--pd-bg-sunken);
  border: 1px dashed var(--pd-divider);
  max-width: 92%;
  text-align: center;
}

.ai-chat-msg__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--pd-divider);
}

.ai-chat-msg__action {
  padding: 2px 8px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--pd-text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;

  &:hover {
    color: var(--pd-primary);
    background: var(--pd-primary-soft);
  }
}
</style>
