import { Suspense, lazy } from 'react'

import { Skeleton } from '@/components/ui/skeleton'

import type { BarProps, DoughnutProps, LineProps } from './charts'

export type { BarProps, BarSeries, DoughnutProps, LineProps, LineSeries } from './charts'
export { PALETTE, SEMANTIC } from './chart-theme'

// Chart.js is a couple of hundred kB, so it loads only when a page actually draws a chart.
const Doughnut = lazy(() => import('./charts').then((m) => ({ default: m.DoughnutChart })))
const Bar = lazy(() => import('./charts').then((m) => ({ default: m.BarChart })))
const Line = lazy(() => import('./charts').then((m) => ({ default: m.LineChart })))

function Fallback({ height = 200 }: { height?: number }) {
  return <Skeleton style={{ height }} />
}

export function DoughnutChart(props: DoughnutProps) {
  return (
    <Suspense fallback={<Fallback height={props.height} />}>
      <Doughnut {...props} />
    </Suspense>
  )
}

export function BarChart(props: BarProps) {
  return (
    <Suspense fallback={<Fallback height={props.height} />}>
      <Bar {...props} />
    </Suspense>
  )
}

export function LineChart(props: LineProps) {
  return (
    <Suspense fallback={<Fallback height={props.height} />}>
      <Line {...props} />
    </Suspense>
  )
}
