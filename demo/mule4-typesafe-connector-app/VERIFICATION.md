# Website vs connector verification

Harness: follow [`website/docs/connectors/mule4-typesafe-connector/`](../../../../website/docs/connectors/mule4-typesafe-connector/) line by line in Studio. One operation at a time. Defects here → PRs.

## Protocol

1. Read the website page.
2. Copy the XML/files as shown.
3. Run; compare payload + attributes to the page.
4. Log defects. Do not patch the connector unless asked.
5. Comment out that flow with Studio’s Comment out action (`<!-- [STUDIO:"flow-name"] … [STUDIO] -->`), enable the next, report. Do not use plain XML comments — Studio will not show the flow.

## Utils progress (complete)

| # | Page | Flow | State |
| --- | --- | --- | --- |
| 1 | [Connection List Models](../../../../website/docs/connectors/mule4-typesafe-connector/operations/connection-list-models.md) | `util-connection-list-models` | **verified** (Studio-commented) |
| 2 | [Connection Get Capabilities](../../../../website/docs/connectors/mule4-typesafe-connector/operations/connection-get-capabilities.md) | `util-connection-get-capabilities` | **verified** (Studio-commented) |
| 3 | [Validate Question Set](../../../../website/docs/connectors/mule4-typesafe-connector/operations/validate-question-set.md) | `util-validate-question-set` | **verified** (Studio-commented) |

## Decide progress

| # | Page | Flow | State |
| --- | --- | --- | --- |
| 1 | [Evaluate](../../../../website/docs/connectors/mule4-typesafe-connector/operations/evaluate.md) | `decide-evaluate` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 2 | [Ask Yes/No](../../../../website/docs/connectors/mule4-typesafe-connector/operations/ask-noul.md) | `decide-ask-noul` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 3 | [Choose](../../../../website/docs/connectors/mule4-typesafe-connector/operations/choose.md) | `decide-choose` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 4 | [Score](../../../../website/docs/connectors/mule4-typesafe-connector/operations/score.md) | `decide-score` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 5 | [Select Candidate](../../../../website/docs/connectors/mule4-typesafe-connector/operations/select-candidate.md) | `select-candidate` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 6 | [Evaluate Batch](../../../../website/docs/connectors/mule4-typesafe-connector/operations/evaluate-batch.md) | `decide-evaluate-batch` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 7 | [Filter](../../../../website/docs/connectors/mule4-typesafe-connector/operations/filter.md) | `select-filter` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 8 | [Apply Policy](../../../../website/docs/connectors/mule4-typesafe-connector/operations/apply-policy.md) | `policy-apply` | **verified** (Studio-commented) — Studio 2026-09-28 |
| 9 | [Sources — On Budget Threshold](../../../../website/docs/connectors/mule4-typesafe-connector/sources.md#on-budget-threshold) | `source-on-budget-threshold` + `source-budget-driver` | **verified** (Studio-commented) — Studio 2026-09-28; also confirms [#18](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/pull/18) |
| 10 | [Sources — On Drift Detected](../../../../website/docs/connectors/mule4-typesafe-connector/sources.md#on-drift-detected) | `source-on-drift-detected` | **deferred** — recorder always rolls at 500 (`DEFAULT_WINDOW_SIZE`); source `windowSize` only gates min samples. Mock answers are constant (no confidence/distribution shift). Studio-commented. |
| 11 | [Sources — On Provider Failover](../../../../website/docs/connectors/mule4-typesafe-connector/sources.md#on-provider-failover) | `source-on-provider-failover` + `source-failover-driver` | **verified** (Studio-commented) — Studio 2026-09-28 |

## Studio run — On Provider Failover (passed)

Driver: `provider=openrouter`, `failedOverFrom=["typesafe"]` (logger then hit a DataWeave “Invalid multibyte sequence” on the array write — harness only). Source fired:

```json
{
  "from": "typesafe",
  "to": "openrouter",
  "reason": "FAILOVER",
  "errorType": null,
  "timestamp": 1790600042924
}
```

Website updated: `timestamp` is epoch ms (was wrongly documented as ISO-8601). Attributes null.

## Defect — Sources `@Connection` type — fixed ([PR #18](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/pull/18))

Studio 2026-09-28: deploy failed with `IllegalModelDefinitionException` when any source used `@Connection TypeSafeConnection`. Fixed: inject `ConnectionProvider`, `connect` in `doStart` / `disconnect` in `doStop`. Re-verified: `on-budget-threshold` started and fired after the driver.

## Studio run — On Budget Threshold (passed)

```json
{
  "metric": "CALLS",
  "used": 2,
  "limit": 2,
  "percent": 100,
  "threshold": 80,
  "estimatedCostUsd": 0.000029,
  "windowStart": 1790598932322
}
```

Website updated: `windowStart` is epoch ms (was wrongly documented as ISO-8601). Attributes null.

## Expected — On Budget Threshold

Config `TypeSafe_Config_Monitoring`: `budgetMaxCallsPerWindow=2`, `statsEnabled=true`. Source: `percent=80`, `metric=CALLS`, poll every 5s. Driver runs 2× ask-noul.

Verified above. Re-run within the same hour window may hit `TYPESAFE:BUDGET_EXCEEDED` (limit 2).

## Studio run — Apply Policy (passed)

| Route | action | routeKey | reasons |
| --- | --- | --- | --- |
| accept-fixture | ACCEPT | billing | empty |
| review-fixture | REVIEW | billing | team prob + urgent noul below 0.7 |

Website updated (`support-ticket-triage.json` + ACCEPT/REVIEW examples). Policy block added to harness question set.

## Expected — Apply Policy

Verified above.

## Studio run — Filter (passed)

| Route | kept / dropped | scores noul | attributes |
| --- | --- | --- | --- |
| TypeSafe | T-1001 / T-1002 | 0.88 / 0.21 | total=2, succeeded=1 (kept count) |
| OpenRouter | T-1001 / T-1002 | 0.90 / 0.21 | same |

Website updated. Note: on Filter, `attributes.succeeded` = kept size (not “successful evaluations”).

## Expected — Filter

Verified above.

## Studio run — Evaluate Batch (passed)

3 items (T-1001, T-1002, dup T-1001), `deduplicate=true`:

| Route | total / succeeded / cached | unique calls (from tokens) | T-1001 | T-1002 |
| --- | --- | --- | --- | --- |
| TypeSafe | 3 / 3 / 0 | ~2 | billing / urgent 0.98 | technical / urgent ~0.14 |
| OpenRouter | 3 / 3 / 0 | ~2 | billing / urgent 0.98 | technical / urgent ~0.15 |

Dedupe reused answers on index 2 with `cached: false` (flag is decision-cache only). Website updated; XML uses `support-ticket-triage.json`.

## Expected — Evaluate Batch

Verified above.

## Studio run — Select Candidate (passed)

Billing-refund query over three queues:

| Route | id | isNoMatch | probability | model |
| --- | --- | --- | ---: | --- |
| TypeSafe | billing-queue | false | 1 | `jev-1.13.0` |
| OpenRouter | billing-queue | false | 1 | `typesafe/jev-1.13-20260917` |

`selected` is the original candidate object. Ranking includes `__no_match__` (default include-no-match). Website updated.

## Expected — Select Candidate

Verified above.

## Studio run — Score (passed)

Scatter-gather on Set Up `T-1001` with website sentiment levels / `step="sentiment"`:

| Route | score | level / label | model | costSource |
| --- | ---: | --- | --- | --- |
| TypeSafe | 0.22 | 0 / very negative | `jev-1.13.0` | ESTIMATE |
| OpenRouter | 0.25 | 0 / very negative | `typesafe/jev-1.13-20260917` | PROVIDER |

Website had documented `legend` as an array; live payload is an object keyed by level index — docs corrected (W4).

## Expected — Score

Verified above.

## Studio run — Choose (passed)

Scatter-gather on Set Up `T-1001` with website options / `step="routing"`:

| Route | choice | runnerUp | model | costSource |
| --- | --- | --- | --- | --- |
| TypeSafe | billing | technical | `jev-1.13.0` | ESTIMATE |
| OpenRouter | billing | technical | `typesafe/jev-1.13-20260917` | PROVIDER |

Matches Evaluate’s team answer. Website updated. Note TypeSafe live log showed `derived.margin` as `0.6000000000000001` (float noise) — logged as C3.

## Expected — Choose

Verified above.

## Studio run — Ask Yes/No (passed)

Scatter-gather on Set Up `T-1001` with website instructions/`step="urgency"`:

| Route | noul | model | costSource | questionSetId |
| --- | ---: | --- | --- | --- |
| TypeSafe | 0.98 | `jev-1.13.0` | ESTIMATE | null |
| OpenRouter | 0.98 | `typesafe/jev-1.13-20260917` | PROVIDER | null |

Matches Evaluate’s urgency answer. Website updated with both live payloads/attributes; HTTP call `POST /{apiVersion}/systemone`.

## Expected — Ask Yes/No

Website example on Set Up `T-1001` outage ticket — verified above.

## Studio run — Evaluate (passed)

Scatter-gather TypeSafe + OpenRouter on Set Up `T-1001` + `support-ticket-triage.json`:

| Route | team.choice | urgent.noul | model | costSource | requestId |
| --- | --- | ---: | --- | --- | --- |
| TypeSafe | billing | 0.98 | `jev-1.13.0` | ESTIMATE | `req_01a0e7e17628712da124ea8c579d60a6` |
| OpenRouter | billing | 0.98 | `typesafe/jev-1.13-20260917` | PROVIDER | `gen-dec-…` |

Same `stateHash`. Answer shapes match. Website updated with both live payloads/attributes; HTTP call now `POST /{apiVersion}/systemone`.

## Findings

### Website

| ID | Page | Severity | Defect | Suggested PR |
| --- | --- | --- | --- | --- |
| W1 | ops index / policy / etc. | fixed | Hardcoded `POST /v1/systemone`. | Now `POST /{apiVersion}/systemone` on ops index; individual ops already fixed |
| W2 | Set Up release table | low | “Publishing / still publishing” may be stale. | Verify Central |
| W3 | validate-question-set.md | fixed | Example used colliding `ticket-triage.json`. | Now `support-ticket-triage.json` |
| W4 | score.md | fixed | Documented `legend` as array; live is `{ "0": "…", … }`. | Docs match Studio |

### Connector

| ID | Area | Severity | Defect | Tracking |
| --- | --- | --- | --- | --- |
| C2 | OpenRouter list-models | closed | Empty list / wrong JSON shape | [issue 13](https://github.com/MuleSoft-Forge/mule4-typesafe-connector/issues/13) — fixed in PR #16 |
