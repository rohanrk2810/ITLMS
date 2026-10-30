import type { QuestionInput, QuizInput } from '@/api/assessments'
import type { CodeLanguageCode } from '@/api/code'

export interface TestJson {
  settings: Partial<Pick<QuizInput, 'title' | 'instructions' | 'durationMinutes' | 'passPercentage' | 'attemptsAllowed'>>
  questions: QuestionInput[]
  /** Questions that were left out, with the reason (open questions cannot be marked, for one). */
  skipped: string[]
}

type Raw = Record<string, unknown>

function letterIndex(value: unknown): number {
  if (typeof value === 'number') return value
  const text = String(value ?? '').trim().toUpperCase()
  return text.length === 1 && text >= 'A' && text <= 'Z' ? text.charCodeAt(0) - 65 : -1
}

function toQuestion(raw: Raw): QuestionInput | string {
  const type = String(raw.type ?? 'MCQ').toUpperCase()
  const code = typeof raw.code === 'string' && raw.code ? `\n\n${raw.code}` : ''
  const text = String(raw.question ?? raw.questionText ?? '').trim()
  if (!text) return 'no question text'
  const base = {
    questionText: text + code,
    marks: typeof raw.marks === 'number' ? raw.marks : undefined,
    explanation: typeof raw.explanation === 'string' ? raw.explanation : undefined,
  }
  const options = Array.isArray(raw.options) ? raw.options.map(String) : []
  const correct = Array.isArray(raw.correct) ? raw.correct : [raw.correct]

  switch (type) {
    case 'MCQ':
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_SELECT':
    case 'MULTI_CHOICE': {
      const right = correct.map(letterIndex)
      if (options.length < 2 || !right.some((i) => i >= 0 && i < options.length)) return 'options or the right answer are missing'
      return {
        ...base,
        type: type === 'MCQ' || type === 'SINGLE_CHOICE' ? 'SINGLE_CHOICE' : 'MULTI_CHOICE',
        options: options.map((optionText, i) => ({ optionText, correct: right.includes(i) })),
      }
    }
    case 'TRUE_FALSE': {
      const isTrue = String(correct[0]).toLowerCase() === 'true'
      return {
        ...base,
        type: 'TRUE_FALSE',
        options: [
          { optionText: 'True', correct: isTrue },
          { optionText: 'False', correct: !isTrue },
        ],
      }
    }
    case 'SHORT_ANSWER': {
      const accepted = correct.map(String).filter(Boolean)
      return accepted.length ? { ...base, type: 'SHORT_ANSWER', acceptedAnswers: accepted } : 'no right answer'
    }
    case 'CODING': {
      const cases = Array.isArray(raw.testCases) ? (raw.testCases as Raw[]) : []
      if (!cases.length) return 'coding needs test cases'
      const language = String(raw.language ?? 'python').toUpperCase() as CodeLanguageCode
      return {
        ...base,
        type: 'CODING',
        codeLanguage: language,
        allowLanguageChoice: language === 'SQL' ? undefined : raw.allowAnyLanguage === true,
        starterCode: typeof raw.starterCode === 'string' && raw.starterCode ? raw.starterCode : undefined,
        setupSql: language === 'SQL' && typeof raw.setupSql === 'string' ? raw.setupSql : undefined,
        testCases: cases.map((c) => ({
          input: String(c.input ?? ''),
          expectedOutput: String(c.expectedOutput ?? ''),
          hidden: c.hidden === true,
          weight: typeof c.weight === 'number' ? c.weight : 1,
        })),
      }
    }
    default:
      return 'open questions are not marked'
  }
}

/**
 * Reads a test file: the question-bank JSON format ({"questions": [...]}), optionally with title, instructions,
 * durationMinutes, passPercentage and attemptsAllowed next to it. Throws a readable message if it is not that.
 */
export function parseTestJson(text: string): TestJson {
  let data: unknown
  try {
    data = JSON.parse(text)
  } catch {
    throw new Error('This file is not valid JSON.')
  }
  const root = (Array.isArray(data) ? { questions: data } : data) as Raw
  if (!root || !Array.isArray(root.questions)) throw new Error('The file needs a "questions" list.')

  const questions: QuestionInput[] = []
  const skipped: string[] = []
  ;(root.questions as Raw[]).forEach((raw, i) => {
    const result = toQuestion(raw ?? {})
    if (typeof result === 'string') skipped.push(`Question ${i + 1}: ${result}`)
    else questions.push(result)
  })

  const num = (key: string) => (typeof root[key] === 'number' ? (root[key] as number) : undefined)
  return {
    settings: {
      title: typeof root.title === 'string' ? root.title : undefined,
      instructions: typeof root.instructions === 'string' ? root.instructions : undefined,
      durationMinutes: num('durationMinutes'),
      passPercentage: num('passPercentage'),
      attemptsAllowed: num('attemptsAllowed'),
    },
    questions,
    skipped,
  }
}
