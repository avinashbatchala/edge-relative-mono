<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { useDark } from '@vueuse/core'
import {
  ColorType,
  CrosshairMode,
  createChart,
  type CandlestickData,
  type HistogramData,
  type IChartApi,
  type ISeriesApi,
  type LogicalRange,
  type MouseEventParams,
  type UTCTimestamp,
} from 'lightweight-charts'
import type { BrokerCandle } from '@/api/types'
import { formatCompact, formatPrice } from '@/lib/format'
import {
  broadcastCrosshair,
  broadcastLogicalRange,
  createChartSyncMemberId,
  joinChartSync,
} from '@/lib/chart-sync'

const props = defineProps<{ candles: BrokerCandle[]; syncKey?: string }>()

const container = ref<HTMLDivElement | null>(null)
const legend = ref<BrokerCandle | null>(null)
const isDark = useDark()

const chart = shallowRef<IChartApi | null>(null)
const candleSeries = shallowRef<ISeriesApi<'Candlestick'> | null>(null)
const volumeSeries = shallowRef<ISeriesApi<'Histogram'> | null>(null)
let resizeObserver: ResizeObserver | null = null
const memberId = createChartSyncMemberId()
let leaveSync: (() => void) | null = null
let applyingRange = false

// NSE bar boundaries are exchange sessions; shifting the UTC axis by the IST offset keeps the
// displayed clock aligned with Asia/Kolkata without changing the underlying epoch.
const IST_OFFSET_SECONDS = 5 * 60 * 60 + 30 * 60

function toTime(iso: string): UTCTimestamp | null {
  const millis = Date.parse(iso)
  if (Number.isNaN(millis)) {
    return null
  }
  return (Math.floor(millis / 1000) + IST_OFFSET_SECONDS) as UTCTimestamp
}

function palette() {
  const styles = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) =>
    styles.getPropertyValue(name).trim() || fallback
  return {
    up: read('--chart-up', '#15803d'),
    down: read('--chart-down', '#b91c1c'),
    grid: read('--chart-grid', 'rgba(0,0,0,0.06)'),
    text: read('--chart-text', '#525252'),
  }
}

function candleData(): CandlestickData<UTCTimestamp>[] {
  const data: CandlestickData<UTCTimestamp>[] = []
  for (const candle of props.candles) {
    const time = toTime(candle.openTime)
    if (time === null) {
      continue
    }
    data.push({
      time,
      // Some broker series (e.g. Groww daily cash equities) omit open; fall back to close so the
      // bar still renders. The historical table shows the true (missing) value.
      open: candle.open ?? candle.close,
      high: candle.high,
      low: candle.low,
      close: candle.close,
    })
  }
  return data
}

function volumeData(): HistogramData<UTCTimestamp>[] {
  const colors = palette()
  const data: HistogramData<UTCTimestamp>[] = []
  for (const candle of props.candles) {
    const time = toTime(candle.openTime)
    if (time === null) {
      continue
    }
    const open = candle.open ?? candle.close
    data.push({
      time,
      value: candle.volume,
      color: candle.close >= open ? `${colors.up}55` : `${colors.down}55`,
    })
  }
  return data
}

function applyTheme() {
  const colors = palette()
  chart.value?.applyOptions({
    layout: {
      background: { type: ColorType.Solid, color: 'transparent' },
      textColor: colors.text,
    },
    grid: {
      vertLines: { color: colors.grid },
      horzLines: { color: colors.grid },
    },
  })
  candleSeries.value?.applyOptions({
    upColor: colors.up,
    downColor: colors.down,
    wickUpColor: colors.up,
    wickDownColor: colors.down,
  })
  volumeSeries.value?.setData(volumeData())
}

function updateData() {
  candleSeries.value?.setData(candleData())
  volumeSeries.value?.setData(volumeData())
  legend.value = props.candles.at(-1) ?? null
}

function handleCrosshair(param: MouseEventParams) {
  const time = (param.time as UTCTimestamp | undefined) ?? null
  if (time === null) {
    legend.value = props.candles.at(-1) ?? null
  } else {
    const match = props.candles.find(
      (candle) => toTime(candle.openTime) === time,
    )
    if (match) {
      legend.value = match
    }
  }
  if (props.syncKey) {
    broadcastCrosshair(props.syncKey, memberId, time)
  }
}

function broadcastRange() {
  const range = chart.value?.timeScale().getVisibleLogicalRange()
  if (props.syncKey && range) {
    broadcastLogicalRange(props.syncKey, memberId, range)
  }
}

function applyRange(range: LogicalRange) {
  applyingRange = true
  chart.value?.timeScale().setVisibleLogicalRange(range)
  applyingRange = false
}

function applyCrosshair(time: UTCTimestamp | null) {
  const instance = chart.value
  const series = candleSeries.value
  if (!instance || !series) {
    return
  }
  if (time === null) {
    instance.clearCrosshairPosition()
    return
  }
  const match = props.candles.find((candle) => toTime(candle.openTime) === time)
  if (match) {
    instance.setCrosshairPosition(match.close, time, series)
  } else {
    instance.clearCrosshairPosition()
  }
}

onMounted(() => {
  if (!container.value) {
    return
  }
  const colors = palette()
  const instance = createChart(container.value, {
    autoSize: true,
    layout: {
      background: { type: ColorType.Solid, color: 'transparent' },
      textColor: colors.text,
      fontSize: 11,
    },
    grid: {
      vertLines: { color: colors.grid },
      horzLines: { color: colors.grid },
    },
    rightPriceScale: { borderVisible: false },
    timeScale: {
      borderVisible: false,
      timeVisible: true,
      secondsVisible: false,
    },
    crosshair: { mode: CrosshairMode.Normal },
  })
  const candles = instance.addCandlestickSeries({
    upColor: colors.up,
    downColor: colors.down,
    wickUpColor: colors.up,
    wickDownColor: colors.down,
    borderVisible: false,
  })
  const volume = instance.addHistogramSeries({
    priceFormat: { type: 'volume' },
    priceScaleId: '',
  })
  volume.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } })

  chart.value = instance
  candleSeries.value = candles
  volumeSeries.value = volume

  instance.subscribeCrosshairMove(handleCrosshair)
  instance.timeScale().subscribeVisibleLogicalRangeChange((range) => {
    if (!range || applyingRange || !props.syncKey) {
      return
    }
    broadcastLogicalRange(props.syncKey, memberId, range)
  })
  if (props.syncKey) {
    leaveSync = joinChartSync(props.syncKey, memberId, {
      applyRange,
      applyCrosshair,
    })
  }
  updateData()
  instance.timeScale().fitContent()
  broadcastRange()

  resizeObserver = new ResizeObserver(() => instance.timeScale().fitContent())
  resizeObserver.observe(container.value)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  leaveSync?.()
  leaveSync = null
  chart.value?.remove()
  chart.value = null
  candleSeries.value = null
  volumeSeries.value = null
})

watch(
  () => props.candles,
  () => {
    updateData()
    if (props.syncKey) {
      chart.value?.timeScale().fitContent()
      broadcastRange()
    }
  },
)
watch(isDark, () => {
  applyTheme()
})
</script>

<template>
  <div class="relative h-[420px] w-full">
    <div ref="container" class="h-full w-full" />

    <div
      v-if="legend"
      class="pointer-events-none absolute left-2 top-2 flex flex-wrap gap-x-3 gap-y-0.5 rounded-md border bg-background/85 px-2.5 py-1.5 text-[11px] text-muted-foreground backdrop-blur-sm tabular-nums"
      aria-hidden="true"
    >
      <span>O {{ formatPrice(legend.open) }}</span>
      <span>H {{ formatPrice(legend.high) }}</span>
      <span>L {{ formatPrice(legend.low) }}</span>
      <span>C {{ formatPrice(legend.close) }}</span>
      <span>V {{ formatCompact(legend.volume) }}</span>
    </div>
  </div>
</template>
