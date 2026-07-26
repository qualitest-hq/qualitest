/**
 * 为 Promise 增加超时；超时后 reject，原 Promise 仍在后台完成。
 */
export function promiseWithTimeout<T>(
  promise: Promise<T>,
  ms: number,
  message = '请求超时',
): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error(message)), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}
