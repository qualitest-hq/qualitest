<template>
    <div v-if="proposals.length > 0" class="ai-asset-proposals">
    <div class="ai-asset-proposals__title">{{ sectionTitle }}</div>
    <p class="ai-asset-proposals__hint">{{ sectionHint }}</p>
    <div
        v-for="item in proposals"
        :key="item.key"
        class="ai-asset-proposal"
        :class="{
          'is-confirmed': item.status === 'confirmed',
          'is-rejected': item.status === 'rejected',
        }"
    >
      <div class="ai-asset-proposal__head">
        <span class="ai-asset-proposal__key">{{ item.key }}</span>
        <span class="ai-asset-proposal__action">{{ actionLabel(item.action) }}</span>
        <span v-if="item.status && item.status !== 'pending'" class="ai-asset-proposal__status">
          {{ statusLabel(item.status) }}
        </span>
      </div>
      <p v-if="item.remark" class="ai-asset-proposal__remark">{{ item.remark }}</p>
      <ul class="ai-asset-proposal__fields">
        <li v-for="name in fieldNameList(item)" :key="name">
          <span class="ai-asset-proposal__field-name">{{ name }}</span>
          <span class="ai-asset-proposal__field-value">
            {{ revealMap[item.key] ? displayValue(item, name) : '••••' }}
          </span>
        </li>
      </ul>
      <div class="ai-asset-proposal__actions">
        <button
            class="btn btn--ghost btn--sm"
            type="button"
            :disabled="busyKey === item.key || item.status === 'confirmed' || item.status === 'rejected'"
            @click="toggleReveal(item)"
        >
          {{ revealMap[item.key] ? '隐藏' : '显示' }}
        </button>
        <template v-if="(!item.status || item.status === 'pending') && !isTemplateCanvas">
          <button
              class="btn btn--primary btn--sm"
              type="button"
              :disabled="busyKey === item.key || !canDecide"
              title="确认写入素材库"
              @click="onConfirm(item)"
          >
            {{ busyKey === item.key && busyAction === 'confirm' ? '…' : '✓ 确认' }}
          </button>
          <button
              class="btn btn--ghost btn--sm"
              type="button"
              :disabled="busyKey === item.key || !canDecide"
              title="拒绝此提案"
              @click="onReject(item)"
          >
            {{ busyKey === item.key && busyAction === 'reject' ? '…' : '✕ 拒绝' }}
          </button>
        </template>
      </div>
      <p v-if="errorByKey[item.key]" class="ai-asset-proposal__error">{{ errorByKey[item.key] }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 助手消息下的素材库写入卡片。
 * 半自动：展示 pending 提案，可 ✓ 确认写库或 ✕ 拒绝。
 * 全自动或已确认：标题改为「素材库写入」，状态「已写入」，无确认按钮。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  confirmAssetUpsertProposal,
  rejectAssetUpsertProposal,
} from '@/api/project/testFlowAi'
import { fetchMessageMetaCached } from '@/utils/ai/lazyMessageMeta'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import {
  parseAssetProposalsFromMeta,
  type AssetUpsertProposalView,
} from '../types/aiDesignTypes'
import { isServerMessageId } from '@/utils/ai/serverMessageId'

const props = defineProps<{
  /** 助手消息 id（确认/拒绝接口需要服务端雪花 id） */
  messageId: string
  /** 本条消息上的提案列表 */
  proposals: AssetUpsertProposalView[]
}>()

const emit = defineEmits<{
  /** 提案状态或 fields 变更后回写父级消息 */
  (e: 'update:proposals', next: AssetUpsertProposalView[]): void
}>()

const store = useFlowCanvasStore()
const isTemplateCanvas = computed(() => store.canvasMode === 'template')
/** key → 是否正在显示明文 */
const revealMap = reactive<Record<string, boolean>>({})
/** key → 操作失败文案 */
const errorByKey = reactive<Record<string, string>>({})
/** 当前正在请求的提案 key */
const busyKey = ref('')
/** 当前请求类型 */
const busyAction = ref<'confirm' | 'reject' | ''>('')

const proposals = computed(() => props.proposals ?? [])

const hasPending = computed(() =>
  proposals.value.some((p) => !p.status || p.status === 'pending'),
)

const allWritten = computed(() =>
  proposals.value.length > 0
    && proposals.value.every((p) => p.status === 'confirmed'),
)

const sectionTitle = computed(() =>
  allWritten.value && !hasPending.value ? '素材库写入' : '素材库提案',
)

const sectionHint = computed(() => {
  if (isTemplateCanvas.value) {
    return '模板画布不支持写入项目素材库；请忽略此类提案，或在真实项目画布中再确认。'
  }
  if (allWritten.value) {
    return '已写入项目素材库（全自动直写或人手确认）。'
  }
  if (!hasPending.value) {
    return '本轮素材提案均已处理完毕。'
  }
  return '确认后写入项目素材库；拒绝则丢弃。Run 前请确认所需条目。'
})

/** 已有项目 id 且消息已同步为服务端 id 时才允许确认/拒绝 */
const canDecide = computed(() => {
  const projectId = store.testProjectId?.trim()
  return Boolean(projectId && isServerMessageId(props.messageId))
})

function actionLabel(action?: string) {
  if (action === 'updated') return '更新'
  if (action === 'created') return '新建'
  return action || '提案'
}

function statusLabel(status?: string) {
  if (status === 'confirmed') return '已写入'
  if (status === 'rejected') return '已拒绝'
  return '待确认'
}

/** 展示用字段名：优先 fieldNames，否则用 fields 的键 */
function fieldNameList(item: AssetUpsertProposalView): string[] {
  if (item.fieldNames && item.fieldNames.length > 0) {
    return item.fieldNames
  }
  if (item.fields) {
    return Object.keys(item.fields)
  }
  return []
}

function displayValue(item: AssetUpsertProposalView, name: string): string {
  const raw = item.fields?.[name]
  if (raw === undefined || raw === null) {
    return '（需加载）'
  }
  return String(raw)
}

/**
 * 若提案缺少 fields（列表摘要），拉完整消息元数据补上明文后再展示。
 */
async function ensureFieldsLoaded(): Promise<AssetUpsertProposalView[]> {
  const needLoad = proposals.value.some((p) => !p.fields || Object.keys(p.fields).length === 0)
  if (!needLoad) {
    return proposals.value
  }
  if (!isServerMessageId(props.messageId)) {
    return proposals.value
  }
  const raw = await fetchMessageMetaCached(props.messageId)
  let meta: Record<string, unknown> = {}
  if (raw.resultMetaJson) {
    try {
      meta = JSON.parse(raw.resultMetaJson) as Record<string, unknown>
    } catch {
      meta = {}
    }
  }
  const full = parseAssetProposalsFromMeta(meta)
  if (full.length > 0) {
    emit('update:proposals', full)
    return full
  }
  return proposals.value
}

async function toggleReveal(item: AssetUpsertProposalView) {
  const next = !revealMap[item.key]
  if (next) {
    try {
      await ensureFieldsLoaded()
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '加载素材提案失败')
      return
    }
  }
  revealMap[item.key] = next
}

/** 本地更新某条提案的 status */
function patchLocalStatus(key: string, status: string) {
  const next = proposals.value.map((p) => (p.key === key ? { ...p, status } : p))
  emit('update:proposals', next)
}

/**
 * 确认（写入素材库）或拒绝（只改元数据状态）。
 * 须等消息 id 已同步到服务端后再调接口。
 */
async function onDecide(item: AssetUpsertProposalView, action: 'confirm' | 'reject') {
  const projectId = store.testProjectId?.trim()
  if (!projectId || !isServerMessageId(props.messageId)) {
    ElMessage.warning(action === 'confirm' ? '请稍候，消息同步完成后再确认' : '请稍候，消息同步完成后再操作')
    return
  }
  busyKey.value = item.key
  busyAction.value = action
  errorByKey[item.key] = ''
  const failLabel = action === 'confirm' ? '确认失败' : '拒绝失败'
  try {
    const payload = {
      testProjectId: projectId,
      aiChatMessageId: props.messageId,
      key: item.key,
    }
    const result = action === 'confirm'
      ? await confirmAssetUpsertProposal(payload)
      : await rejectAssetUpsertProposal(payload)
    if (!result.ok) {
      errorByKey[item.key] = (result.errors && result.errors[0]) || failLabel
      return
    }
    patchLocalStatus(item.key, action === 'confirm' ? 'confirmed' : 'rejected')
    if (action === 'confirm') {
      ElMessage.success(`已写入素材 ${item.key}`)
    }
  } catch (e: unknown) {
    errorByKey[item.key] = e instanceof Error ? e.message : failLabel
  } finally {
    busyKey.value = ''
    busyAction.value = ''
  }
}

function onConfirm(item: AssetUpsertProposalView) {
  return onDecide(item, 'confirm')
}

function onReject(item: AssetUpsertProposalView) {
  return onDecide(item, 'reject')
}
</script>

<style scoped lang="scss">
.ai-asset-proposals {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  background: var(--el-fill-color-blank, #fff);
}

.ai-asset-proposals__title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}

.ai-asset-proposals__hint {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.4;
}

.ai-asset-proposal {
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-extra-light, #f2f6fc);

  &:first-of-type {
    border-top: none;
    padding-top: 0;
  }

  &.is-confirmed {
    opacity: 0.85;
  }

  &.is-rejected {
    opacity: 0.65;
  }
}

.ai-asset-proposal__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ai-asset-proposal__key {
  font-weight: 600;
  font-size: 13px;
}

.ai-asset-proposal__action,
.ai-asset-proposal__status {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.ai-asset-proposal__remark {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}

.ai-asset-proposal__fields {
  list-style: none;
  margin: 0 0 8px;
  padding: 0;
  font-size: 12px;
}

.ai-asset-proposal__fields li {
  display: flex;
  gap: 8px;
  padding: 2px 0;
}

.ai-asset-proposal__field-name {
  min-width: 72px;
  color: var(--el-text-color-secondary, #909399);
}

.ai-asset-proposal__field-value {
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.ai-asset-proposal__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-asset-proposal__error {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
}
</style>
