<template>
  <el-drawer
    :model-value="visible"
    append-to-body
    direction="rtl"
    size="480px"
    :title="drawerTitle"
    @close="emit('update:visible', false)"
  >
    <div v-loading="loading" class="entry-doc-drawer">
      <template v-if="!loading && entries.length === 0">
        <p class="entry-doc-drawer__empty">本流尚无入口节点。打开画布拖入场景 / HTTP / 子流入口后即可在此查看说明。</p>
      </template>
      <template v-else-if="!loading">
        <div class="entry-doc-drawer__switch">
          <label class="entry-doc-drawer__label">入口</label>
          <el-select v-model="activeId" class="entry-doc-drawer__select" placeholder="选择入口">
            <el-option
              v-for="e in entries"
              :key="e.id"
              :label="`${e.name}（${nodeTypeLabel(e.type)}）`"
              :value="e.id"
            />
          </el-select>
        </div>

        <section class="entry-doc-drawer__doc">
          <h3 class="entry-doc-drawer__h">入口说明</h3>
          <EntryDocReadonly :source="activeEntry?.doc ?? ''" />
        </section>

        <section v-if="activeEntry" class="entry-doc-drawer__meta">
          <h3 class="entry-doc-drawer__h">请求配置（只读）</h3>
          <template v-if="activeEntry.type === 'http-entry'">
            <dl class="entry-doc-drawer__dl">
              <div><dt>Method</dt><dd>{{ String(activeEntry.data.method ?? '—') }}</dd></div>
              <div><dt>Path</dt><dd>{{ String(activeEntry.data.path ?? '—') }}</dd></div>
              <div><dt>envId</dt><dd>{{ String(activeEntry.data.envId ?? '—') }}</dd></div>
            </dl>
            <InputShapeSummary :input-shape="activeEntry.data.inputShape" />
          </template>
          <template v-else-if="activeEntry.type === 'subflow-entry'">
            <p class="entry-doc-drawer__line">
              兜底出参：{{ activeEntry.data.fallbackOut ? '已配置' : '未配置' }}
            </p>
            <div class="entry-doc-drawer__decl">
              <div class="entry-doc-drawer__decl-title">入参声明 inputDecl</div>
              <ul v-if="inputDeclRows.length" class="entry-doc-drawer__decl-list">
                <li v-for="(row, i) in inputDeclRows" :key="i">{{ row }}</li>
              </ul>
              <p v-else class="entry-doc-drawer__hint">（无）</p>
            </div>
          </template>
          <template v-else-if="activeEntry.type === 'scenario-entry'">
            <ul v-if="scenarioRows.length" class="entry-doc-drawer__scenarios">
              <li v-for="(sc, i) in scenarioRows" :key="i">
                <strong>{{ sc.name }}</strong>
                <span>envId={{ sc.envId || '—' }}</span>
                <span>inSeed 键：{{ sc.inSeedKeys.length ? sc.inSeedKeys.join(', ') : '（空）' }}</span>
              </li>
            </ul>
            <p v-else class="entry-doc-drawer__hint">本入口暂无场景</p>
          </template>
          <p v-else class="entry-doc-drawer__hint">协议入口：仅展示说明；请求配置以画布为准。</p>
        </section>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 测流列表页的「入口说明」右侧抽屉（只读）。
 * 打开后拉取该流 graphJson，列出全部入口；用户切换入口时主区展示该入口的 Markdown 说明，
 * 旁路按入口类型展示 method/path、入参形状摘要或场景名与环境 id（不展示 inSeed 明文）。
 */
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import { getTestFlow } from '@/api/project/testFlow';
import { nodeTypeLabel } from '@/utils/flow/nodeTypes';

import {
  extractEntryNodesFromGraph,
  summarizeInputDecl,
  summarizeScenarios,
  type EntryDocNode,
} from '../utils/entryDoc';
import EntryDocReadonly from './EntryDocReadonly.vue';
import InputShapeSummary from './InputShapeSummary.vue';

const props = defineProps<{
  /** 抽屉是否可见 */
  visible: boolean;
  /** 当前查看的测流 id；可见且 id 有效时触发加载 */
  testFlowId?: string | number | null;
  /** 测流名称，用于抽屉标题 */
  flowName?: string;
}>();

const emit = defineEmits<{
  'update:visible': [boolean];
}>();

/** 是否正在请求测流详情 */
const loading = ref(false);
/** 当前流上的入口列表 */
const entries = ref<EntryDocNode[]>([]);
/** 下拉框选中的入口节点 id */
const activeId = ref('');

/** 抽屉标题：有流名则带上流名 */
const drawerTitle = computed(() => {
  const name = String(props.flowName ?? '').trim();
  return name ? `入口说明 · ${name}` : '入口说明';
});

/** 当前选中的入口；未选中时为 null */
const activeEntry = computed(() => entries.value.find((e) => e.id === activeId.value) ?? null);

/** 场景入口下的场景摘要行；非场景入口为空数组 */
const scenarioRows = computed(() => {
  if (activeEntry.value?.type !== 'scenario-entry') return [];
  return summarizeScenarios(activeEntry.value.data.scenarios);
});

/** 子流入口 inputDecl 摘要行 */
const inputDeclRows = computed(() => {
  if (activeEntry.value?.type !== 'subflow-entry') return [];
  return summarizeInputDecl(activeEntry.value.data.inputDecl);
});

/** 抽屉打开或测流 id 变化时重新加载入口 */
watch(
  () => [props.visible, props.testFlowId] as const,
  async ([visible, id]) => {
    if (!visible || id == null || id === '') {
      return;
    }
    await loadEntries(id);
  },
);

/**
 * 按测流 id 拉详情，从图 JSON 抽出入口并默认选中第一个。
 */
async function loadEntries(testFlowId: string | number) {
  loading.value = true;
  entries.value = [];
  activeId.value = '';
  try {
    const res = await getTestFlow(testFlowId);
    const row = (res as { data?: Record<string, unknown> })?.data ?? (res as Record<string, unknown>);
    const graphJson = row?.graphJson ?? row?.graph_json;
    const list = extractEntryNodesFromGraph(graphJson);
    entries.value = list;
    activeId.value = list[0]?.id ?? '';
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载入口说明失败');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped lang="scss">
.entry-doc-drawer {
  min-height: 120px;
  padding: 0 4px 16px;
  color: var(--el-text-color-primary, #303133);
}

.entry-doc-drawer__empty,
.entry-doc-drawer__hint {
  margin: 0;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  line-height: 1.5;
}

.entry-doc-drawer__switch {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.entry-doc-drawer__label {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.entry-doc-drawer__select {
  width: 100%;
}

.entry-doc-drawer__h {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.entry-doc-drawer__doc,
.entry-doc-drawer__meta {
  margin-bottom: 20px;
}

.entry-doc-drawer__dl {
  margin: 0 0 12px;
  display: grid;
  gap: 6px;
  font-size: 13px;

  div {
    display: grid;
    grid-template-columns: 72px 1fr;
    gap: 8px;
  }

  dt {
    color: var(--el-text-color-secondary, #909399);
  }

  dd {
    margin: 0;
    word-break: break-all;
  }
}

.entry-doc-drawer__line {
  margin: 0 0 8px;
  font-size: 13px;
}

.entry-doc-drawer__decl {
  margin-top: 8px;
  font-size: 13px;
}

.entry-doc-drawer__decl-title {
  margin-bottom: 6px;
  font-weight: 500;
}

.entry-doc-drawer__decl-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.5;
  color: var(--el-text-color-regular, #606266);
}

.entry-doc-drawer__scenarios {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.6;

  li {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-bottom: 8px;
  }

  span {
    color: var(--el-text-color-secondary, #909399);
    font-size: 12px;
  }
}
</style>
