<template>
  <div :class="{ 'is-open': expanded }" class="script-ctx-help">
    <button
        :aria-expanded="expanded"
        class="script-ctx-help__toggle"
        type="button"
        @click="expanded = !expanded"
    >
      <span class="script-ctx-help__toggle-left">
        <span class="script-ctx-help__icon">?</span>
        <span class="script-ctx-help__title">ctx 内置 API 说明</span>
      </span>
      <span :class="{ 'is-open': expanded }" class="script-ctx-help__chevron" aria-hidden="true">›</span>
    </button>

    <div v-show="expanded" class="script-ctx-help__body">
      <div
          v-for="group in SCRIPT_CTX_API_GROUPS"
          :key="group.id"
          class="script-ctx-help__group"
      >
        <div class="script-ctx-help__group-title">{{ group.title }}</div>
        <div
            v-for="item in group.items"
            :key="item.name"
            class="script-ctx-help__item"
        >
          <div class="script-ctx-help__item-head">
            <code class="script-ctx-help__sig">{{ item.sig }}</code>
            <span :class="`is-${item.access}`" class="script-ctx-help__tag">{{ accessLabel(item.access) }}</span>
          </div>
          <p class="script-ctx-help__desc">{{ item.desc }}</p>
        </div>
      </div>

      <div class="script-ctx-help__examples">
        <div class="script-ctx-help__group-title">示例</div>
        <div
            v-for="(ex, idx) in examples"
            :key="idx"
            class="script-ctx-help__example"
        >
          <div class="script-ctx-help__example-title">{{ ex.title }}</div>
          <pre class="script-ctx-help__example-code">{{ ex.code }}</pre>
        </div>
      </div>

      <ul class="script-ctx-help__notes">
        <li v-for="(note, idx) in SCRIPT_CTX_NOTES" :key="idx">{{ note }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
/**
 * script 节点 ctx 宿主 API 帮助面板：分组列出内置方法、示例与沙箱说明。
 * 可折叠，样式与 Script 属性区青色主题一致。
 */
import { computed, ref } from 'vue'

import {
  getScriptCtxExamples,
  SCRIPT_CTX_API_GROUPS,
  SCRIPT_CTX_NOTES,
} from './scriptCtxApiReference'

const props = defineProps({
  /** 当前脚本语言，用于切换示例代码 */
  language: { type: String, default: 'javascript' },
  /** 初始是否展开 */
  defaultExpanded: { type: Boolean, default: false },
})

const expanded = ref(props.defaultExpanded)

const examples = computed(() => {
  const lang = props.language === 'python' ? 'python' : 'javascript'
  return getScriptCtxExamples(lang)
})

function accessLabel(access) {
  if (access === 'write') return '可写'
  if (access === 'read') return '只读'
  return '工具'
}
</script>

<style scoped lang="scss">
@use './scriptHelpPanelShared.scss';
</style>
