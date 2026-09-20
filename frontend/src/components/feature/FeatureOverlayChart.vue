<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import {
  ColorType,
  CrosshairMode,
  LineStyle,
  createChart,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type LogicalRange,
  type UTCTimestamp,
  type WhitespaceData,
} from 'lightweight-charts'
import { formatPrice } from '@/lib/format'
import {
  broadcastCrosshair,
  broadcastLogicalRange,
  createChartSyncMemberId,
  joinChartSync,
} from '@/lib/chart-sync'

interface OverlayPoint {
  time: number
  value: number | null
}

interface OverlaySeries {
  key: string
  title: string
  color: string
  baseline: number | null
  points: OverlayPoint[]
}

const props = withDefaults(
  defineProps<{ series: OverlaySeries[]; height?: number; syncKey?: string }>(),
  { height: 260, syncKey: '' },
)

// Same IST display shift as the price chart so crosshair times line up across synced charts.
const IST_OFFSET_SECONDS = 5 * 60 * 60 + 30 * 60

const container = ref<HTMLDivElement | null>(null)
const chart = shallowRef<IChartApi | null>(null)
const lines = shallowRef<Record<string, ISeriesApi<'Line'>>>({})
const timeMaps = shallowRef<Record<string, Map<number, number>>>({})
const shown = ref<Record<string, boolean>>(
  Object.fromEntries(props.series.map((item) => [item.key, true])),
)
const hover = ref<Record<string, number | null>>({})
const memberId = createChartSyncMemberId()
let observer: ResizeObserver | null = null
let leaveSync: (() => void) | null = null
let applyingRange = false

function chartTime(seconds: number): UTCTimestamp {
  return (seconds + IST_OFFSET_SECONDS) as UTCTimestamp
}

function dataFor(
  points: OverlayPoint[],
): (LineData<UTCTimestamp> | WhitespaceData<UTCTimestamp>)[] {
  return points.map((point) =>
    point.value === null
      ? { time: chartTime(point.time) }
      : { time: chartTime(point.time), value: point.value },
  )
}

function valueMap(points: OverlayPoint[]): Map<number, number> {
  const map = new Map<number, number>()
  for (const point of points) {
    if (point.value !== null) {
      map.set(chartTime(point.time), point.value)
    }
  }
  return map
}

/** Each series gets its own invisible price scale positioned in a non-overlapping band, so
 * different units share one time axis and crosshair without fighting over the y-range. */
function applyBands() {
  const visible = props.series.filter((item) => shown.value[item.key])
  const count = Math.max(visible.length, 1)
  visible.forEach((item, index) => {
    const line = lines.value[item.key]
    if (!line) {
      return
    }
    line.priceScale().applyOptions({
      scaleMargins: {
        top: index / count + 0.04,
        bottom: (count - 1 - index) / count + 0.04,
      },
    })
  })
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
    leftPriceScale: { visible: false },
    rightPriceScale: { visible: false },
    timeScale: {
      borderVisible: false,
      timeVisible: true,
      secondsVisible: false,
    },
    crosshair: { mode: CrosshairMode.Normal },
    handleScroll: false,
    handleScale: false,
  })
  const map: Record<string, ISeriesApi<'Line'>> = {}
  const maps: Record<string, Map<number, number>> = {}
  for (const item of props.series) {
    const line = chart.value.addLineSeries({
      color: item.color,
      lineWidth: 2,
      priceScaleId: item.key,
      priceLineVisible: false,
      lastValueVisible: false,
    })
    if (item.baseline !== null) {
      line.createPriceLine({
        price: item.baseline,
        color: '#a3a3a3',
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: false,
        title: '',
      })
    }
    line.setData(dataFor(item.points))
    map[item.key] = line
    maps[item.key] = valueMap(item.points)
  }
  lines.value = map
  timeMaps.value = maps
  applyBands()
  chart.value.timeScale().fitContent()
  chart.value.timeScale().subscribeVisibleLogicalRangeChange((range) => {
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
  chart.value.subscribeCrosshairMove((param) => {
    const next: Record<string, number | null> = {}
    for (const item of props.series) {
      const line = map[item.key]
      if (!line || param.time === undefined) {
        next[item.key] = null
        continue
      }
      const point = param.seriesData.get(line)
      next[item.key] =
        point && 'value' in point && typeof point.value === 'number'
          ? point.value
          : null
    }
    hover.value = next
    if (props.syncKey) {
      broadcastCrosshair(
        props.syncKey,
        memberId,
        (param.time as UTCTimestamp | undefined) ?? null,
      )
    }
  })
  broadcastRange()
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
  if (!instance) {
    return
  }
  if (time === null) {
    instance.clearCrosshairPosition()
    return
  }
  for (const item of props.series) {
    if (!shown.value[item.key]) {
      continue
    }
    const line = lines.value[item.key]
    const value = timeMaps.value[item.key]?.get(time)
    if (line && value !== undefined) {
      instance.setCrosshairPosition(value, time, line)
      return
    }
  }
  instance.clearCrosshairPosition()
}

function toggle(key: string) {
  shown.value = { ...shown.value, [key]: !shown.value[key] }
  lines.value[key]?.applyOptions({ visible: shown.value[key] })
  applyBands()
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
  leaveSync?.()
  leaveSync = null
  chart.value?.remove()
  chart.value = null
  lines.value = {}
  timeMaps.value = {}
})

watch(
  () => props.series,
  () => {
    const maps: Record<string, Map<number, number>> = {}
    for (const item of props.series) {
      lines.value[item.key]?.setData(dataFor(item.points))
      maps[item.key] = valueMap(item.points)
    }
    timeMaps.value = maps
    applyBands()
    chart.value?.timeScale().fitContent()
    broadcastRange()
  },
  { deep: false },
)

const legend = computed(() =>
  props.series.map((item) => {
    const last = item.points.length
      ? [...item.points].reverse().find((point) => point.value !== null)?.value
      : null
    return {
      ...item,
      shown: shown.value[item.key] ?? true,
      value: hover.value[item.key] ?? last ?? null,
    }
  }),
)

function decimals(key: string): number {
  return key === 'RVE' ? 3 : 2
}
</script>

<template>
  <div class="space-y-2">
    <div
      class="flex flex-wrap items-center gap-1.5"
      role="group"
      aria-label="Feature overlays"
    >
      <button
        v-for="item in legend"
        :key="item.key"
        type="button"
        class="inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        :class="item.shown ? 'bg-muted/50' : 'opacity-50'"
        :aria-pressed="item.shown"
        :aria-label="`${item.title} overlay ${item.shown ? 'on' : 'off'}`"
        @click="toggle(item.key)"
      >
        <span
          class="size-2 rounded-full"
          :style="{ backgroundColor: item.color }"
          aria-hidden="true"
        />
        <span class="font-medium">{{ item.title }}</span>
        <span class="tabular-nums text-muted-foreground">
          {{
            item.value === null
              ? '—'
              : formatPrice(item.value, decimals(item.key))
          }}
        </span>
      </button>
    </div>
    <div
      ref="container"
      class="w-full"
      role="img"
      aria-label="RRS, RVOL and RVE overlaid on one time axis"
      :style="{ height: `${height}px` }"
    />
    <p class="text-[11px] text-muted-foreground">
      RRS, RVOL and RVE share one time axis and crosshair; each is drawn on its
      own scale. Click a legend entry to show or hide a series.
    </p>
  </div>
</template>
