import request from '@/utils/request'

/**
 * 把浏览器选中的文件上传到服务端本地盘。
 * 成功时返回路径字段（fileName 一般为 /profile/upload/...），供素材库 storagePath 落库。
 *
 * @param {File|Blob} file 待上传文件
 * @returns {Promise<{ url?: string, fileName?: string, newFileName?: string, originalFilename?: string }>}
 */
export function uploadCommonFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/common/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
      // 关闭防重复提交：同一文件可多次上传
      repeatSubmit: false
    },
    timeout: 60000
  })
}
