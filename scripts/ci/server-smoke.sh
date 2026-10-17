#!/usr/bin/env bash
# Boots one Minecraft version's dedicated dev server with Compat Core and the example addon installed,
# and passes once the example logs both its init and its world-load lines -- the end-to-end proof that
# the generated @Mod class loaded, forwarded init, and received WorldEvent.Load as a CompatWorld.
#
# Usage: scripts/ci/server-smoke.sh <module>[:<task>], e.g. mc1122, or mc1710:runServer21 for the
# Java 21 lwjgl3ify run. The task defaults to runServer.
set -uo pipefail

module="${1%%:*}"
task="runServer"
[[ "$1" == *:* ]] && task="${1#*:}"
name="$module-$task"
timeout_seconds="${SMOKE_TIMEOUT:-1500}"
log="build/smoke-$name.log"
mkdir -p build

# A new process group, so the server JVM Gradle forks dies with it.
setsid ./gradlew --no-daemon --stacktrace ":$module:$task" -Pcompat.acceptEula=true >"$log" 2>&1 &
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
grep -E "DEVRUN|installed dev-run mods|CompatExample|CompatCore|compatexample|compatcore|ERROR|Exception|Caused by|FAILED" "$log" | grep -vE "DEVRUN .*classpath: .*/(\.gradle/caches|loom-cache)/" | head -n 200

if [ "$result" != pass ]; then
    echo "----- diagnostics -----"
    # The mods the dev run installed, and what the example jars contain.
    ls -la "$module"/run/mods "$module"/run/*/mods 2>/dev/null
    for jar in "$module"/run/mods/*.jar "$module"/run/*/mods/*.jar example/"$module"/build/libs/*.jar; do
        [ -f "$jar" ] || continue
        echo "== $jar"
        unzip -l "$jar" | grep -vE "jvmdg/|META-INF/$" | head -n 40
    done
    # Which copies of the mixin classes exist, and which Java version each was compiled for.
    find "$module" -name 'Mixin*.class' -not -path '*/run/*' | while read -r class; do
        major=$(od -An -tu1 -j6 -N2 "$class" | awk '{print $1*256+$2}')
        echo "class major $major: $class"
    done
    # Loom's dev launch config: the classpath, system properties and mod groups it actually launches with.
    find "$module" .gradle -path '*loom-cache*' -name 'launch.cfg' 2>/dev/null | while read -r cfg; do
        echo "== $cfg"
        cat "$cfg"
    done
fi

case "$result" in
    pass) echo "SMOKE PASS: $name" ;;
    "") echo "SMOKE FAIL: $name timed out after ${timeout_seconds}s"; tail -n 100 "$log"; exit 1 ;;
    *) echo "SMOKE FAIL: $name ($result)"; tail -n 150 "$log"; exit 1 ;;
esac
