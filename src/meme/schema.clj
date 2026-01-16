(ns meme.schema
  "SQLite schema for the meme layer.

   Tri-store model:
   - Facts live in XTDB (futon1) - authoritative, curated
   - Memes live here (SQLite) - inspectable, grounded proposals
   - Notions live in ANN/HNSW - fast fuzzy recall only

   Core invariants:
   - Similarity results never write to facts directly; they seed proposals
   - Promotion to facts is explicit, not a side effect of search
   - Every proposal is attributable to a method and evidence payload"
  (:require [next.jdbc :as jdbc]))

(def ^:private tables
  "Table creation DDL in dependency order."
  [;; Artifacts: raw inputs (documents, logs, code, MMCA runs)
   "CREATE TABLE IF NOT EXISTS artifacts (
      id TEXT PRIMARY KEY,
      hash TEXT NOT NULL,
      kind TEXT NOT NULL,
      source TEXT,
      content TEXT,
      metadata TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_artifacts_hash ON artifacts(hash)"
   "CREATE INDEX IF NOT EXISTS idx_artifacts_kind ON artifacts(kind)"

   ;; Entities: canonical nodes the system reasons about
   "CREATE TABLE IF NOT EXISTS entities (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      kind TEXT,
      description TEXT,
      metadata TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_entities_name ON entities(name)"
   "CREATE INDEX IF NOT EXISTS idx_entities_kind ON entities(kind)"

   ;; Aliases: variant surface forms pointing to entities
   "CREATE TABLE IF NOT EXISTS aliases (
      id TEXT PRIMARY KEY,
      entity_id TEXT NOT NULL REFERENCES entities(id),
      surface_form TEXT NOT NULL,
      source TEXT,
      confidence REAL DEFAULT 1.0,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      UNIQUE(entity_id, surface_form)
    )"

   "CREATE INDEX IF NOT EXISTS idx_aliases_surface ON aliases(surface_form)"
   "CREATE INDEX IF NOT EXISTS idx_aliases_entity ON aliases(entity_id)"

   ;; Mentions: span-level extractions within artifacts
   "CREATE TABLE IF NOT EXISTS mentions (
      id TEXT PRIMARY KEY,
      artifact_id TEXT NOT NULL REFERENCES artifacts(id),
      entity_id TEXT REFERENCES entities(id),
      alias_id TEXT REFERENCES aliases(id),
      span_start INTEGER,
      span_end INTEGER,
      surface_text TEXT,
      confidence REAL DEFAULT 1.0,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_mentions_artifact ON mentions(artifact_id)"
   "CREATE INDEX IF NOT EXISTS idx_mentions_entity ON mentions(entity_id)"

   ;; Arrows: typed Kolmogorov arrows between entities
   ;; mode: translation, abstraction, metonymy, specialization, etc.
   ;; status: draft, active, retired
   "CREATE TABLE IF NOT EXISTS arrows (
      id TEXT PRIMARY KEY,
      source_id TEXT NOT NULL REFERENCES entities(id),
      target_id TEXT NOT NULL REFERENCES entities(id),
      mode TEXT NOT NULL,
      payload TEXT,
      scope_tags TEXT,
      confidence REAL DEFAULT 0.5,
      status TEXT NOT NULL DEFAULT 'draft',
      rationale TEXT,
      created_by TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_arrows_source ON arrows(source_id)"
   "CREATE INDEX IF NOT EXISTS idx_arrows_target ON arrows(target_id)"
   "CREATE INDEX IF NOT EXISTS idx_arrows_mode ON arrows(mode)"
   "CREATE INDEX IF NOT EXISTS idx_arrows_status ON arrows(status)"

   ;; Proposals: fuzzy outputs recorded with evidence
   ;; kind: arrow, entity, alias, pattern, sigil-sequence
   ;; status: pending, accepted, rejected, superseded
   "CREATE TABLE IF NOT EXISTS proposals (
      id TEXT PRIMARY KEY,
      kind TEXT NOT NULL,
      target_id TEXT,
      status TEXT NOT NULL DEFAULT 'pending',
      score REAL,
      method TEXT NOT NULL,
      evidence TEXT NOT NULL,
      metadata TEXT,
      created_by TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_proposals_kind ON proposals(kind)"
   "CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status)"
   "CREATE INDEX IF NOT EXISTS idx_proposals_method ON proposals(method)"

   ;; Promotions: explicit decisions to promote proposals
   "CREATE TABLE IF NOT EXISTS promotions (
      id TEXT PRIMARY KEY,
      proposal_id TEXT NOT NULL REFERENCES proposals(id),
      promoted_kind TEXT NOT NULL,
      target_id TEXT,
      decided_by TEXT NOT NULL,
      rationale TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_promotions_proposal ON promotions(proposal_id)"

   ;; Bridges: explicit warrants for sense-shift hops
   "CREATE TABLE IF NOT EXISTS bridges (
      id TEXT PRIMARY KEY,
      subject_id TEXT NOT NULL REFERENCES entities(id),
      predicate TEXT NOT NULL,
      object_id TEXT NOT NULL REFERENCES entities(id),
      rationale TEXT,
      status TEXT NOT NULL DEFAULT 'active',
      created_by TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    )"

   "CREATE INDEX IF NOT EXISTS idx_bridges_subject ON bridges(subject_id)"
   "CREATE INDEX IF NOT EXISTS idx_bridges_object ON bridges(object_id)"
   "CREATE INDEX IF NOT EXISTS idx_bridges_predicate ON bridges(predicate)"])

(defn init-db!
  "Initialize the SQLite database with all tables."
  [ds]
  (jdbc/with-transaction [tx ds]
    (doseq [ddl tables]
      (jdbc/execute! tx [ddl]))))

(defn db-path
  "Return the default database path."
  []
  (or (System/getenv "MEME_DB_PATH")
      "meme.db"))

(defn datasource
  "Create a datasource for the meme database."
  ([] (datasource (db-path)))
  ([path]
   (jdbc/get-datasource {:dbtype "sqlite" :dbname path})))

(defn ensure-db!
  "Ensure the database exists and is initialized."
  ([] (ensure-db! (datasource)))
  ([ds]
   (init-db! ds)
   ds))
