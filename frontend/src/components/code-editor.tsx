import Editor, { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'

import type { CodeLanguageCode } from '@/api/code'
import { MONACO_LANGUAGE, registerCompletions } from '@/lib/code-completions'

// Serve Monaco from this bundle instead of the CDN the wrapper defaults to: the page must work offline
// and inside a network that blocks third-party scripts. Only the base worker is needed - there is no
// language server here, just Monaco's own tokenizers plus the suggestions in code-completions.ts.
self.MonacoEnvironment = { getWorker: () => new EditorWorker() }
loader.config({ monaco })

interface CodeEditorProps {
  language: CodeLanguageCode
  value: string
  onChange: (value: string) => void
  /** Ctrl/Cmd+Enter. */
  onRun?: () => void
  readOnly?: boolean
  /** Pixel height. The editor scrolls inside it. */
  height?: number
  ariaLabel?: string
}

/** The one code editor used across the app: practice lessons, live-class questions and tests. */
export default function CodeEditor({ language, value, onChange, onRun, readOnly, height = 320, ariaLabel }: CodeEditorProps) {
  const dark = document.documentElement.classList.contains('dark')

  return (
    <div className="overflow-hidden rounded-md border" style={{ height }}>
      <Editor
        language={MONACO_LANGUAGE[language]}
        value={value}
        theme={dark ? 'vs-dark' : 'vs'}
        onChange={(next) => onChange(next ?? '')}
        beforeMount={registerCompletions}
        onMount={(editor, instance) => {
          if (onRun) {
            editor.addCommand(instance.KeyMod.CtrlCmd | instance.KeyCode.Enter, onRun)
          }
        }}
        loading={<div className="p-3 text-xs text-muted-foreground">Loading editor...</div>}
        options={{
          readOnly,
          ariaLabel: ariaLabel ?? `${language} code`,
          minimap: { enabled: false },
          fontSize: 14,
          lineHeight: 22,
          tabSize: 4,
          insertSpaces: true,
          automaticLayout: true,
          scrollBeyondLastLine: false,
          wordWrap: 'off',
          quickSuggestions: { other: true, comments: false, strings: false },
          suggestOnTriggerCharacters: true,
          snippetSuggestions: 'top',
          acceptSuggestionOnEnter: 'on',
          bracketPairColorization: { enabled: true },
          autoClosingBrackets: 'always',
          autoClosingQuotes: 'always',
          formatOnPaste: false,
          renderLineHighlight: 'line',
          padding: { top: 8, bottom: 8 },
        }}
      />
    </div>
  )
}
