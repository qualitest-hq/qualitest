/**
 * AI 设计 @ 输入：类型、DOM 转换、序列化、候选项检索、contenteditable 编辑逻辑。
 */
import Fuse from 'fuse.js';
import { nextTick, onMounted, onUnmounted, ref, shallowRef } from 'vue';

import {
  flattenApiTree,
  formatApiChipLabel,
  type FlatApiItem,
} from '../utils/apiPickerUtils';
import {
  fetchProjectAssetRows,
  parseAssetVarKeys,
  parseEnvVarKeys,
} from './useProjectVariables';
import {
  templateAssetMentionItems,
} from '../../testProjectTemplate/utils/templateParamUtils';
import type {
  AiDesignMention,
  ComposerDoc,
  ComposerMentionNode,
  ComposerNode,
  ComposerTextNode,
  MentionCategory,
  MentionType,
  VarSubtype,
} from '../types/mentionTypes';
import {
  COMPOSER_DOC_VERSION,
  MAX_MENTIONS_PER_MESSAGE,
  MENTION_CATEGORY_TAGS,
  MENTION_CHIP_CLASS,
} from '../types/mentionTypes';

// ── 类型再导出（兼容既有 import 路径）──────────────────────────────
export type {
  AiDesignMention,
  ComposerDoc,
  ComposerMentionNode,
  ComposerNode,
  ComposerTextNode,
  MentionCategory,
  MentionType,
  VarSubtype,
};
export {
  COMPOSER_DOC_VERSION,
  MAX_MENTIONS_PER_MESSAGE,
  MENTION_CATEGORY_TAGS,
  MENTION_CHIP_CLASS,
};

// ── 编辑器逻辑 ─────────────────────────────────────────────

const MENTION_ZWSP = '\u200B';

/** 下拉 Tab 列表，由 MENTION_CATEGORY_TAGS 派生 */
export const MENTION_TABS: { id: MentionCategory; label: string }[] = (
  Object.entries(MENTION_CATEGORY_TAGS) as [MentionCategory, string][]
).map(([id, label]) => ({ id, label }));

const MENTION_TAG_TO_CATEGORY: Record<string, MentionCategory> = {
  Api: 'api',
  Node: 'node',
  Run: 'run',
  Var: 'var',
};

const MENTION_TAGS_ORDERED = ['Api', 'Node', 'Run', 'Var'] as const;

/** 送入后端的 prompt 占位：@类型:对象名，便于模型从自然语言段识别引用类型 */
export function formatMentionPromptToken(node: ComposerMentionNode): string {
  if (node.mentionType === 'var' && node.subtype) {
    return `@var:${node.subtype}:${node.label}`;
  }
  return `@${node.mentionType}:${node.label}`;
}

function parseAfterAtSegment(rawAfterAt: string): {
  category: MentionCategory | null;
  searchQuery: string;
} {
  for (const tag of MENTION_TAGS_ORDERED) {
    if (rawAfterAt === tag || rawAfterAt.startsWith(tag)) {
      return {
        category: MENTION_TAG_TO_CATEGORY[tag] ?? null,
        searchQuery: rawAfterAt.slice(tag.length),
      };
    }
  }
  if (rawAfterAt && MENTION_TAGS_ORDERED.some((tag) => tag.startsWith(rawAfterAt))) {
    return { category: null, searchQuery: '' };
  }
  return { category: null, searchQuery: rawAfterAt };
}

function isPartialTypeTag(rawAfterAt: string): boolean {
  return Boolean(rawAfterAt) && MENTION_TAGS_ORDERED.some((tag) => tag.startsWith(rawAfterAt) && rawAfterAt !== tag);
}

export interface MentionCandidate {
  type: MentionType;
  id: string;
  label: string;
  subtype?: VarSubtype;
  envId?: string;
  detail?: string;
  pinned?: boolean;
}

export interface ComposerSendPayload {
  doc: ComposerDoc;
  prompt: string;
  mentions: AiDesignMention[];
}

export function isComposerDocEmpty(doc: ComposerDoc): boolean {
  if (!doc.nodes.length) return true;
  return doc.nodes.every((n) => n.type === 'text' && !n.text.trim());
}

// ── DOM ↔ composerDoc ───────────────────────────────────────

const CHIP_ATTR_TYPE = 'data-mention-type';
const CHIP_ATTR_ID = 'data-mention-id';
const CHIP_ATTR_LABEL = 'data-mention-label';
const CHIP_ATTR_SUBTYPE = 'data-mention-subtype';
const CHIP_ATTR_ENV_ID = 'data-mention-env-id';
const CHIP_REMOVE_ATTR = 'data-mention-remove';

function removeMentionChipFromDom(chip: HTMLElement, root: HTMLElement) {
  const prev = chip.previousSibling;
  const next = chip.nextSibling;
  chip.remove();
  if (prev?.nodeType === Node.TEXT_NODE && prev.textContent === MENTION_ZWSP) prev.remove();
  if (next?.nodeType === Node.TEXT_NODE && next.textContent === MENTION_ZWSP) next.remove();
  normalizeEditorDom(root);
}

function createChipElement(node: ComposerMentionNode): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = `${MENTION_CHIP_CLASS} ${MENTION_CHIP_CLASS}--editable ${MENTION_CHIP_CLASS}--${node.mentionType}`;
  span.contentEditable = 'false';
  span.spellcheck = false;
  span.setAttribute(CHIP_ATTR_TYPE, node.mentionType);
  span.setAttribute(CHIP_ATTR_ID, node.id);
  span.setAttribute(CHIP_ATTR_LABEL, node.label);
  if (node.subtype) span.setAttribute(CHIP_ATTR_SUBTYPE, node.subtype);
  if (node.envId) span.setAttribute(CHIP_ATTR_ENV_ID, node.envId);

  const remove = document.createElement('span');
  remove.className = 'ai-mention-chip__remove';
  remove.setAttribute(CHIP_REMOVE_ATTR, '1');
  remove.setAttribute('aria-label', '删除引用');

  const atGlyph = document.createElement('span');
  atGlyph.className = 'ai-mention-chip__glyph ai-mention-chip__glyph--at';
  atGlyph.setAttribute('aria-hidden', 'true');
  atGlyph.textContent = '@';

  const closeGlyph = document.createElement('span');
  closeGlyph.className = 'ai-mention-chip__glyph ai-mention-chip__glyph--close';
  closeGlyph.setAttribute('aria-hidden', 'true');
  closeGlyph.textContent = '×';

  remove.append(atGlyph, closeGlyph);

  const typeEl = document.createElement('span');
  typeEl.className = 'ai-mention-chip__type';
  typeEl.textContent = MENTION_CATEGORY_TAGS[node.mentionType];

  const dot = document.createElement('span');
  dot.className = 'ai-mention-chip__dot';
  dot.textContent = '·';

  const labelEl = document.createElement('span');
  labelEl.className = 'ai-mention-chip__label';
  labelEl.textContent = node.label;

  span.append(remove, typeEl, dot, labelEl);
  return span;
}

function wrapChipWithAnchors(chip: HTMLSpanElement): DocumentFragment {
  const frag = document.createDocumentFragment();
  frag.append(document.createTextNode(MENTION_ZWSP));
  frag.append(chip);
  frag.append(document.createTextNode(MENTION_ZWSP));
  return frag;
}

function isMentionChipElement(node: Node): node is HTMLSpanElement {
  return node instanceof HTMLSpanElement && node.classList.contains(MENTION_CHIP_CLASS);
}

function chipElementToMentionNode(el: HTMLSpanElement): ComposerMentionNode {
  const mentionType = el.getAttribute(CHIP_ATTR_TYPE) as MentionType;
  const id = el.getAttribute(CHIP_ATTR_ID) ?? '';
  const label = el.getAttribute(CHIP_ATTR_LABEL)
    ?? el.querySelector('.ai-mention-chip__label')?.textContent
    ?? '';
  const subtype = el.getAttribute(CHIP_ATTR_SUBTYPE) as VarSubtype | null;
  const envId = el.getAttribute(CHIP_ATTR_ENV_ID);
  const node: ComposerMentionNode = { type: 'mention', mentionType, id, label };
  if (subtype) node.subtype = subtype;
  if (envId) node.envId = envId;
  return node;
}

export function walkDomToDoc(root: HTMLElement): ComposerDoc {
  const nodes: ComposerNode[] = [];

  function appendText(text: string) {
    if (!text) return;
    const last = nodes[nodes.length - 1];
    if (last?.type === 'text') last.text += text;
    else nodes.push({ type: 'text', text });
  }

  function walk(parent: Node) {
    parent.childNodes.forEach((child) => {
      if (child.nodeType === Node.TEXT_NODE) {
        appendText((child.textContent ?? '').split(MENTION_ZWSP).join(''));
        return;
      }
      if (child instanceof HTMLBRElement) {
        appendText('\n');
        return;
      }
      if (isMentionChipElement(child)) {
        nodes.push(chipElementToMentionNode(child));
        return;
      }
      if (child instanceof HTMLElement) walk(child);
    });
  }

  walk(root);

  const merged: ComposerNode[] = [];
  for (const n of nodes) {
    if (n.type === 'text' && !n.text) continue;
    const last = merged[merged.length - 1];
    if (n.type === 'text' && last?.type === 'text') last.text += n.text;
    else merged.push(n.type === 'text' ? { ...n } : { ...n });
  }
  return { version: COMPOSER_DOC_VERSION, nodes: merged };
}

export function renderDocToDom(doc: ComposerDoc, root: HTMLElement): void {
  root.replaceChildren();
  for (const node of doc.nodes) {
    if (node.type === 'text') {
      const parts = node.text.split('\n');
      parts.forEach((part, index) => {
        if (part) root.append(document.createTextNode(part));
        if (index < parts.length - 1) root.append(document.createElement('br'));
      });
      continue;
    }
    root.append(wrapChipWithAnchors(createChipElement(node)));
  }
}

function isEditorEmpty(root: HTMLElement): boolean {
  const doc = walkDomToDoc(root);
  if (!doc.nodes.length) return true;
  return doc.nodes.every((n) => n.type === 'text' && !n.text.trim());
}

function normalizeEditorDom(root: HTMLElement): void {
  root.normalize();
}

// ── 序列化与发送载荷 ─────────────────────────────────────────

function mentionToPromptToken(node: ComposerMentionNode): string {
  return formatMentionPromptToken(node);
}

export function docToPrompt(doc: ComposerDoc): string {
  let out = '';
  for (const node of doc.nodes) {
    out += node.type === 'text' ? node.text : mentionToPromptToken(node);
  }
  return out.trim();
}

export function mentionDedupeKey(m: {
  mentionType?: MentionType;
  type?: MentionType;
  subtype?: string;
  id: string;
}): string {
  const t = ('mentionType' in m && m.mentionType) || m.type || '';
  return `${t}:${m.subtype ?? ''}:${m.id}`;
}

export function docToMentions(doc: ComposerDoc): AiDesignMention[] {
  const seen = new Set<string>();
  const result: AiDesignMention[] = [];
  for (const node of doc.nodes) {
    if (node.type !== 'mention') continue;
    const key = mentionDedupeKey(node);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(mentionNodeToPayload(node));
  }
  return result;
}

/** 将候选项或 mention 节点转为发送给后端的 AiDesignMention */
export function mentionNodeToPayload(node: ComposerMentionNode | MentionCandidate): AiDesignMention {
  const mentionType = 'mentionType' in node ? node.mentionType : node.type;
  const item: AiDesignMention = {
    type: mentionType,
    id: node.id,
    label: node.label,
  };
  if (node.subtype) item.subtype = node.subtype;
  if (node.envId) item.envId = node.envId;
  return item;
}

/** 将下拉候选项转为编辑器内的 mention 文档节点 */
export function candidateToMentionNode(candidate: MentionCandidate): ComposerMentionNode {
  const node: ComposerMentionNode = {
    type: 'mention',
    mentionType: candidate.type,
    id: candidate.id,
    label: candidate.label,
  };
  if (candidate.subtype) node.subtype = candidate.subtype;
  if (candidate.envId) node.envId = candidate.envId;
  return node;
}

export function buildComposerSendPayload(doc: ComposerDoc): ComposerSendPayload {
  if (isComposerDocEmpty(doc)) throw new Error('请输入设计描述');
  const prompt = docToPrompt(doc);
  if (!prompt) throw new Error('请输入设计描述');
  const mentions = docToMentions(doc);
  if (mentions.length > MAX_MENTIONS_PER_MESSAGE) {
    throw new Error(`单条消息最多 ${MAX_MENTIONS_PER_MESSAGE} 个 @ 引用`);
  }
  return { doc, prompt, mentions };
}

// ── @ 候选项检索（按类别懒加载项目 API、画布节点、Run 列表、变量） ──

interface MentionProvider {
  category: MentionCategory;
  search(query: string, varSubtype?: VarSubtype): Promise<MentionCandidate[]>;
}

const CANDIDATE_LIMIT = 12;
/** 按 testProjectId 缓存扁平化 API 列表，避免重复请求 */
let apiCache: FlatApiItem[] = [];
let apiCacheProjectId = '';

async function ensureApiList(projectId: string): Promise<FlatApiItem[]> {
  if (apiCacheProjectId === projectId && apiCache.length) return apiCache;
  if (!projectId) {
    apiCache = [];
    return apiCache;
  }
  try {
    const { getTestProjectApiTree } = await import('@/api/project/testProjectApi');
    const res = await getTestProjectApiTree({ testProjectId: projectId });
    const tree = (res as { data?: unknown[] })?.data ?? res ?? [];
    apiCache = flattenApiTree(Array.isArray(tree) ? tree : []);
    apiCacheProjectId = projectId;
  } catch {
    apiCache = [];
  }
  return apiCache;
}

/** 画布节点 → @ 引用候选项 */
export function buildNodeMentionCandidate(node: {
  id: string;
  type?: string;
  data?: { name?: string };
}): MentionCandidate {
  const name = node.data?.name?.trim() || node.type || '节点';
  return { type: 'node', id: node.id, label: `${node.type} · ${name}`, detail: node.id };
}

function filterByQuery<T>(items: T[], query: string, fuseKeys: string[]): T[] {
  const kw = query.trim();
  if (!kw) return items.slice(0, CANDIDATE_LIMIT);
  return new Fuse(items, { keys: fuseKeys, threshold: 0.4 })
    .search(kw)
    .map((r) => r.item)
    .slice(0, CANDIDATE_LIMIT);
}

const MENTION_PROVIDERS: MentionProvider[] = [
  {
    category: 'api',
    async search(query) {
      const { useFlowCanvasStore } = await import('../stores/flowCanvasStore');
      const store = useFlowCanvasStore();
      const list = await ensureApiList(store.testProjectId);
      const mapped = list.map((api) => ({
        type: 'api' as const,
        id: api.testProjectApiId,
        label: formatApiChipLabel(api),
        detail: api.apiPath,
        _fuse: api,
      }));
      return filterByQuery(mapped, query, ['label', 'detail', '_fuse.apiName', '_fuse.apiPath']).map(
        ({ _fuse: _, ...rest }) => rest,
      );
    },
  },
  {
    category: 'node',
    async search(query) {
      const { useFlowCanvasStore } = await import('../stores/flowCanvasStore');
      const store = useFlowCanvasStore();
      const items: MentionCandidate[] = store.nodes.map((node) =>
        buildNodeMentionCandidate({
          id: node.id,
          type: node.type,
          data: node.data as { name?: string } | undefined,
        }),
      );
      if (store.selected?.kind === 'node') {
        const sel = items.find((i) => i.id === store.selected!.id);
        if (sel) {
          sel.pinned = true;
          items.splice(items.indexOf(sel), 1);
          items.unshift(sel);
        }
      }
      return filterByQuery(items, query, ['label', 'detail']);
    },
  },
  {
    category: 'run',
    async search(query) {
      const { useRunLibraryStore } = await import('../stores/runLibraryStore');
      const runStore = useRunLibraryStore();
      const items: MentionCandidate[] = runStore.runs.slice(0, 10).map((run) => ({
        type: 'run',
        id: run.id,
        label: `Run#${run.id} ${run.status === 'failed' ? '失败' : run.status}`,
        detail: run.startedAt ?? '',
      }));
      return filterByQuery(items, query, ['label', 'detail']);
    },
  },
  {
    category: 'var',
    async search(query, varSubtype: VarSubtype = 'flow') {
      const { useFlowCanvasStore } = await import('../stores/flowCanvasStore');
      const store = useFlowCanvasStore();
      let items: MentionCandidate[] = [];
      const scenario =
        store.runConfig.scenarios?.find((s) => s.id === store.runConfig.activeScenarioId)
        ?? store.runConfig.scenarios?.[0];

      if (varSubtype === 'flow') {
        items = Object.keys(scenario?.flowSeed ?? {}).map((key) => ({
          type: 'var',
          subtype: 'flow',
          id: key,
          label: `flow.${key}`,
          detail: '运行场景初值',
        }));
      } else if (varSubtype === 'env') {
        if (store.canvasMode === 'template') {
          items = (store.templateParamContext?.env || []).map((row) => ({
            type: 'var' as const,
            subtype: 'env' as const,
            id: row.name,
            label: `env.${row.name}`,
            detail: row.remark || '模板 env',
          }));
        } else {
          const envId = scenario?.testProjectEnvId;
          if (envId && store.testProjectId) {
            try {
              const { getTestProjectEnv } = await import('@/api/project/testProjectEnv');
              const res = await getTestProjectEnv(envId);
              const row = (res as { data?: Record<string, unknown> })?.data ?? res;
              items = parseEnvVarKeys(row?.envVariables).map((key) => ({
                type: 'var',
                subtype: 'env',
                id: key,
                label: `env.${key}`,
                envId: String(envId),
                detail: '环境变量',
              }));
            } catch {
              items = [];
            }
          }
        }
      } else if (varSubtype === 'asset') {
        if (store.canvasMode === 'template') {
          items = templateAssetMentionItems(store.templateParamContext?.asset || []).map((row) => ({
            type: 'var' as const,
            subtype: 'asset' as const,
            id: row.id,
            label: row.label,
            detail: row.detail,
          }));
        } else if (store.testProjectId) {
          try {
            const rows = await fetchProjectAssetRows(store.testProjectId);
            items = rows.flatMap((row) => {
              const assetKey = String(row.assetKey ?? row.key ?? '').trim();
              return parseAssetVarKeys(row.assetVariables ?? row.assets).map((key) => ({
                type: 'var',
                subtype: 'asset',
                id: assetKey ? `${assetKey}.${key}` : key,
                label: assetKey ? `asset.${assetKey}.${key}` : `asset.${key}`,
                detail: String(row.remark ?? row.assetName ?? '素材变量'),
              }));
            });
          } catch {
            items = [];
          }
        }
      }
      return filterByQuery(items, query, ['label', 'detail']);
    },
  },
];

export async function searchMentionCandidates(
  category: MentionCategory,
  query: string,
  varSubtype?: VarSubtype,
): Promise<MentionCandidate[]> {
  const provider = MENTION_PROVIDERS.find((p) => p.category === category);
  return provider ? provider.search(query, varSubtype) : [];
}

// ── contenteditable 编辑器：@ 触发检测、下拉定位、chip 插入与删除 ──

export interface MentionMenuState {
  open: boolean;
  x: number;
  y: number;
  query: string;
  category: MentionCategory;
  varSubtype: VarSubtype;
}

const INITIAL_MENU: MentionMenuState = {
  open: false,
  x: 0,
  y: 0,
  query: '',
  category: 'api',
  varSubtype: 'flow',
};

export function useMentionEditor(options: {
  getRoot: () => HTMLElement | null;
  /** 下拉定位参照容器，默认与 getRoot 相同 */
  getAnchor?: () => HTMLElement | null;
  onDocChange?: () => void;
}) {
  const menu = ref<MentionMenuState>({ ...INITIAL_MENU });
  const mentionRange = shallowRef<Range | null>(null);
  const composing = ref(false);

  function getSelection() {
    return typeof window !== 'undefined' ? window.getSelection() : null;
  }

  /** 在定位容器内计算 @ 锚点坐标，始终插入标记元素测量 */
  function getAnchorRelativePosition(range: Range): { left: number; top: number } | null {
    const root = options.getRoot();
    const anchor = options.getAnchor?.() ?? root;
    if (!root || !anchor) return null;

    const probe = range.cloneRange();
    probe.collapse(true);

    const marker = document.createElement('span');
    marker.setAttribute('data-mention-caret-probe', '1');
    marker.style.display = 'inline-block';
    marker.style.width = '0';
    marker.style.overflow = 'hidden';
    marker.append(document.createTextNode('\u200b'));

    try {
      probe.insertNode(marker);
      const markerRect = marker.getBoundingClientRect();
      const anchorRect = anchor.getBoundingClientRect();
      marker.remove();

      return {
        left: markerRect.left - anchorRect.left + anchor.scrollLeft,
        top: markerRect.top - anchorRect.top + anchor.scrollTop,
      };
    } catch {
      marker.remove();
      return null;
    }
  }

  /**
   * 从光标向前查找未闭合的 @ 触发段。
   * 使用 TreeWalker 定位 @ 字符，避免 contenteditable 根节点选区导致坐标偏移。
   */
  function findMentionTrigger(
    root: HTMLElement,
    cursorRange: Range,
  ): { triggerRange: Range; searchQuery: string; category: MentionCategory | null } | null {
    const end = cursorRange.cloneRange();
    end.collapse(true);
    if (!root.contains(end.startContainer)) return null;

    const pre = end.cloneRange();
    pre.setStart(root, 0);
    const before = pre.toString().split(MENTION_ZWSP).join('');
    const atIndex = before.lastIndexOf('@');
    if (atIndex < 0) return null;

    const rawAfterAt = before.slice(atIndex + 1);
    if (rawAfterAt.includes('\n') || rawAfterAt.includes(' ')) return null;
    if (isPartialTypeTag(rawAfterAt)) return null;

    const parsed = parseAfterAtSegment(rawAfterAt);

    let charIndex = 0;
    const triggerRange = document.createRange();
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let textNode: Node | null;
    while ((textNode = walker.nextNode())) {
      const raw = textNode.textContent ?? '';
      const plain = raw.split(MENTION_ZWSP).join('');
      const nodeStart = charIndex;
      const nodeEnd = charIndex + plain.length;
      if (atIndex >= nodeStart && atIndex < nodeEnd) {
        const localPlain = atIndex - nodeStart;
        let rawOffset = 0;
        let plainCount = 0;
        for (let i = 0; i < raw.length && plainCount < localPlain; i += 1) {
          if (raw[i] !== MENTION_ZWSP) plainCount += 1;
          rawOffset = i + 1;
        }
        triggerRange.setStart(textNode, rawOffset);
        triggerRange.setEnd(end.endContainer, end.endOffset);
        return {
          triggerRange,
          searchQuery: parsed.searchQuery,
          category: parsed.category,
        };
      }
      charIndex = nodeEnd;
    }
    return null;
  }

  /** 将 @ 段重写为 @类型标识+搜索词，并恢复光标 */
  function rewriteMentionTriggerText(category: MentionCategory) {
    const root = options.getRoot();
    const range = mentionRange.value;
    const sel = getSelection();
    if (!root || !range || !sel) return;

    const segment = range.toString().split(MENTION_ZWSP).join('');
    const rawAfterAt = segment.startsWith('@') ? segment.slice(1) : segment;
    const parsed = parseAfterAtSegment(rawAfterAt);
    const searchQuery = parsed.category != null ? parsed.searchQuery : rawAfterAt;
    const tag = MENTION_CATEGORY_TAGS[category];
    const newText = `@${tag}${searchQuery}`;

    range.deleteContents();
    const textNode = document.createTextNode(newText);
    range.insertNode(textNode);

    const cursor = document.createRange();
    cursor.selectNodeContents(textNode);
    cursor.collapse(false);
    sel.removeAllRanges();
    sel.addRange(cursor);

    const refreshed = findMentionTrigger(root, cursor);
    if (refreshed) mentionRange.value = refreshed.triggerRange;
  }

  function syncMentionTypePrefix(category: MentionCategory) {
    const root = options.getRoot();
    const range = mentionRange.value;
    if (!root || !range) return;

    const segment = range.toString().split(MENTION_ZWSP).join('');
    const rawAfterAt = segment.startsWith('@') ? segment.slice(1) : segment;
    const parsed = parseAfterAtSegment(rawAfterAt);
    if (parsed.category === category && rawAfterAt.startsWith(MENTION_CATEGORY_TAGS[category])) {
      return;
    }
    rewriteMentionTriggerText(category);
  }

  function updateMenuPosition() {
    const range = mentionRange.value;
    if (!range || !menu.value.open) return;
    const atRange = range.cloneRange();
    atRange.collapse(true);
    const pos = getAnchorRelativePosition(atRange);
    if (!pos) return;
    menu.value = { ...menu.value, x: pos.left, y: pos.top };
  }

  function applyMenuFromTrigger(found: {
    triggerRange: Range;
    searchQuery: string;
    category: MentionCategory | null;
  }) {
    mentionRange.value = found.triggerRange;
    let category = found.category ?? menu.value.category;
    let searchQuery = found.searchQuery;

    if (found.category) {
      menu.value = { ...menu.value, category: found.category };
    } else {
      syncMentionTypePrefix(category);
      const root = options.getRoot();
      const sel = getSelection();
      if (root && sel && sel.rangeCount > 0) {
        const refreshed = findMentionTrigger(root, sel.getRangeAt(0));
        if (refreshed) {
          mentionRange.value = refreshed.triggerRange;
          category = refreshed.category ?? category;
          searchQuery = refreshed.searchQuery;
        }
      }
    }

    const atRange = mentionRange.value.cloneRange();
    atRange.collapse(true);
    const pos = getAnchorRelativePosition(atRange);
    menu.value = {
      open: true,
      x: pos?.left ?? 0,
      y: pos?.top ?? 0,
      query: searchQuery,
      category,
      varSubtype: menu.value.varSubtype,
    };
  }

  function closeMenu() {
    menu.value = { ...INITIAL_MENU };
    mentionRange.value = null;
  }

  function detectMentionTrigger() {
    if (composing.value) return;
    const root = options.getRoot();
    const sel = getSelection();
    if (!root || !sel || sel.rangeCount === 0 || !root.contains(sel.anchorNode)) return;

    const found = findMentionTrigger(root, sel.getRangeAt(0));
    if (!found) {
      if (menu.value.open) closeMenu();
      return;
    }

    applyMenuFromTrigger(found);
  }

  function detectMentionTriggerDeferred() {
    nextTick(() => detectMentionTrigger());
  }

  function insertMention(candidate: MentionCandidate) {
    const root = options.getRoot();
    const range = mentionRange.value;
    const sel = getSelection();
    if (!root || !range || !sel) return;

    const endRange = sel.getRangeAt(0).cloneRange();
    endRange.collapse(true);
    const replaceRange = range.cloneRange();
    if (replaceRange.compareBoundaryPoints(Range.END_TO_END, endRange) !== 0) {
      replaceRange.setEnd(endRange.endContainer, endRange.endOffset);
    }
    replaceRange.deleteContents();

    const chip = createChipElement(candidateToMentionNode(candidate));
    replaceRange.insertNode(wrapChipWithAnchors(chip));

    const after = document.createRange();
    after.setStartAfter(chip.nextSibling ?? chip);
    after.collapse(true);
    sel.removeAllRanges();
    sel.addRange(after);

    closeMenu();
    normalizeEditorDom(root);
    options.onDocChange?.();
  }

  /** 将选区落到编辑器末尾（预填 / 光标在编辑器外时使用） */
  function placeCaretAtEnd(root: HTMLElement) {
    const sel = getSelection();
    if (!sel) return;
    const range = document.createRange();
    range.selectNodeContents(root);
    range.collapse(false);
    sel.removeAllRanges();
    sel.addRange(range);
  }

  /**
   * 在光标处插入纯文本。
   * 光标不在编辑器内时（例如从 Run 详情点「AI 修复」预填）落到末尾再插，
   * 避免 insertNode 污染运行库列表等其它 DOM。
   */
  function insertText(text: string) {
    const root = options.getRoot();
    if (!root || !text) return;

    const sel = getSelection();
    const inRoot = !!sel?.anchorNode && root.contains(sel.anchorNode);
    if (!inRoot) {
      root.focus();
      placeCaretAtEnd(root);
    }

    const active = getSelection();
    if (!active || active.rangeCount === 0 || !root.contains(active.anchorNode)) {
      root.appendChild(document.createTextNode(text));
      options.onDocChange?.();
      return;
    }

    const range = active.getRangeAt(0);
    if (!root.contains(range.commonAncestorContainer)) {
      root.appendChild(document.createTextNode(text));
      options.onDocChange?.();
      return;
    }

    range.deleteContents();
    range.insertNode(document.createTextNode(text));
    range.collapse(false);
    active.removeAllRanges();
    active.addRange(range);
    options.onDocChange?.();
  }

  /** 在编辑器末尾追加 chip（Run 修复预填等场景） */
  function appendMentionChip(candidate: MentionCandidate) {
    const root = options.getRoot();
    if (!root) return;
    root.append(wrapChipWithAnchors(createChipElement(candidateToMentionNode(candidate))));
    normalizeEditorDom(root);
    options.onDocChange?.();
  }

  function removeChipBeforeCursor(): boolean {
    const sel = getSelection();
    if (!sel || sel.rangeCount === 0) return false;
    const range = sel.getRangeAt(0);
    if (!range.collapsed) return false;

    let node: Node | null = range.startContainer;
    const offset = range.startOffset;

    if (node.nodeType === Node.TEXT_NODE && offset === 0) {
      node = node.previousSibling;
    } else if (node.nodeType === Node.TEXT_NODE && offset > 0) {
      const charBefore = (node.textContent ?? '')[offset - 1];
      if (charBefore === MENTION_ZWSP || charBefore === '@') {
        range.setStart(node, offset - 1);
        range.deleteContents();
        options.onDocChange?.();
        return true;
      }
      return false;
    }

    if (node && isMentionChipElement(node)) {
      const root = options.getRoot();
      if (root) removeMentionChipFromDom(node, root);
      options.onDocChange?.();
      return true;
    }
    return false;
  }

  function handleKeydown(e: KeyboardEvent): 'send' | 'handled' | undefined {
    if (menu.value.open) {
      if (e.key === 'Escape') {
        e.preventDefault();
        closeMenu();
        return 'handled';
      }
      if (['Enter', 'Tab', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
        return 'handled';
      }
    }
    if (e.key === 'Backspace' && removeChipBeforeCursor()) {
      e.preventDefault();
      return 'handled';
    }
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      return 'send';
    }
    if (e.key === 'Enter' && !e.shiftKey && !menu.value.open) {
      e.preventDefault();
      return 'send';
    }
    return undefined;
  }

  function handleEditorMouseDown(e: MouseEvent) {
    const target = e.target as HTMLElement;
    if (!target.closest(`[${CHIP_REMOVE_ATTR}]`)) return;
    e.preventDefault();
    const chip = target.closest(`.${MENTION_CHIP_CLASS}`);
    const root = options.getRoot();
    if (!(chip instanceof HTMLElement) || !root) return;
    removeMentionChipFromDom(chip, root);
    options.onDocChange?.();
  }

  function clickOutside(e: MouseEvent) {
    const root = options.getRoot();
    const anchor = options.getAnchor?.() ?? root;
    const target = e.target;
    if (anchor && target instanceof Node && anchor.contains(target)) return;
    closeMenu();
  }

  function onViewportChange() {
    if (menu.value.open) updateMenuPosition();
  }

  onMounted(() => {
    document.addEventListener('mousedown', clickOutside);
    window.addEventListener('scroll', onViewportChange, true);
    window.addEventListener('resize', onViewportChange);
  });

  onUnmounted(() => {
    document.removeEventListener('mousedown', clickOutside);
    window.removeEventListener('scroll', onViewportChange, true);
    window.removeEventListener('resize', onViewportChange);
  });

  return {
    menu,
    closeMenu,
    insertMention,
    appendMentionChip,
    insertText,
    handleInput: () => {
      options.onDocChange?.();
      detectMentionTriggerDeferred();
    },
    handleCompositionStart: () => {
      composing.value = true;
    },
    handleCompositionEnd: () => {
      composing.value = false;
      detectMentionTriggerDeferred();
    },
    handlePaste: (e: ClipboardEvent) => {
      e.preventDefault();
      const text = e.clipboardData?.getData('text/plain') ?? '';
      if (text) insertText(text);
    },
    handleKeydown,
    handleEditorMouseDown,
    setCategory: (category: MentionCategory) => {
      menu.value = { ...menu.value, category };
      if (menu.value.open) {
        syncMentionTypePrefix(category);
        updateMenuPosition();
      }
    },
    setVarSubtype: (subtype: VarSubtype) => {
      menu.value = { ...menu.value, varSubtype: subtype };
    },
    clearEditor: () => {
      options.getRoot()?.replaceChildren();
      closeMenu();
      options.onDocChange?.();
    },
    focusEditor: () => options.getRoot()?.focus(),
    updateMenuPosition,
    isEmpty: () => {
      const root = options.getRoot();
      return !root || isEditorEmpty(root);
    },
    getDoc: () => {
      const root = options.getRoot();
      return root ? walkDomToDoc(root) : { version: COMPOSER_DOC_VERSION, nodes: [] };
    },
  };
}
