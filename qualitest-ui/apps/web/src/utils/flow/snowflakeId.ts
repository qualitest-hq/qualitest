/**
 * 前端雪花 ID 生成器。
 * 用于画布节点、边、运行场景、正式 Run 记录等需要全局唯一数字 id 的场景。
 * 算法与后端 IdUtil.getSnowflakeNextId 同构（41bit 时间 + 5bit 机房 + 5bit 机器 + 12bit 序列）。
 */

const SNOWFLAKE_EPOCH = 1288834974657n;
const SNOWFLAKE_WORKER_ID = 1n;
const SNOWFLAKE_DATACENTER_ID = 1n;

let snowflakeSequence = 0n;
let snowflakeLastTs = -1n;

/** 生成下一个雪花 id，返回十进制数字字符串 */
export function nextSnowflakeId(): string {
  let ts = BigInt(Date.now());
  if (ts === snowflakeLastTs) {
    snowflakeSequence = (snowflakeSequence + 1n) & 4095n;
    if (snowflakeSequence === 0n) {
      while (ts <= snowflakeLastTs) ts = BigInt(Date.now());
    }
  } else {
    snowflakeSequence = 0n;
  }
  snowflakeLastTs = ts;
  return (
    ((ts - SNOWFLAKE_EPOCH) << 22n)
    | (SNOWFLAKE_DATACENTER_ID << 17n)
    | (SNOWFLAKE_WORKER_ID << 12n)
    | snowflakeSequence
  ).toString();
}
