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
        <label>入参初值</label>
        <div v-if="!contractFields.length" class="scenario-in-empty">
          <div>尚未在入参契约声明字段，这里只填值、不加键。</div>
          <button class="btn btn--ghost scenario-in-empty__btn" type="button" @click="store.showInputSpecPanel()">
            去入参契约声明
          </button>
        </div>
        <div v-else class="scenario-in-list">
          <div v-for="f in contractFields" :key="f.name" class="scenario-in-row">
            <div class="scenario-in-row__label">
              <span class="scenario-in-row__title">
                {{ f.label || f.name }}<span v-if="f.required" class="scenario-in-row__req">*</span>
              </span>
              <span v-if="f.label" class="scenario-in-row__name">{{ f.name }}</span>
            </div>
            <input
                :placeholder="`{{in.${f.name}}}`"
                :value="inSeedDisplayValue(f.name)"
                type="text"
                @input="onInSeedFieldInput(f.name, $event)"
            />
          </div>
        </div>
        <div class="field__hint" v-pre>字段在「入参契约」定义；本处只给当前场景灌 in 初值</div>
      </div>
      <!-- 当前场景的步数上限：留空走默认，输入会裁剪到 1～1000 -->
      <div class="field">
        <label>单次运行步数上限</label>
        <input
            :value="maxStepsDisplay"
            inputmode="numeric"
            max="1000"
            min="1"
            placeholder="默认 200"
            type="number"
            @change="onMaxStepsChange"
        />
        <div class="field__hint">留空使用默认 200；合法范围 1～1000，超出上限时运行失败并指出停在哪个节点</div>
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
      请在左侧选择场景预设<br/>再编辑运行参数
    </PropEmpty>

    <EnvManageDialog
        v-model:visible="envManageDialogVisible"
        :test-project-id="store.testProjectId"
        :toolbar-env-id="activeScenario?.testProjectEnvId ?? ''"
        @saved="onEnvManageSaved"
    />
  </div>
</template>

<script setup>
/** 右栏场景预设：Staging 对照 + 当前场景（按契约填 in、失败策略）。 */
import { computed, onMounted, ref } from 'vue';

import EnvManageDialog from '@/views/project/testProject/components/EnvManageDialog.vue';
import AiStagingFieldDiff from '../components/AiStagingFieldDiff.vue';
import PropEmpty from '../components/PropEmpty.vue';
import { useRunConfig } from '../composables/useRunConfig';
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

const onNodeFailure = computed(() => activeScenario.value?.onNodeFailure ?? 'fail');
const onSnapshotFailure = computed(() => activeScenario.value?.onSnapshotFailure ?? 'abort');
/** 步数上限输入框展示值：未配置时为空字符串，占位提示默认 200 */
const maxStepsDisplay = computed(() => {
  const v = activeScenario.value?.maxSteps;
  return v == null ? '' : String(v);
});

/** 入参契约里已命名的字段；场景只按这些键填值 */
const contractFields = computed(() => {
  const list = store.inputSpec?.fields || [];
  const seen = new Set();
  const out = [];
  for (const f of list) {
    const name = String(f?.name || '').trim();
    if (!name || seen.has(name)) continue;
    seen.add(name);
    out.push({
      name,
      label: String(f?.label || '').trim(),
      type: f?.type || 'string',
      required: !!f?.required,
    });
  }
  return out;
});

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
  patchActiveScenario({ testProjectEnvId: String(val ?? '') });
  syncScenarioDraft();
}

function openEnvManageDialog() {
  envManageDialogVisible.value = true;
}

async function onEnvManageSaved(payload) {
  // 环境增删改后重拉列表，并修正当前场景的绑定
  await loadProjectEnvs();
  const sc = activeScenario.value;
  if (!sc) return;
  const curId = String(sc.testProjectEnvId ?? '');
  const deletedId = payload?.deletedId != null ? String(payload.deletedId) : '';
  const stillExists = envOptions.value.some((e) => e.testProjectEnvId === curId);
  if (curId && (!stillExists || (deletedId && deletedId === curId))) {
    // 原绑定已不存在：改绑到列表第一条（没有则清空），并标未保存
    patchActiveScenario({
      testProjectEnvId: envOptions.value[0]?.testProjectEnvId ?? '',
    });
    syncScenarioDraft();
  } else {
    // 原绑定仍有效：若场景本来未绑环境，则自动补绑第一条
    await ensureActiveScenarioEnv();
  }
}

function inSeedDisplayValue(name) {
  const raw = activeScenario.value?.inSeed?.[name];
  if (raw == null) return '';
  if (typeof raw === 'string') return raw;
  try {
    return JSON.stringify(raw);
  } catch {
    return String(raw);
  }
}

/** 只 patch 契约内某一键；契约外脏键保留不展示 */
function onInSeedFieldInput(name, e) {
  const next = { ...(activeScenario.value?.inSeed || {}) };
  next[name] = e.target.value;
  patchActiveScenario({ inSeed: next });
  syncScenarioDraft();
}

/** 步数上限变更：清空或非数字则写 null；否则取整并限制在 1～1000 */
function onMaxStepsChange(e) {
  const raw = String(e.target.value ?? '').trim();
  if (!raw) {
    patchActiveScenario({ maxSteps: null });
    syncScenarioDraft();
    return;
  }
  let n = Number(raw);
  if (!Number.isFinite(n)) {
    patchActiveScenario({ maxSteps: null });
    syncScenarioDraft();
    return;
  }
  n = Math.round(n);
  if (n < 1) n = 1;
  if (n > 1000) n = 1000;
  patchActiveScenario({ maxSteps: n });
  syncScenarioDraft();
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

.scenario-in-empty {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px dashed var(--pd-border-subtle);
  background: var(--pd-bg-sunken, #f8fafc);
  font-size: 11px;
  line-height: 1.5;
  color: var(--pd-text-muted);
}

.scenario-in-empty__btn {
  align-self: flex-start;
  height: 26px;
  font-size: 11px;
  padding: 0 8px;
}

.scenario-in-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scenario-in-row {
  display: flex;
  flex-direction: column;
  gap: 4px;

  input {
    width: 100%;
    height: 32px;
    padding: 6px 8px;
    border: 1px solid var(--pd-border-subtle);
    border-radius: 6px;
    font-size: 12px;
    background: var(--pd-surface-elevated);
    color: var(--pd-text);
    box-sizing: border-box;

    &:focus {
      outline: none;
      box-shadow: 0 0 0 1px var(--pd-primary) inset;
      border-color: var(--pd-primary);
    }
  }
}

.scenario-in-row__label {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.scenario-in-row__title {
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
}

.scenario-in-row__req {
  margin-left: 2px;
  color: var(--pd-danger, #dc2626);
  font-weight: 700;
}

.scenario-in-row__name {
  font-size: 10px;
  color: var(--pd-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  opacity: 0.85;
}
</style>
