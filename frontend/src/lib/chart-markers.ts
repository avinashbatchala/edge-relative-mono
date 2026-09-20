/** Marker rendered on a price chart at an ISO timestamp (entry/exit annotations). */
export interface ChartMarker {
  time: string
  position: 'aboveBar' | 'belowBar' | 'inBar'
  color: string
  shape: 'arrowUp' | 'arrowDown' | 'circle' | 'square'
  text?: string
}
