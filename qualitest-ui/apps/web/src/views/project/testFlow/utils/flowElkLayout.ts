/**
 * 用分层引擎计算整图节点坐标，并附带每条边的正交拐点。
 * 走向来自排版方式：横向从左往右，纵向自上而下；折行时长链会换排。
 * 画布连线不使用这些拐点，而是按连接点另画正交折线。拐点也不写入图。
 * 引擎失败时回退到内置的从左往右分层折行，此时没有拐点。
 */
import ELK from 'elkjs/lib/elk.bundled.js'

import type { LayoutEdgeRef, LayoutNodeRef, LayoutPosition } from './flowGraphLayeredLayout'
import {
  computeLayeredPositions,
  LAYOUT_ORIGIN_X,
  LAYOUT_ORIGIN_Y,
  resolveLayoutSize,
} from './flowGraphLayeredLayout'
import type { FlowLayoutPreset } from './flowLayoutPreset'

/** 同层间距、层间距、平行边间距（像素） */
const SPACING = { node: 52, layer: 160, edge: 24 }

export interface ElkLayoutNode extends LayoutNodeRef {
  /** 条件分支出边锚点，从上到下。没有则所有出边共用一个出口 */
  sourcePortOrder?: string[]
}

export interface ElkLayoutEdge extends LayoutEdgeRef {
  id?: string | null
  sourceHandle?: string | null
}

export interface ElkLayoutResult {
  positions: Map<string, LayoutPosition>
  /** 边 id → 正交拐点（含起终点）。只用于当次绘制，不写入图数据 */
  routes: Map<string, LayoutPosition[]>
  /** true 表示 ELK 未成功，坐标来自内置分层 */
  fallback: boolean
}

type ElkLike = {
  layout: (graph: ElkGraph) => Promise<ElkGraph>
}

interface ElkPoint {
  x: number
  y: number
}

interface ElkPort {
  id: string
  layoutOptions?: Record<string, string>
}

interface ElkEdge {
  id: string
  sources: string[]
  targets: string[]
  sections?: Array<{
    startPoint?: ElkPoint
    endPoint?: ElkPoint
    bendPoints?: ElkPoint[]
  }>
}

interface ElkGraph {
  id: string
  x?: number
  y?: number
  width?: number
  height?: number
  layoutOptions?: Record<string, string>
  ports?: ElkPort[]
  children?: ElkGraph[]
  edges?: ElkEdge[]
}

/**
 * 单测会挂一个没有 Error 的 window 桩。
 * ELK 只要看到 window 就把它当全局对象，这里在构造期间暂时换成真正的全局对象。
 */
function createElk(): ElkLike {
  const host = globalThis as typeof globalThis & { window?: { Error?: unknown } }
  const previous = host.window
  const stub = previous != null && typeof previous.Error !== 'function'
  if (stub) host.window = host
  try {
    return new ELK()
  } finally {
    if (stub) host.window = previous
  }
}

const elk: ElkLike = createElk()

/**
 * 按预设排整图。
 * @param engine 测试可注入会抛错的实现，以覆盖回退路径
 */
export async function layoutWithElk(
  nodes: ElkLayoutNode[],
  edges: ElkLayoutEdge[],
  preset: FlowLayoutPreset,
  engine: ElkLike = elk,
): Promise<ElkLayoutResult> {
  try {
    const laid = await layoutByEngine(nodes, edges, preset, engine)
    if (laid.positions.size === 0 && nodes.length > 0) {
      return { positions: fallbackPositions(nodes, edges, preset), routes: new Map(), fallback: true }
    }
    return { ...laid, fallback: false }
  } catch {
    return { positions: fallbackPositions(nodes, edges, preset), routes: new Map(), fallback: true }
  }
}

async function layoutByEngine(
  nodes: ElkLayoutNode[],
  edges: ElkLayoutEdge[],
  preset: FlowLayoutPreset,
  engine: ElkLike,
): Promise<Pick<ElkLayoutResult, 'positions' | 'routes'>> {
  const gap = SPACING
  const inSide = preset.direction === 'DOWN' ? 'NORTH' : 'WEST'
  const outSide = preset.direction === 'DOWN' ? 'SOUTH' : 'EAST'
  const children: ElkGraph[] = []
  const seen = new Set<string>()
  const nodeById = new Map<string, ElkLayoutNode>()
  for (const n of nodes) {
    if (!n?.id || seen.has(n.id)) continue
    seen.add(n.id)
    nodeById.set(n.id, n)
    const size = resolveLayoutSize(n)
    children.push({
      id: n.id,
      width: size.w,
      height: size.h,
      layoutOptions: { 'elk.portConstraints': 'FIXED_ORDER' },
    })
  }

  const usable = edges.filter((e) => {
    const s = e?.source
    const t = e?.target
    return !!s && !!t && s !== t && seen.has(s) && seen.has(t)
  })

  const portsByNode = new Map<string, ElkPort[]>()
  const elkEdges: ElkEdge[] = []
  usable.forEach((e, index) => {
    const source = String(e.source)
    const target = String(e.target)
    const edgeId = e.id && String(e.id).trim() ? String(e.id) : `e${index}`
    const outKey = outPortKey(nodeById.get(source), e)
    const outPortId = portId(source, outKey)
    const inPortId = portId(target, `in-${edgeId}`)
    addPort(portsByNode, source, outPortId, outSide, outPortIndex(nodeById.get(source), outKey))
    const inIndex = (portsByNode.get(target) ?? []).filter((p) => p.layoutOptions?.['elk.port.side'] === inSide).length
    addPort(portsByNode, target, inPortId, inSide, inIndex)
    elkEdges.push({ id: edgeId, sources: [outPortId], targets: [inPortId] })
  })
  for (const child of children) {
    child.ports = portsByNode.get(child.id) ?? []
  }

  const laid = await engine.layout({
    id: 'root',
    layoutOptions: {
      'elk.algorithm': 'layered',
      'elk.direction': preset.direction,
      'elk.edgeRouting': 'ORTHOGONAL',
      'elk.spacing.nodeNode': String(gap.node),
      'elk.layered.spacing.nodeNodeBetweenLayers': String(gap.layer),
      'elk.spacing.edgeEdge': String(gap.edge),
      'elk.layered.spacing.edgeEdgeBetweenLayers': String(gap.edge),
      'elk.layered.spacing.edgeNodeBetweenLayers': String(gap.edge),
      'elk.spacing.edgeNode': String(Math.max(12, gap.edge - 8)),
      'elk.padding': '[top=0,left=0,bottom=0,right=0]',
      ...wrapOptions(preset),
    },
    children,
    edges: elkEdges,
  })

  const raw = new Map<string, LayoutPosition>()
  for (const child of laid.children ?? []) {
    if (child.x == null || child.y == null) continue
    raw.set(child.id, { x: child.x, y: child.y })
  }
  const { positions, dx, dy } = rebaseToOrigin(raw)
  const routes = new Map<string, LayoutPosition[]>()
  for (const edge of laid.edges ?? []) {
    const points = edgePoints(edge).map((p) => ({ x: p.x + dx, y: p.y + dy }))
    if (points.length >= 2) routes.set(edge.id, points)
  }
  return { positions, routes }
}

/** 长链大约四到六个节点一排。再宽一档，五步左右的流会整行排回去。MULTI_EDGE 才能切开跨层的条件边，否则整图仍是一行 */
function wrapOptions(preset: FlowLayoutPreset): Record<string, string> {
  if (!preset.wrap) return {}
  return {
    'elk.layered.wrapping.strategy': 'MULTI_EDGE',
    'elk.aspectRatio': '6.2',
    'elk.layered.wrapping.additionalEdgeSpacing': '80',
  }
}

function portId(nodeId: string, key: string): string {
  return `${nodeId}__${key}`
}

function outPortKey(node: ElkLayoutNode | undefined, edge: ElkLayoutEdge): string {
  const handle = edge.sourceHandle != null ? String(edge.sourceHandle).trim() : ''
  if (handle) return handle
  return 'out'
}

function outPortIndex(node: ElkLayoutNode | undefined, key: string): number {
  const order = node?.sourcePortOrder ?? []
  const at = order.indexOf(key)
  return at >= 0 ? at : order.length
}

function addPort(portsByNode: Map<string, ElkPort[]>, nodeId: string, id: string, side: string, index: number) {
  const list = portsByNode.get(nodeId) ?? []
  if (list.some((port) => port.id === id)) {
    portsByNode.set(nodeId, list)
    return
  }
  list.push({
    id,
    layoutOptions: {
      'elk.port.side': side,
      'elk.port.index': String(index),
    },
  })
  portsByNode.set(nodeId, list)
}

function edgePoints(edge: ElkEdge): ElkPoint[] {
  const points: ElkPoint[] = []
  for (const section of edge.sections ?? []) {
    const chunk = [
      section.startPoint,
      ...(section.bendPoints ?? []),
      section.endPoint,
    ].filter((p): p is ElkPoint => p != null && Number.isFinite(p.x) && Number.isFinite(p.y))
    for (const point of chunk) {
      const prev = points[points.length - 1]
      if (prev && Math.abs(prev.x - point.x) < 0.5 && Math.abs(prev.y - point.y) < 0.5) continue
      points.push(point)
    }
  }
  return points
}

/** 内置分层只有左到右；纵向时把横纵坐标对调后再对齐原点 */
function fallbackPositions(
  nodes: LayoutNodeRef[],
  edges: LayoutEdgeRef[],
  preset: FlowLayoutPreset,
): Map<string, LayoutPosition> {
  const layered = computeLayeredPositions(nodes, edges)
  if (preset.direction === 'RIGHT') return rebaseToOrigin(layered).positions
  const swapped = new Map<string, LayoutPosition>()
  for (const [id, p] of layered) {
    swapped.set(id, { x: p.y, y: p.x })
  }
  return rebaseToOrigin(swapped).positions
}

/** 把最小坐标挪到画布原点附近，避免整图贴在 (0,0)。拐点使用同一偏移 */
function rebaseToOrigin(positions: Map<string, LayoutPosition>): {
  positions: Map<string, LayoutPosition>
  dx: number
  dy: number
} {
  let minX = Number.POSITIVE_INFINITY
  let minY = Number.POSITIVE_INFINITY
  for (const p of positions.values()) {
    minX = Math.min(minX, p.x)
    minY = Math.min(minY, p.y)
  }
  if (!Number.isFinite(minX) || !Number.isFinite(minY)) {
    return { positions, dx: 0, dy: 0 }
  }
  const dx = LAYOUT_ORIGIN_X - minX
  const dy = LAYOUT_ORIGIN_Y - minY
  const out = new Map<string, LayoutPosition>()
  for (const [id, p] of positions) {
    out.set(id, { x: p.x + dx, y: p.y + dy })
  }
  return { positions: out, dx, dy }
}
