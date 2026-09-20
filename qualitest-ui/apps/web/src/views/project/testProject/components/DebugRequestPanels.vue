<template>
  <div class="debug-request-pane">
    <div class="debug-inner-tab-shell">
      <nav aria-label="请求配置" class="debug-inner-tablist" role="tablist">
        <button
            :aria-selected="activeDebugRequestTab === 'headers'"
            :class="{ active: activeDebugRequestTab === 'headers' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'headers')"
        >
          Headers
        </button>
        <button
            :aria-selected="activeDebugRequestTab === 'query'"
            :class="{ active: activeDebugRequestTab === 'query' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'query')"
        >
          <span>Query</span>
          <span v-if="debugQueryTabBadge > 0" class="debug-tab-count">{{ debugQueryTabBadge }}</span>
        </button>
        <button
            :aria-selected="activeDebugRequestTab === 'body'"
            :class="{ active: activeDebugRequestTab === 'body' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'body')"
        >
          <span>Body</span>
          <span v-if="debugBodyTabBadge > 0" class="debug-tab-count">{{ debugBodyTabBadge }}</span>
        </button>
        <button
            :aria-selected="activeDebugRequestTab === 'path'"
            :class="{ active: activeDebugRequestTab === 'path' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'path')"
        >
          <span>Path</span>
          <span v-if="debugPathTabBadge > 0" class="debug-tab-count">{{ debugPathTabBadge }}</span>
        </button>
        <button
            :aria-selected="activeDebugRequestTab === 'cookies'"
            :class="{ active: activeDebugRequestTab === 'cookies' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'cookies')"
        >
          Cookies
        </button>
        <button
            :aria-selected="activeDebugRequestTab === 'preScript'"
            :class="{ active: activeDebugRequestTab === 'preScript' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'preScript')"
        >
          前置脚本
        </button>
        <button
            :aria-selected="activeDebugRequestTab === 'postScript'"
            :class="{ active: activeDebugRequestTab === 'postScript' }"
            class="debug-inner-tab"
            role="tab"
            type="button"
            @click="emit('update:activeDebugRequestTab', 'postScript')"
        >
          后置脚本
        </button>
      </nav>

      <div
          class="debug-inner-panels"
          :class="{
            'debug-inner-panels--script':
              activeDebugRequestTab === 'preScript' || activeDebugRequestTab === 'postScript',
          }"
      >
        <div v-if="activeDebugRequestTab === 'headers'" class="debug-inner-panel" role="tabpanel">
          <DebugKvSheet
              :rows="draftHeaderRows"
              :virtual-min-rows="embedInDesign ? 28 : 52"
              name-label="名称"
              name-placeholder="Header 名"
              value-label="值"
              value-placeholder="值"
              @remove="(i) => removeRow(draftHeaderRows, i)"
          />
        </div>

        <div v-if="activeDebugRequestTab === 'query'" class="debug-inner-panel" role="tabpanel">
          <DebugKvSheet
              :rows="draftRequestConfig.queryParams"
              :show-description-column="true"
              :show-example-column="false"
              :show-type-column="true"
              :virtual-min-rows="embedInDesign ? 28 : 52"
              description-placeholder="说明"
              name-label="参数名"
              name-placeholder="名称"
              value-label="参数值"
              value-placeholder="值"
              @remove="(i) => removeRow(draftRequestConfig.queryParams, i)"
          >
            <template #type="{ row }">
              <DebugParamTypeCell
                  :allow-type-create="false"
                  :row="row"
                  :type-class-fn="paramTypeSelectClass"
                  :type-options="PARAM_TYPES_QUERY_PATH"
                  @open-schema="openParamSchemaDialog(row, 'query')"
                  @toggle-required="toggleDebugParamRequired(row)"
                  @type-change="onKvParamTypeChange(row)"
              />
            </template>
          </DebugKvSheet>
        </div>

        <DebugBodyPanel
            v-if="activeDebugRequestTab === 'body'"
            :draft-request-config="draftRequestConfig"
            :body-json-text="bodyJsonText"
            :active-body-json-sub-tab="activeBodyJsonSubTab"
            :binary-body-file="binaryBodyFile"
            :binary-file-label="binaryFileLabel"
            :embed-in-design="embedInDesign"
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
            @update:body-json-text="(v) => emit('update:bodyJsonText', v)"
            @update:active-body-json-sub-tab="(v) => emit('update:activeBodyJsonSubTab', v)"
        />

        <div v-if="activeDebugRequestTab === 'path'" class="debug-inner-panel" role="tabpanel">
          <DebugKvSheet
              :rows="draftRequestConfig.pathParams"
              :show-description-column="true"
              :show-example-column="false"
              :show-type-column="true"
              :virtual-min-rows="embedInDesign ? 28 : 52"
              description-placeholder="说明"
              name-label="参数名"
              name-placeholder="名称"
              value-label="参数值"
              value-placeholder="值"
              @remove="(i) => removeRow(draftRequestConfig.pathParams, i)"
          >
            <template #type="{ row }">
              <DebugParamTypeCell
                  :allow-type-create="false"
                  :row="row"
                  :type-class-fn="paramTypeSelectClass"
                  :type-options="PARAM_TYPES_QUERY_PATH"
                  @open-schema="openParamSchemaDialog(row, 'path')"
                  @toggle-required="toggleDebugParamRequired(row)"
                  @type-change="onKvParamTypeChange(row)"
              />
            </template>
          </DebugKvSheet>
        </div>

        <div v-if="activeDebugRequestTab === 'cookies'" class="debug-inner-panel" role="tabpanel">
          <DebugKvSheet
              :rows="draftCookieRows"
              :virtual-min-rows="embedInDesign ? 28 : 52"
              name-label="名称"
              name-placeholder="Cookie 名"
              value-label="值"
              value-placeholder="值"
              @remove="(i) => removeRow(draftCookieRows, i)"
          />
        </div>

        <div
            v-if="activeDebugRequestTab === 'preScript'"
            class="debug-inner-panel debug-inner-panel--script"
            role="tabpanel"
        >
          <ApiScriptWorkbench
              :model-value="draftPreRequestScript"
              phase="pre"
              @update:model-value="(v) => emit('update:draftPreRequestScript', v)"
          />
        </div>
        <div
            v-if="activeDebugRequestTab === 'postScript'"
            class="debug-inner-panel debug-inner-panel--script"
            role="tabpanel"
        >
          <ApiScriptWorkbench
              :model-value="draftPostRequestScript"
              phase="post"
              @update:model-value="(v) => emit('update:draftPostRequestScript', v)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import DebugKvSheet from './DebugKvSheet.vue'
import DebugParamTypeCell from './DebugParamTypeCell.vue'
import DebugBodyPanel from './DebugBodyPanel.vue'
import ApiScriptWorkbench from '@/components/script/ApiScriptWorkbench.vue'
import {PARAM_TYPES_QUERY_PATH} from '@/views/project/testProject/composables/apiDebugParamConstants'

defineProps({
  activeDebugRequestTab: {type: String, default: 'headers'},
  draftRequestConfig: {type: Object, required: true},
  draftHeaderRows: {type: Array, required: true},
  draftCookieRows: {type: Array, required: true},
  draftPreRequestScript: {type: String, default: ''},
  draftPostRequestScript: {type: String, default: ''},
  bodyJsonText: {type: String, default: ''},
  activeBodyJsonSubTab: {type: String, default: 'schema'},
  binaryBodyFile: {default: null},
  binaryFileLabel: {type: String, default: '未选择文件'},
  embedInDesign: {type: Boolean, default: false},
  debugQueryTabBadge: {type: Number, default: 0},
  debugBodyTabBadge: {type: Number, default: 0},
  debugPathTabBadge: {type: Number, default: 0},
  paramTypeSelectClass: {type: Function, required: true},
  removeRow: {type: Function, required: true},
  openParamSchemaDialog: {type: Function, required: true},
  toggleDebugParamRequired: {type: Function, required: true},
  onKvParamTypeChange: {type: Function, required: true},
  onBodyJsonOpenSchema: {type: Function, required: true},
  triggerBinaryFilePick: {type: Function, required: true},
  onBinaryFileInputChange: {type: Function, required: true},
  clearBinaryBodyFile: {type: Function, required: true},
  bindSchemaTreeRef: {type: Function, default: null},
  assignBinaryInputRef: {type: Function, default: null}
})

const emit = defineEmits([
  'update:activeDebugRequestTab',
  'update:bodyJsonText',
  'update:activeBodyJsonSubTab',
  'update:draftPreRequestScript',
  'update:draftPostRequestScript'
])
</script>

<style lang="scss" scoped>
.debug-request-pane {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.debug-inner-tab-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: none;
  border-radius: 0;
  background: var(--pd-surface-elevated);
  box-shadow: none;
  overflow: hidden;
}

.debug-inner-tablist {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 2px;
  padding: 4px 6px 0;
  border-bottom: 1px solid var(--pd-border-subtle, var(--pd-divider, var(--pd-border-muted)));
  background: var(--pd-gradient-tabstrip);
  flex-shrink: 0;
}

.debug-inner-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 0 14px;
  height: 36px;
  border: 1px solid transparent;
  border-bottom: none;
  background: transparent;
  font-family: inherit;
  font-size: var(--pd-font-tab, 14px);
  font-weight: 500;
  color: var(--pd-text-muted, #5a6b86);
  cursor: pointer;
  border-radius: 6px 6px 0 0;
  transition:
    color 0.15s ease,
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;

  &:hover:not(.active) {
    color: var(--pd-text-tab, #334c6e);
    background: rgba(255, 255, 255, 0.6);
    border-color: var(--pd-border-muted, #d6e6f5);
  }

  &.active {
    color: var(--pd-primary, #0b6edc);
    font-weight: 600;
    background: var(--pd-surface-elevated, #fff);
    border-color: var(--pd-border-subtle, #c5d8ec);
    border-bottom: none;
    margin-bottom: -1px;
    z-index: 1;
    box-shadow: 0 1px 3px rgba(20, 60, 120, 0.06);

    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 3px;
      background: var(--pd-primary, #0b6edc);
      border-radius: 0;
      z-index: 2;
    }
  }

  &:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--pd-primary, #0b6edc) 45%, transparent);
    outline-offset: 1px;
  }
}

.debug-tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  color: #fff;
  background: #ef4444;
}

.debug-inner-panels {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-gutter: stable;
  padding: 0 0 4px 0;

  &--script {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding: 0;
  }
}

.debug-inner-panel {
  min-height: 0;
}

.debug-inner-panel--script {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 6px;
  box-sizing: border-box;

  :deep(.api-script-workbench) {
    flex: 1;
    min-height: 0;
    height: 100%;
  }
}
</style>
