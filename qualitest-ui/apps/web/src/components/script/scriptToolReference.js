/**
 * 脚本宿主共用工具函数说明：Script 节点 ctx 与接口脚本 api 同名可用，
 * 涵盖编码与报文、摘要与 HMAC、RSA、AES、国密，供脚本编辑器帮助面板展示。
 */

/**
 * 按宿主前缀生成工具函数分组，签名示例中的对象名随 host 变化。
 *
 * @param {'ctx' | 'api'} host 宿主对象名
 * @returns {{ id: string, title: string, items: { name: string, sig: string, desc: string, access: 'util' }[] }[]}
 */
export function buildScriptToolGroups(host) {
  const p = host
  return [
    {
      id: 'codec',
      title: '编码与报文',
      items: [
        { name: 'jsonParse / jsonStringify', sig: `${p}.jsonParse(text) / ${p}.jsonStringify(obj)`, desc: 'JSON 解析与序列化' },
        { name: 'base64Encode / base64Decode', sig: `${p}.base64Encode('text')`, desc: 'UTF-8 文本 Base64 编解码；无浏览器 btoa/atob' },
        { name: 'urlEncode / urlDecode', sig: `${p}.urlEncode('a b')`, desc: 'UTF-8 URL 编解码（表单风格，空格为 +）' },
        { name: 'xmlParse', sig: `${p}.xmlParse(xmlText)`, desc: 'XML 转对象：去根节点，同名节点转数组，CDATA 取文本，忽略属性；禁止 DOCTYPE' },
        { name: 'xmlStringify', sig: `${p}.xmlStringify(obj, { root: 'xml', cdata: true })`, desc: '对象转 XML；字符串默认包 CDATA，数组展开为重复节点' },
        { name: 'signContent', sig: `${p}.signContent(params, { exclude: ['sign'], skipEmpty: true })`, desc: '按 key ASCII 升序拼 k=v&k2=v2（不 URL 编码），支付宝 / 微信 v2 签名原文' },
      ],
    },
    {
      id: 'digest',
      title: '摘要与 HMAC',
      items: [
        { name: 'md5 / sha1 / sha256 / sha512 / sm3', sig: `${p}.sha256(data, 'hex' | 'base64')`, desc: '消息摘要；默认小写 hex，需要大写自行 toUpperCase()' },
        { name: 'hmacSha1 / hmacSha256 / hmacSha512', sig: `${p}.hmacSha256(data, secret, 'hex' | 'base64')`, desc: 'HMAC，密钥按 UTF-8；默认小写 hex' },
      ],
    },
    {
      id: 'rsa',
      title: 'RSA',
      items: [
        { name: 'rsaSign', sig: `${p}.rsaSign(data, privateKey, 'SHA256withRSA')`, desc: '私钥签名，返回 Base64；私钥支持 PKCS#8 / PKCS#1 PEM 或裸 Base64' },
        { name: 'rsaVerify', sig: `${p}.rsaVerify(data, signBase64, publicKey, 'SHA256withRSA')`, desc: '公钥验签，返回 true/false；公钥支持公钥 PEM、证书 PEM 或裸 Base64' },
        { name: 'rsaEncrypt / rsaDecrypt', sig: `${p}.rsaEncrypt(plain, publicKey, 'OAEP' | 'OAEP256' | 'PKCS1')`, desc: '公钥加密 / 私钥解密，密文 Base64；默认 OAEP（SHA-1）' },
      ],
    },
    {
      id: 'aes',
      title: 'AES',
      items: [
        { name: 'aesGcmEncrypt / aesGcmDecrypt', sig: `${p}.aesGcmEncrypt(plain, key, nonce, aad?)`, desc: 'AES-GCM；key / nonce / aad 按 UTF-8，密文为 Base64(密文+tag)，可用于微信支付 v3 resource' },
        { name: 'aesEncrypt / aesDecrypt', sig: `${p}.aesEncrypt(data, key, { mode: 'CBC', iv, keyEncoding: 'utf8' })`, desc: 'AES CBC / ECB；keyEncoding / ivEncoding 可选 utf8 / base64 / hex；CBC 未传 iv 用全 0；encoding 控制密文 base64 / hex' },
      ],
    },
    {
      id: 'sm',
      title: '国密',
      items: [
        { name: 'sm2Sign / sm2Verify', sig: `${p}.sm2Sign(data, privateKey, { userId, format: 'der' | 'rs', encoding })`, desc: 'SM2 签名 / 验签；userId 默认 1234567812345678；密钥支持 hex、PEM、裸 Base64' },
        { name: 'sm2Encrypt / sm2Decrypt', sig: `${p}.sm2Encrypt(plain, publicKey, { mode: 'C1C3C2' | 'C1C2C3', encoding })`, desc: 'SM2 加解密；默认 C1C3C2、Base64；解密兼容缺 04 前缀的密文' },
        { name: 'sm4Encrypt / sm4Decrypt', sig: `${p}.sm4Encrypt(data, key, { mode: 'CBC' | 'ECB', iv, keyEncoding: 'hex' })`, desc: 'SM4 CBC / ECB 加解密；可选 iv、ivEncoding、padding、encoding；keyEncoding 默认 hex；CBC 未传 iv 用全 0' },
      ],
    },
  ].map((group) => ({
    ...group,
    // 工具函数统一标记为「工具」类，面板据此显示标签
    items: group.items.map((item) => ({ ...item, access: 'util' })),
  }))
}
