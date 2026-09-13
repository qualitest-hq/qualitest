/**
 * 会话消息列表分页与滚动相关常量。
 */

/** 每次向上加载更早消息的条数 */
export const AI_CHAT_MESSAGE_PAGE_SIZE = 40;

/** 已加载消息数达到该值后，消息区改用虚拟滚动 */
export const AI_CHAT_VIRTUAL_SCROLL_THRESHOLD = 40;

/** 距列表顶部小于该值（px）时触发加载更早消息 */
export const AI_CHAT_LOAD_OLDER_TOP_PX = 80;

/** 距列表底部小于该值（px）时视为贴底，恢复自动跟随最新输出 */
export const AI_CHAT_STICK_BOTTOM_PX = 48;
