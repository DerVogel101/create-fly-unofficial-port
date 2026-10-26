# Local checks

Use JDK 25 and Python 3 from the repository root. Test mods, logs and disposable
worlds stay under `build/local-smoke/`; the test mods are never included in the
normal Create jar. Servers bind only to localhost on an automatically chosen port.

```powershell
python .github/smoke-test.py --self-test
python .github/smoke-test.py --checks --online
python .github/smoke-test.py --online
```

Omit `--online` once Gradle dependencies are cached. Fabric/Minecraft server
downloads can still require network access for a fresh test world.

`--checks` builds the production jar and runs three server probes:

- Six plough checks: actual tilling, the port's playerless offhand use, and
  unchanged player/shield/sneaking behavior. The old port used a null-player
  main-hand Mixin guard; this port deliberately uses the offhand instead.
- Nine schedule checks: NBT and packet roundtrips, menu opening, saving the held
  item, instruction execution, delay advancement and departure predictions.
- Seven inventory checks: dense and sparse contents, clearing stale slots,
  empty contents, and the linked-controller, list-filter and package callers.

The default mode opens one test client, runs the carried-over machine scene,
requires all seven gameplay success markers, and verifies server restart and the
saved contraption. GUI mouse/keyboard playback, screenshots and Ponder remain
outside this local runner's coverage. It does not test a full train route.

Every run writes `result.json` with the exact built jar and its SHA-256, alongside
the build and server/client logs. Required failures return a nonzero exit code.
The CI workflow runs the runner self-test and server probes and retains logs even
on failure; it does not publish releases or write a log branch.
