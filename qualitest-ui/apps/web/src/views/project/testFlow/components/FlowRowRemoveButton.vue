<template>
  <button
      :aria-label="title"
      :disabled="disabled"
      :title="title"
      class="flow-row-remove"
      type="button"
      @click="$emit('click', $event)"
  >
    <svg
        aria-hidden="true"
        class="flow-row-remove__icon"
        fill="none"
        viewBox="0 0 16 16"
        xmlns="http://www.w3.org/2000/svg"
    >
      <path
          d="M3 3l10 10M13 3L3 13"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-width="1.5"
      />
    </svg>
  </button>
</template>

<script setup>
/**
 * 画布侧栏 / 键值行共用的删除按钮：细线 X，默认弱化，悬停偏红。
 */
defineProps({
  title: {
    type: String,
    default: '删除',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
});

defineEmits(['click']);
</script>

<style scoped lang="scss">
.flow-row-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 28px;
  min-width: 28px;
  height: 28px;
  margin: 0;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--pd-text-muted, #94a3b8);
  cursor: pointer;
  box-sizing: border-box;
  transition: color 0.15s, background 0.15s, border-color 0.15s;

  &:hover:not(:disabled) {
    color: #dc2626;
    background: #fef2f2;
    border-color: color-mix(in srgb, #ef4444 35%, transparent);
  }

  &:focus-visible {
    outline: 2px solid var(--pd-primary, #3b82f6);
    outline-offset: 1px;
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.flow-row-remove__icon {
  width: 14px;
  height: 14px;
  display: block;
}
</style>
