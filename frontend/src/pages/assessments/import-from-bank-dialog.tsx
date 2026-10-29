import { useState } from 'react'
import { Library } from 'lucide-react'
import { toast } from 'sonner'

import { addQuestion, type QuestionInput } from '@/api/assessments'
import { apiErrorMessage } from '@/api/client'
import type { CodeLanguageCode } from '@/api/code'
import type { DataTable, LiveMaterial } from '@/api/live-classes'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { FolderTree } from '@/pages/live-classes/folder-tree'
import { MaterialList } from '@/pages/live-classes/material-list'

function tableText(table: DataTable) {
  return [table.name, table.columns.join(' | '), ...table.rows.map((r) => r.join(' | '))].join('\n')
}

/**
 * Turns a bank question into a test question, or says why it cannot be one. A test marks by comparison or by running
 * code against test cases; a coding or open question from a class has neither, so it stays in the bank.
 */
export function toTestQuestion(item: LiveMaterial): QuestionInput | string {
  const q = item.question
  const text = [q.prompt, q.snippet, ...(q.dataset ?? []).map(tableText)].filter(Boolean).join('\n\n')
  const base = { questionText: text, marks: q.marks, explanation: q.explanation }

  switch (q.type) {
    case 'MCQ':
    case 'MULTIPLE_SELECT':
      return {
        ...base,
        type: q.type === 'MCQ' ? 'SINGLE_CHOICE' : 'MULTI_CHOICE',
        options: (q.options ?? []).map((optionText, i) => ({ optionText, correct: (q.correctOptions ?? []).includes(i) })),
      }
    case 'TRUE_FALSE':
      return {
        ...base,
        type: 'TRUE_FALSE',
        options: [
          { optionText: 'True', correct: q.correctOptions?.[0] === 0 },
          { optionText: 'False', correct: q.correctOptions?.[0] === 1 },
        ],
      }
    case 'SHORT_ANSWER':
      return q.acceptedAnswers?.length
        ? { ...base, type: 'SHORT_ANSWER', acceptedAnswers: q.acceptedAnswers }
        : 'no right answer set'
    case 'CODING': {
      if (!q.testCases?.length) return 'coding needs test cases'
      const language = (q.language ?? 'python').toUpperCase() as CodeLanguageCode
      return {
        ...base,
        type: 'CODING',
        codeLanguage: language,
        allowLanguageChoice: language === 'SQL' ? undefined : q.allowLanguageChoice,
        starterCode: q.starterCode || undefined,
        setupSql: language === 'SQL' ? q.setupSql || undefined : undefined,
        testCases: q.testCases.map((c) => ({
          input: c.input,
          expectedOutput: c.expectedOutput,
          hidden: c.hidden,
          weight: c.weight,
        })),
      }
    }
    default:
      return 'open questions are not marked'
  }
}

/** Pick questions from the trainer's bank and add them to this draft test. */
export function ImportFromBankDialog({ quizId, onAdded }: { quizId: number; onAdded: () => void }) {
  const [open, setOpen] = useState(false)
  const [picked, setPicked] = useState<Map<number, QuestionInput>>(new Map())
  const [busy, setBusy] = useState(false)
  const [folder, setFolder] = useState('')

  async function importPicked() {
    setBusy(true)
    let added = 0
    try {
      // One at a time: each add renumbers the questions, so parallel requests would race for the same position.
      for (const input of picked.values()) {
        await addQuestion(quizId, input)
        added++
      }
      toast.success(`${added} question${added === 1 ? '' : 's'} added`)
      setPicked(new Map())
      setOpen(false)
    } catch (err) {
      toast.error(apiErrorMessage(err, `Stopped after ${added} added.`))
    } finally {
      setBusy(false)
      if (added > 0) onAdded()
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <Library className="size-3.5" />
          Import from bank
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>Import from your question bank</DialogTitle>
        </DialogHeader>
        <p className="text-xs text-muted-foreground">
          Single choice, multiple select, true/false, short answers with a right answer, and coding questions with test
          cases can be imported. Code shown with a question is added to its text.
        </p>
        <div className="flex flex-col gap-4 sm:flex-row">
        <FolderTree readOnly selected={folder} onSelect={setFolder} />
        <div className="min-w-0 flex-1">
        <MaterialList
          folder={folder}
          leading={(item) => {
            const converted = toTestQuestion(item)
            return typeof converted === 'string' ? (
              <span className="w-24 shrink-0 text-[10px] text-muted-foreground">Can't import: {converted}</span>
            ) : (
              <Checkbox
                checked={picked.has(item.id)}
                onCheckedChange={(on) =>
                  setPicked((prev) => {
                    const next = new Map(prev)
                    if (on === true) next.set(item.id, converted)
                    else next.delete(item.id)
                    return next
                  })
                }
              />
            )
          }}
        />
        </div>
        </div>
        <DialogFooter>
          <Button disabled={busy || picked.size === 0} onClick={() => void importPicked()}>
            {busy ? 'Adding...' : `Add ${picked.size || ''} to test`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
