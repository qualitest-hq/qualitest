<template>
  <div class="prop-form">
    <div class="field">
      <label>pluginId</label>
      <input
        :value="node.data?.pluginId ?? ''"
        type="text"
        placeholder="qualitest.sut-sys-clock-dial"
        @input="(e) => patch({ pluginId: e.target.value })"
      />
    </div>
    <div class="field">
      <label>contributionId</label>
      <input
        :value="node.data?.contributionId ?? ''"
        type="text"
        placeholder="clock-advance"
        @input="(e) => patch({ contributionId: e.target.value })"
      />
    </div>
    <div class="field">
      <label>capability</label>
      <input
        :value="node.data?.capability ?? ''"
        type="text"
        placeholder="clock.advance"
        @input="(e) => patch({ capability: e.target.value })"
      />
    </div>
    <div class="field">
      <label>config（JSON）</label>
      <textarea :value="configText" rows="6" @change="onConfig" />
      <div class="field__hint">能力参数对象；可用态按当前环境启用集即时算，不落盘 enabled</div>
    </div>
    <AlwaysRunPropertySection :node="node" />
  </div>
</template>

<script setup>
/**
 * 插件动作节点属性面板：编辑插件 id、贡献点 id、能力名称与能力参数（JSON）。
 */
import { computed } from 'vue';
import { useFlowNodes } from '../../composables/useFlowNodes';
import AlwaysRunPropertySection from './AlwaysRunPropertySection.vue';

const props = defineProps({
  /** 当前选中的插件动作节点 */
  node: { type: Object, required: true },
});

const { patchNodeData } = useFlowNodes();

/** 能力参数的格式化 JSON 文本，用于文本框回显；序列化失败时回退为空对象 */
const configText = computed(() => {
  try {
    return JSON.stringify(props.node.data?.config ?? {}, null, 2);
  } catch {
    return '{}';
  }
});

/** 把局部字段合并写回当前节点数据 */
function patch(p) {
  patchNodeData(props.node.id, p);
}

/** 参数文本框变更：解析为 JSON 后写回节点；JSON 非法时忽略本次输入，保留原值 */
function onConfig(e) {
  try {
    patch({ config: JSON.parse(e.target.value || '{}') });
  } catch {
    /* JSON 非法，忽略本次修改 */
  }
}
</script>

<style scoped lang="scss">
@use '../../styles/propPanel.scss' as prop;
@include prop.flow-prop-panel-root;
</style>
