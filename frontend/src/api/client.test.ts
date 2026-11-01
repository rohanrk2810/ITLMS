import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'

import { apiErrorMessage, fieldLabel } from './client'

function axiosError(data: unknown, status = 400) {
  const response = { data, status, statusText: '', headers: {}, config: { headers: new AxiosHeaders() } } as AxiosResponse
  return new AxiosError('failed', 'ERR_BAD_REQUEST', response.config, null, response)
}

describe('fieldLabel', () => {
  it.each([
    ['durationMinutes', 'Duration minutes'],
    ['answers[0].questionText', 'Question text'],
    ['title', 'Title'],
    ['first_name', 'First name'],
    ['', ''],
  ])('%s -> %s', (field, label) => {
    expect(fieldLabel(field)).toBe(label)
  })
})

describe('apiErrorMessage', () => {
  it('names the fields the server refused', () => {
    const message = apiErrorMessage(axiosError({ fieldErrors: { durationMinutes: 'must be at least 1', title: 'is required' } }))
    expect(message).toBe('Please complete or correct: Duration minutes: must be at least 1; Title: is required')
  })

  it('shortens a long list of refused fields', () => {
    const fieldErrors = Object.fromEntries(Array.from({ length: 6 }, (_, i) => [`f${i}`, 'bad']))
    expect(apiErrorMessage(axiosError({ fieldErrors }))).toContain('(and 2 more)')
  })

  it("uses the server's own message when there are no fields", () => {
    expect(apiErrorMessage(axiosError({ message: 'This card has expired.' }, 409))).toBe('This card has expired.')
  })

  it('uses the fallback for an error that is not from the server', () => {
    expect(apiErrorMessage(new Error('boom'), 'Try again')).toBe('Try again')
    expect(apiErrorMessage(axiosError({}), 'Try again')).toBe('Try again')
  })
})
