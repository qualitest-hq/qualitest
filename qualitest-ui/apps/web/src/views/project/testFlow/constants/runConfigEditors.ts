/** 运行配置表单：flowSeed / flowOutputs 键值编辑器列定义 */

export interface FlowKvColumn {
  field: string;
  label: string;
  placeholder?: string;
}

export const FLOW_SEED_COLUMNS: FlowKvColumn[] = [
  { field: 'key', label: '变量名', placeholder: '如 loginUser' },
  { field: 'value', label: '初值', placeholder: '如 admin' },
];

export const FLOW_OUTPUT_COLUMNS: FlowKvColumn[] = [
  { field: 'name', label: '变量名', placeholder: '如 token' },
  { field: 'description', label: '说明', placeholder: '可选说明' },
];

export const FLOW_SEED_ADD_LABEL = '＋ 添加变量';
export const FLOW_OUTPUT_ADD_LABEL = '＋ 添加返回值';
