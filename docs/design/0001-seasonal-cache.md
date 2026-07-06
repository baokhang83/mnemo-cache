# ADR 0001 — Seasonality-Aware Cache (`mnemo-cache`)

- **Status:** Accepted (design)
- **Date:** 2026-07-06
- **Module:** `mnemo-cache`
- **Supersedes:** —

---

## Context

`mnemo-ai` is a two-module Maven library (Java 21 LTS):

- `mnemo-ai-core` — AI utilities. First scope: **LLM-call resilience helpers** (retry, rate-limit, backoff).
- `mnemo-cache` — caching utilities. First scope: **a seasonality-aware cache** (this ADR).

The problem we are solving: a cache should be allowed to consume a large amount of
memory during peak hours, but **give that memory back off-peak** (e.g. 3 AM) so other
workloads on the host can use it. The cache's maximum capacity should therefore flex on
a **time-of-day curve** defined by the operator.

The curve is fundamentally an **infrastructure concern** — it depends on the host's
memory budget, which differs per environment — so it must be tunable **without a
recompile or redeploy**.

## Decision

Build `mnemo-cache` as a **seasonality controller layered over a pluggable cache
backend**, *not* as a new cache engine.

### Core insight — don't reimplement eviction

[Caffeine](https://github.com/ben-manes/caffeine) already supports a **runtime-adjustable
maximum** via `cache.policy().eviction().ifPresent(e -> e.setMaximum(n))`. Lowering the
maximum makes Caffeine evict down to the new bound using its own (W-TinyLFU) policy.

So `mnemo-cache`'s novel contribution is **the scheduler that drives `setMaximum` along a
seasonality curve** — not the eviction algorithm. We own the interesting 10%; Caffeine
owns the hard, well-trodden 90%.

### Primary goal

**Reclaim memory off-peak** — shrinking and evicting is the point (not maximizing peak
hit-rate; we lag the curve rather than lead it).

---

## Design

### The curve is a `plateau → ramp → plateau → ramp` shape

A realistic reclaim curve is flat-and-low overnight, ramps up in the morning,
flat-and-high midday, ramps down in the evening:

```
100% ┤                                  ╭────╮
 85% ┤                          ╭───────╯    ╰──╮
     │                     ╭────╯               ╰──
 30% ┤              ╭──────╯
 10% ┤──────────────╯
     └──┬────┬────┬────┬────┬────┬────┬────┬────┬──
       0    2    4    6    8   11   14   17   20  24h
```

Because the curve mixes flat plateaus and ramps, config is expressed in terms of
**segments** (each segment states `hold` vs `ramp`), not a single global interpolation
mode.

**Ramp is the default** — with a scheduled tick lowering `setMaximum` in small steps, a
ramp spreads eviction across the whole evening. A hard step would dump a large fraction
of entries in one tick (an eviction / GC spike). Ramping is therefore correct
specifically for the reclaim goal.

### `CapacitySchedule` — the canonical model

Both configuration front-ends (below) compile to the **same** model: a pure function

```
CapacitySchedule: LocalTime -> fraction (0.0 .. 1.0)
```

The runtime (the scheduler that nudges `setMaximum`) only ever sees this function.
`DailySchedule` is the first implementation; the interface leaves room for weekly /
holiday layers later (explicitly out of scope for v1).

Effective bound at time `t`:

```
maxEntries(t) = max( minEntries, round(maxEntries * fraction(t)) )
```

### Two front-ends, one language

The verbs of the string grammar **mirror the builder method names** so the two front-ends
read as one language in two skins. The string parser drives the same builder, so there is
one canonical model and no duplicated validation.

**Style A — programmatic builder:**

```java
SeasonalCapacity.named("hotItems")
    .max(100_000).min(500).zone("Europe/Vienna")
    .tick(Duration.ofSeconds(60))
    .startAt("00:00", percent(10))   // overnight floor (required anchor)
    .holdUntil("06:00")              // flat plateau
    .rampUntil("11:00", percent(85)) // morning ramp-up
    .holdUntil("20:00")              // daytime plateau
    .rampUntil("23:00", percent(10)) // evening ramp-down
    .build();                        // last value holds until it wraps to 00:00
```

**Meta-language — VM argument / env var:**

```
-Dmnemo.cache.hotItems.schedule="zone=Europe/Vienna; max=100000; min=500; tick=60s; \
   curve=00:00@10, hold 06:00, ramp 11:00@85, hold 20:00, ramp 23:00@10"
```

Mapping: `hold`↔`holdUntil`, `ramp`↔`rampUntil`, `@`↔`percent()`.

### Grammar (meta-language)

Hand-written tokenizer, **zero parser dependencies** (stays lean).

```ebnf
schedule := ('zone=' ZONE ';')? 'max=' INT ';'
            ('min=' INT ';')? ('tick=' DURATION ';')? 'curve=' curve
curve    := anchor (',' segment)*
anchor   := TIME '@' PERCENT           ; required start point, e.g. 00:00@10
segment  := 'hold' TIME                ; carries previous %
          | 'ramp' TIME '@' PERCENT    ; linear interpolate to target %

TIME     := HH ':' MM                   ; 24h wall-clock
PERCENT  := INT                         ; 0..100
DURATION := INT ('s' | 'm')             ; e.g. 60s, 5m
ZONE     := IANA zone id                ; e.g. Europe/Vienna
```

**Zone required.** Although `zone=` is bracketed optional in the grammar above, it is
semantically **required**: a missing zone **fails fast**, never silently defaults to the
system zone (see the explicit-`ZoneId` decision). Treat the optional bracket as syntax
only.

**Wrap rule:** the curve must start with an anchor; every following segment moves forward
in time. The **last segment's value holds until it wraps around to the first anchor**, so
midnight need not be restated.

### Configuration resolution

- **Named caches.** Each `SeasonalCache` has a name; it binds to config by convention:
  - System property: `mnemo.cache.<name>.schedule`
  - Env var: `MNEMO_CACHE_<NAME>_SCHEDULE` (same grammar)
- **Precedence:** `system property → env var → programmatic`. External config **overrides
  code** — the whole point of the VM arg is ops retuning per environment without redeploy.
  Programmatic config is the default.
- **Fail-fast.** A malformed string fails **loudly at startup** with a precise message
  (e.g. `col 23: expected TIME after 'ramp'`). We do **not** silently fall back to
  "no seasonality" — that would hide the very memory blowup this feature prevents.

### Runtime

- A single `ScheduledExecutorService` ticks (default **60s**), recomputes
  `fraction(now)`, and calls `setMaximum` on the backend. `AutoCloseable` lifecycle.
- **`Clock` injected** (`java.time.Clock`) as a first-class design constraint so tests
  fast-forward through a day instead of waiting real hours.

### Cache API shape

Caffeine-like surface:

```java
V getIfPresent(K key);
void put(K key, V value);
void invalidate(K key);
V get(K key, Function<K, V> loader);   // get-or-compute
```

This `get(key, loader)` form is also the natural seam for a future `mnemo-ai-cache`
bridge (caching LLM / embedding responses).

### Observability (minimal in v1)

A `state()` snapshot:

```java
record SeasonalCacheState(
    double currentFraction,
    long   currentMaxEntries,
    long   size,
    long   evictionCount) {}
```

**Micrometer binding is an `optional` dependency** for those who want gauges; core stays
dependency-lean.

### Dependency philosophy

Near-zero required deps in each module. Integrations (Caffeine, Micrometer) are `optional`
/ opt-in. `mnemo-cache` and `mnemo-ai-core` are independent; a caching-for-AI bridge, if
built, is a separate module so both cores stay clean.

---

## Consequences

- ✅ Small, defensible surface — we schedule capacity; Caffeine does eviction.
- ✅ Ops can retune the curve per environment without redeploy.
- ✅ Ramp default keeps eviction/GC load smooth during shrink.
- ⚠️ **RSS caveat (must be documented for users):** evicting entries frees heap
  **within the JVM** immediately (other in-process workloads can reuse it), but returning
  memory **to the OS** only happens if the GC uncommits heap — on JDK 21 that means G1
  (periodic uncommit) or ZGC (`-XX:ZUncommit`, default on). Users must not expect RSS to
  drop without the right collector/flags. This is core to the value prop and a common
  misunderstanding.
- ⚠️ A background thread + lifecycle to manage (mitigated: one shared scheduler,
  `AutoCloseable`).

## Out of scope for v1

- Weight-based maximum (entry-count only for now; `CapacitySchedule` model leaves room).
- Weekly / holiday / multi-layer seasonality (daily-only, wrapping).
- Leading the curve / predictive pre-warming (goal is reclaim, so we lag).
- Redis / distributed backends (Caffeine + in-memory first; backend seam is pluggable).

## Open questions

_(none blocking — reopen here as they arise)_
