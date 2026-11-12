import { apiClient } from './client'

export interface BookBalance {
  code: string
  name: string
  type: string
  debit: number
  credit: number
}

export interface JournalLine {
  account: string
  debit: number
  credit: number
}

export interface JournalEntry {
  date: string
  reference: string
  description: string
  lines: JournalLine[]
}

export interface Books {
  from: string
  to: string
  accounts: { code: string; name: string; type: string }[]
  journal: JournalEntry[]
  trialBalance: BookBalance[]
  profitLoss: { income: BookBalance[]; expenses: BookBalance[]; totalIncome: number; totalExpenses: number; surplus: number }
  balanceSheet: {
    asOf: string
    assets: BookBalance[]
    liabilities: BookBalance[]
    totalAssets: number
    totalLiabilities: number
    surplus: number
    balances: boolean
  }
  balanced: boolean
}

/** The books for a period: journal, trial balance (from the start), profit and loss, and the balance sheet at the end. */
export async function getBooks(from: string, to: string): Promise<Books> {
  const { data } = await apiClient.get<Books>('/api/fees/books', { params: { from, to } })
  return data
}
