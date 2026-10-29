import { type FormEvent, type ReactNode, useCallback, useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

interface ConfirmOptions {
  title: string
  message?: string
  confirmLabel?: string
  /** Deleting something: the button is red. */
  destructive?: boolean
}

interface PromptOptions {
  title: string
  message?: string
  label?: string
  initial?: string
  placeholder?: string
  confirmLabel?: string
}

type Pending =
  | ({ kind: 'confirm'; resolve: (ok: boolean) => void } & ConfirmOptions)
  | ({ kind: 'prompt'; resolve: (value: string | null) => void } & PromptOptions)

/**
 * In-page replacements for window.confirm and window.prompt, which open the browser's own alert box over the app.
 * Render {@code dialog} once in the component, then {@code await confirm(...)} or {@code await prompt(...)} where the
 * old call was; cancelling resolves to false / null, exactly as the browser versions did.
 */
export function useAsk() {
  const [pending, setPending] = useState<Pending | null>(null)
  const [text, setText] = useState('')

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => setPending({ kind: 'confirm', resolve, ...options })),
    [],
  )

  const prompt = useCallback(
    (options: PromptOptions) =>
      new Promise<string | null>((resolve) => {
        setText(options.initial ?? '')
        setPending({ kind: 'prompt', resolve, ...options })
      }),
    [],
  )

  function finish(accepted: boolean) {
    if (!pending) return
    if (pending.kind === 'confirm') pending.resolve(accepted)
    else pending.resolve(accepted ? text : null)
    setPending(null)
  }

  const dialog: ReactNode = (
    <Dialog open={pending !== null} onOpenChange={(open) => !open && finish(false)}>
      <DialogContent className="sm:max-w-md">
        {pending && (
          <form
            className="flex flex-col gap-4"
            onSubmit={(event: FormEvent) => {
              event.preventDefault()
              finish(true)
            }}
          >
            <DialogHeader>
              <DialogTitle>{pending.title}</DialogTitle>
              {pending.message && <DialogDescription>{pending.message}</DialogDescription>}
            </DialogHeader>
            {pending.kind === 'prompt' && (
              <div className="flex flex-col gap-1.5">
                {pending.label && <label className="text-sm font-medium" htmlFor="ask-dialog-input">{pending.label}</label>}
                <Input
                  id="ask-dialog-input"
                  autoFocus
                  value={text}
                  placeholder={pending.placeholder}
                  onChange={(event) => setText(event.target.value)}
                />
              </div>
            )}
            <DialogFooter className="gap-2">
              <Button type="button" variant="outline" onClick={() => finish(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                variant={pending.kind === 'confirm' && pending.destructive ? 'destructive' : 'default'}
                disabled={pending.kind === 'prompt' && text.trim() === ''}
              >
                {pending.confirmLabel ?? (pending.kind === 'confirm' && pending.destructive ? 'Delete' : 'OK')}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )

  return { confirm, prompt, dialog }
}
