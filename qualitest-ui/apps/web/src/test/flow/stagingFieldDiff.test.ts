/**
 * stagingFieldDiff 单元测试。
 */
import { describe, expect, it } from 'vitest';

import {
  applyStagingFieldToDraft,
  buildEdgeStagingFieldRows,
  buildNodeStagingFieldRows,
  buildScenarioStagingFieldRows,
} from '@/views/project/testFlow/utils/stagingFieldDiff';

describe('buildNodeStagingFieldRows', () => {
  it('仅输出 baseline 与 draft 不同的字段', () => {
    const rows = buildNodeStagingFieldRows(
      {
        type: 'http',
        data: { name: '登录', externalUrl: 'https://a.com' },
      },
      {
        type: 'http',
        data: { name: '登录V2', externalUrl: 'https://a.com' },
      },
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].key).toBe('data.name');
    expect(rows[0].baselineText).toBe('登录');
    expect(rows[0].draftValue).toBe('登录V2');
  });
});

describe('buildEdgeStagingFieldRows', () => {
  it('输出变更的 source/target/label', () => {
    const rows = buildEdgeStagingFieldRows(
      { source: '1001', target: '1002', label: 'A' },
      { source: '1001', target: '1003', label: 'B' },
    );
    expect(rows.map((r) => r.key)).toEqual(['target', 'label']);
  });
});

describe('buildScenarioStagingFieldRows', () => {
  it('输出场景字段差异', () => {
    const rows = buildScenarioStagingFieldRows(
      { name: '默认', remark: '旧说明' },
      { name: '登录回归', remark: '旧说明' },
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].key).toBe('name');
  });
});

describe('applyStagingFieldToDraft', () => {
  it('写回 data 字段', () => {
    const next = applyStagingFieldToDraft(
      { data: { name: '旧' } },
      'data.name',
      '新名称',
    );
    expect((next.data as Record<string, unknown>).name).toBe('新名称');
  });

  it('写回场景 name 字段', () => {
    const next = applyStagingFieldToDraft({ name: '旧场景' }, 'name', '新场景');
    expect(next.name).toBe('新场景');
  });
});
