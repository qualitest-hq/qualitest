/**
 * 测 stagingFieldDiff：节点/边/场景字段差异行与写回。
 * 边界：纯函数，无 store 依赖。
 * 单跑：pnpm test stagingFieldDiff   （在 qualitest-ui 或 apps/web 下）
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
    // 前提：baseline 与 draft 仅 name 不同
    // 期望：只输出 data.name 一行差异
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
    expect(rows[0].label).toBe('节点名称');
    expect(rows[0].baselineText).toBe('登录');
    expect(rows[0].draftValue).toBe('登录V2');
  });

  it('extracts 变更带中文标签且标记为多行', () => {
    const rows = buildNodeStagingFieldRows(
      {
        type: 'http',
        position: { x: 100, y: 200 },
        data: { extracts: [{ name: 'a', expr: '$.data.items.cartId' }] },
      },
      {
        type: 'http',
        position: { x: 100, y: 200 },
        data: { extracts: [{ name: 'a', expr: "$.data[?(@.cartId=='5001')].cartId" }] },
      },
    );
    expect(rows).toHaveLength(1);
    expect(rows[0].key).toBe('data.extracts');
    expect(rows[0].label).toBe('抽取配置');
    expect(rows[0].multiline).toBe(true);
    expect(rows[0].draftValue).toContain('\n');
  });

  it('headers 含 profileManaged 时标签带按项目鉴权补全', () => {
    // 前提：draft headers 含托管 Authorization，baseline 无
    // 期望：字段标签为「请求头（按项目鉴权补全）」
    const rows = buildNodeStagingFieldRows(
      { type: 'http', data: {} },
      {
        type: 'http',
        data: {
          headers: [
            {
              name: 'Authorization',
              value: 'Bearer {{flow.token}}',
              profileManaged: true,
            },
          ],
        },
      },
    );
    expect(rows.some((r) => r.key === 'data.headers')).toBe(true);
    expect(rows.find((r) => r.key === 'data.headers')?.label).toBe('请求头（按项目鉴权补全）');
  });
});

describe('buildEdgeStagingFieldRows', () => {
  it('输出变更的 source/target/label', () => {
    // 前提：baseline 与 draft 的 target、label 不同
    // 期望：仅输出 target 与 label 两行
    const rows = buildEdgeStagingFieldRows(
      { source: '1001', target: '1002', label: 'A' },
      { source: '1001', target: '1003', label: 'B' },
    );
    expect(rows.map((r) => r.key)).toEqual(['target', 'label']);
  });
});

describe('buildScenarioStagingFieldRows', () => {
  it('输出场景字段差异', () => {
    // 前提：场景 name 变更，remark 相同
    // 期望：仅输出 name 差异行
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
    // 前提：draft 含 data.name，写入新值
    // 期望：data.name 更新为新名称
    const next = applyStagingFieldToDraft(
      { data: { name: '旧' } },
      'data.name',
      '新名称',
    );
    expect((next.data as Record<string, unknown>).name).toBe('新名称');
  });

  it('写回 extracts JSON 时还原为对象', () => {
    const next = applyStagingFieldToDraft(
      { data: { extracts: [] } },
      'data.extracts',
      '[{"from":"body","expr":"$.data[0].cartId","name":"cartId1"}]',
    );
    const extracts = (next.data as Record<string, unknown>).extracts as Array<Record<string, string>>;
    expect(Array.isArray(extracts)).toBe(true);
    expect(extracts[0].expr).toBe('$.data[0].cartId');
  });

  it('写回场景 name 字段', () => {
    // 前提：场景 draft 含 name 字段
    // 期望：name 更新为新场景名
    const next = applyStagingFieldToDraft({ name: '旧场景' }, 'name', '新场景');
    expect(next.name).toBe('新场景');
  });
});
