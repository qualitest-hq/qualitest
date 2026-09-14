/**
 * 模板预制参数 ↔ 变量条目互转单测。
 * 纯函数，无 UI。
 *
 * 单跑：pnpm test templateParamUtils
 */
import { describe, expect, it } from 'vitest'

import {
  entriesToSheetRows,
  sheetRowsToEntries,
} from '@/views/project/testProject/utils/variableEntryUtils'
import {
  rebuildTemplateParamsFromVariableEntries,
  templateEnvsToEnvParamRows,
  templateParamsToVariableEntries,
  variableEntriesToTemplateParamRows,
} from '@/views/project/testProjectTemplate/utils/templateParamUtils'

describe('templateParams ↔ 变量条目', () => {
  it('object 素材 clientAuth 往返保持字段', () => {
    // 前提：预制参数里有一条 object 型 clientAuth（及会被丢弃的 flow）
    const params = [
      {
        kind: 'flow',
        name: 'legacyToken',
        value: 'debug',
        remark: '',
      },
      {
        kind: 'asset',
        name: 'clientAuth',
        value: { mobile: '13800000001', password: 'Test@123456' },
        remark: '主测号',
      },
    ]

    const entries = templateParamsToVariableEntries(params, 'asset')
    // 期望：条目包装为 assets.clientAuth
    expect(entries).toHaveLength(1)
    expect(entries[0].key).toBe('clientAuth')
    expect(entries[0].assets.clientAuth).toEqual({
      mobile: '13800000001',
      password: 'Test@123456',
    })

    const sheet = entriesToSheetRows(entries)
    const backEntries = sheetRowsToEntries(sheet)
    const rows = variableEntriesToTemplateParamRows(backEntries, 'asset')

    // 期望：压回 templateParam 后 value 仍是对象字段
    expect(rows).toHaveLength(1)
    expect(rows[0]).toMatchObject({
      kind: 'asset',
      name: 'clientAuth',
      value: { mobile: '13800000001', password: 'Test@123456' },
      remark: '主测号',
    })

    const rebuilt = rebuildTemplateParamsFromVariableEntries(params, {
      assetEntries: backEntries,
    })
    // 期望：只保留 asset，不再保留 flow
    expect(rebuilt).toEqual([
      {
        kind: 'asset',
        name: 'clientAuth',
        value: { mobile: '13800000001', password: 'Test@123456' },
        remark: '主测号',
      },
    ])
  })

  it('rebuild 丢弃 params 中的 kind=flow/env', () => {
    // 前提：params 含 flow / env / asset
    const params = [
      { kind: 'flow', name: 'token', value: 'x', remark: '' },
      { kind: 'env', name: 'timeout', value: '5000', remark: '' },
      { kind: 'asset', name: 'adminAuth', value: { username: 'admin' }, remark: '' },
    ]
    const assetEntries = templateParamsToVariableEntries(params, 'asset')

    // 期望：rebuild 只保留传入的 asset
    const rebuilt = rebuildTemplateParamsFromVariableEntries(params, { assetEntries })
    expect(rebuilt).toEqual([
      {
        kind: 'asset',
        name: 'adminAuth',
        value: { username: 'admin' },
        remark: '',
      },
    ])
    expect(rebuilt.every((row) => row.kind === 'asset')).toBe(true)
  })

  it('扁平行编辑 object 子字段后仍能压回 templateParams', () => {
    // 前提：sheet 上建 clientAuth object + 两个子字段
    const entries = templateParamsToVariableEntries(
      [
        {
          kind: 'asset',
          name: 'clientAuth',
          value: { mobile: '13800000001', password: 'Test@123456' },
        },
      ],
      'asset',
    )
    const sheet = entriesToSheetRows(entries)
    expect(sheet.some((r) => r.key === 'mobile')).toBe(true)
    expect(sheet.some((r) => r.type === 'object')).toBe(true)

    const mobileRow = sheet.find((r) => r.key === 'mobile')
    mobileRow.value = '13900000002'

    const next = variableEntriesToTemplateParamRows(sheetRowsToEntries(sheet), 'asset')
    // 期望：改过的 mobile 写回 value
    expect(next[0].value.mobile).toBe('13900000002')
    expect(next[0].value.password).toBe('Test@123456')
  })
})

describe('templateEnvsToEnvParamRows', () => {
  it('envUrl 合成 baseUrl，变量按 key 去重', () => {
    const rows = templateEnvsToEnvParamRows([
      {
        envName: '默认环境',
        envUrl: 'http://localhost:8801',
        envVariables: [
          { key: 'timeout', remark: '', assets: { timeout: '3000' } },
          { key: 'timeout', remark: '重复', assets: { timeout: '9999' } },
        ],
      },
    ])
    expect(rows[0]).toMatchObject({ name: 'baseUrl', value: 'http://localhost:8801' })
    expect(rows.filter((r) => r.name === 'timeout')).toHaveLength(1)
  })
})
