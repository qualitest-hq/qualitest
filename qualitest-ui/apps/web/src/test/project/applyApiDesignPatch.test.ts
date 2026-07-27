/**
 * 测 applyApiDesignChangesToDetail：AI API 设计 patch 合并进 apiDetail 草稿。
 * 边界：纯函数，内存 detail 对象，不落库。
 * 单跑：yarn test applyApiDesignPatch   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest'

import { applyApiDesignChangesToDetail } from '@/views/project/testProject/utils/applyApiDesignPatch'

describe('applyApiDesignChangesToDetail', () => {
  it('写入 query 约束与测试值', () => {
    // 前提：changes 含 query 约束与 paramDefaults
    // 期望：queryParams 写入 pattern/maxLength/value
    const detail = {
      requestConfig: JSON.stringify({
        configVersion: 1,
        method: 'GET',
        queryParams: [{ name: 'mobile', type: 'string', value: '' }],
        pathParams: [],
        declaredHeaders: [],
        body: { mode: 'none', json: { schema: null, example: null }, formData: [], urlencoded: [] },
      }),
      preRequestScript: '',
      postRequestScript: '',
    }
    const next = applyApiDesignChangesToDetail(detail, [
      {
        target: 'request.queryParams',
        path: 'mobile',
        type: 'string',
        action: 'updateConstraints',
        constraints: { pattern: '^1\\d{10}$', maxLength: 11 },
      },
      {
        target: 'testValue.request.paramDefaults',
        path: 'mobile',
        action: 'set',
        value: '{{asset.demo.mobile}}',
      },
    ])
    const rc = JSON.parse(next.requestConfig)
    expect(rc.queryParams[0].pattern).toBe('^1\\d{10}$')
    expect(rc.queryParams[0].maxLength).toBe(11)
    expect(rc.queryParams[0].value).toBe('{{asset.demo.mobile}}')
  })

  it('写入脚本与元信息（apiDescription），未涉及的脚本保持不变', () => {
    // 前提：changes 仅更新 post 脚本与 apiDescription
    // 期望：postRequestScript/apiDescription 更新，preRequestScript 不变
    const detail = { apiDescription: '', preRequestScript: 'old', postRequestScript: '' }
    const next = applyApiDesignChangesToDetail(detail, [
      { target: 'script', phase: 'post', action: 'update', content: 'api.test("x", () => {});' },
      { target: 'meta', path: 'apiDescription', action: 'update', content: '说明' },
    ])
    expect(next.postRequestScript).toContain('api.test')
    expect(next.apiDescription).toBe('说明')
    expect(next.preRequestScript).toBe('old')
  })
})
