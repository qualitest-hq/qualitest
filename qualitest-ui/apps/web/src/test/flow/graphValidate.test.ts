/**
 * validateGraphJson / validateStartNodes 单元测试：验证流程图 JSON 的结构与业务规则校验（与后端 GraphJsonValidator 对齐）。
 *
 * validateGraphJson 检查唯一开始节点、HTTP 节点 API 绑定、脚本 language/source 等，
 * 返回 errors（阻断）与 warnings（提示）。validateStartNodes 单独校验开始节点数量。
 *
 * 数据驱动：用例来自 fixtures/graph-validate-cases.json，与后端 graph-validate-cases.json 共享。
 *
 * 运行（apps/web 目录）：yarn test graphValidate
 */
import { describe, expect, it } from 'vitest';

import { validateGraphJson, validateStartNodes } from '@/utils/flow/graphValidate';

import demoGraph from './fixtures/demo-graph.json';
import graphValidateCases from './fixtures/graph-validate-cases.json';
import duplicateNodeId from './fixtures/invalid-duplicate-node-id.json';
import edgeExtraField from './fixtures/invalid-edge-extra-field.json';
import httpUnbound from './fixtures/invalid-http-unbound.json';
import invalidHttpMissingCallmode from './fixtures/invalid-http-missing-callmode.json';
import missingMetaRun from './fixtures/invalid-missing-meta-run.json';
import multiStart from './fixtures/invalid-multi-start.json';
import invalidNodeType from './fixtures/invalid-node-type.json';
import invalidScriptEmptySource from './fixtures/invalid-script-empty-source.json';
import invalidScriptLanguage from './fixtures/invalid-script-language.json';
import invalidSubflowMissingId from './fixtures/invalid-subflow-missing-id.json';
import noStart from './fixtures/invalid-no-start.json';

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
  // eslint-disable-next-line no-console
  console.log(`\n=== validateGraphJson fixture cases (${graphValidateCases.cases.length}) ===`);

  /** 标准 demo-graph 结构完整、规则合规，errors 和 warnings 均为空 */
  it('demoGraphValidate_zeroErrors', () => {
    const result = validateGraphJson(demoGraph);
    expect(result.ok).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(result.warnings).toHaveLength(0);
  });

  /** 多个无入边节点（多个开始节点）时校验应失败，1 条 error 含「开始节点」 */
  it('multiStartNode_producesError', () => {
    const result = validateGraphJson(multiStart);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('开始节点');
  });

  /** HTTP 节点未绑定 testProjectApiId 时产生 warning，但不阻断校验（ok 仍为 true） */
  it('httpUnbound_producesWarning', () => {
    const result = validateGraphJson(httpUnbound);
    expect(result.ok).toBe(true);
    expect(result.warnings).toHaveLength(1);
    expect(result.warnings[0]).toContain('testProjectApiId');
  });

  /** script 节点 language 非法时产生 error */
  it('scriptInvalidLanguage_producesError', () => {
    const result = validateGraphJson(invalidScriptLanguage);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('language');
  });

  /** script 节点 source 为空时产生 warning */
  it('scriptEmptySource_producesWarning', () => {
    const result = validateGraphJson(invalidScriptEmptySource);
    expect(result.ok).toBe(true);
    expect(result.warnings).toHaveLength(1);
    expect(result.warnings[0]).toContain('source');
  });

  /** HTTP 节点缺少 callMode 时产生 error */
  it('httpMissingCallMode_producesError', () => {
    const result = validateGraphJson(invalidHttpMissingCallmode);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('callMode');
  });

  /** 子流节点缺少 subflowId 时产生 error 与 warnings */
  it('subflowMissingId_producesErrorAndWarnings', () => {
    const result = validateGraphJson(invalidSubflowMissingId);
    expect(result.ok).toBe(false);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]).toContain('subflowId');
    expect(result.warnings).toHaveLength(2);
  });

  /** 数据驱动：遍历 graph-validate-cases manifest，断言 expectErrors / expectWarnings 条数 */
  it('validateCases_fromManifest', () => {
    graphValidateCases.cases.forEach((spec) => {
      const raw = fixtureMap[spec.fixture];
      expect(raw, `missing fixture: ${spec.fixture}`).toBeDefined();
      const result = validateGraphJson(raw);
      expect(result.errors).toHaveLength(spec.expectErrors);
      expect(result.warnings).toHaveLength(spec.expectWarnings);
    });
  });
});

describe('validateStartNodes', () => {
  /** demo-graph 恰有一个开始节点 */
  it('demoGraph_ok', () => {
    const result = validateStartNodes(demoGraph);
    expect(result.ok).toBe(true);
    expect(result.ids).toHaveLength(1);
  });

  /** 多个无入边节点时 validateStartNodes 应失败，返回所有候选开始节点 id */
  it('multiStart_fails', () => {
    const result = validateStartNodes(multiStart);
    expect(result.ok).toBe(false);
    expect(result.message).toContain('开始节点');
    expect(result.ids.length).toBeGreaterThan(1);
  });

  /** 环图无开始节点时应失败，消息含「未找到开始节点」，ids 为空 */
  it('noStart_fails', () => {
    const result = validateStartNodes(noStart);
    expect(result.ok).toBe(false);
    expect(result.message).toContain('未找到开始节点');
    expect(result.ids).toHaveLength(0);
  });
});
