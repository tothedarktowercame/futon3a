CREATE TABLE IF NOT EXISTS artifacts (
  artifact_id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  content TEXT NOT NULL,
  content_hash TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS entities (
  entity_id TEXT PRIMARY KEY,
  label TEXT NOT NULL,
  kind TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS aliases (
  alias_id TEXT PRIMARY KEY,
  entity_id TEXT NOT NULL,
  alias TEXT NOT NULL,
  weight REAL NOT NULL DEFAULT 1.0,
  created_at TEXT NOT NULL,
  FOREIGN KEY (entity_id) REFERENCES entities(entity_id)
);

CREATE TABLE IF NOT EXISTS mentions (
  mention_id TEXT PRIMARY KEY,
  artifact_id TEXT NOT NULL,
  entity_id TEXT,
  mention_text TEXT NOT NULL,
  start_offset INTEGER NOT NULL,
  end_offset INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (artifact_id) REFERENCES artifacts(artifact_id),
  FOREIGN KEY (entity_id) REFERENCES entities(entity_id)
);

CREATE TABLE IF NOT EXISTS arrows (
  arrow_id TEXT PRIMARY KEY,
  from_entity_id TEXT NOT NULL,
  to_entity_id TEXT NOT NULL,
  mode TEXT NOT NULL,
  payload_json TEXT,
  scope_tags TEXT,
  confidence REAL NOT NULL,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (from_entity_id) REFERENCES entities(entity_id),
  FOREIGN KEY (to_entity_id) REFERENCES entities(entity_id),
  CHECK (confidence >= 0.0 AND confidence <= 1.0),
  CHECK (status IN ('draft', 'active', 'retired'))
);

CREATE TABLE IF NOT EXISTS proposals (
  proposal_id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  target_id TEXT,
  status TEXT NOT NULL,
  score REAL NOT NULL,
  method TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  CHECK (status IN ('pending', 'accepted', 'rejected'))
);

CREATE TABLE IF NOT EXISTS promotions (
  promotion_id TEXT PRIMARY KEY,
  proposal_id TEXT NOT NULL,
  promoted_kind TEXT NOT NULL,
  target_id TEXT,
  decided_by TEXT NOT NULL,
  rationale TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (proposal_id) REFERENCES proposals(proposal_id)
);

CREATE TABLE IF NOT EXISTS promotion_log (
  promotion_log_id TEXT PRIMARY KEY,
  promotion_id TEXT NOT NULL,
  decision TEXT NOT NULL,
  actor TEXT NOT NULL,
  source TEXT NOT NULL,
  run_id TEXT NOT NULL,
  recorded_at TEXT NOT NULL,
  metadata_json TEXT,
  FOREIGN KEY (promotion_id) REFERENCES promotions(promotion_id),
  CHECK (decision IN ('accepted', 'rejected'))
);

CREATE TABLE IF NOT EXISTS bridge_triples (
  bridge_id TEXT PRIMARY KEY,
  subject_entity_id TEXT NOT NULL,
  predicate TEXT NOT NULL,
  object_entity_id TEXT NOT NULL,
  rationale TEXT NOT NULL,
  created_by TEXT NOT NULL,
  created_at TEXT NOT NULL,
  status TEXT NOT NULL,
  supersedes_bridge_id TEXT,
  FOREIGN KEY (subject_entity_id) REFERENCES entities(entity_id),
  FOREIGN KEY (object_entity_id) REFERENCES entities(entity_id),
  FOREIGN KEY (supersedes_bridge_id) REFERENCES bridge_triples(bridge_id),
  CHECK (status IN ('active', 'retired'))
);
