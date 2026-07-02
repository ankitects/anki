# Concept-Graph Visualization — Plan & Implementation

> Companion to `brainlift.md` and the phase plans. A **force-directed graph of the deck**: nodes
> are flashcards (or notes / clusters), edges are the **`SPOV 1` relationships** (interference +
> dependency), and nodes are colored by **FSRS state** so the map "lights up" as you master it.
>
> **It is a _view_ over the existing edge data — a companion tool, not a SPOV.** The brainlift
> deliberately excludes UI-only techniques that can't be ablated at the engine, so this graph is
> **not part of the ablation**; it earns its place as (a) a dev aid for validating `SPOV 1`'s
> edges and (b) a user-facing "knowledge map."

## v1 decisions (locked)

- **Node** = a **cluster/concept** that expands to its cards (level-of-detail).
- **Cluster level** = **reading** — the deck's tags are flat reading names (no `::` hierarchy, no
  LOS-level tags), so a reading is the finest available cluster.
- **Edges (v1)** = **within-tag** reading clusters + **cross-tag via co-occurrence** (notes tagged
  with two readings bridge them); shared-prefix hierarchy is **N/A** (flat tags); optional curated
  **reading→topic** map for topic super-clusters; dependency edges deferred.
- **Node color** = **discrete buckets → Anki state tokens** (`--state-new` / `--state-learn` /
  `--state-review`, amber `--state-buried` for learning).
- **Scope** = **current deck**.
- **Renderer** = **d3-force on SVG** (d3 already in-tree; CSS tokens + night mode apply automatically).

## What it is

- A **force-directed graph** where layout naturally pulls confusable clusters into blobs and
  stretches dependency chains into ladders.
- **Nodes** = cards (or notes / clusters — see scale).
- **Edges** = **within-tag** (a card's tag-mates → a confusable cluster) and **cross-tag**
  (related clusters, from the tag hierarchy + tag co-occurrence), plus directed **dependency**
  edges (fact → application; `SPOV 2`). See _Edge sourcing_ below.
- **Node color** = FSRS retrievability/stability, mapped to **Anki state tokens**
  (`--state-review` green = mastered, `--state-learn` red = weak, `--state-new` blue = new); size
  by due/lapses.

## Scope

**In scope**

- Read-only visualization over **existing edge data** — tag-clusters (Phase 1) and/or the
  `card_relationships` table (Phase 2) — plus per-card FSRS state.
- No AI, no scheduler changes.

**Out of scope**

- Any change to scheduling/learning behavior; it's not a SPOV and not in the ablation.

**Where it fits**

- A clean **Phase 1 add-on** (interference/tag edges already exist), automatically richer in
  **Phase 2** once directed dependency edges appear.

## What it shows

| Element                                 | Mapping                                                                                                                |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Node                                    | a card / note / cluster                                                                                                |
| Node color                              | FSRS retrievability via Anki state tokens — `--state-review` (mastered) · `--state-learn` (weak) · `--state-new` (new) |
| Node size                               | due status / lapses (optional)                                                                                         |
| Edge — within-tag (undirected)          | interference / confusable — cards sharing the same tag                                                                 |
| Edge — cross-tag (undirected, weighted) | related clusters — shared tag-prefix depth + tag co-occurrence                                                         |
| Edge — dependency (directed)            | fact → application, worked → faded → solve (`SPOV 2`)                                                                  |
| Cluster blob                            | a confusable group (LOS / sub-topic)                                                                                   |
| Singleton node                          | an orphan card (no shared tag/edge) — useful "gap" signal                                                              |

## Edge sourcing — how connections are decided

Two kinds of connection — **within-tag** (a confusable cluster) and **between-tag** (how clusters
connect) — and in Phase 1 _both_ are derived **deterministically from tags + logs, no AI**.

**Non-AI (now):**

1. **Within-tag (cluster).** Cards sharing the same full tag → one confusable cluster
   (intra-cluster edges). Powers `SPOV 3` contrast and the map's blobs.
2. **Cross-tag — hierarchy / shared prefix.** The tag tree is itself a graph: a deeper shared
   prefix = a stronger relation. `FI::Duration::Modified` ↔ `FI::Duration::Macaulay` (deep →
   strong) vs `FI::Duration` ↔ `FI::Convexity` (share only `FI` → weak). Also nests each concept
   to its parent topic and to sibling concepts.
3. **Cross-tag — co-occurrence.** A card carrying several tags bridges those clusters; tags that
   co-occur on cards are related, weighted by frequency.
4. **Dependency (directed, cross-concept).** Curated cross-links + `SPOV 2` fact → application
   edges — inherently cross-tag.
5. **Behavioral (optional, still no AI).** Co-study / co-confusion mined from review logs
   (deterministic counts).

> **In the provided CFA deck (`CFA_Level_1.apkg`):** tags are **flat reading names** (no `::`), so
> #2 (hierarchy) doesn't apply — within-tag **reading clusters** (#1) are the backbone, cross-tag
> edges come from **co-occurrence** (#3; the 187 two-reading notes), and an optional curated
> **reading→topic** map adds the 10 CFA topic super-clusters.

**Weighting & hairball guard:** rank full-tag > deep shared prefix > shallow prefix >
co-occurrence; **threshold** weak edges and **never connect via the root tag** (everything shares
`CFA::…`) — require a **minimum shared depth** (≥ topic + sub-topic) before drawing a cross-tag edge.

**AI options (later, Phase 2+):** semantic / embedding similarity between cards or tags to
_propose_ cross-tag edges. Note it measures **similarity, not confusability**, so it suits the
**map** and dependency context more than **contrast scheduling**; validate proposed edges before use.

| Edge                      | Source (no-AI, now)           | Best for                               |
| ------------------------- | ----------------------------- | -------------------------------------- |
| Within-tag                | same full tag                 | `SPOV 3` contrast + cluster blobs      |
| Cross-tag (hierarchical)  | shared tag-prefix depth       | the map's nested structure             |
| Cross-tag (co-occurrence) | shared tags on cards          | connecting related clusters on the map |
| Dependency                | curated / `SPOV 2` generators | `SPOV 2` gating + directed arrows      |

## Two jobs it does

1. **Dev / validation (now).** Render the Phase 1 tag-clusters and _look_: are FIFO/LIFO grouped?
   Hairball ⇒ tag level too coarse; lots of singletons ⇒ orphan facts. Fastest way to debug
   `SPOV 1` edges (supports `PHASE1_PLAN.md` M0/M2).
2. **User knowledge map (UX).** A map of what you know — click-to-study, spot weak/under-connected
   areas, motivation as nodes turn green.

## Caveats

- **"All connected" isn't literally true** — connectivity = shared tags/edges; unrelated cards
  appear as **singletons** (informative, not a bug).
- **Scale → hairball.** A few thousand card-nodes in a raw force layout is unreadable and slow
  (~O(n²)). Mitigate with **cluster/LOD nodes that expand to cards**, filtering, and **WebGL**
  rendering for large decks.
- **It's a view, not a lever** — no learning claim, no ablation.

## Implementation (no AI; mostly frontend)

Anki's frontend is **Svelte/TS in `ts/`** and already ships **d3-based stats graphs** served as
mediasrv pages — follow that exact pattern.

- **Backend (rslib):** a small RPC, e.g. `get_concept_graph(deck_id, level)`, returning
  `{ nodes: [{id, label, tags, retrievability, due}], edges: [{source, target, kind}] }`.
  - Edges from `notes.tags` (Phase 1) or the `card_relationships` table (Phase 2).
  - FSRS state via the existing `extract_fsrs_*` helpers (`rslib/src/storage/sqlite.rs`).
  - Define in `proto/` + a service method (mirrors the stats/graph endpoints).
- **Frontend (ts):** a new Svelte page rendering with **d3-force** (already in the tree) for
  modest graphs, or **sigma.js / cytoscape.js** for large/WebGL graphs.
- **Served** at the mediasrv pages URL like the existing stats page; read-only → low risk.

## Styling — match Anki (design tokens)

> Per `.cursor/skills/matching-anki-ui-style`: **reuse before restyle; never hardcode colors,
> sizes, or radii — use Anki's design tokens and existing components; introduce no new visual
> language; must theme correctly in light _and_ night mode.**

**Surfaces & chrome** (CSS custom properties from `ts/lib/sass/_vars.scss`):

- page background `var(--canvas)`; panels `var(--canvas-elevated)`; inputs `var(--canvas-inset)`
- text `var(--fg)` / `var(--fg-subtle)`; borders `var(--border-subtle)` / `var(--border)`
- corners `var(--border-radius-medium)` (panels) / `var(--border-radius)` (controls);
  text `var(--font-size)`; motion `var(--transition)`

**Node color = FSRS state → existing Anki state tokens** (no invented palette):

| State                          | Token                                        |
| ------------------------------ | -------------------------------------------- |
| mastered / high retrievability | `--state-review` (green)                     |
| weak / at-risk                 | `--state-learn` (red) — or `--accent-danger` |
| learning / intermediate        | `--state-buried` (amber)                     |
| new / unseen                   | `--state-new` (blue)                         |

(For a continuous scale, interpolate between `--state-learn` and `--state-review`.)

**Edges:** interference (undirected) → `var(--border)` / `var(--fg-subtle)`; dependency (directed)
→ `var(--accent-card)` / `var(--fg-link)` (blue).

**Reuse components** (`ts/lib/components/`): `Switch` / `SwitchRow` ("Show weak only"), `CheckBox`
(topic filters), `Container` / `TitledContainer` + `Row`/`Col` (layout), `Icon` / `IconConstrain`
(topic/cluster icons), `WithTooltip` (node hover), `Badge` (counts), and an existing input for search.

**Conventions:** `<style lang="scss">` + `@use` shared mixins; **Bootstrap 5** base; AGPL license
header on new `.svelte` / `.scss`; user-facing strings via the `tr.*` ftl API (never hardcoded).

**Theming the canvas (important):** with **d3-force on SVG**, nodes/edges are DOM elements, so
`var(--token)` and CSS classes apply and night mode is automatic — **prefer this**. With a
**canvas/WebGL** renderer (sigma/regl) for scale, CSS vars don't reach the canvas, so **resolve
tokens in JS** — e.g. `getComputedStyle(document.documentElement).getPropertyValue('--state-review')`
— pass the values to the renderer, and **re-resolve when night mode toggles** so the graph
re-themes. Verify both themes via `just run` / `just web-watch`.

## Milestones

### M0 — Cluster tag rule (resolved from the deck)

Inspected the provided deck (`CFA_Level_1.apkg`): **1,523 notes**, **53 distinct tags**, all
**flat CFA _reading_ names** (e.g. `The_Time_Value_of_Money`, `Cost_of_Capital`) — **no `::`
hierarchy**, and **~88% of notes carry exactly one tag** (187 notes carry two readings).

- **Cluster rule:** `cluster_key(card)` = its **reading tag** → ~50 reading clusters (sizes 5–112).
- **Cross-tag (v1):** **co-occurrence** — the 187 two-reading notes bridge their readings
  (shared-prefix hierarchy is unavailable here).
- **Optional topic grouping:** a small curated **reading → CFA topic (10 areas)** lookup
  (deterministic, no AI) to add topic super-clusters and connect sibling readings.
- **Cleanup:** drop noise tags (`Category`, stray single-card tags) before clustering.

### M1 — Backend graph RPC

- `get_concept_graph` returns nodes (id/label/tags/FSRS/due) + edges (source/target/kind),
  sourced from tags (Phase 1) or `card_relationships` (Phase 2).

### M2 — Frontend force-directed page

- New Svelte page; render nodes/edges; **color by FSRS retrievability**; style edges by type
  (undirected interference vs directed dependency).
- Use **Anki design tokens** for every color/size/radius and reuse `ts/lib/components/`
  (`Switch`, `CheckBox`, `Container`, `Icon`) — see **Styling — match Anki** above; verify
  light + night mode.

### M3 — Interactions

- Zoom / expand-collapse clusters, filter by tag/topic, hover for card detail, **click-to-study**
  (deep-link into the reviewer or Browse).

### M4 — Scale handling

- Cluster aggregation + level-of-detail; switch to WebGL (sigma/regl) above a node threshold.

### M5 — Polish (optional)

- Legend, search, "show only weak (red) nodes," snapshot/export.

## Deliverables

1. A `get_concept_graph` backend RPC over tag/edge data + FSRS state.
2. A force-directed **Svelte visualization page** (FSRS-colored nodes, typed edges).
3. Cluster level-of-detail + basic interactions (filter, click-to-study).
4. (Doubles as) a **validation view** for `SPOV 1` edge quality.

## Engine / UI touch points (reference)

| Concern          | File / area                                                                                                                       |
| ---------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Graph RPC        | new service in `proto/` + `rslib/src/` (mirror stats endpoints)                                                                   |
| Edge source      | `notes.tags` (Phase 1) / `card_relationships` (Phase 2)                                                                           |
| FSRS per node    | `extract_fsrs_*` — `rslib/src/storage/sqlite.rs`                                                                                  |
| Frontend page    | new Svelte route in `ts/` (pattern: existing stats/graphs page)                                                                   |
| Graph rendering  | `d3-force` (in-tree, SVG → CSS tokens apply) or `sigma.js` / `cytoscape.js` (WebGL → resolve tokens in JS)                        |
| Styling / tokens | `ts/lib/sass/_vars.scss` (`--canvas*`, `--fg*`, `--border*`, `--state-*`, `--border-radius*`); components in `ts/lib/components/` |

## Risks & decisions

- **Scale/readability** is the main risk → cluster-first nodes + LOD + WebGL.
- **Granularity choice** (card vs cluster) drives usefulness and performance.
- **Edge richness is phase-dependent** — Phase 1 shows mostly clusters (interference); directed
  dependency structure appears in Phase 2.
- **Keep it read-only** — no scheduler coupling, so it never risks study behavior.

## Appendix — Reading → CFA topic map (curated)

A static, deterministic lookup (no AI) mapping the deck's 52 reading tags to the 10 CFA Level I
topic areas. Used to (a) group readings into **topic super-clusters** and (b) draw **cross-reading
edges within a topic**. The noise tag `Category` is dropped. A few assignments follow the
2020/2021 L1 curriculum (Technical Analysis → Quant; Corporate Governance/ESG → Corporate Issuers)
and can be adjusted.

**Ethical & Professional Standards**

- `Ethics_and_Trust_in_the_Investment_Profession`

**Quantitative Methods**

- `The_Time_Value_of_Money`
- `Statistical_Concepts_and_Market_Returns`
- `Probability_Concepts`
- `Common_Probability_Distributions`
- `Sampling_and_Estimation`
- `Hypothesis_Testing`
- `Technical_Analysis`

**Economics**

- `Topics_in_Supply_and_Demand_Analysis`
- `The_Firm_and_Market_Structures`
- `Aggregate_Output,_Prices,_and_Economic_Growth`
- `Understanding_Business_Cycles`
- `Monetary_and_Fiscal_Policy`
- `International_Trade_and_Capital_Flows`
- `Currency_Exchange_Rates`

**Financial Statement Analysis**

- `Introduction_to_Financial_Statement_Analysis`
- `Financial_Reporting_Standards`
- `Understanding_Income_Statements`
- `Understanding_Balance_Sheets`
- `Understanding_Cash_Flow_Statements`
- `Financial_Analysis_Techniques`
- `Inventories`
- `Long-Lived_Assets`
- `Income_Taxes`
- `Non-Current_(Long-Term)_Liabilities`
- `Applications_of_Financial_Statement_Analysis`

**Corporate Issuers**

- `Introduction_to_Corporate_Governance_and_Other_ESG_Considerations`
- `Capital_Budgeting`
- `Cost_of_Capital`
- `Measures_of_Leverage`
- `Working_Capital_Management`

**Equity Investments**

- `Market_Organization_and_Structure`
- `Security_Market_Indexes`
- `Market_Efficiency`
- `Overview_of_Equity_Securities`
- `Introduction_to_Industry_and_Company_Analysis`
- `Equity_Valuation:_Concepts_and_Basic_Tools`

**Fixed Income**

- `Fixed-Income_Securities:_Defining_Elements`
- `Fixed-Income_Markets:_Issuance,_Trading,_and_Funding`
- `Introduction_to_Fixed-Income_Valuation`
- `Introduction_to_Asset-Backed_Securities`
- `Understanding_Fixed-Income_Risk_and_Return`
- `Fundamentals_of_Credit_Analysis`

**Derivatives**

- `Derivative_Markets_and_Instruments`
- `Basics_of_Derivative_Pricing_and_Valuation`

**Alternative Investments**

- `Introduction_to_Alternative_Investments`

**Portfolio Management**

- `Portfolio_Management:_An_Overview`
- `Portfolio_Risk_and_Return:_Part_I`
- `Portfolio_Risk_and_Return:_Part_II`
- `Basics_of_Portfolio_Planning_and_Construction`
- `Introduction_to_Risk_Management`
- `Fintech_in_Investment_Management`
