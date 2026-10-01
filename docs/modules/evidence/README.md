# Evidence Management module

Owns staged upload sessions, evidence bundles, artifact metadata, verification, supersede, custody access, legal hold and retention.

Public contracts: `EvidenceManagementApi`, `EvidenceStoragePort`, `EvidenceMalwareScanner`.

Artifact bytes are never stored in PostgreSQL.
