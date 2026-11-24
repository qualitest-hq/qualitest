<template>
  <aside :class="{ 'is-collapsed': store.ui.leftCollapsed }" class="flow-panel flow-panel--left panel">
    <div class="panel-tabs flow-panel-tabs">
      <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="{ 'is-active': store.ui.leftTab === tab.id }"
          class="panel-tab flow-panel-tab"
          type="button"
          @click="store.setLeftTab(tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>
    <div class="panel__head flow-panel__head">
      <div class="panel__head-row flow-panel__head-row">
        <div class="panel__title flow-panel__title">{{ panelTitle }}</div>
        <button class="panel__toggle flow-panel__toggle" type="button" @click="store.ui.leftCollapsed = true">
          收起
        </button>
      </div>
      <div class="panel__desc flow-panel__desc">{{ panelDesc }}</div>
    </div>
    <div
        :class="{
          'flow-panel__body--flush': store.ui.leftTab === 'runConfig',
          'flow-panel__body--library': ['params', 'nodes', 'runs'].includes(store.ui.leftTab),
        }"
        class="panel__body flow-panel__body"
    >
      <RunConfigPanel v-if="store.ui.leftTab === 'runConfig'" />
      <NodePalette v-else-if="store.ui.leftTab === 'nodes'" />
      <ParamLibraryPanel v-else-if="store.ui.leftTab === 'params'" />
      <RunLibraryPanel v-else-if="store.ui.leftTab === 'runs'" />
    </div>
  </aside>
</template>

<script setup>
/** 左栏四个 Tab：运行场景、节点库、参数库、运行库；模板画布隐藏运行库 */
import { computed } from 'vue'

import NodePalette from './NodePalette.vue'
import ParamLibraryPanel from './ParamLibraryPanel.vue'
import RunConfigPanel from './RunConfigPanel.vue'
import RunLibraryPanel from './RunLibraryPanel.vue'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'

const store = useFlowCanvasStore()

/** 左侧面板的标签页列表（跑前入口 / 节点库 / 参数库 / 运行库），按权限等条件过滤后展示 */
const tabs = computed(() => {
  const all = [
    { id: 'runConfig', label: '跑前入口' },
    { id: 'nodes', label: '节点库' },
    { id: 'params', label: '参数库' },
    { id: 'runs', label: '运行库' },
  ]
  if (store.canvasMode === 'template') {
    return all.filter((t) => t.id !== 'runs')
  }
  return all
})

/** 各标签页顶部显示的标题与说明文案 */
const meta = {
  runConfig: {
    title: '跑前入口',
    desc: '选入口（及场景）后点跑；场景值树在入口属性 / 右侧面板编辑',
  },
  nodes: { title: '节点库', desc: '拖拽或双击添加到画布（可多个入口）' },
  params: { title: '参数库', desc: 'Env / Asset 配置态叶子；搜索；复制 {{path}} 或值' },
  runs: { title: '运行库', desc: '正式 Run 记录与回放' },
}

const panelTitle = computed(() => meta[store.ui.leftTab]?.title ?? '')
const panelDesc = computed(() => meta[store.ui.leftTab]?.desc ?? '')
</script>
