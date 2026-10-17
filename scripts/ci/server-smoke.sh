#!/usr/bin/env bash
# Boots one Minecraft version's dedicated dev server running the example on its own (Compat is linked in),
# and passes once the example logs both its init and its world-load lines -- the end-to-end proof that
# the generated @Mod class loaded, forwarded init, and received WorldEvent.Load as a CompatWorld.
#
# Usage: scripts/ci/server-smoke.sh <project dir>[:<task>], e.g. example/mc1122, or
# example/mc1710:runServer21 for the Java 21 lwjgl3ify run. The task defaults to runServer.
set -uo pipefail

module="${1%%:*}"
task="runServer"
[[ "$1" == *:* ]] && task="${1#*:}"
name="${module//\//-}-$task"
gradle_path=":${module//\//:}"
timeout_seconds="${SMOKE_TIMEOUT:-1500}"
log="build/smoke-$name.log"
mkdir -p build

# A new process group, so the server JVM Gradle forks dies with it.
setsid ./gradlew --no-daemon --stacktrace "$gradle_path:$task" -Pcompat.acceptEula=true >"$log" 2>&1 &
gradle_pid=$!

stop() {
    kill -TERM -- "-$gradle_pid" 2>/dev/null
    sleep 5
    kill -KILL -- "-$gradle_pid" 2>/dev/null
}

result=""
for ((elapsed = 0; elapsed < timeout_seconds; elapsed += 5)); do
    if grep -q "Compat Example initialised" "$log" && grep -q "world is server" "$log"; then
        result="pass"
        break
    fi
    if grep -qE "BUILD FAILED|---- Minecraft Crash Report ----|Exception in server tick loop|Encountered an unexpected exception|Failed to start the minecraft server" "$log"; then
        result="fail"
        break
    fi
    if ! kill -0 "$gradle_pid" 2>/dev/null; then
        result="exited"
        break
    fi
    sleep 5
done
stop

echo "----- relevant lines from $log -----"
grep -E "CompatExample|compatexample|ERROR|Exception|Caused by|FAILED" "$log" | head -n 200

case "$result" in
    pass) echo "SMOKE PASS: $name" ;;
    "") echo "SMOKE FAIL: $name timed out after ${timeout_seconds}s"; tail -n 100 "$log"; exit 1 ;;
    *) echo "SMOKE FAIL: $name ($result)"; tail -n 150 "$log"; exit 1 ;;
esac
