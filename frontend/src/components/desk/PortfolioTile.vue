<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { getPositions, portfolioKeys } from '@/api/portfolio'

const positionsQuery = useQuery(() => ({
  queryKey: portfolioKeys.positions(),
  queryFn: ({ signal }) => getPositions(undefined, signal),
  staleTime: 20_000,
  retry: 1,
}))

const positionCount = computed(() => positionsQuery.data.value?.length ?? null)
</script>

<template>
  <Card>
    <CardHeader class="pb-3">
      <CardDescription>Portfolio</CardDescription>
      <CardTitle class="text-2xl">
        <template v-if="positionsQuery.isPending.value">…</template>
        <template v-else-if="positionCount === null">Read-only</template>
        <template v-else>{{ positionCount }} positions</template>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-3 text-xs text-muted-foreground">
      <p>Read-only account state. Execution stays disabled.</p>
      <Button as-child variant="outline" size="sm">
        <RouterLink to="/trades">Open trades</RouterLink>
      </Button>
    </CardContent>
  </Card>
</template>
