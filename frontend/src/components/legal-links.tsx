import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'

const LINKS = [
  { to: '/privacy', label: 'Privacy Policy' },
  { to: '/terms', label: 'Terms and Conditions' },
  { to: '/cookies', label: 'Cookie Policy' },
  { to: '/refund', label: 'Refund Policy' },
]

/** The four policy links, shown under every public page. */
export function LegalLinks({ className }: { className?: string }) {
  return (
    <nav aria-label="Legal" className={cn('flex flex-wrap justify-center gap-x-4 gap-y-1 text-xs text-muted-foreground', className)}>
      {LINKS.map((link) => (
        <Link key={link.to} to={link.to} className="underline-offset-2 hover:underline">
          {link.label}
        </Link>
      ))}
    </nav>
  )
}
