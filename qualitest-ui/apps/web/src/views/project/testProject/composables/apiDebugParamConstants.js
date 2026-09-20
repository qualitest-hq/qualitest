export const BODY_MODES = [
  {label: 'none', value: 'none'},
  {label: 'form-data', value: 'form-data'},
  {label: 'x-www-form-urlencoded', value: 'x-www-form-urlencoded'},
  {label: 'JSON', value: 'json'},
  {label: 'XML', value: 'xml'},
  {label: 'text', value: 'text'},
  {label: 'binary', value: 'binary'}
]

/** Query / Path：仅 URL 友好标量 */
export const PARAM_TYPES_QUERY_PATH = ['string', 'integer', 'number', 'boolean']

/** x-www-form-urlencoded：无 file、无 object/array/any/null */
export const PARAM_TYPES_URLENCODED = ['string', 'integer', 'number', 'boolean']

/** form-data：含 file 及结构化类型 */
export const PARAM_TYPES_FORM_DATA = [
  'string',
  'integer',
  'number',
  'boolean',
  'array',
  'file',
  'object',
  'any',
  'null'
]

export const DISALLOWED_URLENC_TYPES = ['file', 'object', 'array', 'any', 'null']

export const DISALLOWED_QUERY_PATH_TYPES = ['object', 'array', 'any', 'null']

export const PARAM_SCHEMA_BEHAVIOR = [
  {label: 'Read/Write', value: 'readWrite'},
  {label: 'Read only', value: 'readOnly'},
  {label: 'Write only', value: 'writeOnly'}
]

export function paramTypeSelectClass(type) {
  const raw = type == null || type === '' ? 'string' : String(type)
  const safe = raw.toLowerCase().replace(/[^a-z0-9]/g, '') || 'custom'
  return ['param-type-select', `is-type-${safe}`]
}
