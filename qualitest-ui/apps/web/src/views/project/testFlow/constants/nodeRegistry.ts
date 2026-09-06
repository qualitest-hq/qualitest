/**
 * 节点类型注册表：type → 画布组件 + 右侧属性区。
 * 新增类型时在此登记，画布与属性面板自动生效（含 input）。
 */
import type { Component } from 'vue';

import AssertNode from '../nodes/AssertNode.vue';
import AssignNode from '../nodes/AssignNode.vue';
import ConditionNode from '../nodes/ConditionNode.vue';
import DelayNode from '../nodes/DelayNode.vue';
import HttpNode from '../nodes/HttpNode.vue';
import AssertPropertySection from '../panels/property/AssertPropertySection.vue';
import AssignPropertySection from '../panels/property/AssignPropertySection.vue';
import ConditionPropertySection from '../panels/property/ConditionPropertySection.vue';
import DefaultPropertySection from '../panels/property/DefaultPropertySection.vue';
import DelayPropertySection from '../panels/property/DelayPropertySection.vue';
import HttpPropertySection from '../panels/property/HttpPropertySection.vue';
import ScriptPropertySection from '../panels/property/ScriptPropertySection.vue';
import ScriptNode from '../nodes/ScriptNode.vue';
import SubflowNode from '../nodes/SubflowNode.vue';
import InputNode from '../nodes/InputNode.vue';
import SubflowPropertySection from '../panels/property/SubflowPropertySection.vue';
import InputPropertySection from '../panels/property/InputPropertySection.vue';

import type { FlowNodeTypeKey } from './nodeTypes';

/** 节点类型 → 画布组件 + 右侧属性区，新增类型只需改此文件 */
export interface NodeRegistryEntry {
  canvas: Component;
  property: Component;
}

export const NODE_REGISTRY: Record<FlowNodeTypeKey, NodeRegistryEntry> = {
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
};

export function getNodePropertyComponent(type: string | undefined): Component {
  if (!type) return DefaultPropertySection;
  return NODE_REGISTRY[type]?.property ?? DefaultPropertySection;
}

export function getRegisteredNodeTypes(): string[] {
  return Object.keys(NODE_REGISTRY);
}
