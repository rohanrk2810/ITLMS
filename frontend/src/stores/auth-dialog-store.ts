import { create } from 'zustand'

/** Which sign-in popup is open, if any. The popup opens over whatever public page the visitor is on. */
interface AuthDialogState {
  open: 'login' | 'register' | null
  show: (which: 'login' | 'register') => void
  close: () => void
}

export const useAuthDialog = create<AuthDialogState>((set) => ({
  open: null,
  show: (which) => set({ open: which }),
  close: () => set({ open: null }),
}))
