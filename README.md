<p align="center">
  <img src="banner.png" alt="mnemo-cache" width="100%" />
</p>

[![build](https://github.com/baokhang83/mnemo-cache/actions/workflows/build.yml/badge.svg)](https://github.com/baokhang83/mnemo-cache/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.baokhang83.mnemo/mnemo-cache.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.baokhang83.mnemo/mnemo-cache)
[![Test Coverage](https://cdn.hackernoon.com/images/2hVuiN1gfbdO9OXUxjCttPNETq73-2022-08-08T22:19:48.257Z-cl6lbgrz6002p0as65v8sfgja)](https://github.com/baokhang83/mnemo-cache/)

A **seasonality-aware cache** for the JVM whose maximum capacity flexes on a daily
time-of-day curve, to reclaim memory off-peak. Lean, dependency-light, Java 21.

## How it works

A cache should be allowed to use a lot of memory at peak hours and **give it back off-peak**
so other workloads on the host can use it. `mnemo-cache` lets you describe capacity as a
time-of-day curve:

```
100% ┤                                  ╭────╮
 85% ┤                          ╭───────╯    ╰──╮
     │                     ╭────╯               ╰──
 30% ┤              ╭──────╯
 10% ┤──────────────╯
     └──┬────┬────┬────┬────┬────┬────┬────┬────┬──
       0    2    4    6    8   11   14   17   20  24h
```

It does **not** reimplement eviction. It layers over [Caffeine](https://github.com/ben-manes/caffeine)
and drives its runtime-adjustable maximum along the curve — Caffeine still owns eviction
(W-TinyLFU); `mnemo-cache` owns the schedule.

### Install

Available on Maven Central:

```xml
<dependency>
  <groupId>io.github.baokhang83.mnemo</groupId>
  <artifactId>mnemo-cache</artifactId>
  <version>0.1.0</version>
</dependency>

<!-- Caffeine is an optional dependency; add it to enable the backend -->
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>3.1.8</version>
</dependency>
```

### Configure the curve — two front-ends, one model

The curve is `plateau → ramp → plateau → ramp`: each segment either **holds** the previous
level flat or **ramps** linearly to a new one. Ramping is the default because it spreads
eviction smoothly as capacity shrinks.

> **Sizing is by entry count.** `max` and `min` are **numbers of cached entries** — the same
> unit as Caffeine's `maximumSize`, not bytes. The percentages on the curve scale the entry
> ceiling between `min` and `max` (e.g. `max=100000` at `85%` → 85 000 entries, never below
> `min`). Weight- or byte-based sizing is out of scope for v1.

**Programmatically:**

```java
import static io.github.baokhang83.mnemo.cache.schedule.SeasonalCapacity.percent;

ScheduleSpec spec = SeasonalCapacity.named("hotItems")
    .max(100_000).min(500).zone("Europe/Vienna").tick(Duration.ofSeconds(60))
    .startAt("00:00", percent(10))   // overnight floor
    .holdUntil("06:00")              // flat plateau
    .rampUntil("11:00", percent(85)) // morning ramp-up
    .holdUntil("20:00")              // daytime plateau
    .rampUntil("23:00", percent(10)) // evening ramp-down
    .build();
```

**Or as a string** — the same grammar on a single line, for tuning per environment without a
redeploy. Supply it as either a **JVM system property** or an **environment variable**:

```bash
# JVM system property — key: mnemo.cache.<name>.schedule
-Dmnemo.cache.hotItems.schedule="zone=Europe/Vienna; max=100000; min=500; tick=60s; \
   curve=00:00@10, hold 06:00, ramp 11:00@85, hold 20:00, ramp 23:00@10"

# Environment variable — key: MNEMO_CACHE_<NAME>_SCHEDULE
export MNEMO_CACHE_HOTITEMS_SCHEDULE="zone=Europe/Vienna; max=100000; min=500; tick=60s; \
   curve=00:00@10, hold 06:00, ramp 11:00@85, hold 20:00, ramp 23:00@10"
```

The env-var key is the cache name **upper-cased, with any non-alphanumeric character replaced
by `_`**, wrapped as `MNEMO_CACHE_<NAME>_SCHEDULE` — so `hotItems` becomes
`MNEMO_CACHE_HOTITEMS_SCHEDULE`.

The verbs match the builder: `hold`↔`holdUntil`, `ramp`↔`rampUntil`, `@`↔`percent()`.

**Resolution precedence** is **system property → environment variable → programmatic**, so
external config always overrides code:

```java
ScheduleSpec effective = ScheduleResolver.resolve("hotItems", spec);
// property key: mnemo.cache.hotItems.schedule
// env var key:  MNEMO_CACHE_HOTITEMS_SCHEDULE
```

A malformed string **fails fast** with a message naming the bad fragment — it never silently
falls back to "no seasonality".

### Use the cache

```java
SeasonalCache<String, byte[]> cache = SeasonalCache.start(effective);

cache.put("k", value);
byte[] hit = cache.getIfPresent("k");
byte[] loaded = cache.get("k2", key -> expensiveLoad(key)); // get-or-compute

SeasonalCacheState s = cache.state();
// s.currentFraction(), s.currentMaxEntries(), s.size(), s.evictionCount()

cache.shutdown(); // stops the background scheduler
```

A single daemon thread samples the curve every `tick` and adjusts the backend's maximum.
Call `shutdown()` when the cache is no longer needed. It's typically a long-lived singleton
— in Spring, expose it as a `@Bean(destroyMethod = "shutdown")` so it stops with the context.

### ⚠️ Note on reclaiming memory

Lowering capacity evicts entries, which frees heap **inside the JVM** immediately — other
in-process workloads can reuse it right away. Returning memory **to the OS** (for other
processes) only happens if the garbage collector uncommits heap. On JDK 21 that means
**G1** (periodic uncommit) or **ZGC** (`-XX:ZUncommit`, on by default). Don't expect the
process RSS to drop without the right collector.

## Build

```bash
mvn verify   # compile + test
```

Requires JDK 21+. CI builds on JDK 21 and 25 (see [`.github/workflows/build.yml`](.github/workflows/build.yml)).

## Design

See [`docs/design/0001-seasonal-cache.md`](docs/design/0001-seasonal-cache.md) for the full
architecture decision record.

## License

[Apache License 2.0](LICENSE).
