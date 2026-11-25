<template>
  <div class="field">
    <label>fallbackOut（JSON）</label>
    <textarea :value="text" rows="8" @change="onChange" />
    <div v-if="hint" class="field__hint">{{ hint }}</div>
  </div>
</template>

<script setup>
/**
 * 入口兜底出参（fallbackOut）的 JSON 编辑框。
 * HTTP 入口通常编完整包（status/headers/body）；子流入口通常只编业务对象。
 * 展示时把对象格式化成多行 JSON；失焦（change）时解析回对象并写回。
 * 解析失败则丢弃本次输入，不覆盖原值。具体形状由父组件 hint 说明。
 */
import { computed } from 'vue'

const props = defineProps({
  /** 当前兜底出参对象；空则按 {} 展示 */
  modelValue: { type: Object, default: null },
  /** 字段下方提示文案；空则不显示 */
  hint: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

/** 供文本框展示的 JSON 字符串 */
const text = computed(() => {
  try {
    return JSON.stringify(props.modelValue ?? {}, null, 2)
  } catch {
    return '{}'
  }
})

/**
 * 失焦解析文本框内容。
 * 合法 JSON 对象写回；非法则忽略。
 */
function onChange(e) {
  try {
    emit('update:modelValue', JSON.parse(e.target.value || '{}'))
  } catch {
    /* 非法 JSON：不写回 */
  }
}
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;

.field {
  @include prop.flow-prop-field;
}

.field__hint {
  @include prop.flow-prop-field-hint;
}
</style>
