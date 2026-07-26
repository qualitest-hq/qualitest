<template>
  <div class="ai-staging-actions">
    <button
        :class="btnClass('confirm')"
        :disabled="confirmDisabled"
        :title="confirmTitle"
        type="button"
        @click="onConfirm"
    >
      {{ confirmLabel }}
    </button>
    <button
        v-if="!blockedByDependency"
        :class="btnClass('retry')"
        :disabled="confirmDisabled"
        :title="retryTitle"
        type="button"
        @click="onRetry"
    >
      {{ retryLabel }}
    </button>
    <button
        :class="btnClass('reject')"
        :disabled="confirming"
        type="button"
        @click="onReject"
    >
      {{ rejectLabel }}
    </button>
  </div>
</template>

<script setup lang="ts">
/** Staging 确认 / 取消按钮（Chrome、Banner、FieldDiff 复用） */
import { computed } from 'vue'

import { useAiStagingConfirm } from '../composables/useAiStagingConfirm'
import { useStagingConfirmAvailability } from '../composables/useStagingConfirmAvailability'

const props = defineProps({
  unitId: { type: String, required: true },
  size: { type: String as () => 'compact' | 'full', default: 'compact' },
});

const { confirmUnit, retryUnit, rejectUnit } = useAiStagingConfirm();
const {
  unit,
  confirming,
  blockedByDependency,
  confirmBlockTitle,
  confirmDisabled,
} = useStagingConfirmAvailability(() => props.unitId);

const confirmLabel = computed(() => {
  if (confirming.value) return '…';
  return props.size === 'full' ? '✓ 确认' : '✓';
});

const retryLabel = computed(() => {
  if (confirming.value) return '…';
  return props.size === 'full' ? '↻ 重试' : '↻';
});

const confirmTitle = computed(() => {
  if (confirmBlockTitle.value) return confirmBlockTitle.value;
  return props.size === 'full' ? '确认本项变更' : '确认';
});

const retryTitle = computed(() => {
  if (confirmBlockTitle.value) return confirmBlockTitle.value;
  if (unit.value?.lastValidation && !unit.value.lastValidation.ok) {
    return '重新校验并确认本单元';
  }
  return '重新发起校验并确认（校验通过时也可重试）';
});

const rejectLabel = computed(() => (props.size === 'full' ? '✕ 取消' : '✕'));

function btnClass(variant: 'confirm' | 'retry' | 'reject') {
  const base = props.size === 'compact'
    ? 'ai-staging-actions__btn ai-staging-actions__btn--compact'
    : 'ai-staging-actions__btn ai-staging-actions__btn--full';
  const blocked = variant === 'confirm' && blockedByDependency.value
    ? ' ai-staging-actions__btn--blocked'
    : '';
  return `${base} ai-staging-actions__btn--${variant}${blocked}`;
}

async function onConfirm() {
  if (confirmDisabled.value) return;
  await confirmUnit(props.unitId);
}

async function onRetry() {
  if (confirmDisabled.value) return;
  await retryUnit(props.unitId);
}

function onReject() {
  rejectUnit(props.unitId);
}
</script>

<style scoped lang="scss">
@use '../styles/aiStaging.scss' as staging;

.ai-staging-actions {
  display: flex;
  gap: 4px;
}

.ai-staging-actions__btn--compact {
  @include staging.ai-staging-btn('confirm', true);

  &.ai-staging-actions__btn--reject {
    @include staging.ai-staging-btn('reject', true);
  }

  &.ai-staging-actions__btn--retry {
    @include staging.ai-staging-btn('retry', true);
  }
}

.ai-staging-actions__btn--full {
  @include staging.ai-staging-btn('confirm', false);
  flex: 1;
  min-width: 72px;
  height: 32px;
  font-size: 12px;
  font-weight: 600;

  &.ai-staging-actions__btn--reject {
    @include staging.ai-staging-btn('reject', false);
  }

  &.ai-staging-actions__btn--retry {
    @include staging.ai-staging-btn('retry', false);
  }
}

.ai-staging-actions__btn--compact.ai-staging-actions__btn--blocked:disabled,
.ai-staging-actions__btn--full.ai-staging-actions__btn--blocked:disabled {
  @include staging.ai-staging-btn-blocked;
}
</style>
