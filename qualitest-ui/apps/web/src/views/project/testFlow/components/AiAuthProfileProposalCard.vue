<template>
  <div v-if="proposals.length > 0" class="ai-auth-profile-proposals">
    <div class="ai-auth-profile-proposals__title">{{ sectionTitle }}</div>
    <p class="ai-auth-profile-proposals__hint">{{ sectionHint }}</p>
    <div
      v-for="item in proposals"
      :key="item.profileId"
      class="ai-auth-profile-proposal"
      :class="{
        'is-confirmed': item.status === 'confirmed',
        'is-rejected': item.status === 'rejected',
      }"
    >
      <div class="ai-auth-profile-proposal__head">
        <span class="ai-auth-profile-proposal__key">{{ displayName(item) }}</span>
        <span class="ai-auth-profile-proposal__action">{{ actionLabel(item.action) }}</span>
        <span v-if="item.status && item.status !== 'pending'" class="ai-auth-profile-proposal__status">
          {{ statusLabel(item.status) }}
        </span>
      </div>
      <ul v-if="(item.changedFields || []).length" class="ai-auth-profile-proposal__fields">
        <li v-for="name in item.changedFields" :key="name">
          <span class="ai-auth-profile-proposal__field-name">{{ name }}</span>
          <span class="ai-auth-profile-proposal__field-value">
            {{ formatVal(item.before?.[name]) }} → {{ formatVal(item.after?.[name]) }}
          </span>
        </li>
      </ul>
      <div class="ai-auth-profile-proposal__actions">
        <template v-if="(!item.status || item.status === 'pending') && !isTemplateCanvas">
          <button
            class="btn btn--primary btn--sm"
            type="button"
            :disabled="busyId === item.profileId || !canDecide"
            @click="onConfirm(item)"
          >
            {{ busyId === item.profileId && busyAction === 'confirm' ? '…' : '✓ 确认' }}
          </button>
          <button
            class="btn btn--ghost btn--sm"
            type="button"
            :disabled="busyId === item.profileId || !canDecide"
            @click="onReject(item)"
          >
            {{ busyId === item.profileId && busyAction === 'reject' ? '…' : '✕ 拒绝' }}
          </button>
        </template>
      </div>
      <p v-if="errorById[item.profileId]" class="ai-auth-profile-proposal__error">
        {{ errorById[item.profileId] }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 助手消息下的「多端配置 Profile」写入提案卡片。
 *
 * 展示本轮 AI 建议新建或更新的 Profile（鉴权托管头、pathPrefix、响应约定等变更前后对比），
 * 待确认时可 ✓ 写入项目 auth_config，或 ✕ 拒绝提案。
 * 模板画布仅只读展示；须已有服务端消息 id 与项目 id 才能操作。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  confirmAuthProfileUpsertProposal,
  rejectAuthProfileUpsertProposal,
} from '@/api/project/testFlowAi'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import type { AuthProfileUpsertProposalView } from '../types/aiDesignTypes'
import { isServerMessageId } from '@/utils/ai/serverMessageId'

const props = defineProps<{
  /** 助手消息 id；确认/拒绝接口需要服务端持久化消息 id */
  messageId: string
  /** 本条消息上的鉴权 Profile 提案列表 */
  proposals: AuthProfileUpsertProposalView[]
}>()

const emit = defineEmits<{
  /** 提案状态或 after 变更后回写父级消息 */
  (e: 'update:proposals', next: AuthProfileUpsertProposalView[]): void
  /** 确认写入成功：父级可据此刷新项目鉴权配置 */
  (e: 'confirmed'): void
}>()

const store = useFlowCanvasStore()
/** 模板画布：只读，不展示确认/拒绝按钮 */
const isTemplateCanvas = computed(() => store.canvasMode === 'template')
/** 是否允许发起确认/拒绝：须为服务端消息 id，且当前画布绑定了测试项目 */
const canDecide = computed(() => isServerMessageId(props.messageId) && !!store.testProjectId)
/** 当前正在请求的提案 profileId；空串表示空闲 */
const busyId = ref('')
/** 当前请求类型：确认或拒绝 */
const busyAction = ref<'confirm' | 'reject' | ''>('')
/** profileId → 本条操作失败文案 */
const errorById = reactive<Record<string, string>>({})

/** 区块标题：有待确认项时为「提案」，否则为已处理态「写入」 */
const sectionTitle = computed(() =>
  props.proposals.some((p) => !p.status || p.status === 'pending')
    ? '多端配置 Profile 提案'
    : '多端配置 Profile 写入',
)
/** 区块说明：待确认时提示写入效果；已处理时提示本轮已结束 */
const sectionHint = computed(() =>
  props.proposals.some((p) => !p.status || p.status === 'pending')
    ? '确认后写入项目 auth_config（含鉴权与响应约定），立即生效。'
    : '本轮多端 Profile 已处理。',
)

/** 展示名：优先 after/before 的 name，否则回退 profileId */
function displayName(item: AuthProfileUpsertProposalView) {
  const name = item.after?.name ?? item.before?.name
  return name != null && String(name).trim() ? String(name) : item.profileId
}

/** 动作文案：created→新建，updated→更新 */
function actionLabel(action?: string) {
  if (action === 'created') return '新建'
  if (action === 'updated') return '更新'
  return action || ''
}

/** 状态文案：confirmed→已写入，rejected→已拒绝 */
function statusLabel(status?: string) {
  if (status === 'confirmed') return '已写入'
  if (status === 'rejected') return '已拒绝'
  return status || ''
}

/** 字段值展示：空、数组、对象（如 responseConvention）、标量统一转可读字符串 */
function formatVal(v: unknown) {
  if (v == null) return '（空）'
  if (Array.isArray(v)) return v.join(', ')
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

/**
 * 确认或拒绝单条鉴权 Profile 提案。
 * 成功后更新本地列表状态（及确认后的 after），并向父级回写；确认成功再 toast 并 emit confirmed。
 */
async function onDecide(item: AuthProfileUpsertProposalView, action: 'confirm' | 'reject') {
  if (!canDecide.value || !store.testProjectId) return
  busyId.value = item.profileId
  busyAction.value = action
  errorById[item.profileId] = ''
  try {
    const payload = {
      testProjectId: String(store.testProjectId),
      aiChatMessageId: props.messageId,
      profileId: item.profileId,
    }
    const result =
      action === 'confirm'
        ? await confirmAuthProfileUpsertProposal(payload)
        : await rejectAuthProfileUpsertProposal(payload)
    if (!result.ok) {
      errorById[item.profileId] = (result.errors || []).join('；') || '操作失败'
      return
    }
    const next = props.proposals.map((p) =>
      p.profileId === item.profileId
        ? {
            ...p,
            status: result.status || (action === 'confirm' ? 'confirmed' : 'rejected'),
            after: result.after || p.after,
            profileId: result.profileId || p.profileId,
          }
        : p,
    )
    emit('update:proposals', next)
    if (action === 'confirm') {
      ElMessage.success('已写入项目鉴权')
      emit('confirmed')
    }
  } catch (e: unknown) {
    errorById[item.profileId] = e instanceof Error ? e.message : '请求失败'
  } finally {
    busyId.value = ''
    busyAction.value = ''
  }
}

/** 确认写入该 Profile 提案 */
function onConfirm(item: AuthProfileUpsertProposalView) {
  void onDecide(item, 'confirm')
}

/** 拒绝该 Profile 提案（不写项目鉴权） */
function onReject(item: AuthProfileUpsertProposalView) {
  void onDecide(item, 'reject')
}
</script>

<style scoped>
.ai-auth-profile-proposals {
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;
  background: var(--el-fill-color-blank, #fff);
}
.ai-auth-profile-proposals__title {
  font-weight: 600;
  font-size: 13px;
}
.ai-auth-profile-proposals__hint {
  margin: 4px 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.ai-auth-profile-proposal {
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);
}
.ai-auth-profile-proposal__head {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}
.ai-auth-profile-proposal__key {
  font-weight: 600;
}
.ai-auth-profile-proposal__action,
.ai-auth-profile-proposal__status {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.ai-auth-profile-proposal__fields {
  margin: 6px 0;
  padding-left: 16px;
  font-size: 12px;
}
.ai-auth-profile-proposal__field-name {
  font-weight: 500;
  margin-right: 6px;
}
.ai-auth-profile-proposal__actions {
  display: flex;
  gap: 8px;
}
.ai-auth-profile-proposal__error {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
}
.is-confirmed {
  opacity: 0.85;
}
.is-rejected {
  opacity: 0.65;
}
</style>
