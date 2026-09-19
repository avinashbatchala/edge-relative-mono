<script setup lang="ts">
import { computed } from 'vue'
import { AlertCircle, RotateCcw } from '@lucide/vue'
import { ApiError } from '@/api/http'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  title: string
  error: unknown
}>()

const emit = defineEmits<{ retry: [] }>()

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isRateLimited) {
      return error.retryAfterSeconds
        ? `Rate limited. Retry available in ${error.retryAfterSeconds}s.`
        : 'Rate limited by the broker. Try again shortly.'
    }
    if (error.isAuthFailure) {
      return 'Broker authentication failed. Check the configured credentials.'
    }
    if (error.isBrokerUnavailable) {
      return 'Market data source is temporarily unavailable.'
    }
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Request failed.'
}

const message = computed(() => describe(props.error))
</script>

<template>
  <Alert variant="destructive">
    <AlertCircle class="size-4" aria-hidden="true" />
    <AlertTitle>{{ title }}</AlertTitle>
    <AlertDescription class="flex flex-wrap items-center justify-between gap-3">
      <span>{{ message }}</span>
      <Button variant="outline" size="sm" @click="emit('retry')">
        <RotateCcw class="size-3.5" aria-hidden="true" />
        Retry
      </Button>
    </AlertDescription>
  </Alert>
</template>
