import { Component, type ErrorInfo, type ReactNode } from 'react'

import { Button } from '@/components/ui/button'

interface State {
  failed: boolean
}

/**
 * Catches a crash while rendering and shows a way out instead of a blank white page.
 * Reload fixes most cases (a stale script after a deploy, a server that was down).
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Screen crashed', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div role="alert" className="flex min-h-svh flex-col items-center justify-center gap-3 p-6 text-center">
        <h1 className="text-xl font-semibold">Something went wrong</h1>
        <p className="max-w-sm text-sm text-muted-foreground">
          This screen could not load. The server may be unreachable or restarting. Try again in a moment.
        </p>
        <div className="flex gap-2">
          <Button onClick={() => this.setState({ failed: false })}>Try again</Button>
          <Button variant="outline" onClick={() => window.location.reload()}>
            Reload page
          </Button>
        </div>
      </div>
    )
  }
}
