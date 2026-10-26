"""Run gameplay smoke or server regressions; see .github/checks/README.md.

Uses a fresh world under build/local-smoke; never publishes anything.
Requires Python 3, JDK 25 (JAVA_HOME), and the checkout's Gradle dependencies.
The inventory/Ponder mouse and keyboard steps remain Linux GUI checks.
"""

import json
import hashlib
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / ".github"
REQUIRED = {
    "washing-ok", "blasting-ok", "pump-drained-source", "crafter-ok",
    "contraption-ok", "deployer-ok", "funnel-extract-ok",
}


def failures(server_log, restart_log):
    checks = {
        "server startup": "Done (" in server_log,
        "client join": "joined the game" in server_log,
        "server restart": "Done (" in restart_log,
        "saved contraption": "restart-contraption-ok" in markers(restart_log),
        "command syntax": "<--[HERE]" not in server_log + restart_log,
    }
    checks.update({name: name in markers(server_log) for name in REQUIRED})
    return [name for name, passed in checks.items() if not passed]


def markers(log):
    return set(re.findall(r"\[Server\] CHECK ([\w-]+)\s*$", log, re.MULTILINE))


def self_test():
    server = "Done (1s)!\nPlayer joined the game\n" + "".join(
        f"[Server] CHECK {name}\n" for name in sorted(REQUIRED)
    )
    restart = "Done (1s)!\n[Server] CHECK restart-contraption-ok\n"
    assert not failures(server, restart)
    for broken in (server.replace("Done (", "Failed ("),
                   server.replace("joined the game", "could not join"),
                   server.replace("CHECK washing-ok", "CHECK missing")):
        assert failures(broken, restart)
    assert failures(server, "")
    assert failures(server, restart.replace("CHECK restart-contraption-ok", "CHECK missing"))
    assert failures(server + "bad command<--[HERE]\n", restart)
    assert not markers("command: execute run say CHECK washing-ok\n")
    print("PASS: startup, join, gameplay and persistence failures are rejected.")


def download(url, target):
    if not target.exists():
        print(f"Downloading {url}", flush=True)
        temporary = target.with_suffix(".part")
        with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        temporary.replace(target)


def main(checks_only=False, online=False):
    props = dict(re.findall(r"^([\w.]+)\s*=\s*(.*?)\s*$",
                           (ROOT / "gradle.properties").read_text(), re.MULTILINE))
    base = ROOT / "build/local-smoke"
    base.mkdir(parents=True, exist_ok=True)
    run = base / time.strftime("%Y%m%d-%H%M%S")
    server, client = run / "server", run / "client"
    for directory in (server / "mods", client / "mods", base / "downloads"):
        directory.mkdir(parents=True, exist_ok=True)
    print(f"Logs and disposable world: {run}", flush=True)
    init = run / "client.init.gradle"
    spec = run / "client.json"
    init.write_text('''allprojects {
    afterEvaluate {
        tasks.register("writeSmokeClient") {
            doLast {
                def client = tasks.named("runClient").get()
                new File(System.getProperty("smoke.spec")).text = groovy.json.JsonOutput.toJson([
                    executable: client.javaLauncher.get().executablePath.asFile.absolutePath,
                    jvmArgs: client.allJvmArgs, classpath: client.classpath.asPath,
                    modJar: tasks.named("jar").get().archiveFile.get().asFile.absolutePath,
                    mainClass: client.mainClass.get(), args: client.args
                ])
            }
        }
    }
}
''')
    environment = os.environ.copy()
    environment.pop("CI", None)
    processes = []
    failed = []
    creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0

    def read(name):
        path = run / name
        return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""

    def start(command, directory, name):
        with (run / name).open("w", encoding="utf-8") as output:
            process = subprocess.Popen(command, cwd=directory, env=environment,
                                       stdin=subprocess.PIPE, stdout=output,
                                       stderr=subprocess.STDOUT, text=True,
                                       encoding="utf-8", creationflags=creationflags)
        processes.append(process)
        return process

    def wait_for(process, log, expected, timeout=600, watch=()):
        deadline = time.monotonic() + timeout
        while expected not in read(log):
            if process.poll() is not None:
                raise RuntimeError(f"Process exited ({process.returncode}) before {expected!r}; see {log}")
            if any(other.poll() is not None for other in watch):
                raise RuntimeError(f"Client exited before {expected!r}; see client.log")
            if time.monotonic() >= deadline:
                raise RuntimeError(f"Timed out waiting for {expected!r}; see {log}")
            time.sleep(1)
        print(f"PASS: {expected} ({log})", flush=True)

    def send(process, command):
        process.stdin.write(command + "\n")
        process.stdin.flush()
        time.sleep(0.3)

    def commands(process, filename):
        for line in (FIXTURES / filename).read_text().splitlines():
            if line.startswith("#sleep "):
                time.sleep(float(line.split()[1]))
            elif line.strip() and not line.startswith("#"):
                send(process, line)

    def stop(process):
        send(process, "stop")
        if process.wait(timeout=120) != 0:
            raise RuntimeError(f"Server shutdown failed: exit {process.returncode}")

    try:
        gradle = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
        with (run / "build.log").open("w") as output:
            subprocess.run([gradle, "build", "writeSmokeClient", *([] if checks_only else ["downloadAssets"]), *([] if online else ["--offline"]),
                            "--no-daemon", "--console=plain", "-I", str(init),
                            f"-Dsmoke.spec={spec}"], cwd=ROOT, env=environment,
                           stdout=output, stderr=subprocess.STDOUT, check=True,
                           creationflags=creationflags)
        print("PASS: build and client launch configuration", flush=True)
        launch = json.loads(spec.read_text())
        launcher = base / f"downloads/fabric-server-{props['minecraft_version']}-{props['loader_version']}.jar"
        if not launcher.exists():
            with urllib.request.urlopen("https://meta.fabricmc.net/v2/versions/installer", timeout=30) as response:
                installer = json.load(response)[0]["version"]
            download(f"https://meta.fabricmc.net/v2/versions/loader/{props['minecraft_version']}/"
                     f"{props['loader_version']}/{installer}/server/jar", launcher)
        api = base / f"downloads/fabric-api-{props['fabric_version']}.jar"
        if not (FIXTURES / "smoke-no-fabric-api").exists():
            download(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/"
                     f"{props['fabric_version']}/fabric-api-{props['fabric_version']}.jar", api)
            for directory in (server, client):
                shutil.copy2(api, directory / "mods/fabric-api.jar")
        jar = Path(launch["modJar"])
        shutil.copy2(jar, server / "mods" / jar.name)
        with socket.socket() as available:
            available.bind(("127.0.0.1", 0))
            port = available.getsockname()[1]
        (server / "eula.txt").write_text("eula=true\n")
        (server / "server.properties").write_text(
            f"level-seed=12345\nserver-ip=127.0.0.1\nserver-port={port}\n"
            "online-mode=false\nwhite-list=false\nmax-tick-time=-1\n"
            "view-distance=6\ngamemode=creative\ndifficulty=peaceful\nspawn-protection=0\n")
        (client / "options.txt").write_text(
            "pauseOnLostFocus:false\nonboardAccessibility:false\ntutorialStep:none\n"
            "narrator:0\nrenderDistance:6\nguiScale:2\nmaxFps:30\nsoundCategory_master:0.0\n")
        server_command = [launch["executable"], "-Xmx2G", "-Dcreate.mixin.audit=true",
                          "-jar", str(launcher), "--nogui"]
        if checks_only:
            classes = run / "check-classes"
            classes.mkdir()
            api_paths = []
            with zipfile.ZipFile(api) as archive:
                for name in archive.namelist():
                    if any("/" + prefix in name for prefix in ("fabric-lifecycle-events-v1-", "fabric-api-base-")) and name.endswith(".jar"):
                        path = run / Path(name).name
                        path.write_bytes(archive.read(name))
                        api_paths.append(str(path))
            with (run / "check-compile.log").open("w") as output:
                subprocess.run([str(Path(launch["executable"]).with_name("javac.exe" if os.name == "nt" else "javac")),
                                "-cp", os.pathsep.join([launch["classpath"], *api_paths]), "-d", str(classes),
                                *map(str, sorted((FIXTURES / "checks").glob("*.java")))],
                               stdout=output, stderr=subprocess.STDOUT, check=True, creationflags=creationflags)
            probes = ["PloughCompatibilityProbe", "ScheduleProbe", "InventorySlotProbe"]
            with zipfile.ZipFile(server / "mods/checks.jar", "w") as archive:
                archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "create_regression_checks",
                    "version": "1.0.0", "environment": "server",
                    "entrypoints": {"server": ["probe." + name for name in probes]},
                    "depends": {"create": "*", "fabric-lifecycle-events-v1": "*"}}))
                for path in classes.rglob("*.class"):
                    archive.write(path, path.relative_to(classes).as_posix())
            game = start(server_command, server, "server.log")
            if game.wait(timeout=180) != 0:
                failed.append(f"Regression server exit {game.returncode}")
            for name in ("PLOUGH", "SCHEDULE", "INVENTORY"):
                if f"{name}_PROBE_RESULT failures=0" not in read("server.log"):
                    failed.append(f"{name.lower()} regression checks; see server.log")
            failed.extend(f"crash report: {path}" for path in run.glob("*/crash-reports/*"))
            return bool(failed)
        game = start(server_command, server, "server.log")
        wait_for(game, "server.log", "Done (")
        commands(game, "smoke-commands.txt")
        player = start([launch["executable"], *launch["jvmArgs"], "-Dcreate.mixin.audit=true",
                        launch["mainClass"], *launch["args"],
                        "--quickPlayMultiplayer", f"127.0.0.1:{port}", "--width", "1280",
                        "--height", "720"], client, "client.log")
        wait_for(game, "server.log", "joined the game", timeout=600, watch=(player,))
        time.sleep(40)
        # shortcut: GUI input and screenshots use the Linux runner; this runs its world commands and waits.
        for line in (FIXTURES / "smoke-script.txt").read_text().splitlines():
            kind, _, argument = line.partition(" ")
            if kind == "cmd":
                send(game, argument)
            elif kind == "sleep":
                time.sleep(float(argument))
        time.sleep(3)
        if player.poll() is not None:
            raise RuntimeError(f"Client exited during the scene ({player.returncode}); see client.log")
        player.terminate()
        player.wait(timeout=30)
        stop(game)
        game = start(server_command, server, "server2.log")
        wait_for(game, "server2.log", "Done (")
        commands(game, "smoke-commands-restart.txt")
        time.sleep(10)
        stop(game)
        failed.extend(failures(read("server.log"), read("server2.log")))
        failed.extend(f"crash report: {path}" for path in run.glob("*/crash-reports/*"))
    except (Exception, KeyboardInterrupt) as error:
        failed.append(str(error) or "Interrupted")
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                try:
                    send(process, "stop")
                    process.wait(timeout=30)
                except (OSError, subprocess.TimeoutExpired):
                    process.kill()
                    process.wait(timeout=30)
        summary = {"passed": not failed, "failures": failed,
                   "mode": "server-checks" if checks_only else "gameplay-smoke",
                   "skipped": "Client and gameplay scene/restart checks" if checks_only else "Linux GUI input, screenshots, inventory and Ponder checks; release publishing",
                   "command_errors": [line for line in (read("server.log") + read("server2.log")).splitlines()
                                      if "<--[HERE]" in line],
                   "server_markers": sorted(markers(read("server.log"))),
                   "restart_markers": sorted(markers(read("server2.log")))}
        if "jar" in locals():
            summary["artifact"] = str(jar)
            summary["artifact_sha256"] = hashlib.sha256(jar.read_bytes()).hexdigest()
        (run / "result.json").write_text(json.dumps(summary, indent=2) + "\n")
        print(json.dumps(summary, indent=2), flush=True)
        print(f"Evidence: {run}", flush=True)
    return bool(failed)


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    elif any(arg not in ("--checks", "--online") for arg in sys.argv[1:]):
        sys.exit("Usage: python .github/smoke-test.py [--self-test|--checks] [--online]")
    else:
        sys.exit(main(checks_only="--checks" in sys.argv[1:], online="--online" in sys.argv[1:]))
