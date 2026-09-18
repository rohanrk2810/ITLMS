import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/** Stands in for a module whose page has not been built yet, so the sidebar's routes all resolve. */
export function PlaceholderPage({ title }: { title: string }) {
  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>This module is coming soon.</CardDescription>
      </CardHeader>
    </Card>
  )
}
