<template>
  <div class="debug-inner-panel debug-inner-panel--body" role="tabpanel">
    <div class="body-mode-row">
      <el-radio-group v-model="draftRequestConfig.body.mode" class="body-mode-group" size="small">
        <el-radio-button v-for="opt in BODY_MODES" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="draftRequestConfig.body.mode === 'form-data'">
      <DebugKvSheet
          :enable-file-upload-for-file-type="true"
          :rows="draftRequestConfig.body.formData"
          :show-description-column="true"
          :show-example-column="false"
          :show-type-column="true"
          :virtual-min-rows="embedInDesign ? 28 : 52"
          description-placeholder="说明"
          name-label="参数名"
          name-placeholder="名称"
          value-label="参数值"
          value-placeholder="值"
          @remove="(i) => removeRow(draftRequestConfig.body.formData, i)"
      >
        <template #type="{ row }">
          <DebugParamTypeCell
              :row="row"
              :type-class-fn="paramTypeSelectClass"
              :type-options="PARAM_TYPES_FORM_DATA"
              @open-schema="openParamSchemaDialog(row, 'formData')"
              @toggle-required="toggleDebugParamRequired(row)"
              @type-change="onKvParamTypeChange(row)"
          />
        </template>
      </DebugKvSheet>
    </template>

    <template v-else-if="draftRequestConfig.body.mode === 'x-www-form-urlencoded'">
      <DebugKvSheet
          :rows="draftRequestConfig.body.urlencoded"
          :show-description-column="true"
          :show-example-column="false"
          :show-type-column="true"
          :virtual-min-rows="embedInDesign ? 28 : 52"
          description-placeholder="说明"
          name-label="参数名"
          name-placeholder="名称"
          value-label="参数值"
          value-placeholder="值"
          @remove="(i) => removeRow(draftRequestConfig.body.urlencoded, i)"
      >
        <template #type="{ row }">
          <DebugParamTypeCell
              :allow-type-create="false"
              :row="row"
              :type-class-fn="paramTypeSelectClass"
              :type-options="PARAM_TYPES_URLENCODED"
              @open-schema="openParamSchemaDialog(row, 'urlencoded')"
              @toggle-required="toggleDebugParamRequired(row)"
              @type-change="onKvParamTypeChange(row)"
          />
        </template>
      </DebugKvSheet>
    </template>

    <template v-else-if="draftRequestConfig.body.mode === 'json'">
      <div class="body-json-json-shell">
        <el-tabs
            :model-value="activeBodyJsonSubTab"
            class="body-json-inner-tabs"
            @update:model-value="(v) => emit('update:activeBodyJsonSubTab', v)"
        >
          <el-tab-pane label="数据结构" lazy name="schema">
            <BodyJsonSchemaTree
                :ref="(el) => { if (bindSchemaTreeRef) bindSchemaTreeRef(el) }"
                compact
                :show-example-column="false"
                :virtual-min-flat-rows="embedInDesign ? 32 : 44"
                v-model="draftRequestConfig.body.json.schema"
                @open-schema="onBodyJsonOpenSchema"
            />
          </el-tab-pane>
          <el-tab-pane label="请求示例" lazy name="raw">
            <el-input
                :model-value="bodyJsonText"
                :rows="14"
                class="debug-body-raw"
                placeholder="JSON"
                type="textarea"
                @update:model-value="(v) => emit('update:bodyJsonText', v)"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>

    <template v-else-if="draftRequestConfig.body.mode === 'xml' || draftRequestConfig.body.mode === 'text'">
      <el-input
          v-model="draftRequestConfig.body.text"
          :placeholder="draftRequestConfig.body.mode === 'xml' ? 'XML' : '文本'"
          :rows="14"
          class="debug-body-raw"
          type="textarea"
      />
    </template>

    <template v-else-if="draftRequestConfig.body.mode === 'binary'">
      <div class="debug-binary-body">
        <div class="debug-binary-simple">
          <input
              :ref="(el) => { if (assignBinaryInputRef) assignBinaryInputRef(el) }"
              class="debug-binary-file-native"
              tabindex="-1"
              type="file"
              @change="onBinaryFileInputChange"
          />
          <button class="debug-binary-upload-btn" type="button" @click="triggerBinaryFilePick">
            <el-icon class="debug-binary-upload-btn-ico">
              <Upload/>
            </el-icon>
            <span>上传</span>
          </button>
          <p :title="binaryFileLabel" class="debug-binary-file-line">
            <span class="debug-binary-file-line-text">{{ binaryFileLabel }}</span>
            <button
                v-if="binaryBodyFile"
                class="debug-binary-file-line-clear"
                type="button"
                @click="clearBinaryBodyFile"
            >
              清除
            </button>
          </p>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="debug-body-placeholder">
        <p class="debug-body-placeholder-title">Body 为 none</p>
        <p class="debug-body-placeholder-desc">无需请求体或在上方的 mode 中选择类型</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import {Upload} from '@element-plus/icons-vue'
import BodyJsonSchemaTree from './BodyJsonSchemaTree.vue'
import DebugKvSheet from './DebugKvSheet.vue'
import DebugParamTypeCell from './DebugParamTypeCell.vue'
import {
  BODY_MODES,
  PARAM_TYPES_FORM_DATA,
  PARAM_TYPES_URLENCODED
} from '@/views/project/testProject/composables/apiDebugParamConstants'

defineProps({
  draftRequestConfig: {type: Object, required: true},
  bodyJsonText: {type: String, default: ''},
  activeBodyJsonSubTab: {type: String, default: 'schema'},
  binaryBodyFile: {default: null},
  binaryFileLabel: {type: String, default: '未选择文件'},
  embedInDesign: {type: Boolean, default: false},
  paramTypeSelectClass: {type: Function, required: true},
  removeRow: {type: Function, required: true},
  openParamSchemaDialog: {type: Function, required: true},
  toggleDebugParamRequired: {type: Function, required: true},
  onKvParamTypeChange: {type: Function, required: true},
  onBodyJsonOpenSchema: {type: Function, required: true},
  triggerBinaryFilePick: {type: Function, required: true},
  onBinaryFileInputChange: {type: Function, required: true},
  clearBinaryBodyFile: {type: Function, required: true},
  /** (el) => { bodyJson.bodyJsonSchemaTreeRef.value = el } */
  bindSchemaTreeRef: {type: Function, default: null},
  /** (el) => { draft.binaryFileInputRef.value = el } */
  assignBinaryInputRef: {type: Function, default: null}
})

const emit = defineEmits(['update:bodyJsonText', 'update:activeBodyJsonSubTab'])
</script>

<style lang="scss" scoped>
.debug-inner-panel {
  min-height: 0;

  &--body {
    display: flex;
    flex-direction: column;
    gap: 0;
  }
}

.body-mode-row {
  flex-shrink: 0;
  margin: 0;
  padding: 4px 6px 6px;
  overflow-x: auto;
  background: var(--pd-bg-sunken, rgba(233, 242, 252, 0.5));
  border-bottom: 1px solid var(--pd-divider, var(--pd-border-muted));
}

.body-mode-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  max-width: 100%;
  border: none;
  background: transparent;

  :deep(.el-radio-button) {
    margin: 0 !important;
  }

  :deep(.el-radio-button__inner) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 28px;
    padding: 4px 11px;
    border-radius: 6px !important;
    border: 1px solid var(--pd-border-muted, #d6e6f5) !important;
    box-shadow: none !important;
    font-size: 12px;
    font-weight: 500;
    line-height: 1.3;
    color: var(--pd-text-tab);
    background: rgba(255, 255, 255, 0.92);
    transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  }

  :deep(.el-radio-button:first-child .el-radio-button__inner) {
    border-radius: 6px !important;
  }

  :deep(.el-radio-button:last-child .el-radio-button__inner) {
    border-radius: 6px !important;
  }

  :deep(.el-radio-button:not(.is-active):hover .el-radio-button__inner) {
    color: var(--pd-text);
    border-color: var(--pd-border-subtle, #c5d8ec) !important;
    background: var(--pd-surface-elevated);
  }

  :deep(.el-radio-button.is-active .el-radio-button__inner) {
    color: #fff !important;
    background: var(--pd-primary, #0b6edc) !important;
    border-color: var(--pd-primary, #0b6edc) !important;
  }
}

/* Body：类型条下方通栏铺满，不另加顶/侧垫白 */
.debug-inner-panel--body > .body-mode-row ~ * {
  padding: 0;
  box-sizing: border-box;
}

/* 须高于上一则，否则 padding 被清空后 binary 区顶距不生效 */
.debug-inner-panel--body > .body-mode-row ~ .debug-binary-body {
  padding: 24px 4px 12px;
}

.body-json-json-shell {
  width: 100%;
}

.body-json-inner-tabs {
  width: 100%;

  :deep(.el-tabs__header) {
    margin: 0 0 4px 0;
  }

  :deep(.el-tabs__nav-wrap) {
    padding: 0 6px;
  }

  :deep(.el-tabs__content) {
    padding: 0;
  }
}

.debug-body-raw {
  width: 100%;
  max-width: 100%;
  font-family: ui-monospace, Consolas, 'Courier New', monospace;
  font-size: 12px;

  :deep(.el-textarea) {
    width: 100%;
  }

  :deep(.el-textarea__inner) {
    font-family: inherit;
    line-height: 1.5;
    border-radius: 6px;
    padding: 10px 12px;
    color: var(--pd-text);
    background: var(--pd-surface-elevated);
    border: 1px solid var(--pd-divider, var(--pd-border-muted));
    box-shadow: none;

    &::placeholder {
      color: var(--pd-text-muted);
      opacity: 0.85;
    }

    &:hover {
      border-color: var(--pd-border-subtle, #c5d8ec);
    }

    &:focus {
      border-color: var(--pd-primary, #0b6edc);
      box-shadow: 0 0 0 1px var(--pd-primary, #0b6edc) inset;
    }
  }
}

.debug-body-placeholder {
  margin: 0;
  padding: 16px 6px 20px;
  text-align: center;
  border: none;
  border-radius: 0;
  background: transparent;
}

.debug-body-placeholder-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-text-muted);
}

.debug-body-placeholder-desc {
  margin: 0;
  font-size: 12px;
  color: var(--pd-text-muted);
  line-height: 1.5;
}

.debug-binary-body {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.debug-binary-simple {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
}

.debug-binary-file-native {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

.debug-binary-upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 40px;
  margin: 0;
  padding: 0 16px;
  border: 1px solid var(--pd-divider, #dcdfe6);
  border-radius: 8px;
  background: #fff;
  color: var(--pd-text, #303133);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease,
    background 0.15s ease;
}

.debug-binary-upload-btn:hover {
  border-color: color-mix(in srgb, var(--pd-primary, #409eff) 45%, var(--pd-divider, #dcdfe6));
  color: var(--pd-primary, #409eff);
  background: color-mix(in srgb, var(--pd-primary, #409eff) 6%, #fff);
}

.debug-binary-upload-btn:active {
  background: color-mix(in srgb, var(--pd-primary, #409eff) 10%, #fff);
}

.debug-binary-upload-btn-ico {
  font-size: 18px;
}

.debug-binary-file-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin: 0;
  width: 100%;
  min-height: 18px;
  font-size: 12px;
  color: var(--pd-text-muted);
  text-align: center;
}

.debug-binary-file-line-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  max-width: 100%;
}

.debug-binary-file-line-clear {
  flex-shrink: 0;
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.debug-binary-file-line-clear:hover {
  opacity: 0.85;
}
</style>
