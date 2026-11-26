<template>
  <div class="field">
    <label>fallbackOut（JSON）</label>
    <textarea :value="draft" rows="8" @input="onInput" @change="onChange" />
    <div v-if="parseError" class="field__error">{{ parseError }}</div>
    <div v-else-if="hint" class="field__hint">{{ hint }}</div>
  </div>
</template>

<script setup>
/**
 * 入口兜底出参（fallbackOut）的 JSON 编辑框。
 * HTTP 入口通常编完整包（status/headers/body）；子流入口通常只编业务对象。
 * 展示时把对象格式化成多行 JSON；输入过程保留草稿；失焦时解析：
 * 合法 JSON 对象写回；非法或不为对象则提示错误且不覆盖原值。
 */
import { ref, watch } from 'vue'

const props = defineProps({
  /** 当前兜底出参对象；空则按 {} 展示 */
  modelValue: { type: Object, default: null },
  /** 字段下方提示文案；空则不显示 */
  hint: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const draft = ref('')
const parseError = ref('')

function formatValue(v) {
  try {
    return JSON.stringify(v ?? {}, null, 2)
  } catch {
    return '{}'
  }
}

watch(
  () => props.modelValue,
  (v) => {
    draft.value = formatValue(v)
    parseError.value = ''
  },
  { immediate: true, deep: true },
)

function onInput(e) {
  draft.value = e.target.value
  if (parseError.value) parseError.value = ''
}

/**
 * 失焦解析文本框内容。
 * 合法 JSON 对象写回；数组 / 标量 / 非法 JSON 提示错误且不写回。
 */
function onChange(e) {
  const raw = e.target.value || ''
  try {
    const parsed = JSON.parse(raw || '{}')
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      parseError.value = 'fallbackOut 须为 JSON 对象'
      draft.value = raw
      return
    }
    parseError.value = ''
    emit('update:modelValue', parsed)
  } catch {
    parseError.value = 'JSON 无法解析，已保留原值'
    draft.value = raw
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

.field__error {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger, #dc2626);
}
</style>
