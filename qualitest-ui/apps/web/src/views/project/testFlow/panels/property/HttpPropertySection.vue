<template>
  <div class="http-prop-card">
    <div class="field">
      <label>调用目标</label>
      <div class="call-mode-toggle">
        <label class="call-mode-opt">
          <input
              :checked="callMode === 'project'"
              name="callMode"
              type="radio"
              value="project"
              @change="setCallMode('project')"
          />
          项目接口
        </label>
        <label class="call-mode-opt">
          <input
              :checked="callMode === 'external'"
              name="callMode"
              type="radio"
              value="external"
              @change="setCallMode('external')"
          />
          外联 URL
        </label>
      </div>
    </div>

    <template v-if="callMode === 'project'">
      <div v-if="node.data.testProjectApiId" class="api-preview">
        <div class="api-preview__row">
          <span :class="methodBadgeClass">{{ httpMethod }}</span>
          <span class="api-preview__path">{{ node.data.apiPath || '—' }}</span>
        </div>
        <div class="api-preview__name">{{ node.data.apiName || '未命名接口' }}</div>
      </div>
      <div v-else class="api-preview is-empty">尚未绑定项目接口</div>
      <div class="http-prop-meta">{{ httpMeta }}</div>
      <ul v-if="nodeHealthWarnings.length" class="http-prop-health">
        <li v-for="(w, i) in nodeHealthWarnings" :key="i">{{ w.message || w.detail || w.code }}</li>
      </ul>
      <button class="btn btn--primary" style="width:100%" type="button" @click="emit('open-http-config', node.id)">
        {{ node.data.testProjectApiId ? '编辑接口与参数' : '选择接口并配置参数' }}
      </button>
      <div class="field__hint">请求参数仅存于本 HTTP 节点，不改项目 API 资产定义</div>
    </template>

    <template v-else>
      <div class="field">
        <label>HTTP 方法</label>
        <select :value="externalMethod" @change="onExternalMethodChange">
          <option v-for="m in HTTP_METHODS" :key="m" :value="m">{{ m }}</option>
        </select>
      </div>
      <div class="field">
        <label>外联 URL</label>
        <input
            :value="node.data.externalUrl || ''"
            placeholder="https://oauth.example.com/token 或 {{env.oauthTokenUrl}}"
            type="text"
            @input="onExternalUrlInput"
        />
        <div class="field__hint">支持 http/https 任意地址；凭证用 asset.* / env.* 占位符</div>
      </div>
      <div class="field">
        <label>Headers</label>
        <DebugKvSheet v-model="headersModel" label-key="headers" />
      </div>
      <div class="field">
        <label>Request Body</label>
        <textarea
            :value="node.data.requestBody || ''"
            placeholder="grant_type=client_credentials&client_id={{asset.oauth.clientId}}"
            rows="5"
            @input="onRequestBodyInput"
        />
      </div>
      <div class="field http-prop-scripts">
        <label>前置脚本（preScript）</label>
        <ApiScriptWorkbench
            :min-height="120"
            :model-value="node.data.preScript || ''"
            phase="pre"
            @update:model-value="onPreScriptChange"
        />
      </div>
      <div class="field http-prop-scripts">
        <label>后置脚本（postScript）</label>
        <ApiScriptWorkbench
            :min-height="120"
            :model-value="node.data.postScript || ''"
            phase="post"
            @update:model-value="onPostScriptChange"
        />
        <div class="field__hint">使用 api.request / api.response 对象，与项目接口调试脚本一致</div>
      </div>
    </template>
  </div>

  <div class="field">
    <label>超时时间（ms）</label>
    <input
        :value="timeoutVal"
        min="0"
        placeholder="30000"
        step="1000"
        type="number"
        @input="onTimeoutInput"
    />
    <div class="field__hint">单次 HTTP 请求最长等待；留空则执行时沿用环境配置</div>
  </div>

  <div class="field">
    <label class="http-run-session-label">
      <input
          :checked="useRunSession"
          type="checkbox"
          @change="onUseRunSessionChange"
      />
      共享 Run 会话 Cookie（useRunSession）
    </label>
    <div class="field__hint">开启后自动注入/吸收 Cookie，适用于登录后多步请求</div>
  </div>

  <div v-if="callMode === 'project'" class="field">
    <label>业务 Code 校验</label>
    <select :value="successCheckMode" @change="onSuccessCheckModeChange">
      <option value="inherit">继承项目响应约定</option>
      <option value="off">关闭（仅校验 HTTP 状态码）</option>
    </select>
    <div class="field__hint">默认按项目设置中的 code 路径与成功值校验；无 code 字段的接口可选关闭</div>
  </div>

  <div class="field http-prop-extracts">
    <label>响应提取</label>
    <DebugExtractEditor v-model="extractsModel" :auto-seed-row="false" :trial-body="trialBody" />
  </div>

  <SnapshotPropertySection :node="node" />
</template>

<script setup>
/** HTTP 节点属性：调用模式、接口/外联配置、超时、业务码校验、响应提取；项目接口模式下展示本节点语义健康告警 */
import { computed } from 'vue'

import DebugExtractEditor from '@/views/project/testProject/components/DebugExtractEditor.vue'

import ApiScriptWorkbench from '@/components/script/ApiScriptWorkbench.vue'
import DebugKvSheet from '../../components/DebugKvSheet.vue'
import SnapshotPropertySection from './SnapshotPropertySection.vue'
import { useFlowNodes } from '../../composables/useFlowNodes'
import { useFlowTrialBody } from '../../composables/useFlowTrialBody'
import { getMethodBadgeClass } from '../../constants/flowConfig'
import { useApiHealthStore } from '../../stores/apiHealthStore'
import { countHttpParamStats, emptyKVRow, HTTP_METHODS } from '../../utils/httpWorkbenchUtils'
import { isExternalCallMode } from '../../utils/httpSummary'
import { updateSummary } from '../../utils/nodeDataUtils'

const props = defineProps({
  node: { type: Object, required: true },
})

const { trialBody } = useFlowTrialBody()

const emit = defineEmits(['open-http-config'])

const { patchNodeData, replaceNodeData } = useFlowNodes()
const apiHealth = useApiHealthStore()

/** 当前选中节点上的语义告警（孤儿测值、抽取路径等） */
const nodeHealthWarnings = computed(() => apiHealth.warningsForNode(props.node?.id))

const callMode = computed(() => (isExternalCallMode(props.node?.data?.callMode) ? 'external' : 'project'))

const httpMethod = computed(() => {
  const d = props.node?.data || {}
  return String(d.httpMethod || '—').toUpperCase()
})

const externalMethod = computed(() => String(props.node?.data?.httpMethod || 'POST').toUpperCase())

const methodBadgeClass = computed(() => getMethodBadgeClass(httpMethod.value))

const httpMeta = computed(() => {
  if (!props.node?.data?.testProjectApiId) return '点击配置接口与请求参数'
  const stats = countHttpParamStats(props.node.data)
  return `Query ${stats.query} · Path ${stats.path} · Headers ${stats.headers} · Body ${stats.body}`
})

const timeoutVal = computed(() => {
  const v = props.node?.data?.timeoutMs
  return v != null && v !== '' ? v : ''
})

const useRunSession = computed(() => Boolean(props.node?.data?.useRunSession))

/** 当前节点业务码校验模式。
 * inherit：按项目响应约定校验 body 业务码；
 * off：关闭业务码校验，只看 HTTP 状态码。
 * 未配置时按 inherit 展示。
 */
const successCheckMode = computed(() => {
  const mode = props.node?.data?.successCheck?.mode
  if (mode === 'off') return 'off'
  return 'inherit'
})

const extractsModel = computed({
  get() {
    return props.node?.data?.extracts || []
  },
  set(val) {
    applyPatch({ extracts: val })
  },
})

const headersModel = computed({
  get() {
    const rows = props.node?.data?.headers
    return Array.isArray(rows) && rows.length ? rows : [emptyKVRow()]
  },
  set(val) {
    applyPatch({ headers: val })
  },
})

function applyPatch(patch) {
  const data = { ...props.node.data, ...patch }
  updateSummary('http', data)
  patchNodeData(props.node.id, { ...patch, summary: data.summary })
}

function setCallMode(mode) {
  if (mode === 'external') {
    // 切外联：清掉项目绑定与测值覆盖
    const data = {
      ...props.node.data,
      callMode: 'external',
      testProjectApiId: '',
      apiName: '',
      apiPath: '',
      httpMethod: props.node.data.httpMethod || 'POST',
      externalUrl: props.node.data.externalUrl || '',
      headers: props.node.data.headers || [emptyKVRow()],
      requestBody: props.node.data.requestBody || '',
      successCheck: { mode: 'off' },
    }
    delete data.requestValueOverrides
    updateSummary('http', data)
    replaceNodeData(props.node.id, data)
    return
  }
  // 切回项目接口：清掉外联字段与旧版整份 requestConfig
  const data = {
    ...props.node.data,
    callMode: 'project',
    externalUrl: '',
    requestBody: '',
    successCheck: { mode: 'inherit' },
  }
  delete data.requestConfig
  updateSummary('http', data)
  replaceNodeData(props.node.id, data)
}

/** 切换业务码校验：inherit 按项目约定校验；off 关闭，仅校验 HTTP 状态码 */
function onSuccessCheckModeChange(e) {
  applyPatch({ successCheck: { mode: e.target.value === 'off' ? 'off' : 'inherit' } })
}

function onExternalMethodChange(e) {
  applyPatch({ httpMethod: e.target.value })
}

function onExternalUrlInput(e) {
  applyPatch({ externalUrl: e.target.value })
}

function onRequestBodyInput(e) {
  applyPatch({ requestBody: e.target.value })
}

function onTimeoutInput(e) {
  const raw = e.target.value
  applyPatch({ timeoutMs: raw === '' ? null : Number(raw) })
}

function onUseRunSessionChange(e) {
  applyPatch({ useRunSession: e.target.checked })
}

function onPreScriptChange(val) {
  applyPatch({ preScript: val })
}

function onPostScriptChange(val) {
  applyPatch({ postScript: val })
}
</script>

<style scoped lang="scss">
.http-prop-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.call-mode-toggle {
  display: flex;
  gap: 16px;
}

.call-mode-opt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  cursor: pointer;
}

.api-preview {
  padding: 10px 12px;
  border-radius: var(--pd-radius-sm);
  border: 1px solid var(--pd-border-subtle);
  background: var(--pd-bg-sunken);

  &.is-empty {
    color: var(--pd-text-muted);
    font-size: 12px;
    text-align: center;
  }
}

.api-preview__row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.api-preview__path {
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--pd-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.api-preview__name {
  margin-top: 6px;
  font-size: 11px;
  color: var(--pd-text-muted);
}

.http-prop-meta {
  font-size: 11px;
  color: var(--pd-text-muted);
  line-height: 1.5;
}

.http-prop-health {
  /* 属性面板：当前 HTTP 节点的语义告警列表 */
  margin: 0 0 8px;
  padding-left: 16px;
  font-size: 11px;
  color: #a16207;
  line-height: 1.45;
}

.method-badge {
  flex-shrink: 0;
  min-width: 36px;
  padding: 1px 4px;
  text-align: center;
  font-size: 10px;
  font-weight: 700;
  font-family: ui-monospace, Consolas, monospace;
  line-height: 1.5;
  border-radius: 2px;
  letter-spacing: 0.02em;
  background: var(--pd-bg-sunken);
  color: #606266;
  border: 1px solid var(--pd-border-muted);

  &.is-GET {
    color: #67c23a;
    border-color: #c2e7b0;
    background: #f0f9eb;
  }

  &.is-POST {
    color: #e6a23c;
    border-color: #f5dab1;
    background: #fdf6ec;
  }

  &.is-PUT {
    color: #409eff;
    border-color: #b3d8ff;
    background: #ecf5ff;
  }

  &.is-PATCH {
    color: #909399;
    border-color: #dcdfe6;
    background: #f4f4f5;
  }

  &.is-DELETE {
    color: #f56c6c;
    border-color: #fbc4c4;
    background: #fef0f0;
  }
}

textarea {
  width: 100%;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid var(--pd-border-subtle);
  font-size: 11px;
  font-family: ui-monospace, Consolas, monospace;
  resize: vertical;
  box-sizing: border-box;
}

.http-run-session-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  cursor: pointer;
}

.http-prop-scripts {
  :deep(.api-script-workbench) {
    grid-template-columns: minmax(0, 1fr);
  }

  :deep(.api-script-workbench__aside) {
    display: none;
  }
}
</style>
