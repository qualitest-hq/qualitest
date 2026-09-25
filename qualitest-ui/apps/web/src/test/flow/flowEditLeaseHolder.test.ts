/**
 * 测谁：formatFlowEditLeaseHolder。
 * 边界：web:user:uuid、空串、非 web 前缀。
 * 单跑：pnpm test flowEditLeaseHolder（在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { formatFlowEditLeaseHolder } from '../../views/project/testFlow/utils/flowEditLeaseState'

describe('formatFlowEditLeaseHolder', () => {
  it('前提：web:alice:uuid；期望：alice', () => {
    expect(formatFlowEditLeaseHolder('web:alice:550e8400-e29b-41d4-a716-446655440000')).toBe('alice')
  })

  it('前提：空；期望：其他会话', () => {
    expect(formatFlowEditLeaseHolder('')).toBe('其他会话')
    expect(formatFlowEditLeaseHolder(null)).toBe('其他会话')
  })

  it('前提：mcp 短抢形态；期望：MCP', () => {
    expect(formatFlowEditLeaseHolder('mcp:abc')).toBe('MCP')
  })
})
