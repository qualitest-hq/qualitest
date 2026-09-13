<!--
  AI Composer 底栏模式开关（pill 滑块）。
  关态：深字浅底；开态：白字主色底。文案随状态切换（如「半自动」↔「全自动」）。
-->
<template>
  <button
      :aria-checked="modelValue"
      :aria-label="modelValue ? onLabel : offLabel"
      :class="['ai-composer-toggle', { 'is-on': modelValue, 'is-disabled': disabled }]"
      :disabled="disabled"
      :title="title"
      role="switch"
      type="button"
      @click="toggle"
  >
    <span class="ai-composer-toggle__knob" aria-hidden="true" />
    <span class="ai-composer-toggle__label">{{ modelValue ? onLabel : offLabel }}</span>
  </button>
</template>

<script setup lang="ts">
/**
 * Composer 底栏布尔开关。
 * modelValue=true 显示 onLabel（全自动 / 思考开）；false 显示 offLabel（半自动 / 思考关）。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    /** 开态文案 */
    onLabel: string;
    /** 关态文案 */
    offLabel: string;
    disabled?: boolean;
    title?: string;
  }>(),
  {
    disabled: false,
    title: '',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

function toggle() {
  if (props.disabled) return;
  emit('update:modelValue', !props.modelValue);
}
</script>

<style scoped lang="scss">
.ai-composer-toggle {
  position: relative;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  box-sizing: border-box;
  min-width: 56px;
  height: 28px;
  padding: 0 10px 0 28px;
  border: 1px solid var(--pd-border-muted);
  border-radius: 14px;
  background: var(--pd-bg-sunken);
  color: var(--pd-text);
  font-family: inherit;
  cursor: pointer;
  user-select: none;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease,
    padding 0.15s ease;

  &.is-on {
    padding: 0 28px 0 10px;
    border-color: var(--pd-primary);
    background: var(--pd-primary);
    color: #fff;
  }

  &.is-disabled {
    opacity: 0.55;
    pointer-events: none;
  }
}

.ai-composer-toggle__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px color-mix(in srgb, var(--pd-text) 22%, transparent);
  transition: left 0.15s ease;
}

.ai-composer-toggle.is-on .ai-composer-toggle__knob {
  left: calc(100% - 23px);
}

.ai-composer-toggle__label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1;
  white-space: nowrap;
}
</style>
