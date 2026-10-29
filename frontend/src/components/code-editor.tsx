import { useEffect, useRef } from 'react'
import Editor, { loader } from '@monaco-editor/react'
import * as monaco from 'monaco-editor'
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'

import type { CodeLanguageCode } from '@/api/code'
import { MONACO_LANGUAGE, registerCompletions } from '@/lib/code-completions'
import type { EditorEdit } from '@/lib/typing-recorder'

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
  /** Fill the parent (which must have a height) and draw no border: the parent supplies the frame. */
  fill?: boolean
  ariaLabel?: string
  /** Autocomplete and snippets. On for practice; an exam turns it off unless its author allows it. */
  suggestions?: boolean
  /** Dark editor theme; by default it follows the page. */
  dark?: boolean
  /** Told about every change as it is made (typing replay). Setting the value from outside is reported as one replace-all. */
  onEdits?: (edits: EditorEdit[]) => void
  /** Told when text has just been pasted. */
  onPaste?: () => void
}

/** The one code editor used across the app: practice lessons, live-class questions and tests. */
export default function CodeEditor({ language, value, onChange, onRun, readOnly, height = 320, fill = false, ariaLabel, suggestions = true, dark: darkProp, onEdits, onPaste }: CodeEditorProps) {
  // Monaco wires its listeners once, on mount; these keep them calling the latest callbacks.
  const editsRef = useRef(onEdits)
  const pasteRef = useRef(onPaste)
  useEffect(() => {
    editsRef.current = onEdits
    pasteRef.current = onPaste
  }, [onEdits, onPaste])
  const dark = darkProp ?? document.documentElement.classList.contains('dark')

  return (
    <div className={fill ? 'size-full overflow-hidden' : 'overflow-hidden rounded-md border'} style={fill ? undefined : { height }}>
      <Editor
        language={MONACO_LANGUAGE[language]}
        value={value}
        theme={dark ? 'vs-dark' : 'vs'}
        onChange={(next) => onChange(next ?? '')}
        beforeMount={registerCompletions}
        onMount={(editor, instance) => {
          if (!suggestions) {
            // Ctrl+Space would still open the list by hand; swallow it.
            editor.addCommand(instance.KeyMod.CtrlCmd | instance.KeyCode.Space, () => undefined)
            editor.addCommand(instance.KeyMod.WinCtrl | instance.KeyCode.Space, () => undefined)
          }
          if (onRun) {
            editor.addCommand(instance.KeyMod.CtrlCmd | instance.KeyCode.Enter, onRun)
          }
          editor.onDidPaste(() => pasteRef.current?.())
          editor.onDidChangeModelContent((event) => {
            const report = editsRef.current
            if (!report) return
            if (event.isFlush) {
              report([{ offset: 0, removed: -1, text: editor.getValue() }])
              return
            }
            report(event.changes.map((c) => ({ offset: c.rangeOffset, removed: c.rangeLength, text: c.text })))
          })
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
          quickSuggestions: suggestions ? { other: true, comments: false, strings: false } : false,
          suggestOnTriggerCharacters: suggestions,
          snippetSuggestions: suggestions ? 'top' : 'none',
          wordBasedSuggestions: suggestions ? 'currentDocument' : 'off',
          parameterHints: { enabled: suggestions },
          tabCompletion: suggestions ? 'on' : 'off',
          acceptSuggestionOnEnter: suggestions ? 'on' : 'off',
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
