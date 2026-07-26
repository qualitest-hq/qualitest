<template>
  <div v-if="items.length" :class="blockClass">
    <ul class="flow-node__chip-list">
      <li
          v-for="(item, i) in visibleItems"
          :key="i"
          :class="chipClassFor(item)"
          :title="titleFor(item)"
      >
        {{ formatItem(item) }}
      </li>
      <li v-if="items.length > maxShow" :class="moreChipClass">
        还有 {{ items.length - maxShow }} {{ moreUnit }}…
      </li>
    </ul>
  </div>
</template>

<script setup>
/** 节点卡片内可折叠 chip 列表，超出 maxShow 时显示「还有 N 项」 */
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  maxShow: { type: Number, default: 5 },
  chipClass: { type: [String, Array, Object], default: 'flow-node__chip' },
  formatItem: { type: Function, required: true },
  chipClassFor: { type: Function, default: null },
  titleFor: { type: Function, default: null },
  moreUnit: { type: String, default: '项' },
  blockClass: { type: String, default: '' },
})

const visibleItems = computed(() => props.items.slice(0, props.maxShow))

const moreChipClass = computed(() => {
  const base = props.chipClass
  if (typeof base === 'string' && base) return [base, 'is-more']
  if (Array.isArray(base)) return [...base, 'is-more']
  return ['flow-node__chip', 'is-more']
})

function chipClassFor(item) {
  if (props.chipClassFor) return props.chipClassFor(item)
  return props.chipClass
}

function titleFor(item) {
  if (props.titleFor) return props.titleFor(item)
  return undefined
}
</script>

<style scoped lang="scss">
@use '../../styles/flowCanvasTokens.scss' as flow;

.flow-node__chip-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.flow-node__chip-block {
  margin-top: 6px;
}

:deep(.flow-node__chip) {
  display: block;
  width: 100%;
  box-sizing: border-box;
  padding: var(--node-chip-pad-y) var(--node-chip-pad-x);
  border-radius: var(--node-chip-radius);
  font-size: var(--node-font-mono);
  font-weight: 500;
  font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  @include flow.flow-node-chip-accent;

  &.is-more {
    background: #f8fafc;
    color: #64748b;
    border-color: var(--pd-divider);
    font-family: inherit;
    font-weight: 500;
    font-size: var(--node-font-body);
    font-style: normal;
    opacity: 1;
  }
}
</style>
