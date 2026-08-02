<template>
  <div class="script-prop">
    <div class="field script-prop__field">
      <label>语言</label>
      <div class="script-lang-switch" role="group" aria-label="脚本语言">
        <button
            v-for="opt in languageOptions"
            :key="opt.value"
            :class="{ 'is-active': currentLanguage === opt.value }"
            class="script-lang-switch__btn"
            type="button"
            @click="setLanguage(opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <div class="field script-prop__field script-prop__field--editor">
      <div class="script-editor-head">
        <label>源码</label>
        <span class="script-editor-head__badge">{{ languageBadge }}</span>
      </div>
      <ScriptSourceEditor
          :language="currentLanguage"
          :min-height="200"
          :model-value="node.data.source || ''"
          @update:model-value="onSourceChange"
      />
      <ScriptCtxHelpPanel :default-expanded="true" :language="currentLanguage" />
    </div>

    <div class="field script-prop__field">
      <label>超时</label>
      <div class="script-timeout-row">
        <input
            :max="SCRIPT_MAX_TIMEOUT_MS"
            :min="1"
            :value="node.data.timeoutMs ?? SCRIPT_DEFAULT_TIMEOUT_MS"
            class="script-timeout-row__input"
            step="100"
            type="number"
            @input="onTimeoutInput"
        />
        <span class="script-timeout-row__unit">ms</span>
      </div>
      <p class="field__hint">默认 {{ SCRIPT_DEFAULT_TIMEOUT_MS }} ms，上限 {{ SCRIPT_MAX_TIMEOUT_MS }} ms</p>
    </div>

    <SnapshotPropertySection :node="node" />
  </div>
</template>

<script setup>
/** 脚本节点属性：语言切换、源码编辑器、超时；缺 language 时写入默认 javascript 并同步 Staging draft */
import { computed, onMounted } from 'vue'

import ScriptSourceEditor from '@/components/script/ScriptSourceEditor.vue'
import ScriptCtxHelpPanel from '@/components/script/ScriptCtxHelpPanel.vue'

import {
  SCRIPT_DEFAULT_TIMEOUT_MS,
  SCRIPT_MAX_TIMEOUT_MS,
} from '../../constants/flowConfig'
import { useFlowNodes } from '../../composables/useFlowNodes'
import SnapshotPropertySection from './SnapshotPropertySection.vue'

const props = defineProps({
  node: { type: Object, required: true },
})

const { patchNodeData } = useFlowNodes()

const languageOptions = [
  { value: 'javascript', label: 'JavaScript' },
  { value: 'python', label: 'Python' },
]

/** 展示用语言：data.language 缺失时按 javascript 渲染 */
const currentLanguage = computed(() => {
  const lang = String(props.node.data.language || 'javascript')
  return lang === 'python' ? 'python' : 'javascript'
})

const languageBadge = computed(() =>
  currentLanguage.value === 'python' ? 'Python' : 'JS',
)

/**
 * 切换语言并写入节点 data。
 * 即使当前展示已是该语言，只要 data.language 仍为空/不一致，也要落盘（避免 Staging 确认仍报空）。
 */
function setLanguage(value) {
  const next = value === 'python' ? 'python' : 'javascript'
  if (String(props.node.data?.language || '') === next) return
  patchNodeData(props.node.id, { language: next })
}

function onSourceChange(value) {
  patchNodeData(props.node.id, { source: value })
}

function onTimeoutInput(e) {
  const raw = Number(e.target.value)
  const ms = Number.isFinite(raw) && raw > 0 ? Math.min(raw, SCRIPT_MAX_TIMEOUT_MS) : SCRIPT_DEFAULT_TIMEOUT_MS
  patchNodeData(props.node.id, { timeoutMs: ms })
}

/** 打开属性时若缺 language，补默认值，使 Staging draft 带上合法字段 */
onMounted(() => {
  const raw = props.node?.data?.language
  if (raw == null || String(raw).trim() === '') {
    patchNodeData(props.node.id, { language: 'javascript' })
  }
})
</script>

<style scoped lang="scss">
.script-prop {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.script-prop__field {
  margin-bottom: 0;
}

.script-prop__field--editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.script-lang-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 3px;
  border-radius: 8px;
  background: var(--pd-bg-sunken);
  border: 1px solid var(--pd-border-subtle);
}

.script-lang-switch__btn {
  height: 30px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s, box-shadow 0.15s;

  &:hover {
    color: var(--pd-text);
    background: rgba(255, 255, 255, 0.7);
  }

  &.is-active {
    color: #0e7490;
    background: #fff;
    border-color: #a5f3fc;
    box-shadow: 0 1px 2px rgba(8, 145, 178, 0.12);
  }
}

.script-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  label {
    margin-bottom: 0;
  }
}

.script-editor-head__badge {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #0e7490;
  background: #ecfeff;
  border: 1px solid #a5f3fc;
}

.script-timeout-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.script-timeout-row__input {
  flex: 1;
  min-width: 0;
  height: 32px;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-surface-elevated);
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: var(--pd-text);
  box-sizing: border-box;
  transition: box-shadow 0.15s, border-color 0.15s;

  &:focus {
    outline: none;
    box-shadow: 0 0 0 1px var(--pd-primary) inset;
    border-color: var(--pd-primary);
  }
}

.script-timeout-row__unit {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--pd-text-muted);
}
</style>
