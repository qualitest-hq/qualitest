import { describe, expect, it } from 'vitest'

import { applyApiDesignChangesToDetail } from '@/views/project/testProject/utils/applyApiDesignPatch'

describe('applyApiDesignChangesToDetail', () => {
  it('applies query constraint and test value', () => {
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

  it('applies script and meta', () => {
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
