/** Centralises reading Vite env vars so a typo shows up here, not scattered across the app. */
export const env = {
  /** Everything goes through the gateway - no service is ever addressed directly. */
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8080',
}
