import { render, screen } from '@testing-library/vue'
import { expect, test } from 'vitest'
import WhyPanel from './WhyPanel.vue'

test('renders sections, items and the empty case', () => {
  render(WhyPanel, {
    props: {
      sections: [
        {
          title: 'Why strategy likes it',
          items: [
            { label: 'RRS passed', tone: 'positive' },
            { label: 'RVOL confirmed' },
          ],
        },
        { title: 'Risks', items: [] },
      ],
      note: 'Deterministic reasons only.',
    },
  })

  expect(screen.getByText('Why strategy likes it')).toBeTruthy()
  expect(screen.getByText('RRS passed')).toBeTruthy()
  expect(screen.getByText('RVOL confirmed')).toBeTruthy()
  expect(screen.getByText('Nothing recorded.')).toBeTruthy()
  expect(screen.getByText('Deterministic reasons only.')).toBeTruthy()
})
