<!--
  AI API 助手侧栏：会话聊天、脚本/约束/测值/meta Diff、应用到工作台。
  Composer 含半自动|全自动开关；全自动时 SSE 结束后自动合并 Diff 进草稿。
-->
<template>
  <Teleport :disabled="teleportDisabled" :to="dockHostRef">
    <div v-if="open" class="api-ai-dock">
      <AiChatShell
          ref="shellRef"
          title-id="apiAiTitle"
          title="AI API 助手"
          :sessions="sessions"
          :active-session-id="activeSessionId"
          :is-draft-session="isDraftSession"
          :loading-sessions="loadingSessions"
          :display-messages="displayMessages"
          :has-more-older-messages="hasMoreOlderMessages"
          :loading-older-messages="loadingOlderMessages"
          :empty-hint="autopilotEnabled
            ? '询问接口结构，或描述补约束 / 测值 / 脚本需求；全自动下有建议将直接应用到工作台（仍须人手保存）。'
            : '询问接口结构，或描述补约束 / 测值 / 脚本需求；有修改建议时可勾选后应用到工作台。'"
          :design-error="designError"
          :designing="designing"
          :active-tool="activeTool"
          :model-groups="modelGroups"
          :selected-model-id="selectedModelId"
          :loading-models="loadingModels"
          :thinking-enabled="thinkingEnabled"
          :thinking-capable="thinkingCapable"
          :send-disabled="!selectedModelId || !composerText.trim()"
          :show-jump-to-bottom="!stickToBottom"
          select-popper-class="api-ai-select-popper"
          @close="close"
          @session-change="onSessionChange"
          @session-remove="onSessionRemove"
          @new-conversation="startNewConversation"
          @model-change="onModelChange"
          @thinking-change="onThinkingChange"
          @cancel="cancelDesign"
          @send="submit"
          @load-older="onLoadOlderMessages"
          @scroll="onListScroll"
          @jump-to-bottom="jumpToBottom"
      >
        <template #message="{ message: msg }">
          <AiChatMessageRow
              :key="(msg as ApiDesignMessageView).id"
              :message="msg as ApiDesignMessageView"
              :patch-pending="isMessagePatchPending(msg as ApiDesignMessageView)"
          >
            <p v-if="(msg as ApiDesignMessageView).role === 'system'" class="ai-chat-msg__system">
              {{ (msg as ApiDesignMessageView).content }}
            </p>

            <AiChatUserBubble
                v-else-if="(msg as ApiDesignMessageView).role === 'user'"
                :show-actions="canShowMessageActions(msg as ApiDesignMessageView)"
                @edit="onEditUserMessage((msg as ApiDesignMessageView).id)"
            >
              <p class="ai-chat-msg__text">{{ (msg as ApiDesignMessageView).content }}</p>
            </AiChatUserBubble>

            <AiChatAssistantMessage
                v-else
                :message="msg as ApiDesignMessageView"
                :streaming-message-id="streamingMessageId"
                :stream-text="streamText"
                :stream-thinking="streamThinking"
                :show-actions="canShowMessageActions(msg as ApiDesignMessageView)"
            >
              <template #actions>
                <button
                    class="ai-chat-msg__action"
                    type="button"
                    title="重新生成回复"
                    @click="onRegenerateAssistant((msg as ApiDesignMessageView).id)"
                >
                  重新生成
                </button>
              </template>
              <template #extra>
                <AiToolTracePanel
                    v-if="(msg as ApiDesignMessageView).toolTrace"
                    :tool-trace="(msg as ApiDesignMessageView).toolTrace!"
                />
                <div
                    v-if="shouldShowPatchDiffSection(msg as ApiDesignMessageView)"
                    class="api-ai-diff"
                >
                  <div
                      v-if="isMessagePatchPending(msg as ApiDesignMessageView) || (msg as ApiDesignMessageView).patchLoading"
                      class="api-ai-diff__loading"
                  >
                    正在加载变更详情…
                  </div>
                  <template v-else-if="hasVisiblePatchDiff(msg as ApiDesignMessageView)">
                    <div class="api-ai-diff__head">
                      <span class="api-ai-diff__title">
                        {{ (msg as ApiDesignMessageView).merged
                          ? '已应用变更'
                          : '建议变更' }}
                      </span>
                      <button
                          v-if="!(msg as ApiDesignMessageView).merged"
                          class="btn btn--ghost btn--sm"
                          type="button"
                          @click="acceptAllForMessage((msg as ApiDesignMessageView).id)"
                      >
                        全选
                      </button>
                    </div>
                    <ul class="api-ai-diff__list">
                      <li
                          v-for="item in ((msg as ApiDesignMessageView).diffItems ?? [])"
                          :key="item.id"
                          :class="{
                            'is-accepted': acceptedIdsFor((msg as ApiDesignMessageView).id).has(item.id),
                            'is-readonly': (msg as ApiDesignMessageView).merged,
                          }"
                          class="api-ai-diff__item"
                          @click="!(msg as ApiDesignMessageView).merged && toggleMessageAccepted((msg as ApiDesignMessageView).id, item.id)"
                      >
                        <span class="api-ai-diff__check">
                          {{ acceptedIdsFor((msg as ApiDesignMessageView).id).has(item.id) ? '☑' : '☐' }}
                        </span>
                        <span class="api-ai-diff__kind">{{ diffKindLabel(item.kind) }}</span>
                        <span class="api-ai-diff__label">{{ item.label }}</span>
                      </li>
                    </ul>
                    <button
                        v-if="!(msg as ApiDesignMessageView).merged"
                        :disabled="!canMergeMessage((msg as ApiDesignMessageView).id)"
                        class="btn btn--primary api-ai-diff__merge"
                        type="button"
                        @click="mergeMessagePatch((msg as ApiDesignMessageView).id)"
                    >
                      应用到工作台
                    </button>
                  </template>
                </div>
              </template>
            </AiChatAssistantMessage>
          </AiChatMessageRow>
        </template>

        <template #composer-prompt>
          <AiPromptTemplateStrip
              :disabled="designing"
              :items="promptTemplates"
              :loading="loadingTemplates"
              @select="applyTemplate"
          />
        </template>

        <template #composer-input>
          <el-input
              v-model="composerText"
              :disabled="designing || !selectedModelId"
              :rows="3"
              class="api-ai-panel__textarea"
              placeholder="描述问题或脚本需求，如：生成 HMAC 签名前置脚本"
              type="textarea"
              @keydown.ctrl.enter.prevent="submit"
              @keydown.meta.enter.prevent="submit"
          />
        </template>

        <template #composer-extra>
          <AiComposerToggle
              v-model="autopilotEnabled"
              on-label="全自动"
              off-label="半自动"
              title="半自动：Diff 勾选后应用到工作台；全自动：有建议时直接应用草稿（仍须人手保存接口库）"
          />
        </template>
      </AiChatShell>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, inject, ref, toRef, watch } from 'vue';

import { useAiChatPanelBindings } from '@/composables/ai/useAiChatPanelBindings';
import {
  hasVisiblePatchDiff,
  shouldShowPatchDiffSection,
} from '@/utils/ai/aiPatchMessageSelectors';
import { listApiDesignPromptTemplates } from '@/api/project/testApiAi';
import type { AiPromptTemplateItem } from '@/api/project/testFlowAi';
import AiChatAssistantMessage from '@/components/ai/AiChatAssistantMessage.vue';
import AiChatMessageRow from '@/components/ai/AiChatMessageRow.vue';
import AiChatShell from '@/components/ai/AiChatShell.vue';
import AiChatUserBubble from '@/components/ai/AiChatUserBubble.vue';
import AiComposerToggle from '@/components/ai/AiComposerToggle.vue';
import AiPromptTemplateStrip from '@/components/ai/AiPromptTemplateStrip.vue';
import AiToolTracePanel from '@/components/ai/AiToolTracePanel.vue';
import { useAiPromptTemplates } from '@/composables/ai/useAiPromptTemplates';

import { API_AI_DOCK_KEY } from '../constants/apiAiDock';
import { useApiAi, type ApplyDesignChanges } from '../composables/useApiAi';
import type { ApiDesignMessageView } from '../types/apiDesignAiTypes';
import {
  isApiAiAutopilotEnabled,
  setApiAiAutopilotEnabled,
} from '../utils/apiAiPreferences';

const props = defineProps<{
  open: boolean;
  testProjectId: string;
  testProjectApiId: string;
  preRequestScript?: string;
  postRequestScript?: string;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
  apply: [changes: ApplyDesignChanges];
}>();

const dockHostRef = inject(API_AI_DOCK_KEY, ref<HTMLElement | null>(null));
/** 无挂载点时禁用 Teleport，避免控制台警告 */
const teleportDisabled = computed(() => !dockHostRef.value);

const composerText = ref('');

/** 全自动：有 patch 时自动合并进工作台草稿；关=半自动（Diff 勾选后人手应用） */
const autopilotEnabled = ref(isApiAiAutopilotEnabled());
watch(autopilotEnabled, setApiAiAutopilotEnabled);

/** 快捷提示词：按项目加载 AI API 助手场景模板 */
const {
  items: promptTemplates,
  loading: loadingTemplates,
  load: loadTemplates,
} = useAiPromptTemplates(async () => {
  const projectId = props.testProjectId?.trim();
  if (!projectId) return [];
  return listApiDesignPromptTemplates(projectId);
});

const context = {
  testProjectId: toRef(props, 'testProjectId'),
  testProjectApiId: toRef(props, 'testProjectApiId'),
  preRequestScript: toRef(props, 'preRequestScript'),
  postRequestScript: toRef(props, 'postRequestScript'),
};

/** 将勾选后的 patch 变更上抛给详情页写入工作台 */
function handleApply(changes: ApplyDesignChanges) {
  emit('apply', changes);
}

const {
  modelGroups,
  selectedModelId,
  loadingModels,
  designing,
  designError,
  messages,
  sessions,
  activeSessionId,
  isDraftSession,
  loadingSessions,
  thinkingEnabled,
  thinkingCapable,
  streamText,
  streamThinking,
  activeTool,
  bootstrap,
  resetPanel,
  startNewConversation,
  switchSession,
  deleteSession,
  onModelChange,
  onThinkingChange,
  sendMessage,
  cancelDesign,
  regenerateAssistantResponse,
  startEditUserMessage,
  loadOlderMessages,
  hasMoreOlderMessages,
  loadingOlderMessages,
  toggleMessageAccepted,
  acceptAllForMessage,
  acceptedIdsFor,
  canMergeMessage,
  mergeMessagePatch,
  ensureMessagePatchLoaded,
} = useApiAi(context, handleApply);

/** 壳层绑定：流式占位、智能贴底（!stickToBottom 显示「最新」按钮）、会话 Tab */
const {
  shellRef,
  streamingMessageId,
  displayMessages,
  stickToBottom,
  onListScroll,
  onLoadOlderMessages,
  jumpToBottom,
  canShowMessageActions,
  isMessagePatchPending,
  onSessionChange,
  onSessionRemove,
  onRegenerateAssistant,
} = useAiChatPanelBindings({
  messages,
  designing,
  activeSessionId,
  loadOlderMessages,
  ensureMessagePatchLoaded,
  switchSession,
  onDraftSessionRemove: startNewConversation,
  deleteSession,
  regenerateAssistantResponse,
  onActiveSessionChange: () => {
    composerText.value = '';
  },
});

/** 面板打开时初始化模型与会话，并加载提示词模板 */
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      void bootstrap();
      void loadTemplates();
    }
  },
);

/** 切换当前接口时重置侧栏会话，避免串上下文 */
watch(
  () => props.testProjectApiId,
  () => {
    if (props.open) resetPanel();
  },
);

function close() {
  cancelDesign();
  emit('update:open', false);
}

/** Diff 项 kind 的简短中文标签 */
function diffKindLabel(kind: string): string {
  if (kind === 'updateScript') return '脚本';
  if (kind === 'clearScript') return '清空';
  if (kind === 'updateConstraints') return '约束';
  if (kind === 'setTestValue') return '测值';
  if (kind === 'clearTestValue') return '清空';
  if (kind === 'updateMeta') return '说明';
  if (kind === 'clearMeta') return '清空';
  return kind;
}

async function onEditUserMessage(messageId: string) {
  const text = await startEditUserMessage(messageId);
  if (text == null) return;
  composerText.value = text;
}

/** 将选中的提示词模板填入输入框 */
function applyTemplate(item: AiPromptTemplateItem) {
  const text = item.templateContent?.trim();
  if (!text) return;
  composerText.value = text.endsWith('\n') ? text : `${text}\n`;
}

/** 提交输入并清空 composer（Ctrl/Cmd+Enter 同效） */
function submit() {
  const text = composerText.value.trim();
  if (!text) return;
  void sendMessage(text).then(() => {
    composerText.value = '';
  });
}
</script>

<style lang="scss">
@use '@/components/ai/styles/aiChatDock.scss' as dock;

.api-ai-dock {
  @include dock.ai-chat-dock(300);
}

.api-ai-panel__textarea {
  .el-textarea__inner {
    border: 1px solid var(--pd-border-subtle);
    border-radius: 8px;
    box-shadow: none;
    font-size: 13px;
    line-height: 1.5;
    resize: none;

    &:hover {
      border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-divider));
    }

    &:focus {
      border-color: var(--pd-primary);
      box-shadow: 0 0 0 2px color-mix(in srgb, var(--pd-primary) 18%, transparent);
    }
  }
}

.api-ai-diff {
  margin-top: 8px;
  padding-top: 14px;
  border-top: 1px solid var(--pd-divider);
}

.api-ai-diff__loading {
  font-size: 13px;
  color: var(--pd-text-muted);
  padding: 8px 0;
}

.api-ai-diff__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.api-ai-diff__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--pd-text);
}

.api-ai-diff__list {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
}

.api-ai-diff__item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 4px;
  border-radius: 8px;
  border: 1px solid var(--pd-divider);
  background: var(--pd-surface);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;

  &:hover:not(.is-readonly) {
    border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-divider));
    background: var(--pd-primary-soft);
  }

  &.is-accepted:not(.is-readonly) {
    border-color: color-mix(in srgb, var(--pd-primary) 45%, var(--pd-divider));
    background: color-mix(in srgb, var(--pd-primary) 8%, var(--pd-surface));
  }

  &.is-readonly {
    cursor: default;
    opacity: 0.85;
  }
}

.api-ai-diff__check {
  flex-shrink: 0;
  width: 16px;
  color: var(--pd-primary);
}

.api-ai-diff__kind {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  color: var(--pd-primary);
  background: var(--pd-primary-soft);
}

.api-ai-diff__label {
  flex: 1;
  min-width: 0;
  line-height: 1.45;
  word-break: break-word;
}

.api-ai-diff__merge {
  width: 100%;
}
</style>
