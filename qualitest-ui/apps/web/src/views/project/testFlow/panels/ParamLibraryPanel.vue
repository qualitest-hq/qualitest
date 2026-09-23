<template>
  <div class="param-lib">
    <input
        v-model="keyword"
        class="param-lib__search"
        placeholder="搜索 env / asset…"
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
              :title="`点击复制 ${item.placeholder}`"
              @click="copyParamText(item.placeholder, item.path)"
          >
            <FlowScopeBadge :scope="item.scope" />
            <div class="param-item__main">
              <code class="param-item__path">{{ item.path }}</code>
              <span v-if="item.remark" class="param-item__remark">{{ item.remark }}</span>
              <span
                  v-if="item.sample"
                  class="param-item__sample"
                  :title="item.sample"
              >{{ item.sample }}</span>
            </div>
            <div class="param-item__actions">
              <button
                  v-if="item.sample"
                  class="param-item__copy param-item__copy--value"
                  title="复制值"
                  type="button"
                  @click.stop="copyParamText(item.sample, `${item.path} 值`)"
              >
                <svg aria-hidden="true" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" viewBox="0 0 24 24">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="8" y1="13" x2="16" y2="13"/>
                  <line x1="8" y1="17" x2="14" y2="17"/>
                </svg>
              </button>
              <button
                  class="param-item__copy"
                  :title="`复制 ${item.placeholder}`"
                  type="button"
                  @click.stop="copyParamText(item.placeholder, item.path)"
              >
                <svg aria-hidden="true" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" viewBox="0 0 24 24">
                  <rect height="13" rx="2" width="13" x="9" y="9"/>
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                </svg>
              </button>
            </div>
          </div>
        </template>
        <div v-else-if="!keyword.trim()" class="param-lib__empty is-inline">
          {{ group.emptyHint }}
        </div>
      </template>
      <div v-if="!hasAnyVisible && keyword.trim()" class="param-lib__empty">
        无匹配参数<br/>可切换分类或清空搜索
      </div>
    </div>
  </div>
</template>

<script setup>
/** 左栏参数库：按 scope 分组展示 env/asset 配置态叶子，点击复制占位符 / 值 */
import { computed, onMounted, watch } from 'vue'

import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { useParamLibrary } from '../composables/useParamLibrary'
import FlowScopeBadge from '../components/ui/FlowScopeBadge.vue'

const store = useFlowCanvasStore()
const { scopeFilter, keyword, filteredItems, copyParamText, loadProjectVariables } = useParamLibrary()

const scopes = [
  { value: 'all', label: '全部' },
  { value: 'env', label: 'env' },
  { value: 'asset', label: 'asset' },
]

const groups = [
  { key: 'env', title: 'Env 环境变量', emptyHint: '暂无 env · 请先为当前场景绑定环境' },
  { key: 'asset', title: 'Asset 项目素材', emptyHint: '暂无 asset · 可在项目素材库中配置' },
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

/** 当前激活场景绑定的环境 id（用于触发重载） */
function activeScenarioEnvId() {
  const id = store.runConfig.activeScenarioId
  const scenarios = store.runConfig.scenarios || []
  const scenario = scenarios.find((s) => s.id === id) ?? scenarios[0]
  return scenario?.testProjectEnvId ?? ''
}

onMounted(() => {
  loadProjectVariables()
})

watch(
  () => [
    store.testProjectId,
    store.canvasMode,
    store.runConfig.activeScenarioId,
    activeScenarioEnvId(),
    store.canvasMode === 'template' ? store.templateParamContext : null,
  ],
  () => loadProjectVariables(),
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
  align-items: flex-start;
  gap: 8px;
  padding: 8px 8px;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  cursor: pointer;
  transition: border-color 0.12s, box-shadow 0.12s;

  &:hover {
    border-color: color-mix(in srgb, var(--pd-primary) 40%, var(--pd-border-subtle));
    box-shadow: var(--pd-shadow-card);
  }

  :deep(.flow-scope-badge) {
    align-self: flex-start;
    margin-top: 1px;
  }
}

.param-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding-top: 1px;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.35;
}

.param-item__remark {
  display: block;
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-item__sample {
  display: block;
  margin-top: 1px;
  font-family: "Cascadia Code", "Consolas", monospace;
  font-size: 11px;
  color: color-mix(in srgb, var(--pd-text-muted) 85%, var(--pd-text));
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-item__actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  align-self: center;
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
  line-height: 1;
  font: inherit;
  transition: background 0.12s, border-color 0.12s, color 0.12s, opacity 0.12s;

  svg {
    width: 14px;
    height: 14px;
    pointer-events: none;
    flex-shrink: 0;
  }

  &--value {
    opacity: 0.72;
  }
}

.param-item:hover .param-item__copy {
  border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-border-subtle));
  color: var(--pd-primary);
  opacity: 1;
}

.param-item__copy:hover {
  background: var(--pd-primary-soft);
  border-color: var(--pd-primary);
  color: var(--pd-primary);
  opacity: 1;
}
</style>
