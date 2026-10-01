# Changelog

All notable changes to the TypeSafe Connector are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project uses semantic versioning.

## [Unreleased]

### Fixed
- **`apply-policy` `raiseOnReject` / `raiseOnReview` never worked in 1.0.1.** The operation threw
  `TYPESAFE:REJECTED` / `TYPESAFE:BELOW_THRESHOLD`, but those types were not declared on `@Throws`, so Mule rewrote
  them as `MULE:UNKNOWN` and error handlers for the typed errors never matched. The same undeclared-error rewrite hit
  `INVALID_QUESTION_SET`, `INVALID_STATE` and `TOO_MANY_OPTIONS` from `evaluate`, the shortcuts, `select-candidate`
  and `validate-question-set`. `apply-policy` now has its own error provider; the decision provider lists the
  validation errors. MUnit covers both `raiseOnReject` → `TYPESAFE:REJECTED` and an invalid policy →
  `TYPESAFE:INVALID_QUESTION_SET`.
- **Sample question sets rejected routine and angry tickets.** `ticket-triage` and `support-ticket-triage` used
  `rejectBelow` (so a clear "no" on urgency rejected the whole decision) and left "Very negative" sentiment
  unlisted (so it rejected). They now use three-band Noul (`onNo: ACCEPT`) and put negative sentiment on
  `reviewLevels`. The MUnit reference flow asserts `REVIEW` explicitly instead of treating `REJECT` as review.
- **`apply-policy` and `select-candidate` DataSense.** Both declare all four message parts. `select-candidate` was
  missing input/output resolvers and input attributes entirely.
- **Filter packed untrusted item text into instructions.** Chunks now put items in `state.items` and ask about
  `items[i]`, matching TypeSafe's packing pattern.
- **Drift ignored Noul-only traffic.** Stats now treat Noul certainty (`|noul − 0.5| × 2`) like confidence and record
  yes/no/uncertain bands for distribution shift, so On Drift Detected works without Choice/Score confidence.
- **`demo/` was accidentally a Studio project.** Local `.project` / `.classpath` made both sample apps look nested
  inside a parent Mule project. `demo/` is now a plain scoping folder with a README; import each app from its own
  directory.

### Added
- **Per-option Choice policy rules.** A Choice rule may set `options.<id>` with its own `action`, `minProbability`,
  `minConfidence` and `minMargin`, plus `otherOptions` for a chosen option that is not listed — so riskier options can
  demand higher confidence.
- **Three-band Noul policy rules.** Prefer `yesAbove` / `noBelow` with `onYes` / `onNo` / `onUncertain` (defaults
  `ACCEPT` / `ACCEPT` / `REVIEW`). Legacy `acceptAbove` / `rejectBelow` still works but must not be mixed with the
  three-band form.
- **`policy.routeQuestion`.** Names which Choice supplies `routeKey`, so reordering questions no longer silently changes
  routing. Falls back to the first Choice answer when unset.
- **Filter uncertain band.** Optional `dropBelow` below `threshold` partitions items into `kept` / `uncertain` /
  `dropped`. Scores include `band` and `|noul−0.5|`-style middle cases are no longer forced into keep or drop.

### Changed
- **`apply-policy` fails closed.** It used to return `ACCEPT` whenever it had nothing to judge. Each of these is now
  `REVIEW` with a reason: a decision with no answers (for example `{}` or the wrong variable), a policy rule whose
  question has no answer (for example a shortcut answer judged against a question-set policy), an answer of unknown
  type, and a rule whose keys do not fit its answer's type. Flows that relied on a silent `ACCEPT` now see `REVIEW`.
- **`apply-policy` validates the policy before applying it** and raises `TYPESAFE:INVALID_QUESTION_SET` on a
  misspelt key, a rule for a question id that does not exist, keys for the wrong question type, a threshold that is
  not a number from 0 to 1, an action other than `ACCEPT`/`REVIEW`/`REJECT`, inverted Noul bounds, a Score level out
  of range or in both lists, or a Score rule with no levels. A file policy is checked against the file's questions;
  an inline policy is checked for structure.
- **`validate-question-set` checks a file's `policy` block** with the same rules, as `policy.<id>` errors and
  warnings. It warns when legacy `rejectBelow` turns a "no" into `REJECT` for the whole decision, and when Score
  levels are neither accepted nor reviewed.

## [1.0.1] - 2026-09-28

### Added
- **Anypoint Exchange publish path ([#7](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/issues/7)).**
  Maven profile `exchange` deploys via Exchange Maven Facade v3 with `groupId` set to the Anypoint
  organization id. POM `url` / description point at
  [docs.mulesoftforge.com](https://docs.mulesoftforge.com/connectors/mule4-typesafe-connector/) as the
  documentation source of truth. Maintainer steps: [`docs/exchange-publish.md`](docs/exchange-publish.md).
  Verified by publishing live Central **`1.0.0`** (not SNAPSHOT) to a private org.

### Fixed
- **Governance sources fail to deploy (`IllegalModelDefinitionException` on `@Connection`).**
  Studio website verification (Sources audit) could not start `on-budget-threshold`,
  `on-drift-detected`, or `on-provider-failover`: each injected `@Connection TypeSafeConnection`,
  but Mule sources must inject `ConnectionProvider` and call `connect()` / `disconnect()`
  ([SDK](https://docs.mulesoft.com/mule-sdk/latest/sources-config-connection)). All three sources
  now follow that contract ([PR #18](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/pull/18)).
- **OpenRouter `list-models` empty catalog ([#13](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/issues/13)).**
  OpenRouter returns `{ data: [{ id, name, description, created }, ...] }`. The connector only read TypeSafe's
  `{ models: [{ name, description, release_date }] }`, so HTTP 200 became an empty payload. OpenRouter cards are now
  projected onto the TypeSafe contract (`name` ← `id`, `release_date` ← unix `created` as UTC ISO-8601, optional
  `display_name` ← catalog `name` when it differs from `id`). The public OpenRouter endpoint contains its whole
  458-model catalog, so its route adapter restricts results to OpenRouter's `typesafe/` vendor namespace. Catalog
  scoping is a generic route-adapter hook so other broad gateway catalogs can define their own TypeSafe namespace
  without changing the utility operation. OpenRouter model-list responses have no generation ID, so `requestId` falls
  back to their per-request `cf-ray` trace. TypeSafe responses are unchanged.
- **Studio metadata on OpenRouter config after live model list.** Design-time `ModelValueProvider` no longer dumps the
  full OpenRouter catalog into the model dropdown (hundreds of ids broke Studio metadata serialization). It keeps static
  defaults plus a capped preferred sample; runtime `list-models` still returns the full catalog.
- **Test Connection validates the API key ([#14](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/issues/14)).**
  Keyed routes run one minimal Noul `systemOne` call instead of always returning success. A rejected key fails with
  `UNAUTHORIZED (HTTP <status>): …`. Successful validation logs the credential-free HTTP method and URL; failures show
  the same target in Studio. Mock stays local with no network call.
- **Studio `Couldn't serialize MetadataKey` / `MuleMetadataKeyAdapter` after Test Connection.** Evaluate's question-set
  parameter no longer uses a `TypeKeysResolver` (SDK keys wrap as `MuleMetadataKeyAdapter`, which Studio's Gson adapter
  cannot serialize). It uses `@MetadataKeyId` + `@OfValues` instead; DataSense output typing is unchanged.

## [1.0.0] - 2026-09-27

### Changed
- **Renamed from "Jev Connector" to "TypeSafe Connector".** The connector wraps TypeSafe's
  System One API and Jev is the value of its `model` setting, so it is named after the vendor
  API, as OpenAI connectors are named for OpenAI rather than ChatGPT. The XML prefix and
  namespace are now `typesafe` (`http://www.mulesoft.org/schema/mule/typesafe`), errors are
  `TYPESAFE:*`, the artifact is `com.mulesoftforge:mule4-typesafe-connector`, the Java
  package is `com.mulesoft.connectors.typesafe`, the demo app is `demo/typesafe-dev`, and the
  GitHub repo is `MuleSoft-Forge/mule4-typesafe-connector` (old URLs redirect). `jev-latest`
  remains the default model.

### Fixed
- **Question and answer shapes now match TypeSafe.** `ask-noul`, `choose`, `score` and
  `select-candidate` sent `criteriaTrue`/`criteriaFalse`, `options` and `levels`, which
  TypeSafe rejects (422 direct, 400 via OpenRouter) or silently ignores; they now send
  `criteria`. The bundled `ticket-triage.json` uses `criteria` too, and its sentiment policy
  levels are 0-based (`acceptLevels ["2","3","4"]`, `reviewLevels ["1"]`) to match the
  0-based `legend` TypeSafe returns. `apply-policy` and `filter` read the Noul answer's
  `noul` value (they read a `probability` field TypeSafe never returns, so every yes/no
  judged as 0); `filter`'s `scores` entries are now `{index, noul, kept}`. Stats bucket
  Score answers by `derived.level` rather than the continuous `score`.
- `evaluate` and `evaluate-batch` validate questions locally before calling a route, so a
  malformed set, including `options`/`levels`/`legend`/`criteriaTrue`/`criteriaFalse`, fails
  as `TYPESAFE:INVALID_QUESTION_SET` without a billed call. The `mock` route reads only `criteria`
  and answers in TypeSafe's shapes, so keyless tests catch contract drift.
- **OpenRouter request id (M5).** Live OpenRouter `systemOne` responses carry the generation id
  as header `x-generation-id` (and body `id`), not `x-request-id`. The OpenRouter route now
  records that id on `attributes.providerRequestId`. Confirmed against a live three-question
  call on 2026-09-26; see [`docs/provider-contracts.md`](docs/provider-contracts.md).

### Added
- **M5 release.** Published `com.mulesoftforge:mule4-typesafe-connector:1.0.0`
  to Maven Central with signed source and Javadoc artifacts. Anypoint Exchange
  publication is tracked separately in
  [#7](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/issues/7).
- **Guiding principle.** `CLAUDE.md` leads with **Think: Smart if-statements** — Jev returns a
  value; the flow owns the `if`.
- **M4 — Scale & governance.** The `evaluate-batch` and `filter` scale operations, fanned out
  behind a non-blocking concurrency limit with per-item de-duplication, budgeting, caching and
  stats. Governance foundation: a decision cache, a cluster-wide `BudgetGuard` (call and
  input-token limits per rolling window, raising `TYPESAFE:BUDGET_EXCEEDED`), and a
  privacy-safe `DecisionStatsRecorder` — all backed by the runtime Object Store. Three
  monitoring polling sources — `on-drift-detected` (no-match rate, mean confidence and
  Jensen–Shannon distribution shift), `on-budget-threshold` and `on-provider-failover` —
  each firing once per breach and re-arming on recovery. New config tabs for cache and budget
  settings.
- **M3 — Decide chain.** The `evaluate`, `ask-noul`, `choose`, `score` and `select-candidate`
  decision operations; the `apply-policy` governance operation; question-set files on the
  classpath with a value provider; `validate-question-set`; DataSense output metadata so
  `evaluate` types its answers from the referenced question set; and an MUnit reference-flow
  suite that runs the §8.8 decide chain end-to-end against `mock`. Added a runnable demo app
  under [`demo/typesafe-dev`](demo/typesafe-dev).
- **M2 — Routes & failover.** All five keyed routes (TypeSafe, OpenRouter, Vercel,
  Compatible, Cloudflare), ordered fallback routes for transient failures, per-route
  capabilities, and the `list-models` operation.
- **M1 — Transport & engine.** The Mule HTTP-client transport, the non-blocking
  `DecisionEngine` with a runtime retry scheduler, and the governance/provider-contract docs.
- **M0 — Skeleton.** Project scaffolding on the forward-compatible `mule-java-extension-parent`
  (1.12.3), `min.mule.version` 4.9.0, Apache-2.0 license. Extension class `Jev` (`jev` prefix),
  single `<typesafe:config>`, `mock` connection provider and `MockAdapter`, `Capabilities` model,
  full `TYPESAFE:*` error-type enum, `[Util] Get Capabilities` operation, connector icon, and the
  formatter / impsort / checkstyle quality gates.
