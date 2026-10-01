# Demo apps

This folder is **not** a Mule / Studio project. It only groups the runnable sample apps.
Import each app from its own directory — never import this parent folder into Studio.

| App | Path | Purpose |
| --- | --- | --- |
| **typesafe-dev** | [`typesafe-dev/`](typesafe-dev/) | End-to-end exercise of every connector operation over HTTP (mock or live routes). |
| **mule4-typesafe-connector-app** | [`mule4-typesafe-connector-app/`](mule4-typesafe-connector-app/) | Studio verification app used for website / DataSense checks. See [`VERIFICATION.md`](mule4-typesafe-connector-app/VERIFICATION.md). |

Neither app is part of the connector Maven build (`mvn clean verify` does not run them).
