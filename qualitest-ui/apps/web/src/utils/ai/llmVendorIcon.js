import { getLobeIconCDN } from '@lobehub/icons/es/features/getLobeIconCDN/index.js'

/**
 * 内置厂商 → Lobe Icons 配置。
 * id 为 toc 中的 PascalCase 标识；type 为 color 时 CDN 路径带 -color 后缀。
 */
const TEMPLATE_ICON_CONFIG = {
  deepseek: { id: 'DeepSeek', type: 'color' },
  openai: { id: 'OpenAI', type: 'mono' },
  anthropic: { id: 'Anthropic', type: 'mono' },
  ollama: { id: 'Ollama', type: 'mono' },
  siliconflow: { id: 'SiliconCloud', type: 'color' },
  moonshot: { id: 'Kimi', format: 'avatar' },
  zhipu: { id: 'Zhipu', type: 'color' },
  qwen: { id: 'Qwen', type: 'color' },
  minimax: { id: 'Minimax', type: 'color' },
  volcengine: { id: 'Volcengine', type: 'color' },
  stepfun: { id: 'Stepfun', type: 'color' },
  gemini: { id: 'Gemini', type: 'color' },
  groq: { id: 'Groq', type: 'mono' },
  mistral: { id: 'Mistral', type: 'color' },
  openrouter: { id: 'OpenRouter', type: 'mono' },
  together: { id: 'Together', type: 'color' },
  fireworks: { id: 'Fireworks', type: 'color' },
  azure: { id: 'AzureAI', type: 'color' },
  perplexity: { id: 'Perplexity', type: 'color' },
  xai: { id: 'XAI', type: 'mono' }
}

const FALLBACK_COLORS = [
  '#409eff',
  '#67c23a',
  '#e6a23c',
  '#f56c6c',
  '#909399',
  '#626aef',
  '#13c2c2'
]

/**
 * 解析 Lobe Icons 配置：优先 templateId，与 provider-templates.json 的 icon 字段对齐。
 */
export function resolveLobeIconConfig(templateId, icon) {
  const key = (icon || templateId || '').trim().toLowerCase()
  if (!key || key.startsWith('custom')) {
    return null
  }
  return TEMPLATE_ICON_CONFIG[key] || null
}

/**
 * 生成 Lobe Icons CDN 地址（SVG，国内镜像）。
 */
export function buildLobeIconUrl(config) {
  if (!config?.id) {
    return null
  }
  if (config.format === 'avatar') {
    return getLobeIconCDN(config.id, {
      format: 'avatar',
      cdn: 'aliyun'
    })
  }
  return getLobeIconCDN(config.id, {
    format: 'svg',
    type: config.type || 'mono',
    cdn: 'aliyun'
  })
}

export function resolveFallbackLetter(vendorName) {
  const text = (vendorName || '').trim()
  if (!text) {
    return '?'
  }
  return text.charAt(0).toUpperCase()
}

export function resolveFallbackColor(seed) {
  const text = (seed || '').trim()
  if (!text) {
    return FALLBACK_COLORS[0]
  }
  let hash = 0
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0
  }
  return FALLBACK_COLORS[hash % FALLBACK_COLORS.length]
}
