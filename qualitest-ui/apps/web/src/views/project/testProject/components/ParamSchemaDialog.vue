<template>
  <el-dialog
      :model-value="visible"
      append-to-body
      class="param-schema-dialog"
      destroy-on-close
      title="参数类型"
      width="520px"
      @update:model-value="(v) => emit('update:visible', v)"
      @closed="emit('closed')"
  >
    <div v-if="paramSchemaRow" class="param-schema-dialog-inner">
      <div class="param-schema-body">
        <div class="param-schema-main-type-row">
          <el-select
              v-model="paramSchemaRow.type"
              :allow-create="paramSchemaSource === 'formData'"
              :class="paramTypeSelectClass(paramSchemaRow.type)"
              class="param-schema-type-select"
              default-first-option
              filterable
              placeholder="类型"
              size="small"
          >
            <el-option v-for="t in paramSchemaTypeOptions" :key="t" :label="t" :value="t"/>
          </el-select>
        </div>
        <div class="param-schema-switches">
          <el-switch v-model="paramSchemaRow.required" size="small"/>
          <span class="lbl">必需</span>
          <el-switch v-model="paramSchemaRow.nullable" size="small"/>
          <span class="lbl">允许 NULL</span>
          <el-switch v-model="paramSchemaRow.deprecated" size="small"/>
          <span class="lbl">废弃</span>
        </div>
        <el-form class="param-schema-form" label-position="top" size="small">
          <el-row v-if="paramSchemaFormatVisible" :gutter="8">
            <el-col :span="12">
              <el-form-item label="format">
                <el-input v-model="paramSchemaRow.format" clearable placeholder="如 date、uuid"/>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                <template #label>
                  <span>行为</span>
                  <el-tooltip content="文档中的读写语义" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-select v-model="paramSchemaRow.behavior" class="param-schema-w100">
                  <el-option
                      v-for="o in PARAM_SCHEMA_BEHAVIOR"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-else :gutter="8">
            <el-col :span="24">
              <el-form-item>
                <template #label>
                  <span>行为</span>
                  <el-tooltip content="文档中的读写语义" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-select v-model="paramSchemaRow.behavior" class="param-schema-w100">
                  <el-option
                      v-for="o in PARAM_SCHEMA_BEHAVIOR"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <template v-if="paramSchemaStringConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小长度">
                  <el-input-number
                      v-model="paramSchemaRow.minLength"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大长度">
                  <el-input-number
                      v-model="paramSchemaRow.maxLength"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template v-else-if="paramSchemaNumberConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小值">
                  <el-input-number v-model="paramSchemaRow.minValue" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大值">
                  <el-input-number v-model="paramSchemaRow.maxValue" :controls="false" class="param-schema-w100"/>
                </el-form-item>
              </el-col>
            </el-row>
            <template v-if="paramSchemaAdvancedNumberConstraints">
              <div class="param-schema-advanced-label">高级区间</div>
              <el-row :gutter="8">
                <el-col :span="12">
                  <el-form-item label="exclusiveMinimum">
                    <el-input-number
                        v-model="paramSchemaRow.exclusiveMinimum"
                        :controls="false"
                        class="param-schema-w100"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="exclusiveMaximum">
                    <el-input-number
                        v-model="paramSchemaRow.exclusiveMaximum"
                        :controls="false"
                        class="param-schema-w100"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="8">
                <el-col :span="12">
                  <el-form-item label="multipleOf">
                    <el-input-number v-model="paramSchemaRow.multipleOf" :controls="false" class="param-schema-w100"/>
                  </el-form-item>
                </el-col>
              </el-row>
            </template>
          </template>
          <template v-else-if="paramSchemaArrayConstraints">
            <el-row :gutter="8">
              <el-col :span="12">
                <el-form-item label="最小元素数">
                  <el-input-number
                      v-model="paramSchemaRow.minItems"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最大元素数">
                  <el-input-number
                      v-model="paramSchemaRow.maxItems"
                      :controls="false"
                      :min="0"
                      class="param-schema-w100"
                      placeholder=">= 0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <el-row v-if="!paramSchemaArrayConstraints" :gutter="8">
            <el-col :span="paramSchemaStringConstraints ? 12 : 24">
              <el-form-item label="默认值">
                <el-input v-model="paramSchemaRow.defaultValue" clearable/>
              </el-form-item>
            </el-col>
            <el-col v-if="paramSchemaStringConstraints" :span="12">
              <el-form-item>
                <template #label>
                  <span>pattern</span>
                  <el-tooltip content="正则约束（常用于 string）" placement="top">
                    <el-icon class="param-schema-help">
                      <QuestionFilled/>
                    </el-icon>
                  </el-tooltip>
                </template>
                <el-input v-model="paramSchemaRow.pattern" clearable placeholder="^[A-Za-z0-9_-]+"/>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import {QuestionFilled} from '@element-plus/icons-vue'
import {PARAM_SCHEMA_BEHAVIOR} from '@/views/project/testProject/composables/apiDebugParamConstants'

defineProps({
  visible: {type: Boolean, default: false},
  paramSchemaRow: {type: Object, default: null},
  paramSchemaSource: {type: String, default: 'default'},
  paramSchemaFormatVisible: {type: Boolean, default: false},
  paramSchemaStringConstraints: {type: Boolean, default: false},
  paramSchemaNumberConstraints: {type: Boolean, default: false},
  paramSchemaArrayConstraints: {type: Boolean, default: false},
  paramSchemaAdvancedNumberConstraints: {type: Boolean, default: false},
  paramTypeSelectClass: {type: Function, required: true},
  paramSchemaTypeOptions: {type: Array, default: () => []}
})

const emit = defineEmits(['update:visible', 'closed'])
</script>

<style lang="scss">
.param-schema-dialog {
  .param-schema-dialog-inner {
    margin-top: -6px;
  }

  .param-schema-body {
    min-height: 0;
  }

  .param-schema-main-type-row {
    margin-bottom: 8px;
  }

  .param-schema-type-select {
    width: 100%;

    :deep(.el-input__inner) {
      font-size: 13px;
      font-weight: 500;
      color: #606266;
    }

    &.is-type-string :deep(.el-input__inner) {
      color: #16a34a;
    }

    &.is-type-integer :deep(.el-input__inner),
    &.is-type-number :deep(.el-input__inner) {
      color: #0b6edc;
    }

    &.is-type-boolean :deep(.el-input__inner) {
      color: #7c3aed;
    }

    &.is-type-array :deep(.el-input__inner) {
      color: #c2410c;
    }

    &.is-type-file :deep(.el-input__inner) {
      color: #db2777;
    }
  }

  .param-schema-switches {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 14px;
    margin-bottom: 8px;
    font-size: 12px;
    color: #606266;

    .lbl {
      margin-right: 4px;
      margin-left: -6px;
    }

    &--2 {
      margin-bottom: 10px;
    }
  }

  .param-schema-form {
    :deep(.el-form-item) {
      margin-bottom: 10px;
    }

    :deep(.el-form-item__label) {
      font-size: 12px;
      padding-bottom: 2px;
    }
  }

  .param-schema-help {
    margin-left: 4px;
    font-size: 14px;
    color: #c0c4cc;
    vertical-align: -2px;
    cursor: help;
  }

  .param-schema-w100 {
    width: 100%;
  }

  .param-schema-advanced-label {
    margin: 4px 0 8px;
    font-size: 12px;
    font-weight: 600;
    color: #606266;
  }
}
</style>
