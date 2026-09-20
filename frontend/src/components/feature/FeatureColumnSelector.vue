<script setup lang="ts">
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

const props = defineProps<{ columns: { id: string; label: string }[] }>()
const model = defineModel<Record<string, boolean>>({ required: true })

function setVisible(id: string, value: boolean) {
  model.value = { ...model.value, [id]: value }
}
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="outline" size="sm" class="h-9">Columns</Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="start" class="max-h-80 w-52 overflow-auto">
      <DropdownMenuLabel>Visible columns</DropdownMenuLabel>
      <DropdownMenuSeparator />
      <DropdownMenuCheckboxItem
        v-for="column in props.columns"
        :key="column.id"
        :model-value="model[column.id]"
        @update:model-value="(value: boolean) => setVisible(column.id, value)"
      >
        {{ column.label }}
      </DropdownMenuCheckboxItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
