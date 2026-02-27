# FSRS Algorithm Implementation Guide for Supabase/PostgreSQL

> **Purpose**: This document is a complete reference for implementing FSRS-based spaced repetition
> in a Vite/Vercel app with Supabase (PostgreSQL). It is derived from analysis of the Anki
> source code (rslib/src/scheduler/ and the `fsrs` crate v5.1.0).

---

## Table of Contents

1. [FSRS Overview](#1-fsrs-overview)
2. [Core Concepts](#2-core-concepts)
3. [Mathematical Formulas](#3-mathematical-formulas)
4. [Default Parameters (Weights)](#4-default-parameters-weights)
5. [Recommended Database Schema](#5-recommended-database-schema)
6. [Algorithm Implementation (TypeScript)](#6-algorithm-implementation-typescript)
7. [State Transitions](#7-state-transitions)
8. [Interval Fuzz](#8-interval-fuzz)
9. [Common Spacing Problems & Fixes](#9-common-spacing-problems--fixes)
10. [Supabase Migration Checklist](#10-supabase-migration-checklist)

---

## 1. FSRS Overview

FSRS (Free Spaced Repetition Scheduler) is a memory model that tracks two variables per card:

| Variable | Symbol | Range | Meaning |
|---|---|---|---|
| **Stability** | `S` | 0.01 – 36,500 days | How long until 90% retention (the "half-life") |
| **Difficulty** | `D` | 1.0 – 10.0 | How hard the card is (1 = trivial, 10 = very hard) |

The algorithm uses 21 learned weights (`w[0]`–`w[20]`) to compute how S and D change after each review. Anki ships default weights that work reasonably well for most users, and optionally personalizes them by training on the user's own review history.

There are 4 answer ratings:
- **1 = Again** (failed, forgot)
- **2 = Hard** (remembered with significant difficulty)
- **3 = Good** (normal recall)
- **4 = Easy** (remembered effortlessly)

---

## 2. Core Concepts

### Memory States
Cards move through these states:

```
NEW → LEARNING → REVIEW ←→ RELEARNING
```

- **New**: Card has never been reviewed. No S or D stored.
- **Learning**: Card is in the initial learning phase (shown multiple times per day using step intervals in minutes).
- **Review**: Card is scheduled in days using FSRS. This is the main long-term state.
- **Relearning**: Card was "Again"'d from Review state and must be re-learned before going back to Review.

### Desired Retention
The target probability of recall at review time. Anki default is **0.9** (90%).
This is user-configurable and is the primary lever for adjusting review frequency.
- Higher retention (e.g. 0.95) → shorter intervals → more reviews
- Lower retention (e.g. 0.80) → longer intervals → fewer reviews

### Retrievability (Current Retention)
The probability the user can recall the card right now, based on elapsed days and stability.

---

## 3. Mathematical Formulas

All formulas below use weights array `w` (21 elements, indices 0–20).
See [Section 4](#4-default-parameters-weights) for default values.

---

### 3.1 Retrievability (Power Forgetting Curve)

```
decay   = w[20]                          // default: 0.1542
factor  = 0.9^(1 / decay) - 1           // derived constant ≈ 4.864 (for decay=0.1542)

R(t, S) = (t / S * factor + 1)^(-decay)
```

Where `t` = days elapsed since last review, `S` = stability.

**Verification**: At `t = S`, `R = 0.9` exactly (that's the definition of stability).

**TypeScript**:
```ts
function retrievability(t: number, stability: number, decay = 0.1542): number {
  const factor = Math.pow(0.9, 1 / decay) - 1;
  return Math.pow((t / stability) * factor + 1, -decay);
}
```

---

### 3.2 Next Interval (from Stability + Desired Retention)

The interval (in days) to schedule the next review so that retrievability equals `desiredRetention` at review time:

```
interval = S / factor * (desiredRetention^(1 / decay) - 1)
```

**TypeScript**:
```ts
function nextInterval(stability: number, desiredRetention: number, decay = 0.1542): number {
  const factor = Math.pow(0.9, 1 / decay) - 1;
  return (stability / factor) * (Math.pow(desiredRetention, 1 / decay) - 1);
}
```

> **Note**: Round up to at least 1 day. Apply fuzz (Section 8) before storing.

---

### 3.3 Initial Stability (for New Cards)

When a card is first reviewed, stability is set directly from the rating:

```
S_0(rating) = w[rating - 1]   // w[0] for Again, w[1] for Hard, w[2] for Good, w[3] for Easy
```

Default values: `S_0 = [0.212, 1.2931, 2.3065, 8.2956]` days for ratings 1–4.

---

### 3.4 Initial Difficulty (for New Cards)

```
D_0(rating) = w[4] - exp(w[5] * (rating - 1)) + 1
```

Default values (ratings 1–4, using default weights):
- Again (1): ≈ 6.41
- Hard  (2): ≈ 5.10
- Good  (3): ≈ 2.73
- Easy  (4): ≈ 0.25 → clamped to 1.0

Clamp result to **[1.0, 10.0]**.

---

### 3.5 Stability After Successful Recall (Review State)

Used when `rating ∈ {2, 3, 4}` AND `days_elapsed > 0`:

```
hard_penalty = w[15]  if rating == 2,  else 1.0
easy_bonus   = w[16]  if rating == 4,  else 1.0

S'_recall = S * (
    exp(w[8]) * (11 - D) * S^(-w[9]) * (exp((1 - R) * w[10]) - 1)
    * hard_penalty * easy_bonus
    + 1
)
```

Where `R = retrievability(days_elapsed, S)` is the current retention at review time.

**TypeScript**:
```ts
function stabilityAfterRecall(
  S: number, D: number, R: number, rating: number, w: number[]
): number {
  const hardPenalty = rating === 2 ? w[15] : 1.0;
  const easyBonus   = rating === 4 ? w[16] : 1.0;
  return S * (
    Math.exp(w[8]) * (11 - D) * Math.pow(S, -w[9])
    * (Math.exp((1 - R) * w[10]) - 1)
    * hardPenalty * easyBonus
    + 1
  );
}
```

---

### 3.6 Stability After Failure (Lapse)

Used when `rating == 1` (Again) AND `days_elapsed > 0`:

```
S'_forget_raw = w[11] * D^(-w[12]) * ((S + 1)^w[13] - 1) * exp((1 - R) * w[14])

// Floor: stability can't drop below S / exp(w[17] * w[18])
S'_forget = max(S'_forget_raw, S / exp(w[17] * w[18]))
```

**TypeScript**:
```ts
function stabilityAfterForgetting(
  S: number, D: number, R: number, w: number[]
): number {
  const raw = w[11] * Math.pow(D, -w[12]) * (Math.pow(S + 1, w[13]) - 1)
              * Math.exp((1 - R) * w[14]);
  const floor = S / Math.exp(w[17] * w[18]);
  return Math.max(raw, floor);
}
```

---

### 3.7 Short-Term Stability (Same-Day Reviews, `days_elapsed == 0`)

Used during the learning/relearning phase when the card is reviewed multiple times in the same day:

```
sinc = exp(w[17] * (rating - 3 + w[18])) * S^(-w[19])

// For rating >= 3, sinc is floored at 1.0 (stability can only increase on good/easy)
effective_sinc = rating >= 3 ? max(sinc, 1.0) : sinc

S'_short = S * effective_sinc
```

**TypeScript**:
```ts
function stabilityShortTerm(S: number, rating: number, w: number[]): number {
  const sinc = Math.exp(w[17] * (rating - 3 + w[18])) * Math.pow(S, -w[19]);
  const effectiveSinc = rating >= 3 ? Math.max(sinc, 1.0) : sinc;
  return S * effectiveSinc;
}
```

> **Note**: If `w[17]` and `w[18]` are both 0.0, short-term scheduling is disabled (FSRS-5 mode).

---

### 3.8 Next Difficulty

Applied after every review:

```
delta_d        = -w[6] * (rating - 3)
linear_damping = (10 - D) * delta_d / 9
D_new          = D + linear_damping

// Mean reversion toward the "easy" difficulty baseline
D_easy_baseline = w[4] - exp(w[5] * 3) + 1   // init_difficulty(rating=4)
D_final         = w[7] * (D_easy_baseline - D_new) + D_new
```

Clamp `D_final` to **[1.0, 10.0]**.

**TypeScript**:
```ts
function nextDifficulty(D: number, rating: number, w: number[]): number {
  const deltaD = -w[6] * (rating - 3);
  const linearDamping = (10 - D) * deltaD / 9;
  const dNew = D + linearDamping;
  const dEasyBaseline = w[4] - Math.exp(w[5] * 3) + 1;
  const dFinal = w[7] * (dEasyBaseline - dNew) + dNew;
  return Math.min(Math.max(dFinal, 1.0), 10.0);
}
```

---

### 3.9 Putting It All Together: `nextStates()`

```ts
interface MemoryState {
  stability: number;  // days
  difficulty: number; // 1.0–10.0
}

interface ReviewResult {
  memory: MemoryState;
  interval: number;   // days (before fuzz)
}

function computeNextState(
  current: MemoryState | null,
  daysElapsed: number,
  rating: number,          // 1=Again, 2=Hard, 3=Good, 4=Easy
  desiredRetention: number, // e.g. 0.9
  w: number[]
): ReviewResult {
  let newS: number;
  let newD: number;

  if (current === null) {
    // New card
    newS = w[rating - 1];  // w[0..3]
    newD = Math.min(Math.max(w[4] - Math.exp(w[5] * (rating - 1)) + 1, 1.0), 10.0);
  } else {
    newD = nextDifficulty(current.difficulty, rating, w);

    if (daysElapsed === 0) {
      // Same-day review (learning/relearning phase)
      newS = stabilityShortTerm(current.stability, rating, w);
    } else if (rating === 1) {
      // Lapse
      const R = retrievability(daysElapsed, current.stability, w[20]);
      newS = stabilityAfterForgetting(current.stability, current.difficulty, R, w);
    } else {
      // Successful recall
      const R = retrievability(daysElapsed, current.stability, w[20]);
      newS = stabilityAfterRecall(current.stability, current.difficulty, R, rating, w);
    }
  }

  // Clamp stability
  newS = Math.min(Math.max(newS, 0.001), 36500);

  const interval = nextInterval(newS, desiredRetention, w[20]);

  return {
    memory: { stability: newS, difficulty: newD },
    interval,
  };
}
```

---

## 4. Default Parameters (Weights)

These are the FSRS-6 defaults from the `fsrs` crate v5.1.0 (`DEFAULT_PARAMETERS`):

| Index | Value | Role |
|-------|-------|------|
| w[0]  | 0.2120 | Initial stability: Again |
| w[1]  | 1.2931 | Initial stability: Hard |
| w[2]  | 2.3065 | Initial stability: Good |
| w[3]  | 8.2956 | Initial stability: Easy |
| w[4]  | 6.4133 | Initial difficulty base |
| w[5]  | 0.8334 | Initial difficulty curve steepness |
| w[6]  | 3.0194 | Difficulty delta per rating |
| w[7]  | 0.0010 | Mean reversion factor |
| w[8]  | 1.8722 | Recall stability growth exponent |
| w[9]  | 0.1666 | Recall stability power of S |
| w[10] | 0.7960 | Recall stability retention coefficient |
| w[11] | 1.4835 | Forget stability base |
| w[12] | 0.0614 | Forget stability difficulty power |
| w[13] | 0.2629 | Forget stability power of S |
| w[14] | 1.6483 | Forget stability retention coefficient |
| w[15] | 0.6014 | Hard penalty multiplier |
| w[16] | 1.8729 | Easy bonus multiplier |
| w[17] | 0.5425 | Short-term stability coefficient |
| w[18] | 0.0912 | Short-term stability rating offset |
| w[19] | 0.0658 | Short-term stability power of S |
| w[20] | 0.1542 | Decay (FSRS-6; FSRS-5 used 0.5) |

```ts
export const DEFAULT_FSRS_WEIGHTS = [
  0.212, 1.2931, 2.3065, 8.2956,
  6.4133, 0.8334, 3.0194, 0.001,
  1.8722, 0.1666, 0.796, 1.4835,
  0.0614, 0.2629, 1.6483, 0.6014,
  1.8729, 0.5425, 0.0912, 0.0658,
  0.1542,
];
```

> **Tuning tip**: Do NOT hand-edit individual weights unless you deeply understand the model.
> Instead, adjust `desiredRetention` and learning steps. Weights should be trained from data.

---

## 5. Recommended Database Schema

### 5.1 Cards Table

```sql
CREATE TABLE cards (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES auth.users(id),
  deck_id         UUID,
  front           TEXT NOT NULL,
  back            TEXT NOT NULL,

  -- FSRS memory state
  stability       FLOAT,          -- NULL = new card
  difficulty      FLOAT,          -- NULL = new card; range 1.0–10.0

  -- Scheduling
  state           TEXT NOT NULL DEFAULT 'new',
    -- 'new' | 'learning' | 'review' | 'relearning'
  due             TIMESTAMPTZ,    -- when to show next
  scheduled_days  INT,            -- last scheduled interval in days (for review state)
  elapsed_days    INT,            -- days since last review (computed on review)
  reps            INT NOT NULL DEFAULT 0,
  lapses          INT NOT NULL DEFAULT 0,

  -- Learning phase state (sub-day steps)
  learning_step_index INT,        -- which step the card is currently on
  learning_step_secs  INT,        -- delay in seconds for current step (short-term)

  last_review     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX cards_due_idx ON cards(user_id, due) WHERE due IS NOT NULL;
CREATE INDEX cards_state_idx ON cards(user_id, state);
```

### 5.2 Review Log Table

```sql
CREATE TABLE review_log (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_id     UUID NOT NULL REFERENCES cards(id),
  user_id     UUID NOT NULL REFERENCES auth.users(id),
  rating      SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 4),
  state       TEXT NOT NULL,         -- state BEFORE this review
  scheduled_days INT,                -- scheduled interval that led to this review
  elapsed_days   INT,                -- actual days elapsed since last review
  stability   FLOAT,                 -- S AFTER this review
  difficulty  FLOAT,                 -- D AFTER this review
  review_duration_ms INT,            -- time taken to answer (optional)
  reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX review_log_card_idx ON review_log(card_id, reviewed_at);
CREATE INDEX review_log_user_idx ON review_log(user_id, reviewed_at);
```

### 5.3 Deck Config Table

```sql
CREATE TABLE deck_configs (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id              UUID NOT NULL REFERENCES auth.users(id),
  name                 TEXT NOT NULL DEFAULT 'Default',
  desired_retention    FLOAT NOT NULL DEFAULT 0.9,  -- 0.7–0.97 recommended
  new_cards_per_day    INT NOT NULL DEFAULT 20,
  max_reviews_per_day  INT NOT NULL DEFAULT 200,
  learning_steps       INT[] NOT NULL DEFAULT '{60, 600}',   -- seconds: [1min, 10min]
  relearning_steps     INT[] NOT NULL DEFAULT '{600}',        -- seconds: [10min]
  graduating_interval_good  INT NOT NULL DEFAULT 1,  -- days
  graduating_interval_easy  INT NOT NULL DEFAULT 4,  -- days
  fsrs_weights         FLOAT[] NOT NULL DEFAULT '{
    0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
    1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
    1.8729, 0.5425, 0.0912, 0.0658, 0.1542
  }',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 6. Algorithm Implementation (TypeScript)

Complete, self-contained implementation to use in your Vite app or a Supabase Edge Function.

```ts
// fsrs.ts

export const DEFAULT_WEIGHTS: number[] = [
  0.212, 1.2931, 2.3065, 8.2956,
  6.4133, 0.8334, 3.0194, 0.001,
  1.8722, 0.1666, 0.796, 1.4835,
  0.0614, 0.2629, 1.6483, 0.6014,
  1.8729, 0.5425, 0.0912, 0.0658,
  0.1542,
];

export interface MemoryState {
  stability: number;   // days
  difficulty: number;  // 1.0–10.0
}

export interface SchedulingResult {
  memory: MemoryState;
  intervalDays: number;    // raw interval before fuzz
  scheduledDays: number;   // fuzzed interval to store/schedule
}

// ── Core formulas ─────────────────────────────────────────────────────────────

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max);
}

export function retrievability(t: number, stability: number, decay: number): number {
  // Probability of recall after `t` days, given stability `s`
  const factor = Math.pow(0.9, 1.0 / decay) - 1.0;
  return Math.pow((t / stability) * factor + 1.0, -decay);
}

export function nextIntervalDays(
  stability: number,
  desiredRetention: number,
  decay: number,
): number {
  const factor = Math.pow(0.9, 1.0 / decay) - 1.0;
  return (stability / factor) * (Math.pow(desiredRetention, 1.0 / decay) - 1.0);
}

function initStability(rating: number, w: number[]): number {
  return w[rating - 1]; // w[0..3]
}

function initDifficulty(rating: number, w: number[]): number {
  return clamp(w[4] - Math.exp(w[5] * (rating - 1)) + 1.0, 1.0, 10.0);
}

function stabilityAfterRecall(
  S: number, D: number, R: number, rating: number, w: number[]
): number {
  const hardPenalty = rating === 2 ? w[15] : 1.0;
  const easyBonus   = rating === 4 ? w[16] : 1.0;
  return S * (
    Math.exp(w[8]) * (11 - D) * Math.pow(S, -w[9])
    * (Math.exp((1.0 - R) * w[10]) - 1.0)
    * hardPenalty * easyBonus
    + 1.0
  );
}

function stabilityAfterForgetting(
  S: number, D: number, R: number, w: number[]
): number {
  const raw   = w[11] * Math.pow(D, -w[12]) * (Math.pow(S + 1, w[13]) - 1)
                * Math.exp((1.0 - R) * w[14]);
  const floor = S / Math.exp(w[17] * w[18]);
  return Math.max(raw, floor);
}

function stabilityShortTerm(S: number, rating: number, w: number[]): number {
  const sinc = Math.exp(w[17] * (rating - 3 + w[18])) * Math.pow(S, -w[19]);
  const effectiveSinc = rating >= 3 ? Math.max(sinc, 1.0) : sinc;
  return S * effectiveSinc;
}

function nextDifficulty(D: number, rating: number, w: number[]): number {
  const deltaD       = -w[6] * (rating - 3);
  const linearDamp   = (10 - D) * deltaD / 9.0;
  const dNew         = D + linearDamp;
  const dBaseline    = w[4] - Math.exp(w[5] * 3) + 1; // init_difficulty(easy)
  const dReverted    = w[7] * (dBaseline - dNew) + dNew;
  return clamp(dReverted, 1.0, 10.0);
}

// ── Main scheduling function ──────────────────────────────────────────────────

export function scheduleReview(
  current: MemoryState | null,
  daysElapsed: number,
  rating: number,         // 1–4
  desiredRetention = 0.9,
  w: number[] = DEFAULT_WEIGHTS,
): SchedulingResult {
  const decay = w[20];
  let newS: number;
  let newD: number;

  if (current === null) {
    // New card — first review
    newS = initStability(rating, w);
    newD = initDifficulty(rating, w);
  } else {
    newD = nextDifficulty(current.difficulty, rating, w);

    if (daysElapsed === 0) {
      newS = stabilityShortTerm(current.stability, rating, w);
    } else if (rating === 1) {
      const R = retrievability(daysElapsed, current.stability, decay);
      newS = stabilityAfterForgetting(current.stability, current.difficulty, R, w);
    } else {
      const R = retrievability(daysElapsed, current.stability, decay);
      newS = stabilityAfterRecall(current.stability, current.difficulty, R, rating, w);
    }
  }

  newS = clamp(newS, 0.001, 36500);
  newD = clamp(newD, 1.0, 10.0);

  const rawInterval = nextIntervalDays(newS, desiredRetention, decay);
  const scheduledDays = Math.max(1, Math.round(applyFuzz(rawInterval)));

  return {
    memory: { stability: newS, difficulty: newD },
    intervalDays: rawInterval,
    scheduledDays,
  };
}

// ── Interval fuzz (mirrors Anki's fuzz logic) ─────────────────────────────────

interface FuzzRange {
  start: number;
  end: number;
  factor: number;
}

const FUZZ_RANGES: FuzzRange[] = [
  { start: 2.5,  end: 7.0,      factor: 0.15 },
  { start: 7.0,  end: 20.0,     factor: 0.10 },
  { start: 20.0, end: Infinity,  factor: 0.05 },
];

function fuzzDelta(interval: number): number {
  if (interval < 2.5) return 0;
  return FUZZ_RANGES.reduce((delta, range) => {
    return delta + range.factor * (Math.min(interval, range.end) - range.start);
  }, 1.0);
}

export function applyFuzz(interval: number, fuzzFactor = 0.5): number {
  // fuzzFactor: 0.0 = minimum possible, 1.0 = maximum possible, 0.5 = middle
  if (interval < 2.5) return interval;
  const delta = fuzzDelta(interval);
  const minInterval = Math.round(interval - delta);
  const maxInterval = Math.round(interval + delta);
  return minInterval + Math.round(fuzzFactor * (maxInterval - minInterval));
}
```

---

## 7. State Transitions

### 7.1 Learning Steps Logic

Cards start in **learning** state and move through configurable step intervals (in seconds) before graduating to **review**.

```
learning_steps = [60, 600]  // 1 min, 10 min (typical)

New card:
  Again → step[0] (1 min)
  Hard  → average of step[0] and step[1] (but max 1 day more than step[0])
  Good  → step[1] (10 min) ... then graduate if on last step
  Easy  → Graduate immediately (use graduating_interval_easy days)

On last learning step, Good → Graduate to Review with graduating_interval_good days
```

### 7.2 Review State

Once in review, FSRS computes the next interval:

```
Again → Relearning (enter relearning steps)
Hard  → Review with S'_hard (hard penalty applied, shorter interval)
Good  → Review with S'_good (normal update)
Easy  → Review with S'_easy (easy bonus applied, longer interval)
```

### 7.3 Relearning Steps

```
relearning_steps = [600]  // 10 min (typical)

Again (in Relearning) → back to step[0]
Good/Easy (on last step) → back to Review
```

### 7.4 Implementation Flow (Pseudocode)

```ts
async function processReview(cardId: string, rating: 1 | 2 | 3 | 4) {
  const card = await getCard(cardId);
  const config = await getDeckConfig(card.deck_id);
  const now = new Date();

  // Calculate elapsed days since last review
  const daysElapsed = card.last_review
    ? Math.floor((now.getTime() - card.last_review.getTime()) / 86400000)
    : 0;

  const currentMemory = card.stability !== null
    ? { stability: card.stability, difficulty: card.difficulty }
    : null;

  // Get updated memory state
  const result = scheduleReview(
    currentMemory,
    daysElapsed,
    rating,
    config.desired_retention,
    config.fsrs_weights,
  );

  // Determine next state
  let nextState: string;
  let nextDue: Date;
  let learningStepIndex: number | null = null;

  if (card.state === 'new' || card.state === 'learning' || card.state === 'relearning') {
    const steps = card.state === 'relearning'
      ? config.relearning_steps
      : config.learning_steps;

    if (rating === 4) {
      // Easy always graduates
      nextState = 'review';
      nextDue = addDays(now, config.graduating_interval_easy);
    } else if (card.state === 'new' || shouldAdvanceToNextStep(card, rating, steps)) {
      const nextStepIdx = getNextStepIndex(card, rating, steps);
      if (nextStepIdx >= steps.length) {
        // Graduated
        nextState = 'review';
        nextDue = addDays(now, rating === 1 ? 1 : config.graduating_interval_good);
      } else {
        nextState = card.state === 'relearning' ? 'relearning' : 'learning';
        learningStepIndex = nextStepIdx;
        nextDue = addSeconds(now, steps[nextStepIdx]);
      }
    } else {
      nextState = card.state === 'relearning' ? 'relearning' : 'learning';
      learningStepIndex = 0;
      nextDue = addSeconds(now, steps[0]);
    }
  } else {
    // Review state
    if (rating === 1) {
      nextState = 'relearning';
      learningStepIndex = 0;
      nextDue = config.relearning_steps.length > 0
        ? addSeconds(now, config.relearning_steps[0])
        : addDays(now, 1);
    } else {
      nextState = 'review';
      nextDue = addDays(now, result.scheduledDays);
    }
  }

  // Persist to DB
  await updateCard(cardId, {
    stability: result.memory.stability,
    difficulty: result.memory.difficulty,
    state: nextState,
    due: nextDue,
    scheduled_days: nextState === 'review' ? result.scheduledDays : null,
    elapsed_days: daysElapsed,
    learning_step_index: learningStepIndex,
    reps: card.reps + 1,
    lapses: rating === 1 ? card.lapses + 1 : card.lapses,
    last_review: now,
  });

  await insertReviewLog(cardId, rating, daysElapsed, result.memory, card.state);
}
```

---

## 8. Interval Fuzz

Fuzz randomizes intervals slightly to prevent cards from always being reviewed on the same day ("review clustering"). The fuzz delta grows progressively smaller as a fraction of the interval:

| Interval range | Fuzz factor |
|---|---|
| < 2.5 days | No fuzz |
| 2.5 – 7 days | ±15% of range |
| 7 – 20 days | ±10% of range |
| > 20 days | ±5% of range |

**Example** for a 10-day interval:
- Range 2.5–7: 0.15 × (7 − 2.5) = 0.675
- Range 7–10: 0.10 × (10 − 7) = 0.3
- Base = 1.0
- Total delta = 1.975
- Bounds: [8, 12] days

Without fuzz, all cards reviewed on the same day cluster at the same future dates. Use a deterministic seed based on card ID so fuzz is reproducible.

---

## 9. Common Spacing Problems & Fixes

### Problem: Intervals are too short / too many reviews

**Causes & fixes** (in order of impact):

1. **Lower `desired_retention`**: `0.9` → `0.85` roughly halves the review load.
   - 0.90 = standard, sustainable
   - 0.85 = 30–40% fewer reviews
   - 0.80 = 50–60% fewer reviews

2. **Missing `daysElapsed`**: If `daysElapsed` is always 0 when it should be > 0, all reviews
   are treated as same-day reviews, resetting stability to very low values. **Verify that
   `daysElapsed` is being correctly computed from `last_review` timestamp.**

3. **Stability not persisted**: If `stability` and `difficulty` are not being saved back to the
   DB after each review, every review recalculates from scratch as if the card is new.

4. **Wrong state detection**: If cards in `review` state are accidentally handled as `learning`
   state, the FSRS formulas aren't applied and intervals stay at learning-step lengths.

### Problem: Intervals are too long / card is forgotten before review

1. **Raise `desired_retention`**: Try `0.92`–`0.95`.

2. **Check stability values**: If stability is very high (e.g. 500+ days for a new card), you
   may have a bug where `stabilityAfterRecall` uses `daysElapsed = 0` incorrectly, triggering
   the short-term formula and stacking multipliers on the first review.

3. **Ensure `daysElapsed` rounds to whole days**: FSRS uses integer days, not fractional. Use
   `Math.floor()` not `Math.round()` when computing days since last review.

### Problem: Difficulty is always maxing out at 10

1. **Wrong rating mapping**: Confirm `rating === 1` maps to "Again" (fail), not "Easy". Anki
   uses `1=Again, 2=Hard, 3=Good, 4=Easy`.

2. **Not applying mean reversion**: The `nextDifficulty` function includes a mean reversion
   term (`w[7] * ...`). If this is missing, difficulty will drift to extremes.

### Problem: New cards have insanely high initial stability

Make sure `initStability(rating, w)` is only called when `current === null` (new card, no prior
memory state). If you accidentally call it every review, stability resets to `w[0]`–`w[3]`.

### Problem: `NaN` or `Infinity` in stability/difficulty

Check for:
- Division by zero in `nextIntervalDays` when `stability ≈ 0`
- `Math.pow(0, negative)` when `D = 0` in `stabilityAfterForgetting` (clamp D to [1, 10])
- `Math.exp` overflow — clamp intermediate values where needed

---

## 10. Supabase Migration Checklist

### Step 1: Add columns to existing cards table
```sql
ALTER TABLE cards
  ADD COLUMN IF NOT EXISTS stability     FLOAT,
  ADD COLUMN IF NOT EXISTS difficulty    FLOAT,
  ADD COLUMN IF NOT EXISTS state         TEXT NOT NULL DEFAULT 'new',
  ADD COLUMN IF NOT EXISTS due           TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS scheduled_days INT,
  ADD COLUMN IF NOT EXISTS elapsed_days  INT,
  ADD COLUMN IF NOT EXISTS lapses        INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_review   TIMESTAMPTZ;
```

### Step 2: Create review log table
```sql
CREATE TABLE IF NOT EXISTS review_log (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_id      UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  user_id      UUID NOT NULL,
  rating       SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 4),
  state        TEXT NOT NULL,
  elapsed_days INT,
  stability    FLOAT,
  difficulty   FLOAT,
  reviewed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS review_log_card_idx ON review_log(card_id, reviewed_at DESC);
```

### Step 3: Create deck config table
```sql
CREATE TABLE IF NOT EXISTS deck_configs (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL REFERENCES auth.users(id),
  desired_retention   FLOAT NOT NULL DEFAULT 0.9,
  new_cards_per_day   INT NOT NULL DEFAULT 20,
  max_reviews_per_day INT NOT NULL DEFAULT 200,
  learning_steps      INT[] NOT NULL DEFAULT '{60,600}',
  relearning_steps    INT[] NOT NULL DEFAULT '{600}',
  fsrs_weights        FLOAT[] NOT NULL DEFAULT '{0.212,1.2931,2.3065,8.2956,6.4133,0.8334,3.0194,0.001,1.8722,0.1666,0.796,1.4835,0.0614,0.2629,1.6483,0.6014,1.8729,0.5425,0.0912,0.0658,0.1542}'
);
```

### Step 4: Queue query for due cards
```sql
-- Get cards due for review, new cards up to daily limit
SELECT c.*
FROM cards c
WHERE c.user_id = $1
  AND (
    -- Due reviews
    (c.state IN ('review', 'learning', 'relearning') AND c.due <= now())
    OR
    -- New cards (up to daily limit, handled in app layer)
    (c.state = 'new')
  )
ORDER BY
  CASE c.state
    WHEN 'learning'   THEN 0  -- learning cards first
    WHEN 'relearning' THEN 0
    WHEN 'review'     THEN 1
    WHEN 'new'        THEN 2
  END,
  c.due ASC NULLS LAST
LIMIT 100;
```

### Step 5: RLS Policies
```sql
ALTER TABLE cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE review_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY cards_user ON cards
  FOR ALL USING (user_id = auth.uid());

CREATE POLICY review_log_user ON review_log
  FOR ALL USING (user_id = auth.uid());
```

### Step 6: Verify data integrity after migration
```sql
-- Check for cards stuck in wrong state
SELECT state, COUNT(*) FROM cards GROUP BY state;

-- Check for cards with memory state but in 'new' state (data inconsistency)
SELECT COUNT(*) FROM cards
WHERE state = 'new' AND (stability IS NOT NULL OR difficulty IS NOT NULL);

-- Check for cards in review with NULL stability (missing memory state)
SELECT COUNT(*) FROM cards
WHERE state = 'review' AND stability IS NULL;
```

---

## Quick Reference

### Formula Summary

| Formula | Equation |
|---|---|
| Retrievability | `R = (t/S * factor + 1)^(-decay)`, `factor = 0.9^(1/decay) - 1` |
| Next interval | `I = S/factor * (R_target^(1/decay) - 1)` |
| Init stability | `S_0 = w[rating-1]` |
| Init difficulty | `D_0 = w[4] - exp(w[5]*(r-1)) + 1`, clamped [1,10] |
| Stability recall | `S * (exp(w8)*(11-D)*S^(-w9)*(exp((1-R)*w10)-1)*hp*eb + 1)` |
| Stability forget | `w11 * D^(-w12) * ((S+1)^w13 - 1) * exp((1-R)*w14)` |
| Stability short-term | `S * exp(w17*(r-3+w18)) * S^(-w19)` (floored at S for r≥3) |
| Next difficulty | `D' = D - w6*(r-3)*(10-D)/9`, then mean-revert to easy baseline |
| Decay | `w[20] = 0.1542` (FSRS-6 default) |

### Key Tuning Levers (in order of impact)

1. `desired_retention` — start here. `0.90` is standard.
2. `learning_steps` — e.g. `[60, 600]` seconds (1 min, 10 min)
3. `graduating_interval_good` / `graduating_interval_easy` — days (1 and 4 are typical)
4. `w[0]–w[3]` (initial stabilities) — if new cards feel too easy/hard immediately
5. Full weight training — only worth doing with 1000+ review history per user

---

*Derived from Anki source: `rslib/src/scheduler/states/`, `rslib/src/scheduler/fsrs/`, and `fsrs` crate v5.1.0 (`DEFAULT_PARAMETERS`, `model.rs`, `inference.rs`).*
