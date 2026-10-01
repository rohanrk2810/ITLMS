import type { ChartData, ChartOptions } from 'chart.js'
import { Bar, Doughnut, Line } from 'react-chartjs-2'

import './chart-setup'
import { PALETTE, useChartColors } from './chart-theme'

interface Common {
  /** Read out by screen readers: the chart itself is a picture. */
  ariaLabel: string
  /** Height in pixels. */
  height?: number
}

export interface DoughnutProps extends Common {
  labels: string[]
  values: number[]
  colors?: string[]
}

export function DoughnutChart({ labels, values, colors, ariaLabel, height = 200 }: DoughnutProps) {
  const theme = useChartColors()
  const data: ChartData<'doughnut'> = {
    labels,
    datasets: [
      {
        data: values,
        backgroundColor: colors ?? labels.map((_, i) => PALETTE[i % PALETTE.length]),
        borderColor: theme.card,
        borderWidth: 2,
      },
    ],
  }
  const options: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: { legend: { position: 'bottom', labels: { color: theme.text, boxWidth: 12, padding: 12 } } },
  }
  return (
    <div className="relative" style={{ height }}>
      <Doughnut data={data} options={options} role="img" aria-label={ariaLabel} />
    </div>
  )
}

export interface BarSeries {
  label: string
  values: number[]
  /** One colour for the series, or one per bar. */
  color?: string | string[]
}

export interface BarProps extends Common {
  labels: string[]
  series: BarSeries[]
  horizontal?: boolean
  stacked?: boolean
  /** Fixes the value axis, e.g. 100 for percentages. */
  max?: number
  /** Added after each tick and tooltip value, e.g. "%" or " ms". */
  unit?: string
}

export function BarChart({ labels, series, horizontal, stacked, max, unit = '', ariaLabel, height = 220 }: BarProps) {
  const theme = useChartColors()
  const data: ChartData<'bar'> = {
    labels,
    datasets: series.map((s, i) => ({
      label: s.label,
      data: s.values,
      backgroundColor: s.color ?? PALETTE[i % PALETTE.length],
      borderRadius: 3,
    })),
  }
  const valueAxis = {
    beginAtZero: true,
    max,
    stacked,
    ticks: { color: theme.muted, callback: (v: string | number) => `${v}${unit}` },
    grid: { color: theme.grid },
  }
  const categoryAxis = { stacked, ticks: { color: theme.muted }, grid: { display: false } }
  const options: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: horizontal ? 'y' : 'x',
    scales: horizontal ? { x: valueAxis, y: categoryAxis } : { x: categoryAxis, y: valueAxis },
    plugins: {
      legend: { display: series.length > 1, labels: { color: theme.text, boxWidth: 12 } },
      tooltip: { callbacks: { label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed[horizontal ? 'x' : 'y']}${unit}` } },
    },
  }
  return (
    <div className="relative" style={{ height }}>
      <Bar data={data} options={options} role="img" aria-label={ariaLabel} />
    </div>
  )
}

export interface LineSeries {
  label: string
  points: Array<{ x: number; y: number }>
  /** A CSS colour, or the word primary for the institute brand colour (a canvas cannot read CSS variables). */
  color: string
  width?: number
  dashed?: boolean
  /** Drawn thin and pale, as background for comparison. */
  faint?: boolean
}

export interface LineProps extends Common {
  series: LineSeries[]
  xTitle?: string
  yTitle?: string
  /** Lines that go above this are cut at the top of the chart. */
  yMax?: number
}

export function LineChart({ series, xTitle, yTitle, yMax, ariaLabel, height = 240 }: LineProps) {
  const theme = useChartColors()
  const resolve = (color: string) => (color === 'primary' ? theme.primary : color)
  const data: ChartData<'line'> = {
    datasets: series.map((s) => ({
      label: s.label,
      data: s.points,
      borderColor: resolve(s.color),
      backgroundColor: resolve(s.color),
      borderWidth: s.faint ? 1 : (s.width ?? 2),
      borderDash: s.dashed ? [6, 4] : undefined,
      pointRadius: 0,
      pointHoverRadius: s.faint ? 0 : 4,
      tension: 0.25,
    })),
  }
  const axis = (title?: string) => ({
    title: { display: !!title, text: title, color: theme.muted },
    ticks: { color: theme.muted },
    grid: { color: theme.grid },
  })
  const options: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    parsing: false,
    animation: false,
    interaction: { mode: 'nearest', intersect: false },
    scales: {
      x: { type: 'linear', ...axis(xTitle) },
      y: { type: 'linear', beginAtZero: true, max: yMax, ...axis(yTitle) },
    },
    plugins: { legend: { position: 'bottom', labels: { color: theme.text, boxWidth: 12 } } },
  }
  return (
    <div className="relative" style={{ height }}>
      <Line data={data} options={options} role="img" aria-label={ariaLabel} />
    </div>
  )
}
