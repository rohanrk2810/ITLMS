import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'

export function ForbiddenPage() {
  return (
    <div className="flex flex-col items-start gap-3">
      <h1 className="text-2xl font-semibold">Not available for your role</h1>
      <p className="text-muted-foreground">Your account doesn&apos;t have access to this module.</p>
      <Button asChild>
        <Link to="/app">Back to dashboard</Link>
      </Button>
    </div>
  )
}
