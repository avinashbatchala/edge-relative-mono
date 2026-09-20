import { describe, expect, test } from 'vitest'
import {
  availabilityLabel,
  directionMetaFromState,
  directionMetaOf,
  directionOf,
  featureStateMeta,
  featureStateRank,
  featureVersionTitle,
  formatFeatureVersion,
  isStale,
  isTrustworthy,
  metricDecimals,
  metricLabel,
  presentationState,
  qualityRank,
  rveMeta,
  rveStateOf,
  structureMeta,
} from './feature-presentation'

describe('feature presentation', () => {
  test('maps feature states to labels and distinct tones', () => {
    expect(featureStateMeta('HEALTHY').label).toBe('Healthy')
    expect(featureStateMeta('STALE').label).toBe('Stale')
    expect(featureStateMeta('UNAVAILABLE').label).toBe('Unavailable')
    expect(featureStateMeta('HEALTHY').tone).not.toBe(
      featureStateMeta('STALE').tone,
    )
    expect(featureStateMeta('WAT').label).toBe('WAT')
    expect(featureStateRank('HEALTHY')).toBeLessThan(
      featureStateRank('UNAVAILABLE'),
    )
  })

  test('distinguishes trustworthy from degraded and stale quality', () => {
    expect(isTrustworthy('GOOD', 'VALID')).toBe(true)
    expect(isTrustworthy('GOOD', 'STALE')).toBe(false)
    expect(isTrustworthy('INCOMPLETE', 'VALID')).toBe(false)
    expect(isStale('GOOD', 'STALE')).toBe(true)
    expect(isStale('STALE', 'VALID')).toBe(true)
    expect(isStale('GOOD', 'VALID')).toBe(false)
    expect(qualityRank('GOOD')).toBeLessThan(qualityRank('UNAVAILABLE'))
  })

  test('composes quality and availability into one presented state', () => {
    expect(presentationState('GOOD', 'VALID')).toBe('HEALTHY')
    expect(presentationState('GOOD', 'STALE')).toBe('STALE')
    expect(presentationState('INCOMPLETE', 'VALID')).toBe('DEGRADED')
    expect(presentationState('GOOD', 'WARMING_UP')).toBe('WARMING_UP')
    expect(presentationState('UNAVAILABLE', 'MISSING_INPUT')).toBe(
      'UNAVAILABLE',
    )
    expect(presentationState('GOOD', 'INVALID')).toBe('INVALID')
    expect(availabilityLabel('INSUFFICIENT_HISTORY')).toBe(
      'Insufficient history',
    )
    expect(availabilityLabel('VALID')).toBe('Available')
  })

  test('directions are explicit rather than colour-only', () => {
    expect(directionOf(1)).toBe('POSITIVE')
    expect(directionOf(-1)).toBe('NEGATIVE')
    expect(directionOf(0)).toBe('NEUTRAL')
    expect(directionOf(null)).toBe('NEUTRAL')
    expect(directionMetaOf(1).glyph).toBe('▲')
    expect(directionMetaFromState('NEGATIVE')?.label).toBe('Negative')
    expect(directionMetaFromState(null)).toBeNull()
  })

  test('structures and RVE have human labels', () => {
    expect(structureMeta('BULL_STRUCTURE').label).toBe('Bull')
    expect(structureMeta('BEAR_STRUCTURE').label).toBe('Bear')
    expect(structureMeta(null).label).toBe('—')
    expect(rveStateOf(0.5)).toBe('EXPANDING')
    expect(rveStateOf(-0.5)).toBe('CONTRACTING')
    expect(rveStateOf(0)).toBe('STABLE')
    expect(rveStateOf(null)).toBe('UNAVAILABLE')
    expect(rveMeta(0.5).label).toBe('Expanding')
  })

  test('metric precision is stable and explicit', () => {
    expect(metricDecimals('RRS_RAW')).toBe(2)
    expect(metricDecimals('RVE')).toBe(3)
    expect(metricDecimals('UNKNOWN_METRIC', 4)).toBe(4)
  })

  test('metrics and feature versions are shown as human labels', () => {
    expect(metricLabel('RRS_RAW')).toBe('Relative strength')
    expect(metricLabel('RVOL_INTERVAL')).toBe('Relative volume')
    expect(metricLabel('SOME_NEW_METRIC')).toBe('Some new metric')
    expect(formatFeatureVersion('RRS_V1@ab12cd')).toBe('v1')
    expect(formatFeatureVersion('RVOL_V2@ff')).toBe('v2')
    expect(formatFeatureVersion(null)).toBe('—')
    expect(featureVersionTitle('RRS_V1@ab12cd')).toContain('RRS_V1@ab12cd')
  })
})
