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
  type MouseEventParams,
  type UTCTimestamp,
} from 'lightweight-charts'
import type { BrokerCandle } from '@/api/types'
import { formatCompact, formatPrice } from '@/lib/format'

const props = defineProps<{ candles: BrokerCandle[] }>()

const container = ref<HTMLDivElement | null>(null)
const legend = ref<BrokerCandle | null>(null)
const isDark = useDark()

const chart = shallowRef<IChartApi | null>(null)
const candleSeries = shallowRef<ISeriesApi<'Candlestick'> | null>(null)
const volumeSeries = shallowRef<ISeriesApi<'Histogram'> | null>(null)
let resizeObserver: ResizeObserver | null = null

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
      open: candle.open,
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
    data.push({
      time,
      value: candle.volume,
      color:
        candle.close >= candle.open ? `${colors.up}55` : `${colors.down}55`,
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
  if (!param.time) {
    legend.value = props.candles.at(-1) ?? null
    return
  }
  const time = param.time as number
  const match = props.candles.find((candle) => {
    const converted = toTime(candle.openTime)
    return converted !== null && converted === time
  })
  if (match) {
    legend.value = match
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
  updateData()
  instance.timeScale().fitContent()

  resizeObserver = new ResizeObserver(() => instance.timeScale().fitContent())
  resizeObserver.observe(container.value)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart.value?.remove()
  chart.value = null
  candleSeries.value = null
  volumeSeries.value = null
})

watch(() => props.candles, updateData)
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
