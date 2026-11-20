<template>
  <div class="prop-form input-spec">
    <div class="field">
      <div class="input-spec__section-head">
        <label>入参字段</label>
        <span class="input-spec__section-count">{{ namedFieldCount }} 个</span>
      </div>

      <div v-if="!fields.length" class="input-spec__empty">
        在此声明本流接受哪些入参；场景预设里只填具体值，不必再加键。
      </div>

      <div
          v-for="(f, index) in fields"
          :key="index"
          class="input-spec__field-card"
      >
        <div class="input-spec__field-card-top">
          <input
              :value="f.name"
              class="input-spec__name"
              placeholder="字段名，如 orderId"
              type="text"
              @input="onFieldText(index, 'name', $event)"
          />
          <FlowRowRemoveButton title="删除字段" @click="removeField(index)" />
        </div>
        <div class="input-spec__field-card-grid">
          <input
              :value="f.label"
              placeholder="显示名（可选）"
              type="text"
              @input="onFieldText(index, 'label', $event)"
          />
          <select :value="f.type || 'string'" @change="onFieldText(index, 'type', $event)">
            <option value="string">string</option>
            <option value="number">number</option>
            <option value="boolean">boolean</option>
            <option value="json">json</option>
          </select>
          <label class="input-spec__req" title="场景填值与开放入站均建议有值；开放入站缺值会拒绝请求">
            <input
                :checked="!!f.required"
                class="input-spec__req-check"
                type="checkbox"
                @change="onFieldRequired(index, $event)"
            />
            <span>必填</span>
          </label>
        </div>
      </div>

      <button class="btn btn--ghost input-spec__add" type="button" @click="addField">＋ 字段</button>
    </div>

    <div class="prop-section">
      <div class="field">
        <label>开放入站</label>
        <button
            :aria-pressed="!!spec.open"
            :class="{ 'is-on': !!spec.open }"
            class="input-spec__switch"
            type="button"
            @click="toggleOpen"
        >
          <span class="input-spec__switch-track" aria-hidden="true" />
          <span class="input-spec__switch-text">
            {{ spec.open ? '已对外提供入口' : '未开放（仅画布开跑）' }}
          </span>
        </button>
        <div class="field__hint">开启后可通过 HTTP 回调触发本流；一般联调场景可保持关闭</div>
      </div>

      <template v-if="spec.open">
        <div class="field">
          <label>path</label>
          <input
              :value="spec.path || ''"
              class="input-spec__path-input"
              placeholder="/v3/refund"
              type="text"
              @input="onSpecText('path', $event)"
          />
          <div class="field__hint">完整相对路径，须以 / 开头；不可占用 /login、/system、/mcp 等平台前缀</div>
        </div>
        <div class="field">
          <label>运行环境</label>
          <el-select
              :loading="envLoading"
              :model-value="spec.runEnvId || ''"
              class="prop-el-select"
              placeholder="选择运行环境"
              @change="onRunEnvChange"
          >
            <el-option
                v-for="env in envOptions"
                :key="env.testProjectEnvId"
                :label="env.envName"
                :value="env.testProjectEnvId"
            />
          </el-select>
          <div v-if="!envOptions.length && !envLoading" class="field__hint">
            当前项目暂无环境，请先在场景面板或环境管理中创建
          </div>
          <div v-else class="field__hint">仿第三方请求不会传环境，入站开跑只用此处绑定</div>
        </div>
      </template>
    </div>

    <div class="prop-section">
      <div class="field">
        <div class="input-spec__section-head">
          <label>子流对外产出</label>
          <span class="input-spec__section-count">{{ flowOutputCount }} 个</span>
        </div>
        <div class="field__hint">本流被引用为子流时，父节点 outputs 为空则按此表从子 flow 同名回写</div>
        <FlowKeyValueEditor
            :add-label="FLOW_OUTPUT_ADD_LABEL"
            :columns="FLOW_OUTPUT_COLUMNS"
            :rows="flowOutputRows"
            @update:rows="onFlowOutputRowsChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 入参契约：流级入参字段、可选开放入站、子流对外产出（flowOutputs）。
 * 场景预设只按字段填 inSeed；不在此改 Assign。
 */
import { computed, onMounted } from 'vue'
import FlowKeyValueEditor from '../components/FlowKeyValueEditor.vue'
import FlowRowRemoveButton from '../components/FlowRowRemoveButton.vue'
import {
  FLOW_OUTPUT_ADD_LABEL,
  FLOW_OUTPUT_COLUMNS,
} from '../constants/runConfigEditors'
import {
  flowOutputsToRows,
  rowsToFlowOutputs,
  useRunConfig,
} from '../composables/useRunConfig'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

const store = useFlowCanvasStore()
const { envOptions, envLoading, loadProjectEnvs } = useRunConfig()

const spec = computed(() => store.inputSpec || { fields: [], open: false, path: '', runEnvId: '' })
const fields = computed(() => spec.value.fields || [])
const namedFieldCount = computed(() => fields.value.filter((f) => f?.name).length)
const flowOutputRows = computed(() => flowOutputsToRows(store.flowOutputs))
const flowOutputCount = computed(
  () => store.flowOutputs.filter((o) => String(o?.name || '').trim()).length,
)

onMounted(() => {
  loadProjectEnvs()
})

function patchSpec(patch) {
  store.patchInputSpec(patch)
}

function onSpecText(key, event) {
  patchSpec({ [key]: event.target.value })
}

function onRunEnvChange(value) {
  patchSpec({ runEnvId: value ? String(value) : '' })
}

function toggleOpen() {
  patchSpec({ open: !spec.value.open })
}

function onFieldText(index, key, event) {
  const next = fields.value.map((f, i) => (i === index ? { ...f, [key]: event.target.value } : { ...f }))
  patchSpec({ fields: next })
}

function onFieldRequired(index, event) {
  const next = fields.value.map((f, i) => (i === index ? { ...f, required: !!event.target.checked } : { ...f }))
  patchSpec({ fields: next })
}

function addField() {
  patchSpec({ fields: [...fields.value, { name: '', label: '', type: 'string', required: false }] })
}

function removeField(index) {
  patchSpec({ fields: fields.value.filter((_, i) => i !== index) })
}

function onFlowOutputRowsChange(rows) {
  store.flowOutputs = rowsToFlowOutputs(rows)
  store.markDirty()
}
</script>

<style scoped lang="scss">
@use '../styles/propPanel.scss' as prop;

@include prop.flow-prop-panel-root;
@include prop.flow-prop-el-select('prop-el-select');

.input-spec__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  label {
    margin-bottom: 0;
  }
}

.input-spec__section-count {
  font-size: 10px;
  font-weight: 600;
  color: var(--pd-text-muted);
}

.input-spec__empty {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px dashed var(--pd-border-subtle);
  background: var(--pd-bg-sunken, #f8fafc);
  font-size: 11px;
  line-height: 1.5;
  color: var(--pd-text-muted);
}

.input-spec__field-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 8px;
  background: #fff;
}

.input-spec__field-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
}

.input-spec__name {
  flex: 1;
  min-width: 0;
  height: 32px;
  padding: 6px 8px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--pd-text);
  background: var(--pd-surface-elevated);
  box-sizing: border-box;
  line-height: 1.2;

  &:focus {
    outline: none;
    box-shadow: 0 0 0 1px var(--pd-primary) inset;
    border-color: var(--pd-primary);
  }
}

.input-spec__field-card-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px auto;
  gap: 6px;
  align-items: center;

  input[type='text'],
  select {
    display: block;
    width: 100%;
    min-width: 0;
    height: 32px;
    padding: 0 8px;
    border: 1px solid var(--pd-border-subtle);
    border-radius: 6px;
    font-size: 12px;
    line-height: 32px;
    background: var(--pd-surface-elevated);
    color: var(--pd-text);
    box-sizing: border-box;

    &:focus {
      outline: none;
      box-shadow: 0 0 0 1px var(--pd-primary) inset;
      border-color: var(--pd-primary);
    }
  }

  select {
    cursor: pointer;
  }
}

/* 覆盖 .field label 的 block 样式，与同行 32px 控件垂直居中 */
.field .input-spec__req {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 4px;
  height: 32px;
  margin: 0;
  padding: 0 2px;
  font-size: 11px;
  font-weight: 400;
  line-height: 1;
  color: var(--pd-text-muted);
  white-space: nowrap;
  user-select: none;
  cursor: pointer;
  box-sizing: border-box;
}

.input-spec__req-check {
  width: 14px;
  height: 14px;
  margin: 0;
  padding: 0;
  flex-shrink: 0;
  accent-color: var(--pd-primary);
  vertical-align: middle;
}

.input-spec__add {
  align-self: flex-start;
  height: 26px;
  font-size: 11px;
  padding: 0 8px;
}

.input-spec :deep(.flow-kv-editor__row),
.input-spec :deep(.flow-kv-editor__head) {
  align-items: center;
}

.input-spec__switch {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin: 0;
  padding: 8px 10px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font: inherit;
  box-sizing: border-box;

  &.is-on {
    border-color: color-mix(in srgb, var(--pd-primary) 45%, var(--pd-border-subtle));
    background: var(--pd-primary-soft, #eff6ff);

    .input-spec__switch-track {
      background: var(--pd-primary);

      &::after {
        transform: translateX(14px);
      }
    }

    .input-spec__switch-text {
      color: var(--pd-text);
      font-weight: 600;
    }
  }

  &:focus-visible {
    outline: 2px solid var(--pd-primary);
    outline-offset: 1px;
  }
}

.input-spec__switch-track {
  position: relative;
  flex: 0 0 32px;
  width: 32px;
  height: 18px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--pd-text-muted) 28%, transparent);
  transition: background 0.15s;

  &::after {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #fff;
    box-shadow: 0 1px 2px rgb(0 0 0 / 12%);
    transition: transform 0.15s;
  }
}

.input-spec__switch-text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  line-height: 1.35;
  color: var(--pd-text-muted);
}

.input-spec__path-input {
  width: 100%;
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--pd-border-subtle);
  border-radius: 6px;
  background: var(--pd-surface-elevated);
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--pd-text);
  box-sizing: border-box;

  &:focus {
    outline: none;
    box-shadow: 0 0 0 1px var(--pd-primary) inset;
    border-color: var(--pd-primary);
  }
}
</style>
