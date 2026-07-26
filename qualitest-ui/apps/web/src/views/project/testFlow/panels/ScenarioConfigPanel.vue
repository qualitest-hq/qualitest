<template>
  <div class="prop-form">
    <AiStagingFieldDiff v-if="stagingUnit" :unit-id="stagingUnit.unitId" />

    <template v-if="activeScenario && showNormalFields">
      <div class="field">
        <label>场景名称</label>
        <input
            :value="activeScenario.name"
            type="text"
            @input="onNameInput"
        />
      </div>
      <div class="field">
        <label>说明（可选）</label>
        <input
            :value="activeScenario.remark ?? ''"
            placeholder="例如：验证登录失败分支"
            type="text"
            @input="onRemarkInput"
        />
      </div>
      <div class="field">
        <label>环境</label>
        <div class="field__env-row">
          <el-select
              :loading="envLoading"
              :model-value="activeScenario.testProjectEnvId"
              class="prop-el-select field__env-select"
              placeholder="选择测试环境"
              @change="onEnvChange"
          >
            <el-option
                v-for="env in envOptions"
                :key="env.testProjectEnvId"
                :label="env.envName"
                :value="env.testProjectEnvId"
            />
          </el-select>
          <button
              type="button"
              class="field__env-manage-btn"
              title="环境管理"
              aria-label="环境管理"
              @click="openEnvManageDialog"
          >
            <svg
                class="field__env-manage-icon"
                viewBox="0 0 24 24"
                aria-hidden="true"
                xmlns="http://www.w3.org/2000/svg"
            >
              <path
                  fill="none"
                  stroke="currentColor"
                  stroke-linecap="round"
                  stroke-width="2"
                  d="M5 7h14M5 12h14M5 17h14"
              />
            </svg>
          </button>
        </div>
        <div v-if="!envOptions.length && !envLoading" class="field__hint">
          当前项目暂无环境，点击右侧按钮创建
        </div>
      </div>
      <div class="field">
        <label>流程变量初值</label>
        <FlowKeyValueEditor
            :add-label="FLOW_SEED_ADD_LABEL"
            :columns="FLOW_SEED_COLUMNS"
            :rows="flowSeedRows"
            @update:rows="onFlowSeedRowsChange"
        />
        <div class="field__hint">运行选中场景时注入 flow 变量；跨步骤引用写法如 flow.token，素材见参数库</div>
      </div>
      <div class="field">
        <label>节点失败时</label>
        <select :value="onNodeFailure" @change="onNodeFailureChange">
          <option value="fail">直接失败</option>
          <option value="prompt">暂停等待决策</option>
        </select>
      </div>
      <div class="field">
        <label>快照失败时</label>
        <select :value="onSnapshotFailure" @change="onSnapshotFailureChange">
          <option value="abort">中止运行</option>
          <option value="prompt">暂停等待决策</option>
          <option value="continue">记录失败后继续</option>
        </select>
        <div class="field__hint">需配合节点 snapshotBefore 与环境 allowDestructiveReset 使用；开启数据还原时，同一环境请串行跑</div>
      </div>
    </template>
    <PropEmpty v-else-if="!activeScenario" icon="▶">
      请在左侧选择场景<br/>再编辑运行参数
    </PropEmpty>

    <div class="prop-section">
      <div class="field">
        <label>流程返回值（子流 outputs 默认映射）</label>
        <div class="field__hint">定义本流程对外暴露的 flow 变量；被引用为子流时可作为 outputs 默认映射</div>
        <FlowKeyValueEditor
            :add-label="FLOW_OUTPUT_ADD_LABEL"
            :columns="FLOW_OUTPUT_COLUMNS"
            :rows="flowOutputRows"
            @update:rows="onFlowOutputRowsChange"
        />
      </div>
    </div>

    <EnvManageDialog
        v-model:visible="envManageDialogVisible"
        :test-project-id="store.testProjectId"
        :toolbar-env-id="activeScenario?.testProjectEnvId ?? ''"
        @saved="onEnvManageSaved"
    />
  </div>
</template>

<script setup>
/** 右栏运行配置：Staging 对照 + 当前场景参数 + 流程级 flowOutputs。 */
import { computed, onMounted, ref } from 'vue';

import EnvManageDialog from '@/views/project/testProject/components/EnvManageDialog.vue';
import AiStagingFieldDiff from '../components/AiStagingFieldDiff.vue';
import FlowKeyValueEditor from '../components/FlowKeyValueEditor.vue';
import PropEmpty from '../components/PropEmpty.vue';
import {
  FLOW_OUTPUT_ADD_LABEL,
  FLOW_OUTPUT_COLUMNS,
  FLOW_SEED_ADD_LABEL,
  FLOW_SEED_COLUMNS,
} from '../constants/runConfigEditors';
import {
  flowOutputsToRows,
  flowSeedToRows,
  rowsToFlowOutputs,
  rowsToFlowSeed,
  useRunConfig,
} from '../composables/useRunConfig';
import { syncStagingDraftFromScenario } from '../composables/stagingDraftSync';
import { usePendingStagingUnit, useShowNormalFields } from '../composables/usePendingStagingUnit';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';
import { refreshSavedBaselineIfPristine } from '../utils/reconcileFlowDirty';

const store = useFlowCanvasStore();
const envManageDialogVisible = ref(false);

const {
  envOptions,
  envLoading,
  loadProjectEnvs,
  getActiveScenario,
  patchActiveScenario,
  ensureActiveScenarioEnv,
} = useRunConfig();

const activeScenario = computed(() => getActiveScenario());

const stagingUnit = usePendingStagingUnit(computed(() => activeScenario.value?.id), 'scenario');

const showNormalFields = useShowNormalFields(stagingUnit, ['updateScenario']);

const onNodeFailure = computed(() => activeScenario.value?.onNodeFailure ?? 'fail');
const onSnapshotFailure = computed(() => activeScenario.value?.onSnapshotFailure ?? 'abort');

const flowSeedRows = computed(() => flowSeedToRows(activeScenario.value?.flowSeed));
const flowOutputRows = computed(() => flowOutputsToRows(store.flowOutputs));

onMounted(async () => {
  await loadProjectEnvs();
  ensureActiveScenarioEnv();
  await refreshSavedBaselineIfPristine(store);
});

function syncScenarioDraft() {
  const sc = activeScenario.value;
  if (sc) syncStagingDraftFromScenario(sc.id);
}

function onNameInput(e) {
  patchActiveScenario({ name: e.target.value });
  syncScenarioDraft();
}

function onRemarkInput(e) {
  patchActiveScenario({ remark: e.target.value });
  syncScenarioDraft();
}

function onEnvChange(val) {
  patchActiveScenario({ testProjectEnvId: String(val ?? '') });
  syncScenarioDraft();
}

function openEnvManageDialog() {
  envManageDialogVisible.value = true;
}

async function onEnvManageSaved(payload) {
  await loadProjectEnvs();
  const sc = activeScenario.value;
  if (!sc) return;
  const curId = String(sc.testProjectEnvId ?? '');
  const deletedId = payload?.deletedId != null ? String(payload.deletedId) : '';
  const stillExists = envOptions.value.some((e) => e.testProjectEnvId === curId);
  if (curId && (!stillExists || (deletedId && deletedId === curId))) {
    patchActiveScenario({
      testProjectEnvId: envOptions.value[0]?.testProjectEnvId ?? '',
    });
    syncScenarioDraft();
  } else {
    ensureActiveScenarioEnv();
  }
}

function onFlowSeedRowsChange(rows) {
  patchActiveScenario({ flowSeed: rowsToFlowSeed(rows) });
  syncScenarioDraft();
}

function onFlowOutputRowsChange(rows) {
  store.flowOutputs = rowsToFlowOutputs(rows);
  store.markDirty();
}

function onNodeFailureChange(e) {
  patchActiveScenario({ onNodeFailure: e.target.value });
  syncScenarioDraft();
}

function onSnapshotFailureChange(e) {
  patchActiveScenario({ onSnapshotFailure: e.target.value });
  syncScenarioDraft();
}
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;

@include prop.flow-prop-panel-root;
@include prop.flow-prop-el-select('prop-el-select');

.field__env-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.field__env-select {
  flex: 1;
  min-width: 0;
}

.field__env-manage-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-surface-elevated);
  color: var(--pd-text-muted);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;

  &:hover {
    color: var(--pd-primary);
    border-color: color-mix(in srgb, var(--pd-primary) 35%, var(--pd-border-subtle));
    background: color-mix(in srgb, var(--pd-primary) 6%, var(--pd-surface-elevated));
  }

  &:focus-visible {
    outline: 2px solid var(--pd-primary);
    outline-offset: 1px;
  }
}

.field__env-manage-icon {
  width: 16px;
  height: 16px;
}
</style>
