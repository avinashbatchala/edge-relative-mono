# Dataset Versioning and Reproducibility

- **Status:** implemented (Prompt 04). Dataset construction and analysis are not built yet.
- **Packages:** `research/src/edge_relative_research/datasets/` and the read-only
  `repositories/` catalog views.
- **Schema:** `research.dataset`, `research.dataset_version`,
  `research.dataset_version_input`, extended by
  `V022__dataset_version_reproducibility.sql`.
- **Companions:** `research-data-contracts.md`, `research-data-access.md`.

The exit criterion is that no later experiment can run against an unidentified mutable
folder of Parquet files. Every stored dataset version has an immutable catalog row and an
on-disk manifest whose content checksum and parent lineage are reproducible.

## 1. Recorded fields

A build records all required fields. Those already present in `research.dataset_version`
plus the V022 additions:

| Field | Where |
|---|---|
| dataset identity / type | `research.dataset` (`code`, `dataset_key`, `dataset_type`) |
| immutable version | `dataset_version.version` (unique per dataset) |
| feature schema version | `feature_schema_version_id` |
| outcome schema version | `outcome_schema_version_id` (V022) |
| point-in-time cutoff | `point_in_time_cutoff` |
| universe | `universe` (V022) |
| start / end timestamps | `start_timestamp`, `end_timestamp` (V022) |
| exact parent versions | `dataset_version_input` + manifest `parents` |
| storage URI/path | `storage_uri` |
| partition manifest | `partition_manifest_uri` |
| row count | `row_count` |
| checksum | `checksum` (content checksum) |
| code version | `code_version` |
| build parameters | `build_parameters` JSONB (V022) |
| creation/commit timestamps | `created_at`, `committed_at` |
| status | `BUILDING` / `COMMITTED` / `FAILED` / `RETIRED` |
| failure detail | `failure` JSONB (V022) |

V022 also redefines `research.protect_dataset_version()` so the new columns are
content-immutable once the row is `COMMITTED`, matching the V007 behaviour for the
original columns.

## 2. Architecture

```text
DatasetBuildSpec
      │
      ▼
DatasetBuilder ── begin ──> DatasetCatalog (research.dataset_version = BUILDING)
      │                     │
      │ add_partition ─────►│ LocalDatasetStorage  (Parquet + content/file checksums)
      │                     │
      ├── commit ──────────►│ manifest.json (deterministic checksum) + DB COMMITTED
      └── fail ────────────►│ delete version dir + DB FAILED
```

- `datasets/checksums.py` — canonical JSON (sorted keys, explicit encodings) and SHA-256.
- `datasets/manifest.py` — `DatasetManifest`, `ParentRef`, `PartitionEntry`; content
  checksum and deterministic `dataset_version_key` (`uuid5` over code/version/checksum).
- `datasets/storage.py` — `DatasetStorage` protocol and `LocalDatasetStorage`
  (one Parquet file per partition + `manifest.json`; no cloud coupling).
- `datasets/catalog.py` — parameterized write adapter over the research catalog.
- `datasets/builder.py` — the build lifecycle and context manager.

## 3. Determinism and checksums

The manifest content checksum is computed over the **logical** build descriptor only:
dataset identity/type/version, schema versions, cutoff, universe, time range, parents
(with their checksums and keys), partitions (key, row count, content checksum), total row
count, code version and build parameters. Deliberately excluded: wall-clock `created_at`,
the absolute `storage_uri`, per-file byte checksums and byte sizes.

Consequences:

- The same inputs, code and parameters produce the same checksum and
  `dataset_version_key`; `test_manifest_checksum_and_key_are_deterministic` and
  `test_created_at_and_storage_uri_do_not_affect_checksum` prove it.
- Per-partition `content_checksum` hashes canonical row content; `file_checksum` hashes
  the Parquet bytes for corruption detection.
- `LocalDatasetStorage.verify` recomputes both the manifest checksum and each file
  checksum; `test_corrupted_file_checksum_is_detected` flips file bytes and expects a
  `ChecksumMismatchError`, and `test_tampered_manifest_is_detected` edits `manifest.json`.

## 4. Parent lineage

`dataset_version_input` records exact parent versions. The builder validates before
allocating a version:

- every parent must exist and be `COMMITTED`
  (`test_uncommitted_parent_is_rejected`);
- a parent's `point_in_time_cutoff` may not be later than the child's
  (`test_future_parent_is_rejected`);
- changed parent content produces different child lineage
  (`test_changed_parent_changes_derived_lineage`).

The database trigger `research.validate_dataset_version_input()` (V007) additionally
prevents cycles.

## 5. Commit, failure and immutability

- **Commit**: the builder writes `manifest.json`, then transitions the row to
  `COMMITTED` with checksum, row count, manifest URI and `committed_at`. The DB check
  `ck_dataset_version_commit_shape` requires checksum + `committed_at`.
- **No overwrite**: an existing `COMMITTED`/`RETIRED` version or an existing storage
  directory raises `DatasetAlreadyCommittedError`; direct SQL `UPDATE` of committed
  content raises the database immutability error
  (`test_committed_dataset_cannot_be_overwritten`).
- **Failure**: `fail()` deletes the version directory and marks the row `FAILED` with a
  reason, so a failed build can never appear committed
  (`test_failed_build_is_not_committed`). The context manager fails the build when the
  block raises (`test_context_manager_fails_on_error`).
- **Retire**: `COMMITTED → RETIRED` is the only permitted post-commit transition
  (`test_committed_version_can_be_retired`).

## 6. Local Parquet storage

`LocalDatasetStorage(root)` lays out:

```text
<root>/<dataset_code>/v<version>/<partition_key>.parquet
<root>/<dataset_code>/v<version>/manifest.json
```

`storage_uri` is the version directory URI; `partition_manifest_uri` is the manifest
path. `write_partition` writes a Polars Parquet file and returns a `PartitionEntry`;
`read_partition` reads it back. The same interface can be implemented by an object-store
backend later without changing the builder.

## 7. Tests

`tests/test_dataset_manifest.py` (unit):

- deterministic checksum/key; created_at and storage URI excluded;
- partition content and build parameters change the checksum; file byte checksum does not;
- canonical JSON ordering; local storage round trip and empty partition;
- corrupted file, tampered manifest and missing file detection.

`tests/test_dataset_versioning.py` (integration PostgreSQL + migrations):

- build/commit records the manifest and catalog fields;
- committed version cannot be overwritten (builder and direct SQL);
- failed build is not committed and cleans storage;
- context manager fails on error;
- future/uncommitted parent rejected;
- changed parent changes derived lineage;
- commit → retire; multiple versions coexist.

## 8. Limitations and next steps

- The storage backend is local filesystem only; object storage remains deferred until an
  ADR-001/ADR-002 trigger is met.
- Retention/compaction of old versions is not implemented; RETIRED versions keep their
  files.
- `research.dataset_version` rows created before V022 have empty `build_parameters` and
  NULL range/universe; they are still readable.
- No analytical dataset builder or feature-snapshot reader exists yet; this stage only
  guarantees that any dataset produced from here can be identified, checksummed and
  reproduced.
