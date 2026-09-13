/**
 * pending>0 时的保存门禁。
 *
 * 用户点「保存」且仍有未确认 Staging 时弹出三选一：
 * - 去确认：跳到待确认单元，本次不保存
 * - 仅保存已确认：落盘已确认内容，未确认项不写入 graph_json
 * - 取消：中止保存
 *
 * 关闭弹窗或按 Esc 视为取消。
 */
import { h } from 'vue';
import { ElButton, ElMessageBox } from 'element-plus';

/** 用户在保存门禁弹窗中的选择 */
export type StagingPendingSaveChoice = 'focus' | 'saveConfirmedOnly' | 'cancel';

export interface PromptStagingPendingSaveOptions {
  /** 未确认单元的展示名（一般取 unit.label），用于弹窗列表预览 */
  pendingLabels: string[];
}

/** 弹窗底部一个操作按钮 */
export interface StagingPendingSaveAction {
  choice: StagingPendingSaveChoice;
  label: string;
  /** 是否主按钮样式（「仅保存已确认」） */
  primary?: boolean;
}

/** 弹窗列表最多展示多少条 pending 名称，超出用「还有 M 项」 */
const PREVIEW_LIMIT = 5;

/** 弹窗标题：含未确认项数量 */
export function stagingPendingSaveTitle(pendingCount: number): string {
  return `还有 ${pendingCount} 项 AI 变更未确认`;
}

/** 保存按钮 tooltip：提示尚有未确认项 */
export function stagingPendingSaveTooltip(pendingCount: number): string {
  return `尚有 ${pendingCount} 项 AI 变更未确认，保存前请确认或选择仅保存已确认内容`;
}

/**
 * 生成弹窗内 pending 名称预览行。
 * 最多 PREVIEW_LIMIT 条；再多追加「还有 M 项」；空名称回退为「未命名变更 N」。
 */
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

/**
 * 保存门禁三个操作按钮。
 * 顺序：去确认 → 仅保存已确认（主按钮）→ 取消。
 */
export function buildStagingPendingSaveActions(): StagingPendingSaveAction[] {
  return [
    { choice: 'focus', label: '去确认' },
    { choice: 'saveConfirmedOnly', label: '仅保存已确认', primary: true },
    { choice: 'cancel', label: '取消' },
  ];
}

/**
 * 弹出保存门禁并返回用户选择。
 * pendingLabels 为空时不弹窗，直接当作「仅保存已确认」。
 * 关闭弹窗 / Esc → cancel。
 */
export async function promptStagingPendingSave(
  options: PromptStagingPendingSaveOptions,
): Promise<StagingPendingSaveChoice> {
  const labels = options.pendingLabels ?? [];
  const n = labels.length;
  if (n <= 0) return 'saveConfirmedOnly';

  const lines = buildStagingPendingSavePreviewLines(labels);
  const actions = buildStagingPendingSaveActions();
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
