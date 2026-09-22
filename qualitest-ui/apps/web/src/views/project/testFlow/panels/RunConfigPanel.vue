<template>
  <div class="run-config">
    <div class="run-config__toolbar">
      <button
          v-if="!isScenarioRunActive"
          :disabled="!canEditFlow || isTemplateCanvas"
          :title="runButtonTitle"
          class="btn btn--primary run-config__scenario-run-btn"
          type="button"
          @click="onRunScenario"
      >
        ▶ {{ activeScenarioName }}
      </button>
      <button
          v-else
          class="btn btn--primary run-config__scenario-run-btn is-scenario-run-stop"
          type="button"
          @click="abortScenarioRun"
      >
        ■ 停止
      </button>
      <button class="btn btn--ghost" type="button" @click="addScenario">＋ 场景</button>
      <button class="btn btn--ghost" type="button" @click="duplicateScenario">复制</button>
      <button class="btn btn--ghost" type="button" @click="deleteScenario">删除</button>
    </div>
    <div class="run-config__scenarios">
      <div class="run-config__scenarios-title">场景列表（选中 = 下次运行场景）</div>
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
 * 左栏运行场景：场景列表增删复制，选中后由右栏编辑配置。
 * 工具条可触发当前活动场景的真实跑流，跑流中切换为停止。
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

const { canEditFlow, isTemplateCanvas } = useFlowCanvasPermissions();
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
  if (isTemplateCanvas.value) return '模板画布不支持运行场景';
  if (!canEditFlow.value) return '当前账号无编辑权限，无法运行场景';
  return `运行场景「${activeScenarioName.value}」`;
});

/** 触发当前活动场景跑流：先校验模板画布与编辑权限 */
async function onRunScenario() {
  if (isTemplateCanvas.value) {
    ElMessage.warning('模板画布不支持运行场景');
    return;
  }
  if (!canEditFlow.value) {
    ElMessage.warning('当前账号无编辑权限，无法运行场景');
    return;
  }
  await runActiveScenario();
}

const scenarios = computed(() => store.runConfig.scenarios);
const activeScenarioId = computed(() => store.runConfig.activeScenarioId);

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
