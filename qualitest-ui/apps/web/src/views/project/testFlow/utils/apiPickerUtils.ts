/** API 树扁平化后的条目，供接口选择器搜索与展示 */
export interface FlatApiItem {
  testProjectApiId: string;
  apiName: string;
  apiPath: string;
  httpMethod: string;
  groupPath: string;
}

/** 将项目 API 树节点递归展平为可选接口列表 */
export function flattenApiTree(nodes: unknown[], groupPath = ''): FlatApiItem[] {
  const out: FlatApiItem[] = [];
  (nodes || []).forEach((raw) => {
    const n = raw as Record<string, unknown>;
    if (n.nodeType === 'api' || n.testProjectApiId) {
      out.push({
        testProjectApiId: String(n.testProjectApiId ?? ''),
        apiName: String(n.apiName || n.label || ''),
        apiPath: String(n.apiPath || ''),
        httpMethod: String(n.httpMethod || ''),
        groupPath: groupPath || String(n.groupPath || '未分组'),
      });
    } else if (Array.isArray(n.children) && n.children.length) {
      const g = String(n.label || n.groupName || groupPath);
      out.push(...flattenApiTree(n.children as unknown[], g));
    }
  });
  return out;
}

/** 生成接口 chip 展示文案 */
export function formatApiChipLabel(api: Pick<FlatApiItem, 'httpMethod' | 'apiName'>): string {
  const method = api.httpMethod?.trim() || '—';
  const name = api.apiName?.trim() || '未命名接口';
  return `${method} ${name}`;
}
