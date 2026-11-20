<template>
  <div class="run-config">
    <div class="run-config__toolbar">
      <button
          v-if="!isScenarioRunActive"
          :disabled="!canRunFlow || isTemplateCanvas"
          :title="runButtonTitle"
          class="btn btn--primary run-config__scenario-run-btn"
          type="button"
          @click="onRunScenario"
      >
        ▶ {{ activeScenarioName }}
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
      <button
          v-if="canEditFlow"
          class="btn btn--ghost"
          type="button"
          @click="addScenario"
      >＋ 场景</button>
      <button
          v-if="canEditFlow"
          class="btn btn--ghost"
          type="button"
          @click="duplicateScenario"
      >复制</button>
      <button
          v-if="canEditFlow"
          class="btn btn--ghost"
          type="button"
          @click="deleteScenario"
      >删除</button>
    </div>
    <div class="run-config__body">
      <div class="run-config__spec">
        <div class="run-config__scenarios-title">入参契约</div>
        <button
            :class="{ 'is-active': store.ui.rightMode === 'inputSpec' && store.ui.rightOpen }"
            class="run-config__spec-card"
            type="button"
            @click="store.showInputSpecPanel()"
        >
          <div class="run-config__spec-card-main">
            <div class="run-config__spec-card-title">{{ inputSpecTitle }}</div>
            <div class="run-config__spec-card-badges">
              <span class="run-config__spec-badge">{{ inputSpecFieldLabel }}</span>
              <span
                  :class="inputSpecOpen ? 'is-open' : 'is-closed'"
                  class="run-config__spec-badge"
              >{{ inputSpecOpenLabel }}</span>
              <span class="run-config__spec-badge">{{ flowOutputLabel }}</span>
            </div>
            <div v-if="inputSpecPathHint" class="run-config__spec-card-path">{{ inputSpecPathHint }}</div>
          </div>
          <span class="run-config__spec-card-chevron" aria-hidden="true">›</span>
        </button>
      </div>
      <div class="run-config__scenarios">
        <div class="run-config__scenarios-title">场景预设（选中 = 下次开跑）</div>
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
  </div>
</template>

<script setup>
/**
 * 左栏运行配置：顶部入参契约摘要（点开右栏编辑）、场景预设列表增删复制。
 * 工具条可触发当前活动场景跑流；跑流中切换为停止；受 flow:run 与模板画布限制。
 * Staging 中的场景在卡片上显示确认条与样式标记。
 */
import { computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';

import AiStagingScenarioBanner from '../components/AiStagingScenarioBanner.vue';
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

const { canRunFlow, canEditFlow, isTemplateCanvas } = useFlowCanvasPermissions();
const { isScenarioRunActive, runActiveScenario, abortScenarioRun } = useFlowScenarioRun();

const store = useFlowCanvasStore();
const stagingStore = useAiStagingStore();
const {
  loadProjectEnvs,
  activeScenarioName,
  scenarioCardMeta,
  selectScenario,
  addScenario,
  duplicateScenario,
  deleteScenario,
} = useRunConfig();

const runButtonTitle = computed(() => {
  if (isTemplateCanvas.value) return '模板画布不支持运行配置';
  if (!canRunFlow.value) return '当前账号无执行权限，无法运行场景';
  return `运行场景预设「${activeScenarioName.value}」`;
});

/** 触发当前活动场景跑流：先校验模板画布与 flow:run */
async function onRunScenario() {
  if (isTemplateCanvas.value) {
    ElMessage.warning('模板画布不支持运行配置');
    return;
  }
  if (!canRunFlow.value) {
    ElMessage.warning('当前账号无执行权限，无法运行场景');
    return;
  }
  await runActiveScenario();
}

const scenarios = computed(() => store.runConfig.scenarios);
const activeScenarioId = computed(() => store.runConfig.activeScenarioId);

const inputSpecFieldCount = computed(
  () => store.inputSpec?.fields?.filter((f) => f?.name)?.length ?? 0,
);
const flowOutputCount = computed(
  () => store.flowOutputs.filter((o) => String(o?.name || '').trim()).length,
);
const inputSpecOpen = computed(() => !!store.inputSpec?.open);
const inputSpecTitle = computed(() =>
  inputSpecFieldCount.value || inputSpecOpen.value || flowOutputCount.value
    ? '流级契约'
    : '尚未配置，点击编辑',
);
const inputSpecFieldLabel = computed(() => `${inputSpecFieldCount.value} 个入参`);
const inputSpecOpenLabel = computed(() => (inputSpecOpen.value ? '已开放入站' : '未开放'));
const flowOutputLabel = computed(() => `${flowOutputCount.value} 个产出`);
const inputSpecPathHint = computed(() => {
  if (!inputSpecOpen.value) return '';
  const path = String(store.inputSpec?.path || '').trim();
  return path || '已开放，尚未填写 path';
});

function stagingUnitIdForScenario(scenarioId) {
  return stagingStore.stagingByScenarioId[scenarioId]?.unitId;
}

function scenarioCardClasses(scenarioId) {
  const mark = stagingStore.stagingByScenarioId[scenarioId];
  return {
    'is-active': scenarioId === activeScenarioId.value,
    'is-ai-staging-add': mark?.mode === 'add',
    'is-ai-staging-update': mark?.mode === 'update',
    'is-ai-staging-delete': mark?.mode === 'delete',
  };
}

onMounted(() => {
  loadProjectEnvs();
});
</script>

<style scoped lang="scss">
.run-config {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.run-config__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 8px;
  border-bottom: 1px solid var(--pd-divider);
  flex-shrink: 0;

  :deep(.btn) {
    height: 26px;
    font-size: 11px;
    padding: 0 8px;
  }
}

.run-config__scenario-run-btn {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-config__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.run-config__spec {
  flex-shrink: 0;
  padding: 6px 8px 0;
}

.run-config__spec-card {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin: 0 0 4px;
  padding: 8px 10px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font: inherit;

  &:hover {
    border-color: var(--pd-primary);
  }

  &.is-active {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
    box-shadow: 0 0 0 1px var(--pd-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--pd-primary);
    outline-offset: 1px;
  }
}

.run-config__spec-card-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.run-config__spec-card-title {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--pd-text);
}

.run-config__spec-card-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.run-config__spec-badge {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 4px;
  font-size: 10px;
  line-height: 1;
  color: var(--pd-text-muted);
  background: color-mix(in srgb, var(--pd-text-muted) 10%, transparent);

  &.is-open {
    color: var(--pd-success, #15803d);
    background: color-mix(in srgb, var(--pd-success, #15803d) 12%, transparent);
  }

  &.is-closed {
    color: var(--pd-text-muted);
  }
}

.run-config__spec-card-path {
  font-size: 10px;
  line-height: 1.4;
  color: var(--pd-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-config__spec-card-chevron {
  flex-shrink: 0;
  font-size: 16px;
  line-height: 1;
  color: var(--pd-text-muted);
  opacity: 0.7;
}

.run-config__scenarios {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 8px;
}

.run-config__scenarios-title {
  font-size: 10px;
  font-weight: 600;
  color: var(--pd-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}

.run-config-scenario {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--pd-border-subtle);
  margin-bottom: 6px;
  cursor: pointer;
  background: #fff;

  &:hover {
    border-color: var(--pd-primary);
  }

  &.is-active {
    border-color: var(--pd-primary);
    background: var(--pd-primary-soft);
    box-shadow: 0 0 0 1px var(--pd-primary);
  }

  &.is-ai-staging-add {
    border-style: dashed;
    border-color: v-bind('STAGING_ADD_COLOR');
    background: color-mix(in srgb, v-bind('STAGING_ADD_COLOR') 5%, #fff);
  }

  &.is-ai-staging-update {
    border-color: v-bind('STAGING_UPDATE_COLOR');
    box-shadow: 0 0 0 1px color-mix(in srgb, v-bind('STAGING_UPDATE_COLOR') 35%, transparent);
  }

  &.is-ai-staging-delete {
    border-style: dashed;
    border-color: v-bind('STAGING_DELETE_COLOR');
    opacity: 0.9;
  }
}

.run-config-scenario__radio {
  margin: 2px 0 0;
  accent-color: var(--pd-primary);
  flex-shrink: 0;
}

.run-config-scenario__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.run-config-scenario__name {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.run-config-scenario__meta {
  font-size: 10px;
  color: var(--pd-text-muted);
  line-height: 1.45;
}
</style>
