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
  templateParamsToVariableEntries,
  variableEntriesToTemplateParamRows,
} from '@/views/project/testProjectTemplate/utils/templateParamUtils'

describe('templateParams ↔ 变量条目', () => {
  it('object 素材 clientAuth 往返保持字段', () => {
    // 前提：预制参数里有一条 object 型 clientAuth
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
      envEntries: [],
    })
    // 期望：存量 flow 保留，asset 正确
    expect(rebuilt).toEqual([
      { kind: 'flow', name: 'legacyToken', value: 'debug', remark: '' },
      {
        kind: 'asset',
        name: 'clientAuth',
        value: { mobile: '13800000001', password: 'Test@123456' },
        remark: '主测号',
      },
    ])
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
