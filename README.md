<!-- icon banner: icon/icon.svg -->
# TypeSafe Connector for Mule 4

The connector wraps TypeSafe's **System One** API. Its default model is **Jev**
(`jev-latest`), TypeSafe's flagship System One model; the model is a setting, the same
way an OpenAI connector wraps OpenAI and lets you pick GPT models.

Jev is a decision model, not a chat model. You give it a **state** plus named, typed
**questions** — Noul (yes/no probability), Choice (option + distribution) or Score
(ordered rubric) — and it returns one typed **answer** per question. This connector makes
those answers first-class Mule values that drive Choice routers, Batch filters, error
handlers and follow-up calls.

> **Status:** version **1.0.0**. The initial GitHub release includes the connector skeleton,
> transport and decision engine, all five routes plus failover, the full
> decide/policy/utility operation set with DataSense, and scale operations plus governance —
> cache, budget guard, stats and the three monitoring sources. See [`PLAN.md`](PLAN.md) §15
> for the roadmap and
> [`CHANGELOG.md`](CHANGELOG.md) for what has shipped.

## Contents

- [Concepts](#concepts) · [Routes](#routes) · [Operations](#operations) · [Sources](#sources)
- [Requirements](#requirements) · [Maven](#maven) · [Quick start](#quick-start)
- [Demo app](#demo-app) · [Building](#building) · [License](#license)

## Concepts

| Term | Meaning |
| --- | --- |
| **State** | The JSON context you are deciding about (a ticket, an order, a message). |
| **Question** | A named, typed ask: `noul`, `choice` or `score`. |
| **Answer** | The typed result for a question, enriched with a `derived` block and, where the route supports it, `confidence` and a probability distribution. |
| **Question set** | A reusable file of questions (and an optional `policy` block) on the classpath under `questions/`. |
| **Policy** | Thresholds that turn answers into an `ACCEPT` / `REVIEW` / `REJECT` action, evaluated locally. |

## Routes

The connector calls Jev through five interchangeable routes plus a keyless `mock` route
for testing. All four hosted routes speak the TypeSafe `systemOne` contract; Cloudflare
nests the request under `input`.

| Route | Auth | Data path |
| --- | --- | --- |
| TypeSafe (direct) | Bearer key | Sends state to TypeSafe |
| OpenRouter | Bearer key | Sends state to OpenRouter → TypeSafe |
| Vercel AI Gateway | Bearer key / OIDC | Sends state to Vercel → TypeSafe |
| Compatible gateway | Bearer key | Sends state to the configured gateway |
| Cloudflare Workers AI | Bearer token | Sends state to Cloudflare Workers AI |
| Mock | none | Stays in-process; for tests and demos |

Each keyed route accepts an ordered list of **fallback routes** used on connectivity,
rate-limit, overload or timeout errors (never on validation or authorization failures).

## Operations

Eleven operations across four families. "Billed" operations make a provider call; the rest
are local.

### Decide — the billed decision operations

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Decide] Evaluate** | `evaluate` | ✔ | The workhorse. Runs a **full question set** (many typed questions) against the route in a single call and returns `{model, answers}`, each answer enriched with a `derived` block. Supply questions inline or by naming a classpath question-set file; DataSense then types the output (e.g. `payload.answers.team.choice`). Attributes carry provider, usage, cost, timing and a `traceEntry`. |
| **[Decide] Ask Yes/No** | `ask-noul` | ✔ | Single **Noul** shortcut. Asks one yes/no question and returns just that answer, so a flow reads `payload.noul` (the probability of "yes") directly. Use for a quick boolean judgement with a probability (e.g. "is this urgent?"). |
| **[Decide] Choose** | `choose` | ✔ | Single **Choice** shortcut over a fixed set of options; returns the chosen option and its probability distribution. Use for classification / routing into one of N labels, with an optional no-match option. |
| **[Decide] Score** | `score` | ✔ | Single **Score** shortcut over ordered levels (a rubric); returns the level and `derived.level`. Use for grading on an ordered scale — severity, sentiment, priority. |
| **[Select] Candidate** | `select-candidate` | ✔ | Turns a list of **upstream rows** (DB records, Salesforce queues, search hits) into a dynamic Choice: each candidate becomes an option keyed by `idField` and described by `labelField`/`descriptionField`. Returns the selected candidate object, its probability and confidence, a no-match flag, and the full ranking. Use to let Jev pick the best match from runtime data. |

### Scale — many states at once

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Decide] Evaluate Batch** | `evaluate-batch` | ✔ | Runs one question set over **many states**, fanned out with at most `maxConcurrency` calls in flight. Identical states are evaluated once (`deduplicate`), each item is budget-checked (a limit turns later items into `SKIPPED_BUDGET` rather than failing the batch), and cache/stats apply per item. Returns an array of `{index, key, status, answers, error}`; attributes carry the totals, with usage and cost billed once per unique decision. Rejects batches over `maxItems` with `TYPESAFE:BATCH_TOO_LARGE` — use a Mule Batch Job beyond that. |
| **[Select] Filter** | `filter` | ✔ | Keeps the items for which a yes/no question clears a probability `threshold`, packing several items per call (`chunkSize`) so a long list costs a handful of calls. Returns `{kept, dropped, scores}`. |

### Policy — local governance

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Policy] Apply** | `apply-policy` | ✗ | Turns a decision into an `ACCEPT` / `REVIEW` / `REJECT` action plus a `routeKey` ready for a `<choice>` router, judged against policy thresholds supplied inline or from a question-set file's `policy` block. Pure local evaluation — no provider call, no connection. Can optionally raise `TYPESAFE:BELOW_THRESHOLD` on REVIEW or `TYPESAFE:REJECTED` on REJECT for error-based routing. |

### Utility — discovery and validation

| Operation | Alias | Billed | Purpose |
| --- | --- | :---: | --- |
| **[Util] Connection Get Capabilities** | `get-capabilities` | ✗ | Not a TypeSafe API call. Exists because this connector can front several suppliers of the same contract. Reports what the primary route and each fallback on the connection support. A direct TypeSafe connection is the full set, so the operation is only useful when a route might differ. |
| **[Util] Connection List Models** | `list-models` | ✔ | Lists the models available on the connected routes, primary first. Calls `GET /{apiVersion}/models` on each route that can list models. |
| **[Util] Validate Question Set** | `validate-question-set` | ✗ | Not a connection operation and not an HTTP call. Checks a question-set document on this machine before any billed call. Payload is `{valid, errors, warnings}`. Errors are the hard API limits; warnings flag legal-but-risky sets. |

## Sources

Three polling sources turn governance signals into flow triggers. None of the routes pushes
events, so business triggers stay with existing connectors (Salesforce CDC, Anypoint MQ,
Scheduler); these three watch the connector's own privacy-safe counters — never state text.
Each carries a `<scheduling-strategy>` (default fixed frequency 60 s), runs on the primary
cluster node, fires **once per breach**, and re-arms when the metric returns inside its
threshold.

| Source | Alias | Fires when | Payload |
| --- | --- | --- | --- |
| **On Drift Detected** | `on-drift-detected` | A monitored metric — no-match rate, mean confidence or the choice/level distribution (Jensen–Shannon, bounded 0–1) — moves past its threshold versus a baseline window (first or previous). | `{metric, baseline, current, windowSize, questionSetId, questionId, windowEnd}` |
| **On Budget Threshold** | `on-budget-threshold` | Usage in the current budget window crosses `percent` of the `CALLS` or `INPUT_TOKENS` limit. | `{metric, used, limit, percent, estimatedCostUsd, windowStart}` |
| **On Provider Failover** | `on-provider-failover` | A request the primary route could not serve was answered by a fallback (event stream, one item per failover). | `{from, to, reason, errorType, timestamp}` |

## Requirements

- Java 17
- Mule Runtime ≥ 4.9.0

## Maven

```xml
<dependency>
  <groupId>com.mulesoft.connectors</groupId>
  <artifactId>mule4-typesafe-connector</artifactId>
  <version>1.0.0</version>
  <classifier>mule-plugin</classifier>
</dependency>
```

## Quick start

```xml
<typesafe:config name="TypeSafe_Config">
  <typesafe:openrouter-connection apiKey="${typesafe.openrouter.apiKey}" />
</typesafe:config>

<flow name="triage">
  <http:listener config-ref="HTTP_Listener_config" path="/triage" />
  <typesafe:evaluate config-ref="TypeSafe_Config" questionSet="ticket-triage" step="triage">
    <typesafe:state>#[payload]</typesafe:state>
  </typesafe:evaluate>
  <typesafe:apply-policy config-ref="TypeSafe_Config" questionSet="ticket-triage" target="decision">
    <typesafe:decision>#[payload]</typesafe:decision>
  </typesafe:apply-policy>
  <choice>
    <when expression="#[vars.decision.action == 'ACCEPT']"> <!-- auto-route --> </when>
    <when expression="#[vars.decision.action == 'REVIEW']"> <!-- send to a human --> </when>
    <otherwise> <!-- reject --> </otherwise>
  </choice>
</flow>
```

Never hard-code credentials — read them from a property (e.g. `${typesafe.openrouter.apiKey}`).

## Demo app

A complete, runnable app that exercises **every** operation over HTTP lives in
[`demo/typesafe-dev`](demo/typesafe-dev). It includes step-by-step instructions for both **Anypoint
Studio** and **Anypoint Code Builder**, a logged flow per operation, and an offline
smoke-test endpoint. See [`demo/typesafe-dev/README.md`](demo/typesafe-dev/README.md).

The demo is standalone and is not wired into the connector build, so it never affects
`mvn clean verify`.

## Building

```bash
mvn clean verify
```

The build runs the formatter, import sort and checkstyle gates, the JUnit suite and the
MUnit reference-flow suite. To auto-format before verifying:

```bash
mvn net.revelc.code.formatter:formatter-maven-plugin:format net.revelc.code:impsort-maven-plugin:sort
```

To make a local build available to a consuming app (such as the demo), install it:

```bash
mvn clean install -DskipTests -DskipMunitTests
```

## License

Apache-2.0. See [`LICENSE.txt`](LICENSE.txt).
