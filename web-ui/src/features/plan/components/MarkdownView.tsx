import { useMemo } from 'react'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function MarkdownView({ content }: { content: string }) {
  const html = useMemo(() => {
    const raw = marked.parse(content, { async: false, gfm: true, breaks: false })
    return DOMPurify.sanitize(raw)
  }, [content])

  return <div className="plan-markdown-body" dangerouslySetInnerHTML={{ __html: html }} />
}
