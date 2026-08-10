/**
 * pending>0 保存门禁：弹框让用户选择去确认 / 仅保存已确认 / 取消。
 */
import { h } from 'vue';
import { ElButton, ElMessageBox } from 'element-plus';

export type StagingPendingSaveChoice = 'focus' | 'saveConfirmedOnly' | 'cancel';

export interface PromptStagingPendingSaveOptions {
  /** pending 单元展示名（通常 unit.label） */
  pendingLabels: string[];
  /** 为 true 时隐藏「仅保存已确认」，强制先确认 */
  blockWhenStagingPending?: boolean;
}

export interface StagingPendingSaveAction {
  choice: StagingPendingSaveChoice;
  label: string;
  /** 默认主操作（仅保存已确认） */
  primary?: boolean;
}

const PREVIEW_LIMIT = 5;

/** 弹框标题 */
export function stagingPendingSaveTitle(pendingCount: number): string {
  return `还有 ${pendingCount} 项 AI 变更未确认`;
}

/** 保存按钮 tooltip / 与弹框同语义 */
export function stagingPendingSaveTooltip(pendingCount: number): string {
  return `尚有 ${pendingCount} 项 AI 变更未确认，保存前请确认或选择仅保存已确认内容`;
}

/** 列表预览：前 5 项 +「还有 M 项」 */
export function buildStagingPendingSavePreviewLines(pendingLabels: string[]): string[] {
  const preview = pendingLabels.slice(0, PREVIEW_LIMIT).map((label, i) => {
    const text = String(label ?? '').trim() || `未命名变更 ${i + 1}`;
    return text;
  });
  const remaining = pendingLabels.length - preview.length;
  if (remaining > 0) {
    preview.push(`还有 ${remaining} 项`);
  }
  return preview;
}

/** 门禁按钮：默认三选一；强挡仅「去确认 / 取消」 */
export function buildStagingPendingSaveActions(
  blockWhenStagingPending = false,
): StagingPendingSaveAction[] {
  const actions: StagingPendingSaveAction[] = [{ choice: 'focus', label: '去确认' }];
  if (!blockWhenStagingPending) {
    actions.push({ choice: 'saveConfirmedOnly', label: '仅保存已确认', primary: true });
  }
  actions.push({ choice: 'cancel', label: '取消' });
  return actions;
}

/**
 * 弹出保存门禁。关闭 / Esc → cancel。
 * @returns 用户选择
 */
export async function promptStagingPendingSave(
  options: PromptStagingPendingSaveOptions,
): Promise<StagingPendingSaveChoice> {
  const labels = options.pendingLabels ?? [];
  const n = labels.length;
  if (n <= 0) return 'saveConfirmedOnly';

  const block = Boolean(options.blockWhenStagingPending);
  const lines = buildStagingPendingSavePreviewLines(labels);
  const actions = buildStagingPendingSaveActions(block);
  let choice: StagingPendingSaveChoice = 'cancel';

  const pick = (next: StagingPendingSaveChoice) => {
    choice = next;
    ElMessageBox.close();
  };

  try {
    await ElMessageBox({
      title: stagingPendingSaveTitle(n),
      message: h('div', { class: 'staging-pending-save-prompt' }, [
        h(
          'ul',
          {
            class: 'staging-pending-save-prompt__list',
            style: {
              margin: '0 0 16px',
              paddingLeft: '1.25em',
              lineHeight: '1.5',
            },
          },
          lines.map((line) => h('li', { key: line }, line)),
        ),
        h(
          'div',
          {
            class: 'staging-pending-save-prompt__actions',
            style: {
              display: 'flex',
              flexWrap: 'wrap',
              gap: '8px',
              justifyContent: 'flex-end',
            },
          },
          actions.map((action) =>
            h(
              ElButton,
              {
                key: action.choice,
                type: action.primary ? 'primary' : undefined,
                onClick: () => pick(action.choice),
              },
              () => action.label,
            ),
          ),
        ),
      ]),
      showConfirmButton: false,
      showCancelButton: false,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      distinguishCancelAndClose: true,
    });
  } catch {
    /* 关闭 / Esc / 自定义按钮触发 close */
  }
  return choice;
}
