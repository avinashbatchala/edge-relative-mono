import { defineStore } from 'pinia'
import { useStorage } from '@vueuse/core'
import { computed } from 'vue'

export type Density = 'comfortable' | 'compact'

/** Persisted, per-browser operator preferences (single-user, localhost). */
export const usePreferencesStore = defineStore('preferences', () => {
  const density = useStorage<Density>('er.density', 'comfortable')

  const isCompact = computed(() => density.value === 'compact')

  function setDensity(value: Density) {
    density.value = value
  }

  function toggleDensity() {
    density.value = isCompact.value ? 'comfortable' : 'compact'
  }

  return { density, isCompact, setDensity, toggleDensity }
})
