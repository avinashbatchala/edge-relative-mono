import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getFeatureDiagnostics, featureKeys } from '@/api/features'
import { getHealth, systemKeys } from '@/api/system'
import { deriveReadiness } from '@/lib/system-readiness'
import { useFeatureStreamStore } from '@/stores/feature-stream'

/**
 * Aggregates the available readiness signals into a single derived state for the header. Derived and
 * non-authoritative: the backend does not yet expose an authoritative readiness/mode endpoint.
 */
export function useSystemReadiness() {
  const health = useQuery({
    queryKey: systemKeys.health(),
    queryFn: ({ signal }) => getHealth(signal),
    refetchInterval: 30000,
  })
  const diagnostics = useQuery({
    queryKey: featureKeys.diagnostics(),
    queryFn: ({ signal }) => getFeatureDiagnostics(signal),
    refetchInterval: 30000,
  })
  const stream = useFeatureStreamStore()

  return computed(() =>
    deriveReadiness({
      health: health.data.value?.status ?? null,
      stream: stream.connection,
      freshness: diagnostics.data.value?.freshness?.state ?? null,
      session: diagnostics.data.value?.freshness?.sessionContext ?? null,
    }),
  )
}
