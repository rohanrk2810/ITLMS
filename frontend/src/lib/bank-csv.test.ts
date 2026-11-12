import { describe, expect, it } from 'vitest'

import { bankDate, parseBankCsv, splitCsvLine } from './bank-csv'

describe('bank statement CSV', () => {
  it('reads the common date formats', () => {
    expect(bankDate('12/03/2026')).toBe('2026-03-12')
    expect(bankDate('2026-3-2')).toBe('2026-03-02')
    expect(bankDate('12-Mar-2026')).toBe('2026-03-12')
    expect(bankDate('Opening balance')).toBeNull()
  })

  it('keeps commas inside quoted fields', () => {
    expect(splitCsvLine('12/03/2026,"UPI/412345678901/ASHA, PUNE",,5000')).toEqual(['12/03/2026', 'UPI/412345678901/ASHA, PUNE', '', '5000'])
  })

  it('finds the columns by heading and keeps lines with a date and one amount', () => {
    const csv = [
      'Account statement for 1647201001212',
      'Date,Narration,Chq/Ref No,Withdrawal,Deposit,Balance',
      '01/03/2026,Opening balance,,,,"1,00,000.00"',
      '05/03/2026,UPI/412345678901/ASHA,412345678901,,"5,000.00","1,05,000.00"',
      '06/03/2026,Rent March,CHQ 000123,"20,000.00",,"85,000.00"',
    ].join('\n')

    const { lines, skipped } = parseBankCsv(csv)

    expect(skipped).toBe(1)
    expect(lines).toHaveLength(2)
    expect(lines[0]).toMatchObject({ date: '2026-03-05', credit: 5000, debit: 0, reference: '412345678901' })
    expect(lines[1]).toMatchObject({ date: '2026-03-06', debit: 20000, credit: 0 })
  })

  it('says so when it cannot find the columns', () => {
    expect(parseBankCsv('a,b,c\n1,2,3').problem).toBeTruthy()
  })
})
