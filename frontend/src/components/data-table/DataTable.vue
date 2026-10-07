<script setup lang="ts" generic="TData">
import { ref } from 'vue'
import {
  FlexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useVueTable,
  type ColumnDef,
  type ColumnFiltersState,
  type SortingState,
  type VisibilityState,
} from '@tanstack/vue-table'
import { ArrowUpDown } from '@lucide/vue'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const props = withDefaults(
  defineProps<{
    columns: ColumnDef<TData, unknown>[]
    data: TData[]
    getRowId?: (row: TData) => string
    initialSorting?: SortingState
    initialVisibility?: VisibilityState
    emptyMessage?: string
  }>(),
  {
    getRowId: undefined,
    initialSorting: () => [],
    initialVisibility: () => ({}),
    emptyMessage: 'No rows.',
  },
)

const emit = defineEmits<{ rowClick: [row: TData] }>()

const sorting = ref<SortingState>(props.initialSorting)
const columnVisibility = ref<VisibilityState>(props.initialVisibility)
const columnFilters = ref<ColumnFiltersState>([])

const table = useVueTable<TData>({
  get data() {
    return props.data
  },
  get columns() {
    return props.columns
  },
  getRowId: props.getRowId,
  state: {
    get sorting() {
      return sorting.value
    },
    get columnVisibility() {
      return columnVisibility.value
    },
    get columnFilters() {
      return columnFilters.value
    },
  },
  onSortingChange: (
    updater: SortingState | ((old: SortingState) => SortingState),
  ) => {
    sorting.value =
      typeof updater === 'function' ? updater(sorting.value) : updater
  },
  onColumnVisibilityChange: (
    updater: VisibilityState | ((old: VisibilityState) => VisibilityState),
  ) => {
    columnVisibility.value =
      typeof updater === 'function' ? updater(columnVisibility.value) : updater
  },
  onColumnFiltersChange: (
    updater:
      ColumnFiltersState | ((old: ColumnFiltersState) => ColumnFiltersState),
  ) => {
    columnFilters.value =
      typeof updater === 'function' ? updater(columnFilters.value) : updater
  },
  getCoreRowModel: getCoreRowModel(),
  getSortedRowModel: getSortedRowModel(),
  getFilteredRowModel: getFilteredRowModel(),
})

function toggleSort(column: {
  getCanSort: () => boolean
  getIsSorted: () => false | 'asc' | 'desc'
  toggleSorting: (desc?: boolean) => void
}) {
  if (!column.getCanSort()) {
    return
  }
  column.toggleSorting(column.getIsSorted() === 'asc')
}
</script>

<template>
  <div class="space-y-2">
    <div v-if="$slots.toolbar" class="flex items-center justify-between gap-2">
      <slot name="toolbar" :table="table" />
    </div>

    <div class="overflow-hidden rounded-md border" data-slot="table-container">
      <Table>
        <TableHeader>
          <TableRow
            v-for="headerGroup in table.getHeaderGroups()"
            :key="headerGroup.id"
          >
            <TableHead
              v-for="header in headerGroup.headers"
              :key="header.id"
              :class="
                header.column.getCanSort()
                  ? 'cursor-pointer select-none'
                  : undefined
              "
              @click="toggleSort(header.column)"
            >
              <span class="inline-flex items-center gap-1">
                <FlexRender
                  v-if="!header.isPlaceholder"
                  :render="header.column.columnDef.header"
                  :props="header.getContext()"
                />
                <ArrowUpDown
                  v-if="header.column.getCanSort()"
                  class="size-3 text-muted-foreground"
                  aria-hidden="true"
                />
              </span>
            </TableHead>
          </TableRow>
        </TableHeader>

        <TableBody>
          <template v-if="table.getRowModel().rows.length">
            <TableRow
              v-for="row in table.getRowModel().rows"
              :key="row.id"
              class="cursor-pointer"
              @click="emit('rowClick', row.original)"
            >
              <TableCell v-for="cell in row.getVisibleCells()" :key="cell.id">
                <FlexRender
                  :render="cell.column.columnDef.cell"
                  :props="cell.getContext()"
                />
              </TableCell>
            </TableRow>
          </template>
          <TableRow v-else>
            <TableCell
              :colspan="columns.length"
              class="h-24 text-center text-muted-foreground"
            >
              <slot name="empty">{{ emptyMessage }}</slot>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>
