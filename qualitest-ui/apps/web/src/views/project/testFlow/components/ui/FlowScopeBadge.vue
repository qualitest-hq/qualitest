<template>
  <span :class="['flow-scope-badge', `is-${scope}`]">{{ displayLabel }}</span>
</template>

<script setup>
/** 参数库 scope 色块（flow / env / asset） */
import { computed } from 'vue'

import { SCOPE_LABELS } from '../../constants/flowConfig'

const props = defineProps({
  scope: { type: String, required: true },
  label: { type: String, default: '' },
})

const displayLabel = computed(() => props.label || SCOPE_LABELS[props.scope] || props.scope)
</script>

<style scoped lang="scss">
.flow-scope-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 46px;
  width: 46px;
  min-height: 20px;
  padding: 3px 4px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  text-align: center;
  box-sizing: border-box;
  align-self: center;

  &.is-flow {
    background: var(--scope-flow-bg);
    color: var(--scope-flow-fg);
  }

  &.is-env {
    background: var(--scope-env-bg);
    color: var(--scope-env-fg);
  }

  &.is-asset {
    background: var(--scope-asset-bg);
    color: var(--scope-asset-fg);
  }
}
</style>
