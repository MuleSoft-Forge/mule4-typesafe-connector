# Publish to Anypoint Exchange

Exchange is the Studio / ACB **install** surface. Product docs stay on
**[docs.mulesoftforge.com](https://docs.mulesoftforge.com/connectors/mule4-typesafe-connector/)** —
never mirror the full website into Exchange.

Maven Central (`com.mulesoftforge`) remains the open coordinate. Profile `exchange` only
rewrites `groupId` to the Anypoint organization id required by the Exchange Maven Facade.

**Verified (2026-09-28):** private org publish of Central `1.0.0` bits with TypeSafe icon + Home
pointer. After a hard-delete of Exchange `1.0.0`, the live smoke asset is **1.0.1** (same bits):
https://anypoint.mulesoft.com/exchange/eca25329-9592-4ff1-9054-1b08d103b991/mule4-typesafe-connector/1.0.1

## Prerequisites

- **JDK 17** on `PATH` for the deploy (parent `mule-java-extension-parent` / extension compile).
  Newer JDKs (e.g. 27) fail the connector build.
- Maven 3.8+ (3.9.8+ recommended)
- Anypoint org with Exchange Contributor or Administrator
- Local credentials (never commit):
  - `ANYPOINT_ORG_ID` — organization / business-group UUID
  - `~/.m2/settings.xml` server id **`anypoint-exchange-v3`**

### `settings.xml` (user password — verified working)

```xml
<server>
  <id>anypoint-exchange-v3</id>
  <username>YOUR_ANYPOINT_USERNAME</username>
  <password>YOUR_ANYPOINT_PASSWORD</password>
</server>
```

### `settings.xml` (Connected App)

Preferred when the app has **Exchange Contributor** (or Administrator) and
`client_credentials` succeeds. Username `~~~Client~~~`, password `CLIENT_ID~?~CLIENT_SECRET`.

In this workspace: `.credentials/credentials.json` → `mulesoft.*` (and optionally
`MuleSoft-Agent-Network-Project/credentials/anypoint.env`).

## What must be in the tree before deploy

| Path | Role |
| --- | --- |
| `icon/icon.svg` | Studio palette + packed into `META-INF/mule-artifact/icon.svg` |
| `Exchange-docs/home.md` | Exchange portal Home (**lowercase** `home.md` — `Home.md` is rejected) |
| POM `url` / `description` | Point at docs.mulesoftforge.com |
| Profile `exchange` | Facade v3 + `exchange-mule-maven-plugin` 0.1.7 |

`home.md` is only a pointer to docs.mulesoftforge.com — not a second docs tree.

## Publish (Central version → Exchange)

Publish the **same version that is on Maven Central** (live `1.0.0`). Do **not** publish
Studio-only `1.0.1-SNAPSHOTn` builds to Exchange.

Git tag `v1.0.0` is stale (pre-Forge `groupId`). Use **`main` at `1.0.0`**
(`com.mulesoftforge:mule4-typesafe-connector:1.0.0`).

```bash
export ANYPOINT_ORG_ID='your-org-uuid'
# ensure JDK 17
java -version

mvn -Pexchange help:evaluate -Dexpression=project.version -q -DforceStdout   # → 1.0.0
mvn -Pexchange help:evaluate -Dexpression=project.groupId -q -DforceStdout  # → org uuid

mvn clean deploy -Pexchange -DskipTests -DskipMunitTests -Dmaven.javadoc.skip=true
```

### Deploy pipeline (do not skip steps)

1. `exchange-pre-deploy` — opens Facade `runId` session (**HTTP 412** if missing)
2. `maven-deploy-plugin` — uploads pom / jars / mule-plugin into that session (**HTTP 500** on EOF if skipped)
3. `exchange-deploy` — uploads EOF and waits until status `completed`

### After deploy: set the Exchange card icon

The mule-plugin icon alone often leaves the Exchange card on the generic plug. PUT the same SVG
as the asset icon classifier (Basic auth or Bearer):

```bash
curl -X PUT \
  -u "$ANYPOINT_USERNAME:$ANYPOINT_PASSWORD" \
  -H 'Content-Type: image/svg+xml' \
  --data-binary @icon/icon.svg \
  "https://maven.anypoint.mulesoft.com/api/v1/organizations/${ANYPOINT_ORG_ID}/maven/${ANYPOINT_ORG_ID}/mule4-typesafe-connector/1.0.0/mule4-typesafe-connector-1.0.0-icon.svg"
```

Expect **HTTP 200**. Confirm the magenta TypeSafe mark on the asset card (not the generic connector).

## Delete / republish the same version

Soft-delete leaves a tombstone: republish of the same version fails with
“deleted asset with the same version number”.

Hard-delete the version first, then redeploy:

```bash
# soft delete (UI or API)
DELETE https://anypoint.mulesoft.com/exchange/api/v1/organizations/{ORG}/assets/{ORG}/mule4-typesafe-connector/1.0.0

# if republish is blocked, hard-delete then retry deploy
DELETE https://anypoint.mulesoft.com/exchange/api/v2/assets/{ORG}/mule4-typesafe-connector/1.0.0
  Header: Authorization: Bearer <token>
  Header: x-delete-type: hard-delete
```

Then run `mvn clean deploy -Pexchange …` and the icon PUT again.

## Exchange coordinates (Studio)

```text
groupId:    ${ANYPOINT_ORG_ID}
artifactId: mule4-typesafe-connector
version:    1.0.0
classifier: mule-plugin
```

In Studio: **Search in Exchange** inside the target org (private until the asset is made public).

## Verify checklist

1. `mvn clean verify` without `-Pexchange` stays green (`com.mulesoftforge`).
2. Plugin jar has `mule-artifact.json` (Java 17, `minMuleVersion` 4.9.0) and
   `META-INF/mule-artifact/icon.svg`.
3. Asset description / Home link → docs.mulesoftforge.com.
4. Exchange card shows the TypeSafe icon after the icon PUT.
5. Studio can add the connector from Exchange in that org.

## Do not mix with Central

| Profile     | `groupId`              | Destination                       |
| ----------- | ---------------------- | --------------------------------- |
| *(default)* | `com.mulesoftforge`    | local / reactor                   |
| `central`   | `com.mulesoftforge`    | Maven Central (signed)            |
| `exchange`  | `$ANYPOINT_ORG_ID`     | Anypoint Exchange Maven Facade v3 |

Never activate `central` and `exchange` together.

## Naming (Q7)

Public Exchange listing under the name **TypeSafe** still needs owner confirmation with the
vendor (PLAN Q7). Private org publication for install testing does not wait on that.
