/**
 * 节点类型注册表：type → 画布组件 + 右侧属性区。
 * 新增类型时在此登记，画布与属性面板自动生效。
 */
import type { Component } from 'vue';

import AssertNode from '../nodes/AssertNode.vue';
import AssignNode from '../nodes/AssignNode.vue';
import ConditionNode from '../nodes/ConditionNode.vue';
import DelayNode from '../nodes/DelayNode.vue';
import EntryNode from '../nodes/EntryNode.vue';
import HttpNode from '../nodes/HttpNode.vue';
import HttpReturnNode from '../nodes/HttpReturnNode.vue';
import SubflowReturnNode from '../nodes/SubflowReturnNode.vue';
import AssertPropertySection from '../panels/property/AssertPropertySection.vue';
import AssignPropertySection from '../panels/property/AssignPropertySection.vue';
import ConditionPropertySection from '../panels/property/ConditionPropertySection.vue';
import DefaultPropertySection from '../panels/property/DefaultPropertySection.vue';
import DelayPropertySection from '../panels/property/DelayPropertySection.vue';
import HttpEntryPropertySection from '../panels/property/HttpEntryPropertySection.vue';
import HttpPropertySection from '../panels/property/HttpPropertySection.vue';
import HttpReturnPropertySection from '../panels/property/HttpReturnPropertySection.vue';
import SubflowReturnPropertySection from '../panels/property/SubflowReturnPropertySection.vue';
import ScenarioEntryPropertySection from '../panels/property/ScenarioEntryPropertySection.vue';
import ScriptPropertySection from '../panels/property/ScriptPropertySection.vue';
import ScriptNode from '../nodes/ScriptNode.vue';
import SubflowEntryPropertySection from '../panels/property/SubflowEntryPropertySection.vue';
import SubflowNode from '../nodes/SubflowNode.vue';
import InputNode from '../nodes/InputNode.vue';
import PluginActionNode from '../nodes/PluginActionNode.vue';
import SubflowPropertySection from '../panels/property/SubflowPropertySection.vue';
import InputPropertySection from '../panels/property/InputPropertySection.vue';
import PluginActionPropertySection from '../panels/property/PluginActionPropertySection.vue';
import ProtocolEntryPropertySection from '../panels/property/ProtocolEntryPropertySection.vue';

import { isProtocolEntryType } from '@/utils/flow/nodeTypes';
import type { FlowNodeTypeKey } from './nodeTypes';

/** 节点类型 → 画布组件 + 右侧属性区，新增类型只需改此文件 */
export interface NodeRegistryEntry {
  canvas: Component;
  property: Component;
}

export const NODE_REGISTRY: Record<FlowNodeTypeKey, NodeRegistryEntry> = {
  'scenario-entry': {
    canvas: EntryNode,
    property: ScenarioEntryPropertySection,
  },
  'http-entry': {
    canvas: EntryNode,
    property: HttpEntryPropertySection,
  },
  'subflow-entry': {
    canvas: EntryNode,
    property: SubflowEntryPropertySection,
  },
  'http-return': {
    canvas: HttpReturnNode,
    property: HttpReturnPropertySection,
  },
  'subflow-return': {
    canvas: SubflowReturnNode,
    property: SubflowReturnPropertySection,
  },
  http: {
    canvas: HttpNode,
    property: HttpPropertySection,
  },
  assert: {
    canvas: AssertNode,
    property: AssertPropertySection,
  },
  delay: {
    canvas: DelayNode,
    property: DelayPropertySection,
  },
  condition: {
    canvas: ConditionNode,
    property: ConditionPropertySection,
  },
  assign: {
    canvas: AssignNode,
    property: AssignPropertySection,
  },
  script: {
    canvas: ScriptNode,
    property: ScriptPropertySection,
  },
  subflow: {
    canvas: SubflowNode,
    property: SubflowPropertySection,
  },
  input: {
    canvas: InputNode,
    property: InputPropertySection,
  },
  // 插件动作：卡片展示插件与能力，右侧面板编辑插件 id / 能力 / 参数
  pluginAction: {
    canvas: PluginActionNode,
    property: PluginActionPropertySection,
  },
};

/** 按节点 type 取右侧属性面板组件；协议入口走平台壳 maxSteps；未知类型回落默认面板 */
export function getNodePropertyComponent(type: string | undefined): Component {
  if (!type) return DefaultPropertySection;
  if (isProtocolEntryType(type)) return ProtocolEntryPropertySection;
  return NODE_REGISTRY[type as FlowNodeTypeKey]?.property ?? DefaultPropertySection;
}

/** 已注册的全部节点 type 列表（含入口与返回） */
export function getRegisteredNodeTypes(): string[] {
  return Object.keys(NODE_REGISTRY);
}
