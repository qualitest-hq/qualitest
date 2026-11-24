<template>
  <div class="run-config">
    <div class="run-config__toolbar">
      <button
          v-if="!isScenarioRunActive"
          :disabled="!canRunFlow || isTemplateCanvas || !selectedEntry"
          :title="runButtonTitle"
          class="btn btn--primary run-config__scenario-run-btn"
          type="button"
          @click="onRunScenario"
      >
        ▶ {{ runButtonLabel }}
      </button>
      <button
          v-else
          :disabled="!canRunFlow"
          :title="canRunFlow ? '停止当前跑流' : '当前账号无执行权限，无法停止'"
          class="btn btn--primary run-config__scenario-run-btn is-scenario-run-stop"
          type="button"
          @click="abortScenarioRun"
      >
        ■ 停止
      </button>
    </div>

    <div class="run-config__scenarios">
      <div class="run-config__scenarios-title">入口（跑前选择）</div>
      <div v-if="!entryNodes.length" class="run-config__empty">
        画布上还没有入口。请从节点库拖入场景 / HTTP / 子流入口。
      </div>
      <label
          v-for="entry in entryNodes"
          :key="entry.id"
          :class="{ 'is-active': entry.id === selectedEntryId }"
          class="run-config-scenario"
          @click.prevent="selectEntry(entry.id)"
      >
        <input
            :checked="entry.id === selectedEntryId"
            class="run-config-scenario__radio"
            name="flow-run-entry"
            type="radio"
            @change="selectEntry(entry.id)"
        />
        <div class="run-config-scenario__body">
          <div class="run-config-scenario__name">{{ entryLabel(entry) }}</div>
          <div class="run-config-scenario__meta">{{ entryMeta(entry) }}</div>
        </div>
      </label>
    </div>

    <div v-if="selectedEntry" class="run-config__entry-doc">
      <div class="run-config__scenarios-title">当前入口说明</div>
      <EntryDocReadonly
          class="run-config__entry-doc-body"
          :source="selectedEntryDoc"
          empty-text="未写说明（可在入口节点属性中编辑）"
      />
    </div>

    <div v-if="selectedEntry?.type === 'scenario-entry'" class="run-config__scenarios">
      <div class="run-config__scenarios-title">场景（选中 = 下次运行）</div>
      <div class="run-config__hint">编辑场景名称 / 环境 / inSeed：点入口节点后在右侧属性与场景面板改</div>
      <label
          v-for="sc in scenarios"
          :key="sc.id"
          :class="scenarioCardClasses(sc.id)"
          class="run-config-scenario"
          @click.prevent="selectScenario(sc.id)"
      >
        <input
            :checked="sc.id === activeScenarioId"
            class="run-config-scenario__radio"
            name="flow-scenario"
            type="radio"
            @change="selectScenario(sc.id)"
        />
        <div class="run-config-scenario__body">
          <div class="run-config-scenario__name">{{ sc.name }}</div>
          <div class="run-config-scenario__meta">{{ scenarioCardMeta(sc) }}</div>
          <AiStagingScenarioBanner
              v-if="stagingUnitIdForScenario(sc.id)"
              :unit-id="stagingUnitIdForScenario(sc.id)"
          />
        </div>
      </label>
    </div>
  </div>
</template>

<script setup>
/**
 * 左栏「跑前入口」面板。
 * 列出画布上的平台入口供单选；选中后跟读该入口的 Markdown 说明。
 * 选中场景入口时再列出其场景供单选，点运行按所选入口（及场景）触发真实跑流；跑流中按钮变为停止。
 * 运行按钮受执行权限、模板画布、是否已选入口限制；场景增删与 inSeed 编辑在右侧面板完成。
 */
import { computed, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';

import { PLATFORM_ENTRY_TYPES } from '@/utils/flow/nodeTypes';

import AiStagingScenarioBanner from '../components/AiStagingScenarioBanner.vue';
import EntryDocReadonly from '../components/EntryDocReadonly.vue';
import {
  STAGING_ADD_COLOR,
  STAGING_DELETE_COLOR,
  STAGING_UPDATE_COLOR,
} from '../constants/stagingTheme';
import { useFlowScenarioRun } from '../composables/useFlowScenarioRun';
import { useRunConfig } from '../composables/useRunConfig';
import { useFlowCanvasPermissions } from '../composables/useFlowCanvasPermissions';
import { useAiStagingStore } from '../stores/aiStagingStore';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

/** 画布点击运行时可选的入口类型：场景入口、HTTP 入口、子流入口（协议入口不在此列） */
const ENTRY_TYPES = new Set(PLATFORM_ENTRY_TYPES);

const { canRunFlow, isTemplateCanvas } = useFlowCanvasPermissions();
const { isScenarioRunActive, runActiveScenario, abortScenarioRun } = useFlowScenarioRun();

const store = useFlowCanvasStore();
const stagingStore = useAiStagingStore();
const {
  loadProjectEnvs,
  activeScenarioName,
  scenarioCardMeta,
  selectScenario,
} = useRunConfig();

/** 画布上所有可选的入口节点 */
const entryNodes = computed(() =>
  store.nodes.filter((n) => n?.type && ENTRY_TYPES.has(n.type)),
);

/** 当前选中的入口节点 id */
const selectedEntryId = computed(() => store.selectedRunEntryNodeId);
/** 当前选中的入口节点；未选或已被删除时为 null */
const selectedEntry = computed(() =>
  entryNodes.value.find((n) => n.id === selectedEntryId.value) || null,
);

/** 当前选中入口的说明原文，用于跑前栏只读跟读 */
const selectedEntryDoc = computed(() => String(selectedEntry.value?.data?.doc ?? ''));

/** 当前入口下的场景列表（选中场景入口时展示） */
const scenarios = computed(() => store.runConfig.scenarios);
/** 当前选中的场景 id（下次运行使用） */
const activeScenarioId = computed(() => store.runConfig.activeScenarioId);

/** 运行按钮文案：场景入口显示当前场景名，其他入口显示入口名称 */
const runButtonLabel = computed(() => {
  if (selectedEntry.value?.type === 'scenario-entry') {
    return activeScenarioName.value || '运行场景';
  }
  return entryLabel(selectedEntry.value) || '运行';
});

/** 运行按钮悬浮提示：按不可运行原因给出说明，可运行时说明将运行的场景或入口 */
const runButtonTitle = computed(() => {
  if (isTemplateCanvas.value) return '模板画布不支持运行';
  if (!canRunFlow.value) return '当前账号无执行权限，无法运行';
  if (!selectedEntry.value) return '请先选择入口';
  if (selectedEntry.value.type === 'scenario-entry') {
    return `运行场景「${activeScenarioName.value}」`;
  }
  return `从入口「${entryLabel(selectedEntry.value)}」运行`;
});

/** 入口展示名：优先节点名称；HTTP 入口回退为「方法 路径」；其余按入口类型给默认名 */
function entryLabel(entry) {
  if (!entry) return '';
  const name = String(entry.name || '').trim();
  if (name) return name;
  if (entry.type === 'http-entry') {
    const method = String(entry.data?.method || 'GET').toUpperCase();
    const path = String(entry.data?.path || '').trim() || '/';
    return `${method} ${path}`;
  }
  if (entry.type === 'subflow-entry') return '子流入口';
  if (entry.type === 'scenario-entry') return '场景入口';
  return entry.id;
}

/** 入口卡片副标题：说明入口类型及运行方式 */
function entryMeta(entry) {
  if (!entry) return '';
  if (entry.type === 'scenario-entry') return '场景入口 · 下方选场景后点跑';
  if (entry.type === 'http-entry') return '开放 HTTP 入口 · 画布点跑按场景轨注入';
  if (entry.type === 'subflow-entry') return '子流入口 · 通常由父流调用';
  return entry.type;
}

/**
 * 选中跑前入口：写入 selectedRunEntryNodeId。
 * 若为场景入口，把该节点 data.scenarios 载入跑前场景状态（默认激活第一条）并在画布上选中该节点。
 */
function selectEntry(entryId) {
  store.selectedRunEntryNodeId = entryId;
  const entry = store.nodes.find((n) => n.id === entryId);
  if (entry?.type === 'scenario-entry') {
    const raw = entry.data?.scenarios;
    if (Array.isArray(raw) && raw.length) {
      store.runConfig = {
        activeScenarioId: raw[0].id,
        scenarios: JSON.parse(JSON.stringify(raw)),
      };
    }
    store.selected = { kind: 'node', id: entryId };
  }
}

/** 取场景在 AI 暂存区对应的变更单元 id；无暂存标记时为 undefined */
function stagingUnitIdForScenario(scenarioId) {
  return stagingStore.stagingByScenarioId[scenarioId]?.unitId;
}

/** 场景卡片样式：当前选中高亮，以及 AI 暂存的新增 / 修改 / 删除标记 */
function scenarioCardClasses(scenarioId) {
  const mark = stagingStore.stagingByScenarioId[scenarioId];
  return {
    'is-active': scenarioId === activeScenarioId.value,
    'is-ai-staging-add': mark?.mode === 'add',
    'is-ai-staging-update': mark?.mode === 'update',
    'is-ai-staging-delete': mark?.mode === 'delete',
  };
}

/** 点击运行：依次校验模板画布、执行权限、是否已选入口，通过后按所选入口发起真实跑流 */
async function onRunScenario() {
  if (isTemplateCanvas.value) {
    ElMessage.warning('模板画布不支持运行');
    return;
  }
  if (!canRunFlow.value) {
    ElMessage.warning('当前账号无执行权限，无法运行');
    return;
  }
  if (!selectedEntry.value) {
    ElMessage.warning('请先选择入口');
    return;
  }
  await runActiveScenario();
}

/**
 * 保证有一个有效的选中入口：
 * 已选入口仍存在则保持；否则优先选画布上当前选中的入口节点，再退到第一个场景入口，最后取第一个入口；无入口则清空。
 */
function ensureSelectedEntry() {
  const entries = entryNodes.value;
  if (!entries.length) {
    store.selectedRunEntryNodeId = null;
    return;
  }
  const current = store.selectedRunEntryNodeId;
  if (current && entries.some((e) => e.id === current)) return;
  const selectedNode = store.selected?.kind === 'node'
    ? store.nodes.find((n) => n.id === store.selected.id)
    : null;
  if (selectedNode && ENTRY_TYPES.has(selectedNode.type)) {
    selectEntry(selectedNode.id);
    return;
  }
  const firstScenario = entries.find((e) => e.type === 'scenario-entry');
  selectEntry((firstScenario || entries[0]).id);
}

// 入口节点增删时重新校正选中入口
watch(entryNodes, () => ensureSelectedEntry(), { immediate: true });

onMounted(() => {
  loadProjectEnvs();
  ensureSelectedEntry();
});
</script>

<style scoped lang="scss">
.run-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.run-config__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.run-config__scenario-run-btn {
  flex: 1;
  min-width: 120px;
}

.run-config__scenarios-title {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 8px;
}

.run-config__hint,
.run-config__empty {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 8px;
  line-height: 1.4;
}

.run-config__entry-doc {
  padding-bottom: 4px;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
}

.run-config__entry-doc-body {
  font-size: 12px;
  line-height: 1.45;
  max-height: 140px;
  overflow: auto;
}

.run-config-scenario {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  background: #fff;

  &.is-active {
    border-color: var(--el-color-primary, #409eff);
    background: var(--el-color-primary-light-9, #ecf5ff);
  }

  &.is-ai-staging-add {
    border-color: v-bind(STAGING_ADD_COLOR);
  }

  &.is-ai-staging-update {
    border-color: v-bind(STAGING_UPDATE_COLOR);
  }

  &.is-ai-staging-delete {
    border-color: v-bind(STAGING_DELETE_COLOR);
  }
}

.run-config-scenario__radio {
  margin-top: 4px;
}

.run-config-scenario__name {
  font-weight: 600;
  font-size: 13px;
}

.run-config-scenario__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-top: 2px;
}
</style>
