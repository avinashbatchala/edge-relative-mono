<script setup lang="ts">
import { computed } from 'vue'
import type { FeatureDashboardRow } from '@/api/features'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { sectorSummary } from '@/lib/desk'

const props = defineProps<{ rows: FeatureDashboardRow[] }>()

const groups = computed(() => sectorSummary(props.rows))

function strengthTone(value: number | null): string {
  if (value === null) return 'text-muted-foreground'
  if (value > 0) return 'text-positive'
  if (value < 0) return 'text-negative'
  return 'text-muted-foreground'
}

function format(value: number | null): string {
  return value === null ? '—' : value.toFixed(2)
}
</script>

<template>
  <Card class="gap-0 py-0">
    <CardHeader class="px-5 py-4">
      <CardTitle class="text-base">Sector context</CardTitle>
      <CardDescription>
        Sector strength versus the broad market, grouped from point-in-time
        sector mappings. Derived — not an execution recommendation.
      </CardDescription>
    </CardHeader>
    <CardContent class="border-t p-5">
      <Table v-if="groups.length">
        <TableHeader>
          <TableRow class="hover:bg-transparent">
            <TableHead>Sector</TableHead>
            <TableHead class="text-right">Strength (RRS)</TableHead>
            <TableHead class="text-right">Members</TableHead>
            <TableHead class="text-right">Breadth</TableHead>
            <TableHead>Structure</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="group in groups" :key="group.code ?? 'unmapped'">
            <TableCell class="text-xs">
              <span class="font-medium">{{
                group.name ?? group.code ?? 'Unmapped'
              }}</span>
              <span
                v-if="group.code"
                class="ml-1 text-[10px] text-muted-foreground"
                >{{ group.code }}</span
              >
            </TableCell>
            <TableCell
              class="text-right text-xs tabular-nums"
              :class="strengthTone(group.sectorRrs)"
            >
              {{ format(group.sectorRrs) }}
            </TableCell>
            <TableCell class="text-right text-xs tabular-nums">{{
              group.count
            }}</TableCell>
            <TableCell class="text-right text-xs tabular-nums">
              <template v-if="group.rrsCount">
                {{ group.positives }}/{{ group.rrsCount }}
              </template>
              <template v-else>—</template>
            </TableCell>
            <TableCell class="text-xs">
              <Badge v-if="group.state" variant="outline" class="font-normal">{{
                group.state.replace(/_/g, ' ').toLowerCase()
              }}</Badge>
              <span v-else class="text-muted-foreground">—</span>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      <p v-else class="text-sm text-muted-foreground">
        No sector mappings resolved yet. Add sector-mapped instruments to the
        watchlist to see sector context.
      </p>
    </CardContent>
  </Card>
</template>
