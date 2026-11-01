import { defineConfig, devices } from '@playwright/test'

/**
 * Browser tests of the real, built web app with the backend replaced by mocks (e2e/helpers.ts), so they run in a
 * minute on any machine with no Docker stack. They catch what unit tests cannot: routing, role gates, forms, and
 * whether a page renders what the API gives it. The full-stack check against real services is
 * deploy/scripts/smoke-test.mjs.
 *
 * Locally, PW_CHANNEL=chrome uses the Chrome that is already installed instead of downloading a browser.
 */
const PORT = 4173
const ORIGIN = `http://127.0.0.1:${PORT}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // Each test starts a fresh browser page and the tests that build a file wait for it; a slow machine or a two-core CI
  // runner needs more than the 30 seconds a test gets by default, and fewer pages at once.
  timeout: 60_000,
  workers: process.env.CI ? 2 : 3,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: ORIGIN,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}) },
    },
  ],
  webServer: {
    // The app is built to call the API on its own origin, so every request can be answered by a mock without CORS.
    command: `npm run build && npx vite preview --host 127.0.0.1 --port ${PORT} --strictPort`,
    url: ORIGIN,
    reuseExistingServer: !process.env.CI,
    timeout: 240_000,
    env: { VITE_API_BASE_URL: ORIGIN },
  },
})
