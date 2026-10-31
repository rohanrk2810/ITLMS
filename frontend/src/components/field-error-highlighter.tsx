import { useEffect } from 'react'

import { API_FIELD_ERRORS_EVENT, fieldLabel } from '@/api/client'

const INVALID_CLASS = 'field-invalid'
const MESSAGE_CLASS = 'field-error-text'

/** Lower case letters and digits only, so "durationMinutes", "duration_minutes" and "Duration minutes" compare equal. */
function normal(text: string | null | undefined): string {
  return (text ?? '').toLowerCase().replace(/[^a-z0-9]/g, '')
}

/** The words a person reads next to a control: its <label>, the one wrapped around it, or its aria-label. */
function labelOf(control: HTMLElement): string {
  const id = control.id
  const forLabel = id ? document.querySelector(`label[for="${CSS.escape(id)}"]`) : null
  const wrapping = control.closest('label')
  return forLabel?.textContent ?? wrapping?.textContent ?? control.getAttribute('aria-label') ?? ''
}

/** The control a server field name belongs to: by id, name or data-field, else by the label beside it. */
function findControl(root: ParentNode, field: string): HTMLElement | null {
  const last = field.split('.').pop() ?? field
  const key = normal(last.replace(/\[\d+\]/g, ''))
  if (key === '') return null
  const controls = Array.from(root.querySelectorAll<HTMLElement>('input, select, textarea')).filter(
    (c) => !(c instanceof HTMLInputElement && (c.type === 'hidden' || c.type === 'file')) && c.offsetParent !== null,
  )
  const exact = controls.find((c) => [c.id, c.getAttribute('name'), c.dataset.field].some((v) => normal(v) === key))
  if (exact) return exact
  // "Duration (min)" belongs to durationMinutes: one starts with the other.
  return (
    controls.find((c) => {
      const label = normal(labelOf(c))
      return label.length >= 3 && (key.startsWith(label) || label.startsWith(key))
    }) ?? null
  )
}

function clearMark(control: HTMLElement) {
  control.classList.remove(INVALID_CLASS)
  control.removeAttribute('aria-invalid')
  const next = control.nextElementSibling
  if (next?.classList.contains(MESSAGE_CLASS)) next.remove()
}

function mark(control: HTMLElement, message: string) {
  clearMark(control)
  control.classList.add(INVALID_CLASS)
  control.setAttribute('aria-invalid', 'true')
  const note = document.createElement('p')
  note.className = MESSAGE_CLASS
  note.textContent = message
  control.insertAdjacentElement('afterend', note)
  const clear = () => clearMark(control)
  control.addEventListener('input', clear, { once: true })
  control.addEventListener('change', clear, { once: true })
}

/**
 * When the server says which fields of a form it refused, turn those fields red and say why under each, and move to the
 * first one, so nobody has to guess which of the fields is the one that is wrong or still empty. It looks in the open
 * dialog if there is one, else in the page; a field it cannot find is still named in the error message that is shown.
 */
export function FieldErrorHighlighter() {
  useEffect(() => {
    function onErrors(event: Event) {
      const errors = (event as CustomEvent<Record<string, string>>).detail
      const root: ParentNode = document.querySelector('[role="dialog"]') ?? document
      let first: HTMLElement | null = null
      for (const [field, message] of Object.entries(errors)) {
        const control = findControl(root, field)
        if (!control) continue
        mark(control, message || `${fieldLabel(field)} needs your attention`)
        first ??= control
      }
      if (first) {
        first.scrollIntoView({ block: 'center', behavior: 'smooth' })
        first.focus({ preventScroll: true })
      }
    }
    window.addEventListener(API_FIELD_ERRORS_EVENT, onErrors)
    return () => window.removeEventListener(API_FIELD_ERRORS_EVENT, onErrors)
  }, [])
  return null
}
