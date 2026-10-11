# Synchronization diagnostics

Install the service-specific journal settings with `bash scripts/install-netcup-logging.sh`.
The game uses the namespace after its next restart. This does not change other services' logs.

Search an error ID with:

```sh
sudo journalctl --namespace=seabattle -u seabattle --since '3 days ago' --grep 'ERROR_ID' -o short-iso-precise
```

`motion-sync-failed` includes the ID, UTC, requested and server positions when applicable,
and a bounded client history (up to 24 records / 30 seconds). The recovery event repeats
the failure context, so it can reach the server after a network outage. This is client
reported evidence, not an authoritative server trace. Death/respawn events carry the
same player/ship identifiers. Successful individual position corrections are silent.

The journal is capped at 128 MiB, rotates at 8 MiB or one hour, and retains two days.
An hourly cleanup rotates even idle journals and vacuums expired files. The two-day
target leaves margin below the requested three-day maximum while the server is running.
An offline machine cannot execute cleanup; it runs again after boot. Older entries in
the pre-existing shared system journal are not deleted by this installation.

Client event payloads are bounded and limited to 40 events per player per ten seconds.
The periodic verbose performance logger remains disabled unless explicitly enabled.
