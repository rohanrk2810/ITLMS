import { describe, expect, it } from 'vitest'

import { parseTestJson } from './test-json'

describe('parseTestJson', () => {
  it('reads a multiple choice question with the right answer given as a letter', () => {
    const { questions, skipped } = parseTestJson(
      JSON.stringify({ questions: [{ question: 'Capital of India?', options: ['Mumbai', 'Delhi', 'Pune'], correct: 'B' }] }),
    )
    expect(skipped).toEqual([])
    expect(questions).toHaveLength(1)
    expect(questions[0].type).toBe('SINGLE_CHOICE')
    expect(questions[0].options?.map((o) => o.correct)).toEqual([false, true, false])
  })

  it('accepts a bare list of questions', () => {
    const { questions } = parseTestJson(JSON.stringify([{ question: 'Is 2 even?', type: 'TRUE_FALSE', correct: true }]))
    expect(questions[0].type).toBe('TRUE_FALSE')
    expect(questions[0].options?.[0]).toEqual({ optionText: 'True', correct: true })
  })

  it('takes the test settings that sit beside the questions', () => {
    const { settings } = parseTestJson(
      JSON.stringify({ title: 'Week 1', durationMinutes: 30, passPercentage: 40, attemptsAllowed: 2, questions: [] }),
    )
    expect(settings).toMatchObject({ title: 'Week 1', durationMinutes: 30, passPercentage: 40, attemptsAllowed: 2 })
  })

  it('leaves out a question it cannot mark and says why', () => {
    const { questions, skipped } = parseTestJson(
      JSON.stringify({
        questions: [
          { question: 'Fine', options: ['a', 'b'], correct: 'A' },
          { question: 'No key', options: ['a', 'b'] },
          { question: '', options: ['a', 'b'], correct: 'A' },
          { question: 'Essay', type: 'ESSAY' },
        ],
      }),
    )
    expect(questions).toHaveLength(1)
    expect(skipped).toHaveLength(3)
    expect(skipped[0]).toContain('Question 2')
  })

  it('a coding question needs test cases', () => {
    const none = parseTestJson(JSON.stringify({ questions: [{ question: 'Add', type: 'CODING' }] }))
    expect(none.questions).toHaveLength(0)
    const some = parseTestJson(
      JSON.stringify({ questions: [{ question: 'Add', type: 'CODING', testCases: [{ input: '1 2', expectedOutput: '3' }] }] }),
    )
    expect(some.questions[0].codeLanguage).toBe('PYTHON')
    expect(some.questions[0].testCases).toEqual([{ input: '1 2', expectedOutput: '3', hidden: false, weight: 1 }])
  })

  it('says plainly when the file is not usable', () => {
    expect(() => parseTestJson('{ not json')).toThrow('not valid JSON')
    expect(() => parseTestJson('{"title":"x"}')).toThrow('questions')
  })
})
