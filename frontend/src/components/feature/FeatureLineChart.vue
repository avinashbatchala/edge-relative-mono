<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import {
  ColorType,
  CrosshairMode,
  LineStyle,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type UTCTimestamp,
} from 'lightweight-charts'
import { formatPrice } from '@/lib/format'

const props = withDefaults(
  defineProps<{
    title: string
    points: { time: number; value: number }[]
    color?: string
    baseline?: number | null
    height?: number
  }>(),
  { color: '#2563eb', baseline: null, height: 140 },
)

const container = ref<HTMLDivElement | null>(null)
const hover = ref<number | null>(null)
const chart = shallowRef<IChartApi | null>(null)
const series = shallowRef<ISeriesApi<'Line'> | null>(null)
let observer: ResizeObserver | null = null

function data(): LineData<UTCTimestamp>[] {
  return props.points.map((point) => ({
    time: point.time as UTCTimestamp,
    value: point.value,
  }))
}

function build() {
  if (!container.value) {
    return
  }
  chart.value = createChart(container.value, {
    height: props.height,
    layout: {
      background: { type: ColorType.Solid, color: 'transparent' },
      textColor: '#737373',
      fontSize: 10,
    },
    grid: { vertLines: { visible: false }, horzLines: { visible: false } },
    rightPriceScale: { borderVisible: false },
    timeScale: {
      borderVisible: false,
      timeVisible: true,
      secondsVisible: false,
    },
    crosshair: { mode: CrosshairMode.Normal },
    handleScroll: false,
    handleScale: false,
  })
  series.value = chart.value.addLineSeries({ color: props.color, lineWidth: 2 })
  if (props.baseline !== null) {
    series.value.createPriceLine({
      price: props.baseline,
      color: '#a3a3a3',
      lineWidth: 1,
      lineStyle: LineStyle.Dashed,
      axisLabelVisible: false,
      title: '',
    })
  }
  series.value.setData(data())
  chart.value.timeScale().fitContent()
  chart.value.subscribeCrosshairMove((param) => {
    const current = series.value
    if (!current || param.time === undefined) {
      hover.value = null
      return
    }
    const point = param.seriesData.get(current)
    hover.value =
      point && 'value' in point && typeof point.value === 'number'
        ? point.value
        : null
  })
}

onMounted(() => {
  build()
  if (typeof ResizeObserver !== 'undefined' && container.value) {
    observer = new ResizeObserver(() => {
      chart.value?.applyOptions({ width: container.value?.clientWidth })
    })
    observer.observe(container.value)
  }
})

onUnmounted(() => {
  observer?.disconnect()
  observer = null
  chart.value?.remove()
  chart.value = null
  series.value = null
})

watch(
  () => props.points,
  () => {
    series.value?.setData(data())
    chart.value?.timeScale().fitContent()
  },
  { deep: false },
)
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center justify-between text-xs">
      <span class="font-medium">{{ title }}</span>
      <span class="tabular-nums text-muted-foreground" aria-live="polite">
        {{ hover === null ? '—' : formatPrice(hover, 3) }}
      </span>
    </div>
    <div
      ref="container"
      class="w-full"
      role="img"
      :aria-label="`${title} line chart`"
      :style="{ height: `${height}px` }"
    />
  </div>
</template>
