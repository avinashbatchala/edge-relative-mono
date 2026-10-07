import { fireEvent, render, screen } from '@testing-library/vue'
import type { ColumnDef } from '@tanstack/vue-table'
import { expect, test, vi } from 'vitest'
import type { DefineComponent } from 'vue'
import DataTable from './DataTable.vue'

interface Row {
  symbol: string
  change: number
}

const columns: ColumnDef<Row, unknown>[] = [
  { accessorKey: 'symbol', header: 'Symbol' },
  { accessorKey: 'change', header: 'Change' },
]

const data: Row[] = [
  { symbol: 'RELIANCE', change: 1.2 },
  { symbol: 'TCS', change: -0.5 },
]

/** The SFC is generic; give the test a concrete, typed handle for `render`. */
const TypedDataTable = DataTable as unknown as DefineComponent<{
  columns: ColumnDef<Row, unknown>[]
  data: Row[]
  emptyMessage?: string
  onRowClick?: (row: Row) => void
}>

test('renders rows and emits a row click', async () => {
  const onRowClick = vi.fn()
  render(TypedDataTable, {
    props: { columns, data, onRowClick },
  })

  expect(screen.getByText('RELIANCE')).toBeTruthy()
  expect(screen.getByText('TCS')).toBeTruthy()

  await fireEvent.click(screen.getByText('RELIANCE'))
  expect(onRowClick).toHaveBeenCalledTimes(1)
  expect(onRowClick.mock.calls[0]?.[0]).toMatchObject({ symbol: 'RELIANCE' })
})

test('renders the empty message when there are no rows', () => {
  render(TypedDataTable, {
    props: { columns, data: [], emptyMessage: 'Nothing here' },
  })

  expect(screen.getByText('Nothing here')).toBeTruthy()
})
