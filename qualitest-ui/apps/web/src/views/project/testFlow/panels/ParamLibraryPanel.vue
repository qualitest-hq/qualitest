<template>
  <div class="param-lib">
    <input
        v-model="keyword"
        class="param-lib__search"
        placeholder="搜索 flow / env / asset…"
        type="search"
    />
    <div class="param-lib__filters">
      <button
          v-for="s in scopes"
          :key="s.value"
          :class="{ 'is-active': scopeFilter === s.value }"
          class="param-lib__filter"
          type="button"
          @click="scopeFilter = s.value"
      >
        {{ s.label }}
      </button>
    </div>
    <div class="param-lib__list">
      <template v-for="group in groupedItems" :key="group.key">
        <div class="param-lib__group-title">{{ group.title }}</div>
        <template v-if="group.items.length">
          <div
              v-for="item in group.items"
              :key="item.path"
              class="param-item"
              @click="copyParamText(item.placeholder, item.path)"
          >
            <FlowScopeBadge :scope="item.scope" />
            <div class="param-item__main">
              <code class="param-item__path">{{ item.path }}</code>
              <span v-if="itemMeta(item)" class="param-item__meta">{{ itemMeta(item) }}</span>
            </div>
            <button
                class="param-item__copy"
                title="复制占位符"
                type="button"
                @click.stop="copyParamText(item.placeholder, item.path)"
            >
              <svg aria-hidden="true" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" viewBox="0 0 24 24">
                <rect height="13" rx="2" width="13" x="9" y="9"/>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
              </svg>
            </button>
          </div>
        </template>
        <div v-else-if="group.key === 'flow' && !keyword.trim()" class="param-lib__empty is-inline">
          暂无 flow 变量 · HTTP 响应提取或 Assign 节点写入
        </div>
      </template>
      <div v-if="!hasAnyVisible" class="param-lib__empty">
        无匹配参数<br/>可切换分类或清空搜索
      </div>
    </div>
    <div class="param-lib__foot">
      点击条目复制 <code>{{ placeholderHint }}</code>；条件分支左值可用不带花括号的 path
    </div>
  </div>
</template>

<script setup>
/** 左栏参数库：按 scope 分组展示 flow/env/asset 占位符，点击复制 */
import { computed, onMounted, watch } from 'vue'

import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useParamLibrary } from '../composables/useParamLibrary'
import FlowScopeBadge from '../components/ui/FlowScopeBadge.vue'

const store = useFlowCanvasStore()
const { scopeFilter, keyword, filteredItems, copyParamText, loadProjectVariables } = useParamLibrary()

const scopes = [
  { value: 'all', label: '全部' },
  { value: 'flow', label: 'flow' },
  { value: 'env', label: 'env' },
  { value: 'asset', label: 'asset' },
]

const placeholderHint = '{{占位符}}'

const groups = [
  { key: 'flow', title: 'Flow 运行时' },
  { key: 'env', title: 'Env 环境变量' },
  { key: 'asset', title: 'Asset 项目素材' },
]

const groupedItems = computed(() => {
  const filtered = filteredItems()
  return groups
    .filter((g) => scopeFilter.value === 'all' || scopeFilter.value === g.key)
    .map((g) => ({
      ...g,
      items: filtered.filter((i) => i.scope === g.key),
    }))
})

const hasAnyVisible = computed(() => groupedItems.value.some((g) => g.items.length > 0))

function itemMeta(item) {
  return [item.remark, item.sample ? `示例: ${item.sample}` : ''].filter(Boolean).join(' · ')
}

onMounted(() => {
  loadProjectVariables()
})

watch(
  () => store.testProjectId,
  () => loadProjectVariables(),
)

watch(
  () => store.nodes,
  () => { /* reactive refresh */ },
  { deep: true },
)
</script>

<style scoped lang="scss">
/* 搜索、筛选、分组列表与复制按钮 */
.param-lib {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  height: 100%;
  flex: 1;
}

.param-lib__search {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: var(--pd-radius-sm);
  background: var(--pd-surface-elevated);
  font-size: 14px;
  box-sizing: border-box;
  flex-shrink: 0;
  font-family: inherit;
  color: var(--pd-text);

  &:focus {
    outline: none;
    border-color: var(--pd-primary);
    box-shadow: 0 0 0 2px var(--pd-primary-soft);
  }
}

.param-lib__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
}

.param-lib__filter {
  height: 28px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  color: var(--pd-text-muted);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    border-color: var(--pd-primary);
    color: var(--pd-primary);
  }

  &.is-active {
    background: var(--pd-primary-soft);
    border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-border-subtle));
    color: var(--pd-primary);
  }
}

.param-lib__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.param-lib__group-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--pd-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-top: 2px;
  padding-bottom: 0;
  flex-shrink: 0;

  &:first-child {
    margin-top: 0;
  }
}

.param-lib__empty {
  padding: 10px 0;
  text-align: center;
  font-size: 13px;
  color: var(--pd-text-muted);
  line-height: 1.5;

  &.is-inline {
    padding: 2px 0 6px;
    text-align: left;
    font-size: 12px;
  }
}

.param-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  cursor: pointer;
  transition: border-color 0.12s, box-shadow 0.12s;

  &:hover {
    border-color: color-mix(in srgb, var(--pd-primary) 40%, var(--pd-border-subtle));
    box-shadow: var(--pd-shadow-card);
  }
}

.param-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.param-item__path {
  display: block;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  font-family: "Cascadia Code", "Consolas", monospace;
  font-size: 13px;
  font-weight: 500;
  color: var(--pd-text);
  word-break: break-all;
  line-height: 1.35;
}

.param-item__meta {
  display: block;
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.35;
}

.param-item__copy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  min-width: 28px;
  padding: 0;
  margin: 0;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-bg-sunken);
  color: var(--pd-text-muted);
  cursor: pointer;
  align-self: center;
  line-height: 1;
  font: inherit;
  transition: background 0.12s, border-color 0.12s, color 0.12s;

  svg {
    width: 14px;
    height: 14px;
    pointer-events: none;
    flex-shrink: 0;
  }
}

.param-item:hover .param-item__copy {
  border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-border-subtle));
  color: var(--pd-primary);
}

.param-item__copy:hover {
  background: var(--pd-primary-soft);
  border-color: var(--pd-primary);
  color: var(--pd-primary);
}

.param-lib__foot {
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.45;
  padding-top: 0;
  border-top: 1px dashed var(--pd-divider);
  flex-shrink: 0;

  code {
    padding: 0;
    background: transparent;
    font-family: "Cascadia Code", "Consolas", monospace;
    font-size: 11px;
    color: inherit;
  }
}
</style>
