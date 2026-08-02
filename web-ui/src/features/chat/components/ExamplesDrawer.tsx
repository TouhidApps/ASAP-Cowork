import { EXAMPLE_CATEGORIES } from '@/features/chat/examplePrompts'

export function ExamplesDrawer({
  open,
  onClose,
  onPick,
}: {
  open: boolean
  onClose: () => void
  onPick: (prompt: string) => void
}) {
  if (!open) return null

  return (
    <>
      <div className="chat-drawer-backdrop" onClick={onClose} />
      <aside className="chat-drawer examples-drawer">
        <div className="chat-drawer-header">
          <h3>What can I ask?</h3>
          <button className="chat-drawer-close" onClick={onClose} aria-label="Close examples">
            ×
          </button>
        </div>
        <p className="examples-drawer-hint">
          Pick an example to try it, or use it as a starting point — the agent figures out which of its tools to use from
          what you type.
        </p>

        <div className="examples-drawer-body">
          {EXAMPLE_CATEGORIES.map((category) => (
            <section key={category.title} className="examples-category">
              <h4 className="examples-category-title">{category.title}</h4>
              <ul className="examples-category-list">
                {category.prompts.map((prompt) => (
                  <li key={prompt}>
                    <button
                      type="button"
                      className="examples-prompt"
                      onClick={() => {
                        onPick(prompt)
                        onClose()
                      }}
                    >
                      {prompt}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </aside>
    </>
  )
}
