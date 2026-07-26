import {nextTick, watch} from 'vue'
import {useDebounceFn} from '@vueuse/core'

/**
 * 合并对多组 KV 行的「末尾空行」维护：原先 6 路 deep watch 会在一次输入链上触发多次 nextTick；
 * 改为单路 deep 订阅 + 短防抖，在一帧内至多执行一轮补行。
 */
export function useApiDebugTrailingEmptyRows({
  draftHeaderRows,
  draftCookieRows,
  draftRequestConfig,
  ensureTrailingEmptyRow
}) {
  function flushTrailingEmptyRows() {
    nextTick(() => {
      ensureTrailingEmptyRow(draftHeaderRows.value)
      ensureTrailingEmptyRow(draftCookieRows.value)
      const rc = draftRequestConfig.value
      if (!rc) return
      ensureTrailingEmptyRow(rc.queryParams)
      ensureTrailingEmptyRow(rc.pathParams)
      if (rc.body) {
        ensureTrailingEmptyRow(rc.body.formData)
        ensureTrailingEmptyRow(rc.body.urlencoded)
      }
    })
  }

  const scheduleTrailingEmptyRows = useDebounceFn(flushTrailingEmptyRows, 32)

  watch(
      () => ({
        headers: draftHeaderRows.value,
        cookies: draftCookieRows.value,
        queryParams: draftRequestConfig.value?.queryParams,
        pathParams: draftRequestConfig.value?.pathParams,
        formData: draftRequestConfig.value?.body?.formData,
        urlencoded: draftRequestConfig.value?.body?.urlencoded
      }),
      () => scheduleTrailingEmptyRows(),
      {deep: true}
  )

  return {flushTrailingEmptyRowsNow: flushTrailingEmptyRows}
}
