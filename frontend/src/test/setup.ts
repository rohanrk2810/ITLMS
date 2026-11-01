import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Components mounted by one test must not leak into the next.
afterEach(() => {
  cleanup()
})
