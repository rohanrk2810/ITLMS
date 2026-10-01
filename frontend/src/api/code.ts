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

/** n^(p2/2) * (log n)^log, or exponential: the growth rate in numbers, so it can be drawn. */
export interface ComplexityModel {
  p2: number
  log: number
  exp: boolean
}

export interface CodeSuggestion {
  title: string
  explanation: string
  currentTime: string
  currentSpace: string
  betterTime: string
  betterSpace: string
  betterTimeModel: ComplexityModel
  betterSpaceModel: ComplexityModel
}

/** An estimate read from the source, never a measurement and never a proof. */
export interface CodeAnalysis {
  language: string
  supported: boolean
  estimated: boolean
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  timeComplexity: string | null
  timeModel: ComplexityModel | null
  timeReason: string | null
  timeSteps: string[]
  spaceComplexity: string | null
  spaceModel: ComplexityModel | null
  spaceReason: string | null
  spaceSteps: string[]
  verdict: 'EFFICIENT' | 'COULD_BE_BETTER' | 'UNKNOWN'
  verdictMessage: string
  suggestions: CodeSuggestion[]
  notes: string[]
}

/** Reads the code and estimates its time and space complexity. Nothing is executed. */
export async function analyzeCode(language: CodeLanguageCode, sourceCode: string): Promise<CodeAnalysis> {
  const { data } = await apiClient.post<CodeAnalysis>('/api/code/analyze', { language, sourceCode })
  return data
}
