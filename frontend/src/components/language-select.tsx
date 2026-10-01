import { CODE_LANGUAGES, type CodeLanguageCode } from '@/api/code'

/**
 * Lets a student pick the language to write in, where the question or lesson allows it. SQL is left out: it needs
 * its own database, so it cannot stand in for the others (and they cannot stand in for it).
 */
export function LanguageSelect({
  value,
  onChange,
  disabled,
}: {
  value: CodeLanguageCode
  onChange: (language: CodeLanguageCode) => void
  disabled?: boolean
}) {
  return (
    <label className="flex items-center gap-2 text-xs text-muted-foreground">
      Language
      <select
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as CodeLanguageCode)}
        className="h-8 rounded-md border bg-transparent px-2 text-sm text-foreground"
      >
        {CODE_LANGUAGES.filter((l) => l.code !== 'SQL').map((l) => (
          <option key={l.code} value={l.code}>
            {l.label}
          </option>
        ))}
      </select>
    </label>
  )
}
