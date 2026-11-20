/**
 * 节点库指针拖放。
 * 用 Pointer 事件实现：拖过阈值后出现整行拖影，body 加类强制四向箭头光标，
 * 在画布根内松手则调用 addNodeAtClient 创建节点；拖出画布外不创建。
 * 不使用浏览器 HTML5 拖放，以便自定义光标与拖影样式。
 */
import { onBeforeUnmount, type Ref } from 'vue'

import { isOverFlowCanvas } from './dropTarget'

/** 拖拽进行中挂在 document.body 上的 class，配合全局样式改光标 */
export const PALETTE_BODY_DRAG_CLASS = 'is-flow-palette-dragging'

/** 按下后移动超过该像素才进入正式拖拽，避免单击误触 */
const DRAG_THRESHOLD_PX = 5

/** 克隆拖影时需要从源行拷贝的计算样式键，保证拖影观感接近原卡片 */
const DRAG_GHOST_STYLE_KEYS = [
  'display',
  'flex-direction',
  'align-items',
  'justify-content',
  'align-self',
  'gap',
  'place-items',
  'width',
  'height',
  'min-width',
  'max-width',
  'flex',
  'flex-shrink',
  'flex-grow',
  'padding',
  'margin',
  'box-sizing',
  'border-top',
  'border-right',
  'border-bottom',
  'border-left',
  'border-radius',
  'background-color',
  'background-image',
  'box-shadow',
  'color',
  'font-family',
  'font-size',
  'font-weight',
  'line-height',
  'white-space',
  'overflow',
  'text-overflow',
]

type DragSession = {
  type: string
  source: HTMLElement
  startX: number
  startY: number
  active: boolean
  offsetX: number
  offsetY: number
  pointerId: number
}

/** 把源节点树的计算样式烤进克隆树，避免拖影丢失布局与配色 */
function bakeComputedStyles(sourceRoot: HTMLElement, cloneRoot: HTMLElement) {
  const sources = [sourceRoot, ...sourceRoot.querySelectorAll('*')]
  const clones = [cloneRoot, ...cloneRoot.querySelectorAll('*')]
  for (let i = 0; i < sources.length; i++) {
    const from = sources[i]
    const to = clones[i]
    if (!(from instanceof HTMLElement) || !(to instanceof HTMLElement)) continue
    const cs = getComputedStyle(from)
    for (const key of DRAG_GHOST_STYLE_KEYS) {
      to.style.setProperty(key, cs.getPropertyValue(key))
    }
    to.style.setProperty('transition', 'none')
    to.style.setProperty('transform', 'none')
  }
}

/**
 * 按源列表行克隆整行拖影，略放大并加阴影；返回外壳节点与指针相对偏移。
 */
function buildFullRowDragGhost(source: HTMLElement, clientX: number, clientY: number) {
  const rect = source.getBoundingClientRect()
  const card = source.cloneNode(true)
  if (!(card instanceof HTMLElement)) return null

  bakeComputedStyles(source, card)

  const scale = 1.12
  const pad = 12
  const baseW = Math.ceil(rect.width)
  const baseH = Math.ceil(rect.height)
  const scaledW = Math.ceil(baseW * scale)
  const scaledH = Math.ceil(baseH * scale)

  card.style.width = `${baseW}px`
  card.style.height = `${baseH}px`
  card.style.margin = '0'
  card.style.transform = `scale(${scale})`
  card.style.transformOrigin = 'top left'
  card.style.opacity = '0.92'
  card.style.backgroundColor = card.style.backgroundColor || '#ffffff'
  card.style.boxShadow = '0 8px 22px rgba(15, 40, 80, 0.22)'
  if (!card.style.borderTopWidth || card.style.borderTopWidth === '0px') {
    card.style.border = '1px solid #d8e2ee'
  }

  const shell = document.createElement('div')
  shell.setAttribute('aria-hidden', 'true')
  shell.style.cssText = [
    'position:fixed',
    'top:0',
    'left:0',
    'z-index:99999',
    `width:${scaledW + pad * 2}px`,
    `height:${scaledH + pad * 2}px`,
    `padding:${pad}px`,
    'box-sizing:border-box',
    'background:transparent',
    'overflow:visible',
    'pointer-events:none',
  ].join(';')
  shell.appendChild(card)
  document.body.appendChild(shell)

  const offsetX = pad + Math.max(0, Math.min(scaledW, (clientX - rect.left) * scale))
  const offsetY = pad + Math.max(0, Math.min(scaledH, (clientY - rect.top) * scale))
  return { ghost: shell, offsetX, offsetY }
}

/**
 * 绑定节点库行的 pointerdown；返回 onPointerDown 供模板挂载。
 * @param options.canEditFlow 无编辑权限时不进入拖拽、不落点
 * @param options.addNodeAtClient 松手且落在画布内时创建对应类型节点
 */
export function usePalettePointerDrag(options: {
  canEditFlow: Ref<boolean>
  addNodeAtClient: (type: string, clientX: number, clientY: number) => unknown
}) {
  let session: DragSession | null = null
  let dragGhostEl: HTMLElement | null = null

  function clearDragGhost() {
    if (dragGhostEl?.parentNode) dragGhostEl.parentNode.removeChild(dragGhostEl)
    dragGhostEl = null
  }

  function moveGhost(clientX: number, clientY: number, offsetX: number, offsetY: number) {
    if (!dragGhostEl) return
    dragGhostEl.style.left = `${clientX - offsetX}px`
    dragGhostEl.style.top = `${clientY - offsetY}px`
  }

  function endSession() {
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerup', onPointerUp)
    window.removeEventListener('pointercancel', onPointerUp)
    document.body.classList.remove(PALETTE_BODY_DRAG_CLASS)
    clearDragGhost()
    session = null
  }

  function onPointerMove(event: PointerEvent) {
    if (!session) return
    const dx = event.clientX - session.startX
    const dy = event.clientY - session.startY
    if (!session.active) {
      if (dx * dx + dy * dy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) return
      if (!options.canEditFlow.value) {
        endSession()
        return
      }
      clearDragGhost()
      const built = buildFullRowDragGhost(session.source, session.startX, session.startY)
      if (!built) {
        endSession()
        return
      }
      dragGhostEl = built.ghost
      session.offsetX = built.offsetX
      session.offsetY = built.offsetY
      session.active = true
      document.body.classList.add(PALETTE_BODY_DRAG_CLASS)
    }
    moveGhost(event.clientX, event.clientY, session.offsetX, session.offsetY)
  }

  function onPointerUp(event: PointerEvent) {
    if (!session) return
    const { type, active } = session
    const { clientX, clientY } = event
    endSession()
    if (!active || !options.canEditFlow.value) return
    if (!isOverFlowCanvas(clientX, clientY)) return
    options.addNodeAtClient(type, clientX, clientY)
  }

  function onPointerDown(event: PointerEvent, type: string) {
    if (event.button !== 0) return
    const source = event.currentTarget
    if (!(source instanceof HTMLElement)) return
    endSession()
    session = {
      type,
      source,
      startX: event.clientX,
      startY: event.clientY,
      active: false,
      offsetX: 0,
      offsetY: 0,
      pointerId: event.pointerId,
    }
    window.addEventListener('pointermove', onPointerMove)
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('pointercancel', onPointerUp)
  }

  onBeforeUnmount(() => {
    endSession()
  })

  return { onPointerDown, endSession }
}
