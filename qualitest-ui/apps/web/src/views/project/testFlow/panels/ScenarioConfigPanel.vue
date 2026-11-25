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
              :model-value="activeScenario.envId"
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
        <label>入参值（inSeed）</label>
        <FlowKeyValueEditor
            :add-label="FLOW_SEED_ADD_LABEL"
            :columns="FLOW_SEED_COLUMNS"
            :required-keys="requiredInSeedKeys"
            :rows="inSeedRows"
            @update:rows="onInSeedRowsChange"
        />
        <!-- v-pre：把 {{in.orderId}} 当纯文案展示，不要当成模板变量求值 -->
        <div class="field__hint" v-pre>运行选中场景时注入 in，写法如 {{in.orderId}}</div>
        <div v-if="requiredInSeedKeys.length" class="field__hint">
          标 * 的键来自入口 inSeedKeys 的必填提示；缺值不阻止开跑
        </div>
      </div>
    </template>
    <PropEmpty v-else-if="!activeScenario" icon="▶">
      请在左侧选择场景<br/>再编辑运行参数
    </PropEmpty>

    <EnvManageDialog
        v-model:visible="envManageDialogVisible"
        :test-project-id="store.testProjectId"
        :toolbar-env-id="activeScenario?.envId ?? ''"
        @saved="onEnvManageSaved"
    />
  </div>
</template>

<script setup>
/** 右栏运行配置：Staging 对照 + 当前场景参数。 */
import { computed, onMounted, ref } from 'vue';

import EnvManageDialog from '@/views/project/testProject/components/EnvManageDialog.vue';
import AiStagingFieldDiff from '../components/AiStagingFieldDiff.vue';
import FlowKeyValueEditor from '../components/FlowKeyValueEditor.vue';
import PropEmpty from '../components/PropEmpty.vue';
import { FLOW_SEED_ADD_LABEL, FLOW_SEED_COLUMNS } from '../constants/runConfigEditors';
import { mergeInSeedRowsWithKeys, rowsToInSeed, useRunConfig } from '../composables/useRunConfig';
import { syncStagingDraftFromScenario } from '../composables/stagingDraftSync';
import { usePendingStagingUnit, useShowNormalFields } from '../composables/usePendingStagingUnit';
import { useFlowCanvasStore } from '../stores/flowCanvasStore';

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

/**
 * 当前跑前选中的场景入口上的 inSeedKeys。
 * 用于补空 inSeed 行、标必填；入口未选或非场景入口时为空。
 */
const entryInSeedKeys = computed(() => {
  const entryId = store.selectedRunEntryNodeId;
  if (!entryId) return [];
  const node = store.nodes.find((n) => n.id === entryId && n.type === 'scenario-entry');
  const keys = node?.data?.inSeedKeys;
  return Array.isArray(keys) ? keys : [];
});

/** 测值行 = inSeed 现有值 + inSeedKeys 补空 */
const inSeedRows = computed(() =>
  mergeInSeedRowsWithKeys(activeScenario.value?.inSeed, entryInSeedKeys.value),
);

/** inSeedKeys 里标了 required 的键名，交给键值表在行首画 * */
const requiredInSeedKeys = computed(() =>
  entryInSeedKeys.value
    .filter((k) => k && k.required)
    .map((k) => String(k.name ?? '').trim())
    .filter(Boolean),
);

onMounted(async () => {
  // 打开场景面板时拉取项目环境；未绑环境的激活场景会自动补绑并更新已保存基线
  await loadProjectEnvs();
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
  const envId = String(val ?? '');
  patchActiveScenario({ envId });
  syncScenarioDraft();
}

function openEnvManageDialog() {
  envManageDialogVisible.value = true;
}

async function onEnvManageSaved(payload) {
  // 环境增删改后强制重拉列表，并修正当前场景的绑定
  await loadProjectEnvs(true);
  const sc = activeScenario.value;
  if (!sc) return;
  const curId = String(sc.envId ?? '');
  const deletedId = payload?.deletedId != null ? String(payload.deletedId) : '';
  const stillExists = envOptions.value.some((e) => e.testProjectEnvId === curId);
  if (curId && (!stillExists || (deletedId && deletedId === curId))) {
    const envId = envOptions.value[0]?.testProjectEnvId ?? '';
    patchActiveScenario({ envId });
    syncScenarioDraft();
  } else {
    // 原绑定仍有效：若场景本来未绑环境，则自动补绑第一条
    await ensureActiveScenarioEnv();
  }
}

function onInSeedRowsChange(rows) {
  const inSeed = rowsToInSeed(rows);
  patchActiveScenario({ inSeed });
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
