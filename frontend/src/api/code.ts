import { apiClient } from './client'

/** Must match com.itilms.common.code.CodeLanguage. PL/SQL is deliberately absent: it needs an Oracle database. */
export const CODE_LANGUAGES = [
  { code: 'JAVA', label: 'Java' },
  { code: 'PYTHON', label: 'Python' },
  { code: 'C', label: 'C' },
  { code: 'CPP', label: 'C++' },
  { code: 'CSHARP', label: 'C#' },
  { code: 'SQL', label: 'SQL' },
] as const

export type CodeLanguageCode = (typeof CODE_LANGUAGES)[number]['code']

export function codeLanguageLabel(code: string): string {
  return CODE_LANGUAGES.find((language) => language.code === code)?.label ?? code
}

export interface CodeLanguageResponse {
  code: CodeLanguageCode
  label: string
}

export interface RunCodeInput {
  language: CodeLanguageCode
  sourceCode: string
  stdin?: string
}

export interface RunCodeResponse {
  language: CodeLanguageCode
  /** A compile error or a crash is a normal answer, not an HTTP error. */
  outcome: 'SUCCESS' | 'COMPILE_ERROR' | 'RUNTIME_ERROR' | 'TIME_LIMIT_EXCEEDED'
  statusDescription: string | null
  stdout: string | null
  stderr: string | null
  compileOutput: string | null
  message: string | null
  timeSeconds: number | null
  memoryKb: number | null
  outputTruncated: boolean
}

/** Only the languages switched on in the server's configuration. Empty until Judge0 is set up. */
export async function getCodeLanguages(): Promise<CodeLanguageResponse[]> {
  const { data } = await apiClient.get<CodeLanguageResponse[]>('/api/code/languages')
  return data
}

/**
 * Runs the code in the sandbox. Can take up to ~15 seconds. 429 means too many runs (or a busy runner),
 * 503 means the sandbox is not set up or not reachable - both carry a message fit to show the student.
 */
export async function runCode(input: RunCodeInput): Promise<RunCodeResponse> {
  const { data } = await apiClient.post<RunCodeResponse>('/api/code/run', input)
  return data
}
