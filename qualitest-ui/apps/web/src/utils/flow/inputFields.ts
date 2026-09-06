/**
 * Input 节点字段类型、默认行与画布摘要。
 *
 * type：text / textarea / password / number / boolean / select / multiselect / date / datetime。
 * 空 type 按 text。select / multiselect 须配置静态 options。
 */

/** 属性区 type 下拉选项（value 持久化，label 展示） */
export const INPUT_FIELD_TYPES = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'password', label: '密码' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '布尔' },
  { value: 'select', label: '单选' },
  { value: 'multiselect', label: '多选' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
] as const;

export type InputFieldType = (typeof INPUT_FIELD_TYPES)[number]['value'];

const KNOWN = new Set<string>(INPUT_FIELD_TYPES.map((t) => t.value));

/**
 * 设计态校验：type 是否合法。
 * null / 空白视为缺省 text，通过。
 */
export function isKnownInputFieldType(type: unknown): boolean {
  if (type == null || String(type).trim() === '') return true;
  return KNOWN.has(String(type).trim().toLowerCase());
}

/**
 * 规范化 type：小写；未知或空回退 text。
 */
export function normalizeInputFieldType(type: unknown): InputFieldType {
  const s = String(type || 'text').trim().toLowerCase();
  return (KNOWN.has(s) ? s : 'text') as InputFieldType;
}

/** 是否需要编辑 options（select / multiselect） */
export function requiresOptions(type: unknown): boolean {
  const t = normalizeInputFieldType(type);
  return t === 'select' || t === 'multiselect';
}

/** 单选 / 多选的一项 */
export interface InputFieldOption {
  /** 展示文案 */
  label: string;
  /** 写入 flow 的值 */
  value: string;
}

/** 节点 data.fields[] 单项 */
export interface InputFieldDef {
  /** 写入 flow 的变量名 */
  name: string;
  /** 表单标签 */
  label?: string;
  /** 字段类型，缺省 text */
  type?: string;
  /** 是否必填 */
  required?: boolean;
  /** 占位提示 */
  placeholder?: string;
  /** 默认值（与 type 匹配） */
  defaultValue?: unknown;
  /** select / multiselect 静态选项 */
  options?: InputFieldOption[];
}

/** 属性区新增一行时的空字段 */
export function emptyInputField(): InputFieldDef {
  return {
    name: '',
    label: '',
    type: 'text',
    required: false,
    placeholder: '',
    defaultValue: '',
    options: [],
  };
}

/** 新建 Input 节点时的默认 fields（一个必填验证码文本框） */
export function defaultInputFields(): InputFieldDef[] {
  return [
    {
      name: 'captchaCode',
      label: '验证码',
      type: 'text',
      required: true,
      placeholder: '',
      defaultValue: '',
      options: [],
    },
  ];
}

/**
 * 画布卡片摘要：截断后的 prompt + 字段数量与前几个 name。
 */
export function formatInputSummary(data: Record<string, unknown>): string {
  const prompt = data.prompt != null ? String(data.prompt).trim() : '';
  const fields = Array.isArray(data.fields) ? data.fields : [];
  const n = fields.length;
  const names = fields
    .slice(0, 3)
    .map((f) => {
      if (f && typeof f === 'object' && 'name' in f) {
        return String((f as { name?: unknown }).name || '?');
      }
      return '?';
    })
    .filter(Boolean);
  const fieldPart = n ? `${n} 字段${names.length ? ` · ${names.join(', ')}` : ''}` : '未配置字段';
  if (prompt) {
    const short = prompt.length > 24 ? `${prompt.slice(0, 24)}…` : prompt;
    return `${short} · ${fieldPart}`;
  }
  return fieldPart;
}
