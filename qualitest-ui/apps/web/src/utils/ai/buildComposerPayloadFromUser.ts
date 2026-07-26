import {
  buildComposerSendPayload,
  type ComposerSendPayload,
} from '@/views/project/testFlow/composables/mentionComposer';
import {
  COMPOSER_DOC_VERSION,
  type ComposerDoc,
} from '@/views/project/testFlow/types/mentionTypes';

/** 从 user 消息视图重建发送 payload（重新生成 / 编辑重发） */
export function buildComposerPayloadFromUserMessage(
  content: string,
  composerDoc?: ComposerDoc,
): ComposerSendPayload {
  const doc: ComposerDoc =
    composerDoc?.version === COMPOSER_DOC_VERSION
      ? composerDoc
      : {
          version: COMPOSER_DOC_VERSION,
          nodes: [{ type: 'text', text: content }],
        };
  return buildComposerSendPayload(doc);
}
