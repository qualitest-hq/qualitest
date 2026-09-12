<!--
  测试流画布 AI 助手侧栏。
  非模态停靠在画布右侧，不遮挡画布交互。
  业务差异：Mention 输入、Staging 变更摘要与定位、确认后保存开关。
-->
<template>
  <div v-if="store.aiDesignPanelOpen" class="ai-design-chat-dock">
    <AiChatShell
        ref="shellRef"
        title-id="aiDesignChatTitle"
        :sessions="sessions"
        :active-session-id="activeSessionId"
        :is-draft-session="isDraftSession"
        :loading-sessions="loadingSessions"
        :display-messages="displayMessages"
        :has-more-older-messages="hasMoreOlderMessages"
        :loading-older-messages="loadingOlderMessages"
        empty-hint="描述测试意图，AI 将给出流程建议；在画布上逐项确认变更。"
        :design-error="designError"
        :designing="designing"
        :active-tool="activeTool"
        :model-groups="modelGroups"
        :selected-model-id="selectedModelId"
        :loading-models="loadingModels"
        :thinking-enabled="thinkingEnabled"
        :thinking-capable="thinkingCapable"
        :send-disabled="!selectedModelId || composerEmpty"
        select-popper-class="ai-design-select-popper"
        @close="close"
        @session-change="onSessionChange"
        @session-remove="onSessionRemove"
        @new-conversation="startNewConversation"
        @model-change="handleModelChange"
        @thinking-change="onThinkingChange"
        @cancel="cancelDesign"
        @send="submitComposer"
        @load-older="onLoadOlderMessages"
        @scroll="onListScroll"
    >
      <template #message="{ message: msg }">
          <AiChatMessageRow
              :key="(msg as AiDesignMessageView).id"
              :message="msg as AiDesignMessageView"
              :patch-pending="isMessagePatchPending(msg as AiDesignMessageView)"
          >
            <div v-if="(msg as AiDesignMessageView).role === 'system'" class="ai-design-msg__system-wrap">
              <p class="ai-chat-msg__system">{{ (msg as AiDesignMessageView).content }}</p>
              <div v-if="(msg as AiDesignMessageView).actions?.length" class="ai-design-msg__system-actions">
                <button
                    v-for="(action, actionIndex) in (msg as AiDesignMessageView).actions"
                    :key="actionIndex"
                    class="btn btn--primary btn--sm"
                    type="button"
                    @click="onSystemAction((msg as AiDesignMessageView).id, action)"
                >
                  {{ action.label }}
                </button>
              </div>
            </div>

            <AiChatUserBubble
                v-else-if="(msg as AiDesignMessageView).role === 'user'"
                :show-actions="canShowMessageActions(msg as AiDesignMessageView)"
                @edit="onEditUserMessage((msg as AiDesignMessageView).id)"
            >
              <div v-if="(msg as AiDesignMessageView).composerDoc" class="ai-mention-message">
                <template v-for="(node, index) in (msg as AiDesignMessageView).composerDoc!.nodes" :key="index">
                  <span v-if="node.type === 'text'" class="ai-mention-message__text">{{ node.text }}</span>
                  <span
                      v-else
                      :class="['ai-mention-chip', 'ai-mention-chip--readonly', `ai-mention-chip--${node.mentionType}`]"
                  >
                    <span class="ai-mention-chip__at" aria-hidden="true">@</span>
                    <span class="ai-mention-chip__type">{{ MENTION_CATEGORY_TAGS[node.mentionType] }}</span>
                    <span class="ai-mention-chip__dot">·</span>
                    <span class="ai-mention-chip__label">{{ node.label }}</span>
                  </span>
                </template>
              </div>
              <p v-else class="ai-chat-msg__text">{{ (msg as AiDesignMessageView).content }}</p>
            </AiChatUserBubble>

            <AiChatAssistantMessage
                v-else
                :message="msg as AiDesignMessageView"
                :streaming-message-id="streamingMessageId"
                :stream-text="streamText"
                :stream-thinking="streamThinking"
                :show-actions="canShowMessageActions(msg as AiDesignMessageView)"
            >
              <template #actions>
                <button
                    class="ai-chat-msg__action"
                    type="button"
                    title="重新生成回复"
                    @click="onRegenerateAssistant((msg as AiDesignMessageView).id)"
                >
                  重新生成
                </button>
              </template>
              <template #extra>
                <!-- explainOnly：本轮无成功 submit_*，明示未产生 Staging -->
                <div
                    v-if="shouldShowExplainOnlyHint(msg as AiDesignMessageView, streamingMessageId)"
                    class="ai-design-explain-only"
                >
                  本轮未提交 Staging（未调用任何 submit_* 单元工具）。若要改画布请重新生成并明确要求提交修改。
                </div>
                <AiAssetProposalCard
                    v-if="shouldShowAssetProposals(msg as AiDesignMessageView)"
                    :message-id="(msg as AiDesignMessageView).id"
                    :proposals="(msg as AiDesignMessageView).assetProposals ?? []"
                    @update:proposals="(next) => onAssetProposalsUpdate((msg as AiDesignMessageView).id, next)"
                />
                <div
                    v-if="shouldShowStagingSummary(msg as AiDesignMessageView)"
                    class="ai-design-staging-extra"
                >
                  <ul
                      v-if="authValidationWarnings(msg as AiDesignMessageView).length"
                      class="ai-design-auth-warnings"
                  >
                    <li
                        v-for="(warn, wi) in authValidationWarnings(msg as AiDesignMessageView)"
                        :key="`${(msg as AiDesignMessageView).id}-w-${wi}`"
                    >
                      {{ warn }}
                    </li>
                  </ul>
                  <div
                      v-if="isMessagePatchPending(msg as AiDesignMessageView) || (msg as AiDesignMessageView).patchLoading"
                      class="ai-design-staging-extra__loading"
                  >
                    正在加载变更详情…
                  </div>
                  <AiStagingChangeSummary
                      v-else
                      :message-id="(msg as AiDesignMessageView).id"
                  />
                </div>
              </template>
            </AiChatAssistantMessage>
          </AiChatMessageRow>
      </template>

      <template #composer-prompt>
        <AiPromptTemplateStrip
            :disabled="designing"
            :items="promptTemplates"
            :loading="loadingPromptTemplates"
            @select="applyPromptTemplate"
        />
      </template>

      <template #composer-input>
        <AiMentionComposer
            ref="composerRef"
            :disabled="designing || !selectedModelId"
            @submit="onComposerSubmit"
            @change="onComposerChange"
        />
      </template>

      <template #composer-extra>
        <el-switch
            v-model="autopilotEnabled"
            active-text="全自动"
            inactive-text="半自动"
            class="ai-design-chat-panel__auto-save"
            inline-prompt
            size="small"
            title="半自动：Staging/素材须人审；全自动：素材直写、改图隐式落盘，模型可 run_test_flow"
        />
        <el-switch
            v-model="autoSaveAfterConfirm"
            active-text="确认后保存"
            class="ai-design-chat-panel__auto-save"
            inline-prompt
            size="small"
        />
        <el-switch
            v-model="blockWhenStagingPending"
            active-text="保存须先确认"
            class="ai-design-chat-panel__auto-save"
            inline-prompt
            size="small"
            title="开启后 pending 未清零不可「仅保存已确认」"
        />
      </template>
    </AiChatShell>
  </div>
</template>

<script setup lang="ts">
/**
 * 测试流 AI 助手面板。
 *
 * 壳层 UI 由 AiChatShell 承担；本文件处理：
 * Mention 输入与发送、画布变更摘要、素材库写入提案卡片、
 * Run 失败修复 / 节点添加入口预填、确认后自动保存开关。
 */
import { nextTick, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import { useAiChatPanelBindings } from '@/composables/ai/useAiChatPanelBindings';

import {
  listAiDesignPromptTemplates,
  type AiPromptTemplateItem,
} from '@/api/project/testFlowAi';
import { useAiPromptTemplates } from '@/composables/ai/useAiPromptTemplates';
import AiChatAssistantMessage from '@/components/ai/AiChatAssistantMessage.vue';
import AiChatMessageRow from '@/components/ai/AiChatMessageRow.vue';
import AiChatShell from '@/components/ai/AiChatShell.vue';
import AiChatUserBubble from '@/components/ai/AiChatUserBubble.vue';
import AiStagingChangeSummary from '../components/AiStagingChangeSummary.vue';
import AiAssetProposalCard from '../components/AiAssetProposalCard.vue';
import { useAiDesign } from '../composables/useAiDesign';
import { useFlowGraph } from '../composables/useFlowGraph';
import { isAutoSaveAfterConfirm, isAutopilotEnabled, isBlockWhenStagingPending, setAutoSaveAfterConfirm, setAutopilotEnabled, setBlockWhenStagingPending } from '../utils/aiDesignPreferences';
import type { ComposerDoc, ComposerSendPayload } from '../composables/mentionComposer';
import { isComposerDocEmpty, MENTION_CATEGORY_TAGS } from '../composables/mentionComposer';
import AiMentionComposer from './AiMentionComposer.vue';
import AiPromptTemplateStrip from '@/components/ai/AiPromptTemplateStrip.vue';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import type {
  AiDesignMessageView,
  AiDesignSystemAction,
  AssetUpsertProposalView,
} from '../types/aiDesignTypes';
import { shouldShowExplainOnlyHint, shouldShowStagingSummary } from '../utils/stagingMessage';
import { filterAuthRelatedWarnings } from '../utils/stagingAuthHints';

const store = useFlowCanvasStore();
const stagingStore = useAiStagingStore();
const { saveFlow } = useFlowGraph();
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
  startNewConversation,
  discardDraft,
  switchSession,
  deleteSession,
  onModelChange: handleModelChange,
  onThinkingChange,
  sendMessage,
  cancelDesign,
  regenerateAssistantResponse,
  startEditUserMessage,
  consumePendingRunContext,
  pendingNodeToken,
  consumePendingNodeContext,
  ensureMessagePatchLoaded,
  loadOlderMessages,
  hasMoreOlderMessages,
  loadingOlderMessages,
  clearSystemMessageActions,
  restorePendingStagingIfNeeded,
} = useAiDesign();

/** Mention 编辑器实例 */
const composerRef = ref<InstanceType<typeof AiMentionComposer> | null>(null);
/** Composer 是否为空，用于禁用发送按钮 */
const composerEmpty = ref(true);
/** 「合并后保存」开关，持久化到 localStorage */
const autoSaveAfterConfirm = ref(isAutoSaveAfterConfirm());
/** 全自动：请求注入 run_test_flow、改图隐式落盘、素材直写；关=半自动（Staging / 素材人审） */
const autopilotEnabled = ref(isAutopilotEnabled());
/** pending 保存强挡：隐藏「仅保存已确认」 */
const blockWhenStagingPending = ref(isBlockWhenStagingPending());
watch(autoSaveAfterConfirm, setAutoSaveAfterConfirm);
watch(autopilotEnabled, setAutopilotEnabled);
watch(blockWhenStagingPending, setBlockWhenStagingPending);

const {
  items: promptTemplates,
  loading: loadingPromptTemplates,
  load: loadPromptTemplates,
} = useAiPromptTemplates(async () => {
  const projectId = store.testProjectId?.trim();
  if (store.canvasMode === 'template') {
    return listAiDesignPromptTemplates(null);
  }
  if (!projectId) return [];
  return listAiDesignPromptTemplates(projectId);
});

/** Shell 绑定：流式占位、消息列表滚动、会话 Tab 操作 */
const {
  shellRef,
  streamingMessageId,
  displayMessages,
  onListScroll,
  onLoadOlderMessages,
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
  onDraftSessionRemove: discardDraft,
  deleteSession,
  regenerateAssistantResponse,
  onActiveSessionChange: () => {
    composerRef.value?.clear();
    composerEmpty.value = true;
  },
});

/**
 * 侧栏打开时：
 * 1. 加载模型与会话列表/当前会话消息
 * 2. 拉取造流快捷提示词芯片
 * 3. 若 Staging 已空但会话仍有未确认造流 patch，则回灌到画布供确认
 * 4. 消费 Run 失败修复、节点「添加到对话」预填到输入框
 */
watch(
  () => store.aiDesignPanelOpen,
  (open) => {
    if (open) {
      bootstrap().then(async () => {
        void loadPromptTemplates();
        await restorePendingStagingIfNeeded();
        applyPendingRunToComposer();
        applyPendingNodeToComposer();
      });
    }
  },
);

function applyPromptTemplate(item: AiPromptTemplateItem) {
  const text = item.templateContent?.trim();
  if (!text || !composerRef.value) return;
  composerRef.value.appendText(text.endsWith('\n') ? text : `${text}\n`);
  composerRef.value.focus();
  composerEmpty.value = false;
}

/** 节点右键「添加到对话」：pendingNodeToken 变化时写入 Composer */
watch(pendingNodeToken, () => {
  if (!store.aiDesignPanelOpen || pendingNodeToken.value === 0) return;
  void nextTick(() => applyPendingNodeToComposer());
});

function onComposerChange(doc: ComposerDoc) {
  composerEmpty.value = isComposerDocEmpty(doc);
}

/** 把用户消息内容回填到 Mention 编辑器，供修改后重发 */
async function onEditUserMessage(messageId: string) {
  const doc = await startEditUserMessage(messageId);
  if (!doc || !composerRef.value) return;
  await nextTick();
  composerRef.value.setDoc(doc);
  composerRef.value.focus();
  composerEmpty.value = isComposerDocEmpty(doc);
}

/** 助手消息是否带有可展示的素材库写入提案 */
function shouldShowAssetProposals(msg: AiDesignMessageView) {
  return msg.role === 'assistant' && Array.isArray(msg.assetProposals) && msg.assetProposals.length > 0;
}

/** 造流结果里鉴权类软提示：聊天区只展示一句摘要 */
function authValidationWarnings(msg: AiDesignMessageView): string[] {
  const all = filterAuthRelatedWarnings(msg.validation?.warnings)
  if (!all.length) return []
  if (all.length === 1) return all
  return [`${all[0]}（另有 ${all.length - 1} 项鉴权提示，见属性 Diff）`]
}

/** 卡片确认/拒绝或懒加载 fields 后，回写该消息上的提案列表 */
function onAssetProposalsUpdate(messageId: string, next: AssetUpsertProposalView[]) {
  const idx = messages.value.findIndex((m) => m.id === messageId);
  if (idx < 0) return;
  const copy = [...messages.value];
  copy[idx] = {
    ...copy[idx],
    assetProposals: next,
    assetProposalsPending: false,
  };
  messages.value = copy;
}

/** Run 失败修复入口：预插 run chip；若仍有未确认 Staging 则先 toast 提醒 */
function applyPendingRunToComposer() {
  const pending = consumePendingRunContext();
  if (!pending || !composerRef.value) return;
  if (stagingStore.pendingCount > 0) {
    ElMessage.warning(
      `画布尚有 ${stagingStore.pendingCount} 项未确认 AI 变更；建议先全部确认或取消后再修复，以免叠图`,
    );
  }
  nextTick(() => {
    composerRef.value?.focus();
    composerRef.value?.appendRunMention(pending.runId, 'failed');
    if (pending.prompt) {
      composerRef.value?.appendText(pending.prompt);
    }
    composerRef.value?.focus();
  });
}

/** 节点右键「添加到对话」：预插节点 chip */
function applyPendingNodeToComposer() {
  const nodeId = consumePendingNodeContext();
  if (!nodeId || !composerRef.value) return;
  const node = store.nodes.find((n) => n.id === nodeId);
  if (!node) return;
  composerRef.value.appendNodeMention({
    id: node.id,
    type: node.type,
    data: node.data as { name?: string } | undefined,
  });
  composerRef.value.focus();
}

/** 处理 system 消息操作按钮：saveFlow 调用画布保存 API 并更新消息文案 */
async function onSystemAction(messageId: string, action: AiDesignSystemAction) {
  if (action.type !== 'saveFlow') return;
  try {
    const ok = await saveFlow();
    if (ok) {
      const index = messages.value.findIndex((m) => m.id === messageId);
      if (index >= 0) {
        const updated = [...messages.value];
        updated[index] = {
          ...updated[index],
          content: '已保存到服务器',
        };
        messages.value = updated;
      }
      clearSystemMessageActions(messageId);
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  }
}

async function onComposerSubmit(payload: ComposerSendPayload) {
  if (designing.value || !selectedModelId.value) return;
  composerRef.value?.clear();
  composerEmpty.value = true;
  await sendMessage(payload);
}

function submitComposer() {
  composerRef.value?.trySubmit();
}

function close() {
  cancelDesign();
  store.closeAiDesignPanel();
}

</script>

<style lang="scss">
@use '../styles/mentionChip.scss' as *;
@use '@/components/ai/styles/aiChatDock.scss' as dock;

/* 侧栏外层 dock，z-index 100 低于部分画布浮层 */
.ai-design-chat-dock {
  @include dock.ai-chat-dock(100);
}

/* Composer 底栏「合并后保存」开关 */
.ai-design-chat-panel__auto-save {
  flex-shrink: 0;
  align-self: flex-end;
  margin-bottom: 2px;
}

/* 用户消息中的 @节点 / @Run 等 Mention 片段展示 */
.ai-mention-message {
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-mention-message__text {
  white-space: pre-wrap;
}

/* system 消息带操作按钮时的容器（如「合并后请保存」+ 保存按钮） */
.ai-design-msg__system-wrap {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--pd-divider);
  background: var(--pd-bg-sunken);
  max-width: 92%;
}

.ai-design-msg__system-wrap .ai-chat-msg__system {
  margin: 0;
  text-align: left;
}

.ai-design-msg__system-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.ai-design-staging-extra__loading {
  margin-top: 8px;
  font-size: 13px;
  color: var(--pd-text-secondary);
  padding: 8px 0;
}

.ai-design-auth-warnings {
  margin: 0 0 8px;
  padding: 8px 10px 8px 22px;
  border-radius: 6px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.45;
}

.ai-design-explain-only {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid color-mix(in srgb, #d97706 35%, var(--pd-border-subtle));
  background: color-mix(in srgb, #d97706 10%, var(--pd-surface-elevated));
  font-size: 12px;
  line-height: 1.45;
  color: #92400e;
}

/* el-select 下拉 teleported 到 body，需高于侧栏 */
.ai-design-select-popper.el-popper {
  z-index: 200 !important;
  border: 1px solid var(--pd-border-muted) !important;
  box-shadow: 0 6px 20px rgba(20, 60, 120, 0.14) !important;
}
</style>
