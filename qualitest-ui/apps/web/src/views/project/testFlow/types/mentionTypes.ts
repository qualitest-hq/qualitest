/**
 * AI 设计 @ 提及相关类型与常量。
 * 涵盖 mention 条目、composerDoc 文档结构及编辑器 chip 展示常量。
 */

/** @ 引用业务类型：api、node、run、var */
export type MentionCategory = 'api' | 'node' | 'run' | 'var';
/** 与 MentionCategory 同义，用于 mention 节点字段 */
export type MentionType = MentionCategory;
export type VarSubtype = 'flow' | 'env' | 'asset';

export const COMPOSER_DOC_VERSION = 1 as const;
export const MAX_MENTIONS_PER_MESSAGE = 20;
export const MENTION_CHIP_CLASS = 'ai-mention-chip';

/** chip 与 @ 触发段中的类型标识（Tab / 左右键切换，用户无需手打） */
export const MENTION_CATEGORY_TAGS: Record<MentionCategory, string> = {
  api: 'Api',
  node: 'Node',
  run: 'Run',
  var: 'Var',
};

export interface AiDesignMention {
  type: MentionType;
  id: string;
  label: string;
  subtype?: VarSubtype;
  envId?: string;
}

export interface ComposerTextNode {
  type: 'text';
  text: string;
}

export interface ComposerMentionNode {
  type: 'mention';
  mentionType: MentionType;
  id: string;
  label: string;
  subtype?: VarSubtype;
  envId?: string;
}

export type ComposerNode = ComposerTextNode | ComposerMentionNode;

export interface ComposerDoc {
  version: typeof COMPOSER_DOC_VERSION;
  nodes: ComposerNode[];
}
