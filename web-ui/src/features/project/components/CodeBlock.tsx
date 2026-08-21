import { useMemo } from 'react'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import cpp from 'highlight.js/lib/languages/cpp'
import css from 'highlight.js/lib/languages/css'
import dart from 'highlight.js/lib/languages/dart'
import go from 'highlight.js/lib/languages/go'
import groovy from 'highlight.js/lib/languages/groovy'
import ini from 'highlight.js/lib/languages/ini'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import ruby from 'highlight.js/lib/languages/ruby'
import rust from 'highlight.js/lib/languages/rust'
import sql from 'highlight.js/lib/languages/sql'
import swift from 'highlight.js/lib/languages/swift'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import '@/features/project/codeBlock.css'

// Cherry-picked language set (not `highlight.js/lib/common`) so the bundle
// only carries grammars this app's own backend ever reports — see
// LANGUAGE_BY_EXTENSION in chat-gateway's ProjectFilesService.kt, plus the
// stacks this repo targets end to end (Kotlin/KMP, Swift, Flutter/Dart,
// React Native/TS, web).
let registered = false
function ensureLanguagesRegistered() {
  if (registered) return
  hljs.registerLanguage('bash', bash)
  hljs.registerLanguage('cpp', cpp)
  hljs.registerLanguage('css', css)
  hljs.registerLanguage('dart', dart)
  hljs.registerLanguage('go', go)
  hljs.registerLanguage('groovy', groovy)
  hljs.registerLanguage('ini', ini)
  hljs.registerLanguage('java', java)
  hljs.registerLanguage('javascript', javascript)
  hljs.registerLanguage('json', json)
  hljs.registerLanguage('kotlin', kotlin)
  hljs.registerLanguage('markdown', markdown)
  hljs.registerLanguage('python', python)
  hljs.registerLanguage('ruby', ruby)
  hljs.registerLanguage('rust', rust)
  hljs.registerLanguage('sql', sql)
  hljs.registerLanguage('swift', swift)
  hljs.registerLanguage('typescript', typescript)
  hljs.registerLanguage('xml', xml)
  hljs.registerLanguage('yaml', yaml)
  registered = true
}

// Maps ProjectFileResult.language (the ids chat-gateway's
// LANGUAGE_BY_EXTENSION emits, e.g. "tsx", "toml") onto the highlight.js
// grammar name that actually renders it — hljs itself has no tsx/jsx/html/c
// grammar distinct from typescript/javascript/xml/cpp.
const HLJS_LANGUAGE_BY_ID: Record<string, string> = {
  kotlin: 'kotlin',
  java: 'java',
  dart: 'dart',
  typescript: 'typescript',
  tsx: 'typescript',
  javascript: 'javascript',
  jsx: 'javascript',
  json: 'json',
  markdown: 'markdown',
  xml: 'xml',
  groovy: 'groovy',
  yaml: 'yaml',
  html: 'xml',
  css: 'css',
  python: 'python',
  ruby: 'ruby',
  go: 'go',
  rust: 'rust',
  swift: 'swift',
  c: 'cpp',
  cpp: 'cpp',
  bash: 'bash',
  sql: 'sql',
  toml: 'ini',
  properties: 'ini',
}

/** Syntax-highlighted code view for the Project panel's text preview — falls back to plain text for a language hljs has no grammar for. */
export function CodeBlock({ code, language }: { code: string; language: string | null }) {
  ensureLanguagesRegistered()

  const highlighted = useMemo(() => {
    const hljsLang = language ? HLJS_LANGUAGE_BY_ID[language] : undefined
    if (!hljsLang) return null
    try {
      return hljs.highlight(code, { language: hljsLang }).value
    } catch {
      return null
    }
  }, [code, language])

  if (highlighted == null) {
    return (
      <pre className="project-file-detail-text">
        <code>{code}</code>
      </pre>
    )
  }

  return (
    <pre className="project-file-detail-text hljs">
      {/* eslint-disable-next-line react/no-danger -- markup hljs generated from `code`, not raw user HTML; hljs escapes source text while tokenizing. */}
      <code dangerouslySetInnerHTML={{ __html: highlighted }} />
    </pre>
  )
}
