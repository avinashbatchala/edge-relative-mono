import { fireEvent, render, screen } from '@testing-library/vue'
import { expect, test, vi } from 'vitest'
import SegmentedTabs from './SegmentedTabs.vue'

test('renders tabs, marks the active one, and emits updates', async () => {
  const onUpdate = vi.fn()
  render(SegmentedTabs, {
    props: {
      modelValue: 'a',
      tabs: [
        { value: 'a', label: 'Alpha' },
        { value: 'b', label: 'Beta' },
      ],
      'onUpdate:modelValue': onUpdate,
    },
  })

  expect(
    screen.getByRole('tab', { name: 'Alpha' }).getAttribute('aria-selected'),
  ).toBe('true')
  expect(
    screen.getByRole('tab', { name: 'Beta' }).getAttribute('aria-selected'),
  ).toBe('false')

  await fireEvent.click(screen.getByRole('tab', { name: 'Beta' }))

  expect(onUpdate).toHaveBeenCalledWith('b')
})
