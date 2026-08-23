/**
 * 测值剥离：结构里的 value / example 进 testValueConfig，结构侧删掉。
 */
import { describe, expect, it } from 'vitest'
import { peelTestValuesFromStructure } from '@/views/project/testProject/utils/peelTestValueConfig'

describe('peelTestValuesFromStructure', () => {
  it('剥离 query value 与 body example', () => {
    const { requestConfig, testValueConfig } = peelTestValuesFromStructure(
      {
        method: 'POST',
        queryParams: [{ name: 'q', value: 'x', type: 'string' }],
        body: { mode: 'json', json: { schema: { type: 'object' }, example: { a: 1 } } },
      },
      { configVersion: 1, responses: [] },
      null,
    )
    expect(requestConfig.queryParams[0].value).toBeUndefined()
    expect(requestConfig.body.json.example).toBeUndefined()
    expect(testValueConfig.request.paramDefaults.q).toBe('x')
    expect(testValueConfig.request.bodyExample).toEqual({ a: 1 })
  })

  it('剥离响应 example 到 examplesById', () => {
    const { responseConfig, testValueConfig } = peelTestValuesFromStructure(
      {},
      {
        responses: [{ id: 'r1', example: { code: 0 }, schema: { type: 'object' } }],
      },
      null,
    )
    expect(responseConfig.responses[0].example).toBeUndefined()
    expect(testValueConfig.response.examplesById.r1).toEqual({ code: 0 })
  })
})
