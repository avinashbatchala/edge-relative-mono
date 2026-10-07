/**
 * Pick the default expiry: the nearest date on or after `today`, falling back to the last
 * (latest) when every listed date is in the past. Dates are ISO calendar strings
 * (`YYYY-MM-DD`) so a lexicographic comparison is chronological.
 */
export function nearestExpiry(
  dates: string[],
  today: Date = new Date(),
): string {
  if (dates.length === 0) {
    return ''
  }
  const sorted = [...dates].sort()
  const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return sorted.find((date) => date >= todayIso) ?? sorted[sorted.length - 1]!
}
