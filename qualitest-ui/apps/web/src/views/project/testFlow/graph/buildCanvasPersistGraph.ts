/**
 * 从画布编辑态组装「可落盘」的 graph_json。
 *
 * 包含：节点、边、视口、运行场景、流输出，以及这条流当前的排版方式；
 * 会排除尚未确认的 AI Staging 新增项，并把未确认的修改按基线回滚后再序列化，
 * 避免临时草稿污染结构校验与 API 语义预检结果。
 */
import type { GraphJson } from '@/utils/flow/graphTypes'

import { buildPersistFilter } from '../staging/canvasStagingFacade'
import { buildFlowGraphInput } from '../utils/stagingGraphInput'
import { useFlowCanvasStore } from '../stores/flowCanvasStore'
import { toGraphJson } from './graphAdapter'

/** @returns 当前画布对应的 GraphJson 对象 */
export function buildCanvasPersistGraph(): GraphJson {
  const store = useFlowCanvasStore()
  return toGraphJson({
    ...buildFlowGraphInput(store),
    stagingFilter: buildPersistFilter(),
  })
}
