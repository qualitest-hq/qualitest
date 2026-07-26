/** 是否为服务端持久化的消息 id（雪花 id 数字字符串） */
export function isServerMessageId(id: string | null | undefined): boolean {
  return Boolean(id && /^\d+$/.test(id));
}
