<template>
  <div class="debug-flow-vars">
    <div class="debug-flow-vars-head">
      <span>Flow 变量（{{ entries.length }}）</span>
      <el-button link type="warning" @click="$emit('reset')">清空 flow</el-button>
    </div>
    <div v-if="!entries.length" class="debug-flow-vars-empty">暂无运行时变量；配置提取后发送请求即可写入</div>
    <div v-else class="debug-flow-vars-list">
      <div v-for="item in entries" :key="item.key" class="debug-flow-vars-row">
        <code class="debug-flow-vars-key">{{ item.placeholder }}</code>
        <span class="debug-flow-vars-val">{{ item.display }}</span>
        <el-button link type="primary" @click="copyPlaceholder(item.placeholder)">复制</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
/** 只读展示当前 flow 变量，支持复制 {{flow.*}} 占位符 */
import { computed } from 'vue'
import { getCurrentInstance } from 'vue'
import { copyTextSync } from '@/utils/clipboard'

const props = defineProps({
  flow: { type: Object, default: () => ({}) },
})

defineEmits(['reset'])

const { proxy } = getCurrentInstance()

const entries = computed(() => {
  const f = props.flow || {}
  return Object.keys(f)
    .sort()
    .map((key) => ({
      key,
      placeholder: `{{flow.${key}}}`,
      display: formatValue(f[key]),
    }))
})

function formatValue(v) {
  if (v == null) return 'null'
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

function copyPlaceholder(text) {
  if (copyTextSync(text)) {
    proxy?.$modal?.msgSuccess?.('已复制')
    return
  }
  proxy?.$modal?.msgError?.('复制失败')
}
</script>

<style scoped>
.debug-flow-vars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.debug-flow-vars-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.debug-flow-vars-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.debug-flow-vars-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.debug-flow-vars-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-size: 12px;
}

.debug-flow-vars-key {
  flex: 0 0 auto;
  color: var(--el-color-primary);
}

.debug-flow-vars-val {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-regular);
}
</style>
