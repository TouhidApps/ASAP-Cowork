import { Diff, Hunk, parseDiff } from 'react-diff-view'
import type { FileDiff } from '@/features/history/types'
import 'react-diff-view/style/index.css'

/**
 * Renders one file's real git unified-diff patch text (produced server-side by JGit's
 * DiffFormatter) as a GitHub-style diff — parsing is delegated entirely to
 * `gitdiff-parser`/`react-diff-view` rather than re-diffing content in JS, since the patch
 * text is already a proper diff.
 */
export function FileDiffView({ file }: { file: FileDiff }) {
  const [parsed] = parseDiff(file.patch)

  if (!parsed || parsed.hunks.length === 0) {
    return <p className="history-diff-empty">No textual diff to show (binary file or no line changes).</p>
  }

  return (
    <div className="code-changes-diff-scroll">
      <Diff viewType="split" diffType={parsed.type} hunks={parsed.hunks}>
        {(hunks) => hunks.map((hunk) => <Hunk key={hunk.content} hunk={hunk} />)}
      </Diff>
    </div>
  )
}
