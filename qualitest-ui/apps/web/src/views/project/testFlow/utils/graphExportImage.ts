/**
 * 画布导出为 PNG：根据节点/边数据生成 SVG，再栅格化为图片并触发下载。
 * 用于导出抽屉「导出图片」；不依赖额外截图库。
 */
import type { Edge, Node } from '@vue-flow/core';

import { filterFilledExtracts } from '@/utils/flow/extract';
import { formatAssignAssignment, getAssignAssignments } from '@/utils/flow/assign';

import {
  COND_ROW_H,
  FLOW_VUE_FLOW_ID,
  NODE_HEAD_H,
} from '../constants/flowConfig';
import { NODE_TYPES } from '../constants/nodeTypes';
import {
  branchKindLabel,
  formatBranchSummary,
  getConditionBranches,
  getConditionEdgeHandle,
} from './conditionUtils';
import { resolveLayoutSize } from './flowGraphLayeredLayout';
import {
  formatAssertRule,
  formatAssertSummary,
  formatExtractTargetDest,
  formatHttpRequestLine,
  getAssertRules,
} from './nodeDataUtils';

const EXPORT_PAD = 80;
const EXPORT_SCALE = 2;
/** 无节点时导出的默认画布尺寸（仅背景网格） */
const EMPTY_EXPORT_WIDTH = 640;
const EMPTY_EXPORT_HEIGHT = 480;

function svgText(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function truncateText(s: unknown, max: number): string {
  const t = String(s ?? '');
  return t.length > max ? `${t.slice(0, max - 1)}…` : t;
}

/** 按节点类型估算导出用宽高（不读 DOM，保证导出稳定可复现） */
function getNodeDimensions(node: Node): { w: number; h: number } {
  const branches = node.type === 'condition'
    ? getConditionBranches(node.data as Record<string, unknown>).length
    : undefined;
  return resolveLayoutSize({
    id: String(node.id ?? ''),
    type: typeof node.type === 'string' ? node.type : undefined,
    branchCount: branches,
  });
}

/** 计算所有节点（含锚点）的外接矩形 */
function getGraphBounds(nodes: Node[], pad = EXPORT_PAD) {
  if (!nodes.length) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  nodes.forEach((node) => {
    const { w, h } = getNodeDimensions(node);
    const x = node.position.x;
    const y = node.position.y;
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x + w);
    maxY = Math.max(maxY, y + h);

    const el = document.querySelector(
      `#${FLOW_VUE_FLOW_ID} .vue-flow__node[data-id="${node.id}"]`,
    );
    el?.querySelectorAll('.vue-flow__handle').forEach((handle) => {
      const hx = x + (handle as HTMLElement).offsetLeft + (handle as HTMLElement).offsetWidth / 2;
      const hy = y + (handle as HTMLElement).offsetTop + (handle as HTMLElement).offsetHeight / 2;
      minX = Math.min(minX, hx);
      minY = Math.min(minY, hy);
      maxX = Math.max(maxX, hx);
      maxY = Math.max(maxY, hy);
    });
  });

  return {
    x: minX - pad,
    y: minY - pad,
    width: maxX - minX + pad * 2,
    height: maxY - minY + pad * 2,
  };
}

/** 逻辑坐标下的锚点中心（无 DOM 时按布局估算） */
function getHandlePosLogical(node: Node, handle: string) {
  const { w, h } = getNodeDimensions(node);
  const x = node.position.x;
  const y = node.position.y;

  const el = document.querySelector(
    `#${FLOW_VUE_FLOW_ID} .vue-flow__node[data-id="${node.id}"]`,
  );
  const handleEl = el?.querySelector(
    `.vue-flow__handle[data-handleid="${handle}"], .vue-flow__handle[id="${handle}"]`,
  ) as HTMLElement | null;
  if (handleEl) {
    return {
      x: x + handleEl.offsetLeft + handleEl.offsetWidth / 2,
      y: y + handleEl.offsetTop + handleEl.offsetHeight / 2,
    };
  }

  if (handle === 'in' || handle.startsWith('target')) {
    return { x, y: y + h / 2 };
  }
  if (handle.startsWith('out-') && node.type === 'condition') {
    const branchId = handle.slice(4);
    const branches = getConditionBranches(node.data as Record<string, unknown>);
    const idx = branches.findIndex((b) => b.id === branchId);
    if (idx >= 0) {
      return { x: x + w, y: y + NODE_HEAD_H + idx * COND_ROW_H + COND_ROW_H / 2 };
    }
  }
  return { x: x + w, y: y + h / 2 };
}

function bezierPath(x1: number, y1: number, x2: number, y2: number) {
  const dx = Math.abs(x2 - x1) * 0.5;
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`;
}

function getEdgeDisplayLabel(
  edge: Edge,
  nodes: Node[],
): string {
  if (edge.label != null && String(edge.label).trim()) return String(edge.label).trim();
  const src = nodes.find((n) => n.id === edge.source);
  if (src?.type === 'condition') {
    const branch = getConditionBranches(src.data as Record<string, unknown>).find(
      (b) => b.target === edge.target,
    );
    if (branch) return branchKindLabel(branch);
  }
  return '';
}

function getHttpExportLines(data: Record<string, unknown>): string[] {
  const lines = [formatHttpRequestLine(data)];
  const extracts = filterFilledExtracts((data.extracts as never[]) || []);
  extracts.slice(0, 5).forEach((t) => lines.push(formatExtractTargetDest(t as Record<string, unknown>)));
  if (extracts.length > 5) lines.push(`还有 ${extracts.length - 5} 项…`);
  return lines;
}

function buildExportNodeSvg(node: Node): string {
  const cfg = NODE_TYPES[String(node.type)] || NODE_TYPES.http;
  const dim = getNodeDimensions(node);
  const x = node.position.x;
  const y = node.position.y;
  const w = dim.w;
  const h = dim.h;
  const data = node.data as Record<string, unknown>;
  let svg = '';

  svg += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="8" fill="#ffffff" stroke="#c5d8ec" stroke-width="1"/>`;
  svg += `<rect x="${x}" y="${y}" width="${w}" height="5" rx="8" fill="${cfg.color}"/>`;
  svg += `<rect x="${x}" y="${y + 4}" width="${w}" height="2" fill="${cfg.color}"/>`;
  svg += `<rect x="${x + 14}" y="${y + 15}" width="38" height="22" rx="5" fill="${cfg.color}"/>`;
  svg += `<text x="${x + 33}" y="${y + 31}" fill="#ffffff" font-size="12" font-weight="700" text-anchor="middle" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(cfg.icon)}</text>`;
  svg += `<text x="${x + 62}" y="${y + 32}" fill="#0f172a" font-size="15" font-weight="600" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(truncateText(data.name || cfg.label, 18))}</text>`;

  if (node.type === 'condition') {
    const branches = getConditionBranches(data);
    branches.forEach((b, i) => {
      const rowTop = y + NODE_HEAD_H + i * COND_ROW_H;
      if (i > 0) {
        svg += `<line x1="${x + 14}" y1="${rowTop}" x2="${x + w - 14}" y2="${rowTop}" stroke="#dbe8f4"/>`;
      }
      svg += `<text x="${x + 14}" y="${rowTop + COND_ROW_H * 0.55}" fill="#5a6b86" font-size="13" font-weight="700" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(branchKindLabel(b))}</text>`;
      svg += `<text x="${x + 58}" y="${rowTop + COND_ROW_H * 0.55}" fill="#0f172a" font-size="13" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(truncateText(formatBranchSummary(b), 28))}</text>`;
    });
  } else if (node.type === 'http') {
    getHttpExportLines(data).forEach((line, i) => {
      const fill = i === 0 ? '#5a6b86' : cfg.color;
      svg += `<text x="${x + 14}" y="${y + 58 + i * 18}" fill="${fill}" font-size="13" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(truncateText(line, 36))}</text>`;
    });
  } else if (node.type === 'assert') {
    const rules = getAssertRules(data).filter((r) => String(r.left || '').trim());
    const lines = rules.length ? rules.map(formatAssertRule) : [formatAssertSummary(data)];
    lines.forEach((line, i) => {
      svg += `<text x="${x + 14}" y="${y + 58 + i * 18}" fill="${cfg.color}" font-size="13" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(truncateText(line, 36))}</text>`;
    });
  } else if (node.type === 'assign') {
    const items = getAssignAssignments(data).map(formatAssignAssignment);
    const lines = items.length ? items : [String(data.summary || '变量赋值')];
    lines.forEach((line, i) => {
      svg += `<text x="${x + 14}" y="${y + 58 + i * 18}" fill="${cfg.color}" font-size="13" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(truncateText(line, 36))}</text>`;
    });
  } else {
    svg += `<text x="${x + 14}" y="${y + 80}" fill="#5a6b86" font-size="14" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(truncateText(data.summary || '', 36))}</text>`;
  }

  return svg;
}

function buildEdgeLabelSvg(cx: number, cy: number, label: string) {
  if (!label) return '';
  const text = truncateText(label, 20);
  const w = Math.max(40, text.length * 7 + 16);
  return `<rect x="${cx - w / 2}" y="${cy - 10}" width="${w}" height="18" rx="4" fill="#ffffff" stroke="#c5d8ec"/>
<text x="${cx}" y="${cy + 4}" text-anchor="middle" fill="#5a6b86" font-size="10" font-weight="600" font-family="Segoe UI,PingFang SC,sans-serif">${svgText(text)}</text>`;
}

function buildExportEdgesSvg(nodes: Node[], edges: Edge[]) {
  let svg = '';
  edges.forEach((edge) => {
    const src = nodes.find((n) => n.id === edge.source);
    const tgt = nodes.find((n) => n.id === edge.target);
    if (!src || !tgt) return;
    const handle = getConditionEdgeHandle(src, edge);
    const p1 = getHandlePosLogical(src, handle);
    const p2 = getHandlePosLogical(tgt, 'in');
    svg += `<path d="${bezierPath(p1.x, p1.y, p2.x, p2.y)}" fill="none" stroke="#94b8dc" stroke-width="2.5"/>`;
    const label = getEdgeDisplayLabel(edge, nodes);
    if (label) {
      svg += buildEdgeLabelSvg((p1.x + p2.x) / 2, (p1.y + p2.y) / 2 - 10, label);
    }
  });
  return svg;
}

function buildExportHandlesSvg(nodes: Node[]) {
  let svg = '';
  nodes.forEach((node) => {
    const el = document.querySelector(
      `#${FLOW_VUE_FLOW_ID} .vue-flow__node[data-id="${node.id}"]`,
    );
    if (!el) return;
    el.querySelectorAll('.vue-flow__handle').forEach((h) => {
      const handle = h as HTMLElement;
      const cx = node.position.x + handle.offsetLeft + handle.offsetWidth / 2;
      const cy = node.position.y + handle.offsetTop + handle.offsetHeight / 2;
      svg += `<circle cx="${cx}" cy="${cy}" r="7" fill="#ffffff" stroke="#c5d8ec" stroke-width="2"/>`;
    });
  });
  return svg;
}

function buildGraphExportSvg(nodes: Node[], edges: Edge[], bounds: { x: number; y: number; width: number; height: number }) {
  const { x, y, width, height } = bounds;
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <defs>
    <pattern id="export-grid" width="20" height="20" patternUnits="userSpaceOnUse">
      <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#c5d8ec" stroke-width="0.6" opacity="0.45"/>
    </pattern>
  </defs>
  <rect width="100%" height="100%" fill="#e6f0fb"/>
  <rect width="100%" height="100%" fill="url(#export-grid)"/>
  <g transform="translate(${-x}, ${-y})">
    ${buildExportEdgesSvg(nodes, edges)}
    ${nodes.map(buildExportNodeSvg).join('')}
    ${buildExportHandlesSvg(nodes)}
  </g>
</svg>`;
}

function svgToPngBlob(svgString: string, width: number, height: number, scale = EXPORT_SCALE): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = Math.ceil(width * scale);
      canvas.height = Math.ceil(height * scale);
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error('Canvas 不可用'));
        return;
      }
      ctx.scale(scale, scale);
      ctx.fillStyle = '#e6f0fb';
      ctx.fillRect(0, 0, width, height);
      ctx.drawImage(img, 0, 0, width, height);
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('PNG 生成失败'))),
        'image/png',
      );
    };
    img.onerror = () => reject(new Error('SVG 渲染失败'));
    img.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgString)}`;
  });
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/** 无节点时的默认导出范围：空白背景图 */
function getEmptyExportBounds() {
  return { x: 0, y: 0, width: EMPTY_EXPORT_WIDTH, height: EMPTY_EXPORT_HEIGHT };
}

/**
 * 将当前画布节点/边导出为 PNG 并下载。
 * 画布无节点时导出仅含背景网格的空白图。
 * @throws 渲染失败时抛错
 */
export async function exportGraphAsPng(
  nodes: Node[],
  edges: Edge[],
  basename = 'test-flow',
): Promise<void> {
  const bounds = nodes.length ? getGraphBounds(nodes) : getEmptyExportBounds();
  if (!bounds) {
    throw new Error('无法计算画布范围');
  }
  const svg = buildGraphExportSvg(nodes, edges, bounds);
  const blob = await svgToPngBlob(svg, bounds.width, bounds.height);
  downloadBlob(blob, `${basename}.png`);
}
