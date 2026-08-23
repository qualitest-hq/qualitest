/**
 * CodeMirror 6 编辑器封装：动态加载核心与语言包，供 ScriptSourceEditor 等使用。
 */
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { EditorState } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLine } from '@codemirror/view'
import { defaultKeymap, indentWithTab } from '@codemirror/commands'
import { tags } from '@lezer/highlight'

let jsLangPromise = null
let pyLangPromise = null
let jsonLangPromise = null

/** 与画布右栏一致的浅色编辑器主题 */
const flowEditorTheme = EditorView.theme(
  {
    '&': {
      backgroundColor: '#f8fafc',
      color: '#334155',
      fontSize: '12px',
    },
    '.cm-content': {
      fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
      caretColor: '#0891b2',
      padding: '8px 0',
    },
    '.cm-gutters': {
      backgroundColor: '#f1f5f9',
      color: '#94a3b8',
      border: 'none',
      minWidth: '32px',
    },
    '.cm-activeLine': { backgroundColor: 'rgba(226, 232, 240, 0.55)' },
    '.cm-activeLineGutter': { backgroundColor: '#e2e8f0' },
    '&.cm-focused .cm-cursor': { borderLeftColor: '#0891b2' },
    '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, &::selection': {
      backgroundColor: 'rgba(186, 230, 253, 0.55) !important',
    },
    '.cm-line': { padding: '0 4px 0 2px' },
  },
  { dark: false },
)

const flowHighlight = HighlightStyle.define([
  { tag: tags.keyword, color: '#7c3aed', fontWeight: '600' },
  { tag: [tags.atom, tags.bool, tags.number], color: '#c2410c' },
  { tag: tags.string, color: '#059669' },
  { tag: tags.comment, color: '#94a3b8', fontStyle: 'italic' },
  { tag: tags.function(tags.variableName), color: '#0369a1' },
  { tag: tags.propertyName, color: '#0f766e' },
  { tag: tags.operator, color: '#64748b' },
  { tag: tags.punctuation, color: '#64748b' },
])

/** 按语言 id 返回 CodeMirror 语言扩展 */
export async function languageExtension(language) {
  if (language === 'python') {
    if (!pyLangPromise) {
      pyLangPromise = import('@codemirror/lang-python').then((m) => m.python())
    }
    return pyLangPromise
  }
  if (language === 'json') {
    if (!jsonLangPromise) {
      jsonLangPromise = import('@codemirror/lang-json').then((m) => m.json())
    }
    return jsonLangPromise
  }
  if (!jsLangPromise) {
    jsLangPromise = import('@codemirror/lang-javascript').then((m) => m.javascript())
  }
  return jsLangPromise
}

/**
 * 创建 CodeMirror 编辑器实例。
 *
 * @param {object} options
 * @param {HTMLElement} options.parent 挂载容器
 * @param {string} options.doc 初始文档
 * @param {boolean} options.readOnly 是否只读
 * @param {import('@codemirror/state').Extension} options.languageExtension 语言高亮扩展
 * @param {(value: string) => void} options.onChange 内容变更回调
 */
export function createEditor({ parent, doc, readOnly, languageExtension: langExt, onChange }) {
  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      onChange(update.state.doc.toString())
    }
  })

  const state = EditorState.create({
    doc: doc ?? '',
    extensions: [
      lineNumbers(),
      highlightActiveLine(),
      langExt,
      flowEditorTheme,
      syntaxHighlighting(flowHighlight),
      keymap.of([...defaultKeymap, indentWithTab]),
      EditorView.lineWrapping,
      EditorState.readOnly.of(!!readOnly),
      updateListener,
      EditorView.theme({
        '&': { height: '100%' },
        '.cm-scroller': { overflow: 'auto', minHeight: 'inherit' },
      }),
    ],
  })

  return new EditorView({ state, parent })
}

/** 外部 modelValue 变更时同步文档，避免光标跳动 */
export function syncDoc(view, nextValue) {
  const current = view.state.doc.toString()
  if (current === (nextValue ?? '')) return
  view.dispatch({
    changes: { from: 0, to: current.length, insert: nextValue ?? '' },
  })
}

/**
 * 在光标处插入文本；无选区时在文档末尾追加（必要时补换行）。
 *
 * @param {EditorView} view
 * @param {string} text
 */
export function insertTextAtCursor(view, text) {
  if (!text) return
  const doc = view.state.doc
  const main = view.state.selection.main
  const hasSelection = main.from !== main.to
  let from = main.from
  let to = main.to
  let insert = text

  if (!hasSelection) {
    if (main.from === doc.length) {
      if (doc.length > 0 && doc.sliceString(doc.length - 1) !== '\n') {
        insert = `\n${text}`
      }
      from = doc.length
      to = doc.length
    } else if (doc.length > 0) {
      insert = `\n${text}`
    }
  }

  view.dispatch({
    changes: { from, to, insert },
    selection: { anchor: from + insert.length },
  })
  view.focus()
}
