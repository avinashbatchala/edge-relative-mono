<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ExternalLink } from '@lucide/vue'
import type { FeatureDashboardRow } from '@/api/features'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import WhyPanel from '@/components/common/WhyPanel.vue'
import { formatIstDateTime } from '@/lib/format'
import { featureWhySections } from '@/lib/scanner-why'

const props = defineProps<{
  row: FeatureDashboardRow | null
  open: boolean
}>()

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const router = useRouter()

const sections = computed(() =>
  props.row ? featureWhySections(props.row) : [],
)

function openInChart() {
  if (!props.row) {
    return
  }
  void router.push({
    name: 'market-ticker',
    params: { symbol: props.row.symbol },
  })
}
</script>

<template>
  <Sheet
    :open="open"
    @update:open="(value: boolean) => emit('update:open', value)"
  >
    <SheetContent class="w-full overflow-y-auto sm:max-w-xl">
      <SheetHeader>
        <SheetTitle>Why {{ row?.symbol }} looks like this</SheetTitle>
        <SheetDescription>
          {{ row?.displayName ?? row?.symbol }} · {{ row?.timeframe }} ·
          observation {{ formatIstDateTime(row?.observationTime) }} IST
        </SheetDescription>
      </SheetHeader>

      <div v-if="row" class="mt-4 space-y-5">
        <WhyPanel
          :sections="sections"
          note="Deterministic measurements only. This is context, not a qualified setup or a trade instruction."
        />
        <Button size="sm" variant="outline" @click="openInChart">
          <ExternalLink class="size-3.5" aria-hidden="true" />
          Open in Chart
        </Button>
      </div>
    </SheetContent>
  </Sheet>
</template>
