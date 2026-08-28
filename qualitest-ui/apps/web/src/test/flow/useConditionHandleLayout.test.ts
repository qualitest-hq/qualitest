/**
 * condition handle 行实测工具单测。
 * @vitest-environment jsdom
 */
import { describe, expect, it } from 'vitest';

import { measureConditionHandleTops } from '@/views/project/testFlow/composables/useConditionHandleLayout';

describe('measureConditionHandleTops', () => {
  it('按分支行 DOM 测算相对节点根的 top', () => {
    const root = document.createElement('div');
    root.style.position = 'relative';
    root.style.width = '340px';
    document.body.appendChild(root);

    const branchesEl = document.createElement('div');
    branchesEl.className = 'cond-node__branches';
    root.appendChild(branchesEl);

    const rowIf = document.createElement('div');
    rowIf.dataset.branchRow = 'b_if';
    rowIf.style.height = '40px';
    branchesEl.appendChild(rowIf);

    const rowElse = document.createElement('div');
    rowElse.dataset.branchRow = 'b_else';
    rowElse.style.height = '48px';
    branchesEl.appendChild(rowElse);

    const tops = measureConditionHandleTops(root, [
      { id: 'b_if', kind: 'if' },
      { id: 'b_else', kind: 'else' },
      { id: 'b_term', kind: 'if', terminal: true },
    ]);

    expect(tops.b_if).toBeDefined();
    expect(tops.b_else).toBeDefined();
    expect(tops.b_else!).toBeGreaterThanOrEqual(tops.b_if!);
    expect(tops.b_term).toBeUndefined();

    document.body.removeChild(root);
  });
});
