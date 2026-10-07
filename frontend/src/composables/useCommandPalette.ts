import { ref } from 'vue'

const open = ref(false)

/** Process-wide command palette state (⌘K). */
export function useCommandPalette() {
  return {
    open,
    show: () => {
      open.value = true
    },
    hide: () => {
      open.value = false
    },
    toggle: () => {
      open.value = !open.value
    },
  }
}
