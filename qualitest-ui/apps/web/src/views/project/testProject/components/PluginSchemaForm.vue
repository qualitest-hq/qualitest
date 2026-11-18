<template>
  <!-- 无有效 schema 时不挂载表单，避免空对象渲染出空白控件区 -->
  <VueForm
      v-if="hasSchema"
      class="plugin-schema-form"
      :model-value="modelValue || {}"
      :schema="schema"
      :form-footer="footerHidden"
      :form-props="formProps"
      @update:model-value="onUpdate"
  />
</template>

<script setup>
/**
 * 插件环境配置 / 项目挂载共用的 JSON Schema 表单。
 * 调用方直接传入标准 JSON Schema 与表单数据；本组件只负责渲染与双向绑定，不做其它格式翻译。
 * 启用、保存等业务按钮由外层页面自行提供，因此关闭组件自带的页脚提交区。
 */
import { computed } from 'vue'
import VueFormPkg from '@lljj/vue3-form-element'

/**
 * 解析第三方表单组件的真实导出。
 * 部分打包结果直接给出组件；另一些会再包一层 default，需要剥开后才能当作 Vue 组件使用。
 */
function resolveVueForm(mod) {
  if (!mod) return mod
  if (typeof mod.setup === 'function' || mod.name === 'VueForm') return mod
  const inner = mod.default
  if (inner && (typeof inner.setup === 'function' || inner.name === 'VueForm')) return inner
  return inner ?? mod
}

/** 实际用于模板的表单根组件 */
const VueForm = resolveVueForm(VueFormPkg)

const props = defineProps({
  /** 标准 JSON Schema；含 properties 或 type 时才渲染 */
  schema: { type: Object, default: null },
  /** 当前表单值对象，字段名与 schema.properties 对应 */
  modelValue: { type: Object, default: () => ({}) },
  /** 为 true 时整表只读，用于已启用配置回看或无编辑权限场景 */
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

/** 隐藏组件自带的保存/取消页脚，提交动作交给外层 */
const footerHidden = { show: false }

/** 判断 schema 是否值得渲染：至少有属性字段，或声明了类型 */
const hasSchema = computed(() => {
  const s = props.schema
  if (!s || typeof s !== 'object') return false
  if (s.properties && typeof s.properties === 'object' && Object.keys(s.properties).length) return true
  return !!s.type
})

/** 传给底层表单的布局与禁用态；标签置顶以适配窄卡片 */
const formProps = computed(() => ({
  disabled: props.disabled,
  labelPosition: 'top',
  labelWidth: 'auto',
}))

/** 向上同步表单值；异常值归一为空对象，保证外层始终拿到对象 */
function onUpdate(next) {
  emit('update:modelValue', next && typeof next === 'object' ? next : {})
}
</script>

<style scoped>
/* 卡片内表单项间距略收紧 */
.plugin-schema-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
/* 双保险：若页脚配置未生效，仍不显示内置按钮区 */
.plugin-schema-form :deep(.formFooter_item) {
  display: none;
}
</style>
