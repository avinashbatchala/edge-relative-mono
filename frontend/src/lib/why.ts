export type WhyTone = 'default' | 'positive' | 'negative' | 'muted'

export interface WhyItem {
  label: string
  detail?: string
  tone?: WhyTone
}

export interface WhySection {
  title: string
  items: WhyItem[]
}

/** Tone → badge classes for the "why" explanations. */
export const WHY_TONE_CLASS: Record<WhyTone, string> = {
  default: '',
  positive: 'border-transparent bg-positive/15 text-positive',
  negative: 'border-transparent bg-negative/15 text-negative',
  muted: 'text-muted-foreground',
}
