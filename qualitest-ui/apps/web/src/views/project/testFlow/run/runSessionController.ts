/**
 * 跑流会话互斥中枢：场景正式跑、运行库回放、路径模拟三者共用。
 *
 * 各会话模块在定义停止/步进函数后向本模块注册；
 * 启动某一会话前停掉其它种类；键盘 Esc 与左右方向键只依赖本模块已注册的钩子，
 * 从而回放模块与路径模拟模块不必互相 import。
 */

export type RunSessionKind = 'replay' | 'simulate' | 'scenario';

export type RunSessionHooks = {
  /** 立即停止该会话；画布高亮清理由各实现自行完成 */
  stop: () => void;
  /** 当前是否占用该种类会话（Esc 按优先级挑选时用） */
  isActive?: () => boolean;
  /** 单步后退（回放 / 路径模拟） */
  stepPrev?: () => void;
  /** 单步前进 */
  stepNext?: () => void;
};

const hooks: Partial<Record<RunSessionKind, RunSessionHooks>> = {};

/** 各会话模块在模块加载时注册一次钩子 */
export function registerRunSession(kind: RunSessionKind, h: RunSessionHooks) {
  hooks[kind] = h;
}

/** 停止指定种类会话 */
export function stopRunSession(kind: RunSessionKind) {
  hooks[kind]?.stop();
}

/** 启动某一会话前，停掉其它种类，保证同一时刻只有一类会话在跑 */
export function stopOtherRunSessions(keep: RunSessionKind) {
  (Object.keys(hooks) as RunSessionKind[]).forEach((k) => {
    if (k !== keep) hooks[k]?.stop();
  });
}

/**
 * 按优先级停止当前占用中的会话：正式场景跑 → 路径模拟 → 运行库回放。
 * @returns 是否实际停止了某一会话
 */
export function abortActiveRunSession(): boolean {
  for (const kind of ['scenario', 'simulate', 'replay'] as const) {
    const h = hooks[kind];
    if (h?.isActive?.()) {
      h.stop();
      return true;
    }
  }
  return false;
}

/**
 * 对当前占用中的路径模拟或回放会话做单步前进 / 后退。
 * @returns 是否命中活动会话并完成步进
 */
export function stepActiveRunSession(dir: 'prev' | 'next'): boolean {
  for (const kind of ['simulate', 'replay'] as const) {
    const h = hooks[kind];
    if (h?.isActive?.()) {
      if (dir === 'prev') h.stepPrev?.();
      else h.stepNext?.();
      return true;
    }
  }
  return false;
}
