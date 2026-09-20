<template>
  <div :class="{ 'is-embed-design': embedInDesign }" class="api-debug-workbench">
    <DebugUrlBar
        :method="draftRequestConfig.method"
        :draft-api-path="draftApiPath"
        :embed-in-design="embedInDesign"
        :debug-sending="debugSending"
        :api-debug-saving="apiDebugSaving"
        @update:method="setMethod"
        @update:draft-api-path="setDraftApiPath"
        @send="handleDebugSend"
        @save="handleSaveApiDebug"
    />

    <div class="debug-request-response">
      <DebugRequestPanels
          :active-debug-request-tab="activeDebugRequestTab"
          :draft-request-config="draftRequestConfig"
          :draft-header-rows="draftHeaderRows"
          :draft-cookie-rows="draftCookieRows"
          :draft-pre-request-script="draftPreRequestScript"
          :draft-post-request-script="draftPostRequestScript"
          :body-json-text="bodyJsonText"
          :active-body-json-sub-tab="activeBodyJsonSubTab"
          :binary-body-file="binaryBodyFile"
          :binary-file-label="binaryFileLabel"
          :embed-in-design="embedInDesign"
          :debug-query-tab-badge="debugQueryTabBadge"
          :debug-body-tab-badge="debugBodyTabBadge"
          :debug-path-tab-badge="debugPathTabBadge"
          :param-type-select-class="paramTypeSelectClass"
          :remove-row="removeRow"
          :open-param-schema-dialog="openParamSchemaDialog"
          :toggle-debug-param-required="toggleDebugParamRequired"
          :on-kv-param-type-change="onKvParamTypeChange"
          :on-body-json-open-schema="onBodyJsonOpenSchema"
          :trigger-binary-file-pick="triggerBinaryFilePick"
          :on-binary-file-input-change="onBinaryFileInputChange"
          :clear-binary-body-file="clearBinaryBodyFile"
          :bind-schema-tree-ref="bindSchemaTreeRef"
          :assign-binary-input-ref="assignBinaryInputRef"
          @update:active-debug-request-tab="setActiveDebugRequestTab"
          @update:body-json-text="setBodyJsonText"
          @update:active-body-json-sub-tab="setActiveBodyJsonSubTab"
          @update:draft-pre-request-script="setDraftPreRequestScript"
          @update:draft-post-request-script="setDraftPostRequestScript"
      />

      <DebugResponsePane
          v-if="!embedInDesign"
          :debug-response="debugResponse"
          :active-resp-tab="activeRespTab"
          :debug-script-tests-badge="debugScriptTestsBadge"
          :format-response-headers="formatResponseHeaders"
          @update:active-resp-tab="setActiveRespTab"
      />
    </div>

    <ParamSchemaDialog
        :visible="paramSchemaDialogVisible"
        :param-schema-row="paramSchemaRow"
        :param-schema-source="paramSchemaSource"
        :param-schema-format-visible="paramSchemaFormatVisible"
        :param-schema-string-constraints="paramSchemaStringConstraints"
        :param-schema-number-constraints="paramSchemaNumberConstraints"
        :param-schema-array-constraints="paramSchemaArrayConstraints"
        :param-schema-advanced-number-constraints="paramSchemaAdvancedNumberConstraints"
        :param-type-select-class="paramTypeSelectClass"
        :param-schema-type-options="paramSchemaTypeOptions"
        @update:visible="setParamSchemaDialogVisible"
        @closed="onParamSchemaDialogClosed"
    />
  </div>
</template>

<script setup>
import DebugUrlBar from './DebugUrlBar.vue'
import DebugRequestPanels from './DebugRequestPanels.vue'
import DebugResponsePane from './DebugResponsePane.vue'
import ParamSchemaDialog from './ParamSchemaDialog.vue'
import {paramTypeSelectClass} from '@/views/project/testProject/composables/apiDebugParamConstants'
import {useApiDebugDraft} from '@/views/project/testProject/composables/useApiDebugDraft'
import {useApiDebugBodyJson} from '@/views/project/testProject/composables/useApiDebugBodyJson'
import {useParamSchemaDialog} from '@/views/project/testProject/composables/useParamSchemaDialog'
import {useApiDebugScript} from '@/views/project/testProject/composables/useApiDebugScript'
import {useApiDebugSend} from '@/views/project/testProject/composables/useApiDebugSend'
import {useApiDebugPersist} from '@/views/project/testProject/composables/useApiDebugPersist'

const {proxy} = getCurrentInstance()

const props = defineProps({
  apiDetail: {type: Object, default: null},
  envList: {type: Array, default: () => []},
  testProjectEnvId: {type: [Number, String], default: null},
  /** 项目设置中的调试发送模式（Web）；桌面端由主进程固定，忽略此项 */
  httpTransportMode: {type: String, default: 'browser'},
  /** 嵌入「设计」页：仅请求配置，无发送/响应区/保存按钮 */
  embedInDesign: {type: Boolean, default: false}
})

const emit = defineEmits(['saved'])

const draft = useApiDebugDraft(props)
const {
  activeDebugRequestTab,
  activeRespTab,
  draftApiPath,
  draftRequestConfig,
  draftHeaderRows,
  draftCookieRows,
  bodyJsonText,
  binaryBodyFile,
  binaryFileInputRef,
  activeBodyJsonSubTab,
  draftPreRequestScript,
  draftPostRequestScript,
  debugResponse,
  debugSending,
  apiDebugSaving,
  removeRow,
  debugPathTabBadge,
  debugQueryTabBadge,
  debugBodyTabBadge
} = draft

const bodyJson = useApiDebugBodyJson(draft)
const {
  bodyJsonSchemaTreeRef,
  binaryFileLabel,
  triggerBinaryFilePick,
  onBinaryFileInputChange,
  clearBinaryBodyFile
} = bodyJson

const schema = useParamSchemaDialog(proxy, bodyJsonSchemaTreeRef)
const {
  paramSchemaDialogVisible,
  paramSchemaRow,
  paramSchemaSource,
  paramSchemaTypeOptions,
  paramSchemaStringConstraints,
  paramSchemaNumberConstraints,
  paramSchemaArrayConstraints,
  paramSchemaFormatVisible,
  paramSchemaAdvancedNumberConstraints,
  toggleDebugParamRequired,
  onKvParamTypeChange,
  openParamSchemaDialog,
  onBodyJsonOpenSchema,
  onParamSchemaDialogClosed
} = schema

const script = useApiDebugScript(() => ({
  testProjectId: props.apiDetail?.testProjectId,
  testProjectEnvId: props.testProjectEnvId,
  envList: props.envList
}))

const send = useApiDebugSend(props, draft, bodyJson, proxy, script)
const {handleDebugSend, formatResponseHeaders, debugScriptTestsBadge} = send

const persist = useApiDebugPersist(props, emit, draft, proxy)
const {handleSaveApiDebug, buildPersistPayload} = persist

function setMethod(v) {
  draftRequestConfig.value.method = v
}

function setDraftApiPath(v) {
  draftApiPath.value = v
}

function setActiveDebugRequestTab(v) {
  activeDebugRequestTab.value = v
}

function setBodyJsonText(v) {
  bodyJsonText.value = v
}

function setActiveBodyJsonSubTab(v) {
  activeBodyJsonSubTab.value = v
}

function setDraftPreRequestScript(v) {
  draftPreRequestScript.value = v
}

function setDraftPostRequestScript(v) {
  draftPostRequestScript.value = v
}

function setActiveRespTab(v) {
  activeRespTab.value = v
}

function setParamSchemaDialogVisible(v) {
  paramSchemaDialogVisible.value = v
}

function bindSchemaTreeRef(el) {
  bodyJsonSchemaTreeRef.value = el
}

function assignBinaryInputRef(el) {
  binaryFileInputRef.value = el
}

defineExpose({
  buildPersistPayload
})
</script>

<style lang="scss" scoped>
.api-debug-workbench {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: 100%;
  min-height: 360px;
  padding: 0;
  position: relative;

  &.is-embed-design {
    /* 由设计页 .design-embed-debug 给出高度，避免与下方区块 flex 均分导致叠层 */
    min-height: 0;
    height: 100%;

    .debug-request-response {
      flex: 1;
      min-height: 0;
    }

    :deep(.debug-request-pane) {
      flex: 1;
      min-height: 0;
    }
  }

  .debug-request-response {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 0;
  }
}
</style>
