/**
 * v-copyText：点击时把绑定文本写入剪贴板。
 * 用法：v-copyText="text" ，可选 v-copyText:callback="fn"
 */
import { copyTextSync } from '@/utils/clipboard'

export default {
  beforeMount(el, { value, arg }) {
    if (arg === 'callback') {
      el.$copyCallback = value
    } else {
      el.$copyValue = value
      const handler = () => {
        copyTextSync(el.$copyValue)
        if (el.$copyCallback) {
          el.$copyCallback(el.$copyValue)
        }
      }
      el.addEventListener('click', handler)
      el.$destroyCopy = () => el.removeEventListener('click', handler)
    }
  },
}
