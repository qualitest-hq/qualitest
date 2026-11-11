import request from '@/utils/request'

/**
 * 把浏览器选中的文件上传到后端通用接口 /common/upload。
 * 由服务端写入当前配置的存储（本机盘或对象存储），前端不直连对象存储。
 *
 * 成功时常用字段：
 * - fileName：建议入库路径（本地多为 /profile/upload/...，对象存储可为完整 https）
 * - url：可访问地址
 * - storage：local / rustfs / qiniu
 * - objectKey：对象键
 *
 * @param {File|Blob} file 待上传文件
 * @returns {Promise<{ url?: string, fileName?: string, newFileName?: string, originalFilename?: string, storage?: string, objectKey?: string }>}
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
      // 关闭防重复提交：同一文件允许多次上传
      repeatSubmit: false
    },
    timeout: 60000
  })
}
