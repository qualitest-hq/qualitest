/**
 * 测 validateGraphJson / validateStartNodes：流程图 JSON 结构与业务规则校验。
 * 边界：纯函数；manifest 用例来自 @flow-fixtures/graph-validate-cases.json。
 * 单跑：yarn test graphValidate   （在 qualitest-ui 或 apps/web 下）
 */
import { describe, expect, it } from 'vitest';

import { validateGraphJson, validateStartNodes } from '@/utils/flow/graphValidate';

import demoGraph from '@flow-fixtures/demo-graph.json';
import graphValidateCases from '@flow-fixtures/graph-validate-cases.json';
import duplicateNodeId from '@flow-fixtures/invalid-duplicate-node-id.json';
import edgeExtraField from '@flow-fixtures/invalid-edge-extra-field.json';
import httpUnbound from '@flow-fixtures/invalid-http-unbound.json';
import invalidHttpMissingCallmode from '@flow-fixtures/invalid-http-missing-callmode.json';
import missingMetaRun from '@flow-fixtures/invalid-missing-meta-run.json';
import multiStart from '@flow-fixtures/invalid-multi-start.json';
import invalidNodeType from '@flow-fixtures/invalid-node-type.json';
import invalidScriptEmptySource from '@flow-fixtures/invalid-script-empty-source.json';
import invalidScriptLanguage from '@flow-fixtures/invalid-script-language.json';
import invalidSubflowMissingId from '@flow-fixtures/invalid-subflow-missing-id.json';
import noStart from '@flow-fixtures/invalid-no-start.json';

const fixtureMap: Record<string, unknown> = {
  'demo-graph.json': demoGraph,
  'invalid-multi-start.json': multiStart,
  'invalid-http-unbound.json': httpUnbound,
  'invalid-duplicate-node-id.json': duplicateNodeId,
  'invalid-node-type.json': invalidNodeType,
  'invalid-edge-extra-field.json': edgeExtraField,
  'invalid-missing-meta-run.json': missingMetaRun,
  'invalid-script-language.json': invalidScriptLanguage,
  'invalid-script-empty-source.json': invalidScriptEmptySource,
  'invalid-http-missing-callmode.json': invalidHttpMissingCallmode,
  'invalid-subflow-missing-id.json': invalidSubflowMissingId,
};

describe('validateGraphJson', () => {
  /** 标准 demo-graph 结构完整、规则合规，errors 和 warnings 均为空 */
  it('标准 demo-graph 校验通过，errors 与 warnings 均为空', () => {
    // 前提：输入标准 demo-graph fixture
    // 期望：ok 为 true，errors 与 warnings 均为空
    const result = validateGraphJson(demoGraph);
    expect(result.ok).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(result.warnings).toHaveLength(0);
  });

  /** 多个无入边节点（多个开始节点）时校验应失败，1 条 error 含「开始节点」 */
  it('存在多个开始节点时产生 1 条 error', () => {
    // 前提：图含多个无入边开始节点
    // 期望：ok 为 false，1 条 error 含「开始节点」
    const result = validateGraphJson(multiStart);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('开始节点');
  });

  /** HTTP 节点未绑定 testProjectApiId 时产生 warning，但不阻断校验（ok 仍为 true） */
  it('HTTP 节点未绑定 API 时产生 warning 但不阻断', () => {
    // 前提：HTTP 节点缺 testProjectApiId
    // 期望：ok 仍为 true，1 条 warning
    const result = validateGraphJson(httpUnbound);
    expect(result.ok).toBe(true);
    expect(result.warnings).toHaveLength(1);
    expect(result.warnings[0]).toContain('testProjectApiId');
  });

  /** script 节点 language 非法时产生 error */
  it('script 节点 language 非法时产生 error', () => {
    // 前提：script 节点 language 不在允许范围
    // 期望：ok 为 false，error 含 language
    const result = validateGraphJson(invalidScriptLanguage);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('language');
  });

  /** script 节点 source 为空时产生 warning */
  it('script 节点 source 为空时产生 warning', () => {
    // 前提：script 节点 source 为空
    // 期望：ok 为 true，warning 含 source
    const result = validateGraphJson(invalidScriptEmptySource);
    expect(result.ok).toBe(true);
    expect(result.warnings).toHaveLength(1);
    expect(result.warnings[0]).toContain('source');
  });

  /** HTTP 节点缺少 callMode 时产生 error */
  it('HTTP 节点缺少 callMode 时产生 error', () => {
    // 前提：HTTP 节点无 callMode
    // 期望：ok 为 false，error 含 callMode
    const result = validateGraphJson(invalidHttpMissingCallmode);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('callMode');
  });

  /** 子流节点缺少 subflowId 时产生 error 与 warnings */
  it('子流节点缺少 subflowId 时产生 error 与 warnings', () => {
    // 前提：subflow 节点缺 subflowId
    // 期望：1 条 error、2 条 warnings
    const result = validateGraphJson(invalidSubflowMissingId);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('subflowId');
    expect(result.warnings).toHaveLength(2);
  });

  it('manifest：demo-graph 结构合规，errors=0 warnings=0', () => {
    // 前提：manifest demo-graph-ok fixture
    // 期望：errors=0，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'demo-graph-ok')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：多个开始节点，errors=1 warnings=0', () => {
    // 前提：manifest multi-start fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'multi-start')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：HTTP 节点未绑定，errors=0 warnings=1', () => {
    // 前提：manifest http-unbound fixture
    // 期望：errors=0，warnings=1
    const spec = graphValidateCases.cases.find((x) => x.id === 'http-unbound')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：节点 id 重复，errors=1 warnings=0', () => {
    // 前提：manifest duplicate-node-id fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'duplicate-node-id')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：非法节点类型，errors=1 warnings=0', () => {
    // 前提：manifest invalid-node-type fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'invalid-node-type')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：边包含未知字段，errors=1 warnings=0', () => {
    // 前提：manifest edge-extra-field fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'edge-extra-field')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：缺少 meta.run 配置，errors=1 warnings=0', () => {
    // 前提：manifest missing-meta-run fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'missing-meta-run')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：script 节点 language 非法，errors=1 warnings=0', () => {
    // 前提：manifest script-invalid-language fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'script-invalid-language')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：HTTP 节点缺少 callMode，errors=1 warnings=0', () => {
    // 前提：manifest http-missing-callmode fixture
    // 期望：errors=1，warnings=0
    const spec = graphValidateCases.cases.find((x) => x.id === 'http-missing-callmode')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：script 节点 source 为空，errors=0 warnings=1', () => {
    // 前提：manifest script-empty-source fixture
    // 期望：errors=0，warnings=1
    const spec = graphValidateCases.cases.find((x) => x.id === 'script-empty-source')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('manifest：子流节点缺少 subflowId，errors=1 warnings=2', () => {
    // 前提：manifest subflow-missing-id fixture
    // 期望：errors=1，warnings=2
    const spec = graphValidateCases.cases.find((x) => x.id === 'subflow-missing-id')!;
    const raw = fixtureMap[spec.fixture];
    const result = validateGraphJson(raw);
    expect(result.errors).toHaveLength(spec.expectErrors);
    expect(result.warnings).toHaveLength(spec.expectWarnings);
  });

  it('assert 空 rules / assign 空 assignments / delay 超限 产生 error', () => {
    const base = {
      meta: { scenarios: [{ id: 's1', name: '默认' }] },
      edges: [] as unknown[],
    };
    expect(
      validateGraphJson({
        ...base,
        nodes: [{ id: '1', type: 'assert', position: { x: 0, y: 0 }, data: { name: 'a', rules: [] } }],
      }).errors.some((e) => e.includes('rules 不能为空')),
    ).toBe(true);
    expect(
      validateGraphJson({
        ...base,
        nodes: [{ id: '1', type: 'assign', position: { x: 0, y: 0 }, data: { name: 'as', assignments: [] } }],
      }).errors.some((e) => e.includes('assignments 不能为空')),
    ).toBe(true);
    expect(
      validateGraphJson({
        ...base,
        nodes: [{ id: '1', type: 'delay', position: { x: 0, y: 0 }, data: { name: 'd', ms: 70000 } }],
      }).errors.some((e) => e.includes('ms 超过上限')),
    ).toBe(true);
    expect(
      validateGraphJson({
        ...base,
        nodes: [{ id: '1', type: 'condition', position: { x: 0, y: 0 }, data: { name: 'c', branches: [] } }],
      }).errors.some((e) => e.includes('缺少 branches')),
    ).toBe(true);
  });
});

describe('validateStartNodes', () => {
  /** demo-graph 恰有一个开始节点 */
  it('demo-graph 恰有一个开始节点', () => {
    // 前提：标准 demo-graph
    // 期望：ok 为 true，ids 长度为 1
    const result = validateStartNodes(demoGraph);
    expect(result.ok).toBe(true);
    expect(result.ids).toHaveLength(1);
  });

  /** 多个无入边节点时 validateStartNodes 应失败，返回所有候选开始节点 id */
  it('多个候选开始节点时校验失败，返回全部候选 id', () => {
    // 前提：multi-start 图
    // 期望：ok 为 false，ids 多于 1
    const result = validateStartNodes(multiStart);
    expect(result.ok).toBe(false);
    expect(result.message).toContain('开始节点');
    expect(result.ids.length).toBeGreaterThan(1);
  });

  /** 环图无开始节点时应失败，消息含「未找到开始节点」，ids 为空 */
  it('环图无开始节点时校验失败，提示未找到开始节点', () => {
    // 前提：no-start 环图
    // 期望：ok 为 false，消息含「未找到开始节点」，ids 为空
    const result = validateStartNodes(noStart);
    expect(result.ok).toBe(false);
    expect(result.message).toContain('未找到开始节点');
    expect(result.ids).toHaveLength(0);
  });
});
