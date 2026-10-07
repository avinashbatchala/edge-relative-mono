<script setup lang="ts">
import { Info } from '@lucide/vue'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

withDefaults(
  defineProps<{
    title: string
    description?: string
  }>(),
  { description: '' },
)
</script>

<template>
  <Alert>
    <Info aria-hidden="true" />
    <AlertTitle>{{ title }}</AlertTitle>
    <AlertDescription v-if="description">{{ description }}</AlertDescription>
  </Alert>
</template>
