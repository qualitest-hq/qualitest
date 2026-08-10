/**
 * 测 promptStagingPendingSave：门禁文案与三选一/强挡行为。
 * 边界：MessageBox mock；无真实 DOM。
 * 单跑：yarn test promptStagingPendingSave
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { messageBoxMock } = vi.hoisted(() => ({
  messageBoxMock: vi.fn(),
}));

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus');
  return {
    ...actual,
    ElMessageBox: Object.assign(messageBoxMock, { close: vi.fn() }),
  };
});

import {
  buildStagingPendingSaveActions,
  buildStagingPendingSavePreviewLines,
  promptStagingPendingSave,
  stagingPendingSaveTitle,
  stagingPendingSaveTooltip,
} from '@/views/project/testFlow/utils/promptStagingPendingSave';

describe('promptStagingPendingSave helpers', () => {
  it('标题与 tooltip 含 pending 数', () => {
    // 前提：pending=3
    // 期望：标题/tooltip 文案含「3」
    expect(stagingPendingSaveTitle(3)).toBe('还有 3 项 AI 变更未确认');
    expect(stagingPendingSaveTooltip(3)).toContain('3');
  });

  it('预览最多 5 项并追加还有 M 项', () => {
    // 前提：7 个 label
    // 期望：前 5 + 「还有 2 项」
    const lines = buildStagingPendingSavePreviewLines([
      'a',
      'b',
      'c',
      'd',
      'e',
      'f',
      'g',
    ]);
    expect(lines).toEqual(['a', 'b', 'c', 'd', 'e', '还有 2 项']);
  });

  it('空 label 回退为未命名变更', () => {
    // 前提：首项为空串
    // 期望：显示「未命名变更 1」
    expect(buildStagingPendingSavePreviewLines([''])).toEqual(['未命名变更 1']);
  });

  it('默认三选一、强挡两选', () => {
    // 前提：对照默认与 block 模式
    // 期望：默认含仅保存；强挡不含
    expect(buildStagingPendingSaveActions(false).map((a) => a.choice)).toEqual([
      'focus',
      'saveConfirmedOnly',
      'cancel',
    ]);
    expect(buildStagingPendingSaveActions(true).map((a) => a.choice)).toEqual(['focus', 'cancel']);
  });
});

describe('promptStagingPendingSave', () => {
  beforeEach(() => {
    messageBoxMock.mockReset();
  });

  it('无 pending 直接视为仅保存已确认', async () => {
    // 前提：labels 为空
    // 期望：不弹框，返回 saveConfirmedOnly
    const choice = await promptStagingPendingSave({ pendingLabels: [] });
    expect(choice).toBe('saveConfirmedOnly');
    expect(messageBoxMock).not.toHaveBeenCalled();
  });

  it('关闭 MessageBox 返回 cancel', async () => {
    // 前提：MessageBox reject（关闭/Esc）
    // 期望：choice=cancel
    messageBoxMock.mockRejectedValueOnce(new Error('cancel'));
    const choice = await promptStagingPendingSave({
      pendingLabels: ['节点 A', '边 B'],
    });
    expect(choice).toBe('cancel');
    expect(messageBoxMock).toHaveBeenCalledOnce();
    const opts = messageBoxMock.mock.calls[0][0];
    expect(opts.title).toBe('还有 2 项 AI 变更未确认');
    expect(opts.showConfirmButton).toBe(false);
  });

  it('弹框 message 含预览列表与操作区', async () => {
    // 前提：有 pending；MessageBox resolve（异常路径外）
    // 期望：传入的 message 为含 list/actions class 的 VNode
    messageBoxMock.mockResolvedValueOnce(undefined);
    const choice = await promptStagingPendingSave({ pendingLabels: ['节点 A'] });
    expect(choice).toBe('cancel');
    const opts = messageBoxMock.mock.calls[0][0];
    const root = opts.message as { props?: { class?: string }; children?: unknown[] };
    expect(root.props?.class).toBe('staging-pending-save-prompt');
    const classes = (root.children ?? [])
      .filter((c): c is { props?: { class?: string } } => !!c && typeof c === 'object')
      .map((c) => c.props?.class);
    expect(classes).toContain('staging-pending-save-prompt__list');
    expect(classes).toContain('staging-pending-save-prompt__actions');
  });
});
