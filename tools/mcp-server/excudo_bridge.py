#!/usr/bin/env python3
"""
Excudo stdio MCP bridge.

Sits between an MCP client (Claude Desktop, Cursor, etc.) and Excudo's
in-process HTTP/SSE server. The client speaks JSON-RPC over stdio to this
script; this script forwards requests as HTTP POSTs to the live Excudo
server when one is running.

Why a bridge exists:
  1. Claude Desktop's MCP config only accepts stdio (command + args), not
     HTTP URLs. We need *some* stdio shim between it and our HTTP server.
  2. The previous shim was `npx mcp-remote <URL>`, which baked the URL
     into Claude's config. Every Excudo restart picks a new port + token,
     so that config went stale instantly. Worse: when Excudo wasn't
     running at all, mcp-remote couldn't connect, exited, and Claude
     Desktop logged "failed to attach" warnings on every launch.
  3. This shim always boots cleanly. It discovers Excudo's live URL
     fresh on every tool call via `~/.excudo/mcp-endpoint.json`, which
     Excudo writes on `arrange mcp` startup and deletes on shutdown.
  4. When Excudo isn't running, the shim still presents a valid MCP
     server -- it just advertises a single `launch_excudo` tool that
     starts the GUI on demand, plus returns a friendly error from any
     other tool call instead of hanging or crashing.

Tool discovery across the Excudo lifecycle:
  MCP clients fetch tools/list ONCE at connect and only re-fetch when the
  server sends `notifications/tools/list_changed`. Excudo is usually off
  at connect time, so the first tools/list returns only launch_excudo.
  A background thread watches the endpoint file and pushes
  tools/list_changed whenever Excudo's MCP server comes online (or goes
  away), so the client re-discovers the full authoring tool surface
  without a restart. This is what makes our advertised
  `capabilities.tools.listChanged` actually true.

Wire protocol: line-delimited JSON-RPC 2.0 on stdin/stdout. One request
per line; one response per line. Notifications get no response.
"""

import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

PROTOCOL_VERSION = "2024-11-05"
SERVER_NAME = "excudo-bridge"
SERVER_VERSION = "1.0.0"

# Excudo writes this file when its in-process MCP HTTP server starts and
# removes it on shutdown. Single source of truth for the live endpoint.
ENDPOINT_FILE = Path.home() / ".excudo" / "mcp-endpoint.json"

# Last-known tool schemas, cached so the bridge can advertise the FULL Excudo
# surface on the single tools/list a host issues at connect -- even when
# Excudo isn't running yet. This is the spec-recommended workaround for hosts
# (notably Claude Desktop) that ignore notifications/tools/list_changed and so
# never re-fetch after launch. See anthropics/claude-code issue #50339.
TOOLS_CACHE_FILE = Path.home() / ".excudo" / "tools-cache.json"

# Project root is two directories above this script:
#   <project_root>/tools/mcp-server/excudo_bridge.py
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
PC_PY = PROJECT_ROOT / "pc.py"

# How long launch_excudo waits for the endpoint file to appear after
# spawning the GUI process. JavaFX cold-start + arrange-mcp ~5-15s.
LAUNCH_TIMEOUT_SECONDS = 30
LAUNCH_POLL_INTERVAL_SECONDS = 0.5

# Per-call HTTP timeout when proxying. Tool work can be slow but should
# never legitimately block a full minute.
HTTP_TIMEOUT_SECONDS = 60

# Short timeout for the liveness ping that distinguishes a live endpoint from
# a stale discovery file. A dead port refuses fast; this only bounds the rare
# hung-socket case.
LIVENESS_TIMEOUT_SECONDS = 3

# How often the background watcher polls the endpoint file to detect
# Excudo coming online/offline so it can push tools/list_changed.
ENDPOINT_POLL_INTERVAL_SECONDS = 1.0


# ========== Diagnostics ==========

def log(message):
    """Write a diagnostic line to stderr. MCP clients (Claude Desktop)
    capture a server's stderr into their own MCP logs, so this is THE
    channel that makes the bridge debuggable instead of a silent black box
    -- the absence of it is why a month-old stale endpoint file turned into
    a cat-and-mouse hunt. stdout stays reserved for JSON-RPC frames.
    """
    print(f"[excudo-bridge] {message}", file=sys.stderr, flush=True)


# ========== Endpoint discovery ==========

def read_endpoint():
    """Return the live endpoint dict, or None if Excudo isn't running."""
    try:
        if not ENDPOINT_FILE.exists():
            return None
        with open(ENDPOINT_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict) or "url" not in data:
            return None
        return data
    except (json.JSONDecodeError, OSError):
        return None


def watch_endpoint():
    """
    Poll the endpoint file and push tools/list_changed whenever it
    transitions between absent and present. Runs on a daemon thread for
    the life of the bridge.

    Excudo coming online (endpoint appears)  -> client re-lists, sees the
    full authoring surface. Excudo shutting down (endpoint disappears) ->
    client re-lists, sees just launch_excudo again. Either way the
    client's tool view stays honest without a manual restart.
    """
    last_present = read_endpoint() is not None
    while True:
        time.sleep(ENDPOINT_POLL_INTERVAL_SECONDS)
        present = read_endpoint() is not None
        if present != last_present:
            last_present = present
            if present:
                # Populate the cache the moment Excudo comes online, so the
                # full surface is servable even though the host won't re-list.
                ep = read_endpoint()
                if ep:
                    refresh_tools_cache(ep)
            log("endpoint " + ("appeared (Excudo online)" if present
                else "disappeared (Excudo offline)")
                + "; sending tools/list_changed")
            write_message({
                "jsonrpc": "2.0",
                "method": "notifications/tools/list_changed",
            })


# ========== HTTP proxy ==========

def proxy_request(url, request_obj, timeout=HTTP_TIMEOUT_SECONDS):
    """
    POST a JSON-RPC request to Excudo's HTTP server and return the
    parsed response (or None on transport error).
    """
    body = json.dumps(request_obj).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            if not raw:
                return None
            return json.loads(raw.decode("utf-8"))
    except (urllib.error.URLError, urllib.error.HTTPError, OSError,
            json.JSONDecodeError):
        return None


def endpoint_is_live(endpoint):
    """
    True iff the discovered endpoint actually answers. The discovery file
    persists when Excudo crashes, is force-killed, or syncs in from another
    machine without running its graceful-shutdown cleanup -- so its mere
    existence is NOT proof of life. Probe with a short ping.
    """
    url = endpoint.get("url") if isinstance(endpoint, dict) else None
    if not url:
        return False
    resp = proxy_request(
        url,
        {"jsonrpc": "2.0", "id": "liveness", "method": "ping"},
        timeout=LIVENESS_TIMEOUT_SECONDS,
    )
    return resp is not None


def remove_stale_endpoint():
    """Delete the discovery file once we've found it points at a dead server,
    so subsequent calls and the watcher see 'Excudo not running' and the
    launch path can recover without manual cleanup."""
    try:
        stale = ENDPOINT_FILE.read_text(encoding="utf-8").strip()
    except OSError:
        stale = "<unreadable>"
    try:
        ENDPOINT_FILE.unlink()
        log(f"removed stale endpoint file (server did not answer a ping): {stale}")
    except OSError:
        pass


# ========== Tool-schema cache ==========

def read_cached_tools():
    """Return the cached tool schemas (without launch_excudo), or [] if no
    cache exists yet."""
    try:
        data = json.loads(TOOLS_CACHE_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, list) else []
    except (OSError, json.JSONDecodeError):
        return []


def write_tools_cache(tools):
    """Persist the live tool schemas (excluding launch_excudo, which the bridge
    always injects itself) so future connects can advertise the full surface
    before Excudo is up."""
    real = [t for t in tools
            if isinstance(t, dict) and t.get("name") != "launch_excudo"]
    if not real:
        return
    try:
        TOOLS_CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
        TOOLS_CACHE_FILE.write_text(json.dumps(real), encoding="utf-8")
    except OSError as e:
        log(f"failed to write tools cache: {e}")


def refresh_tools_cache(endpoint):
    """Proactively pull and cache the live tool list. Called when Excudo comes
    online so the cache populates WITHOUT depending on the host re-issuing
    tools/list (which Claude Desktop won't -- see issue #50339)."""
    proxied = proxy_request(endpoint["url"], {
        "jsonrpc": "2.0", "id": "cache-refresh", "method": "tools/list",
    })
    if proxied and isinstance(proxied.get("result"), dict):
        tools = proxied["result"].get("tools")
        if isinstance(tools, list) and tools:
            write_tools_cache(tools)
            n = len([t for t in tools if t.get("name") != "launch_excudo"])
            log(f"cached {n} tool schemas from {endpoint['url']}")


# ========== Tool: launch_excudo ==========

LAUNCH_EXCUDO_TOOL = {
    "name": "launch_excudo",
    "description": (
        "Start Excudo in GUI mode with its in-process MCP server if it is "
        "not already running, then wait for that server to come online. "
        "After this returns successfully, all other Excudo tools become "
        "available via tools/list (the bridge pushes tools/list_changed "
        "automatically). Safe to call when Excudo is already running -- "
        "returns immediately in that case."
    ),
    "inputSchema": {
        "type": "object",
        "properties": {},
        "required": [],
    },
}


def launch_excudo():
    """
    Spawn `<python> pc.py run gui --mcp` detached from this process, then
    poll the endpoint file until it appears (or LAUNCH_TIMEOUT_SECONDS
    elapses). The `--mcp` flag is the MCP-launcher path: it starts the GUI
    AND brings up the in-process MCP HTTP server (writes the endpoint
    file). A plain `pc.py run gui` is normal mode and does NOT start the
    server. Returns (success, message).
    """
    existing = read_endpoint()
    if existing is not None and endpoint_is_live(existing):
        log(f"launch_excudo: already live at {existing.get('url')}; no spawn")
        return True, (
            f"Excudo is already running at {existing.get('url')}. "
            "No new process spawned."
        )
    if existing is not None:
        # Discovery file present but the server does not answer -- a stale
        # file from a crashed/force-killed Excudo (or one synced in from
        # another machine, e.g. via OneDrive). Clear it so we neither
        # falsely report "already running" nor refuse to relaunch.
        log(f"launch_excudo: discovery file present but server unreachable "
            f"(pid={existing.get('pid')}, url={existing.get('url')}); relaunching")
        remove_stale_endpoint()

    if not PC_PY.exists():
        return False, (
            f"Cannot locate Excudo launcher at {PC_PY}. "
            "Bridge script may be in the wrong location relative to the "
            "Excudo project root."
        )

    # Detached spawn so the GUI outlives this tool call. On Windows we
    # need a different flag set; everywhere else, start_new_session is
    # enough to disown the child.
    popen_kwargs = {
        "cwd": str(PROJECT_ROOT),
        "stdin": subprocess.DEVNULL,
        "stdout": subprocess.DEVNULL,
        "stderr": subprocess.DEVNULL,
    }
    if os.name == "nt":
        popen_kwargs["creationflags"] = (
            subprocess.DETACHED_PROCESS
            | subprocess.CREATE_NEW_PROCESS_GROUP
        )
    else:
        popen_kwargs["start_new_session"] = True

    # sys.executable is the interpreter running this bridge -- the one Claude
    # Desktop spawned us with -- so it always resolves, unlike a bare
    # "python3" that may not be on a Windows PATH.
    cmd = [sys.executable, str(PC_PY), "run", "gui", "--mcp"]
    log(f"launch_excudo: spawning {' '.join(cmd)}")
    try:
        subprocess.Popen(cmd, **popen_kwargs)
    except OSError as e:
        log(f"launch_excudo: spawn failed: {e}")
        return False, f"Failed to spawn Excudo GUI: {e}"

    deadline = time.monotonic() + LAUNCH_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        endpoint = read_endpoint()
        if endpoint is not None:
            log(f"launch_excudo: server online at {endpoint.get('url')}")
            return True, (
                f"Excudo GUI launched and MCP server is listening at "
                f"{endpoint.get('url')}. Its tools are now available."
            )
        time.sleep(LAUNCH_POLL_INTERVAL_SECONDS)

    log(f"launch_excudo: timed out after {LAUNCH_TIMEOUT_SECONDS}s waiting for "
        "the MCP server to come online")
    return False, (
        f"Excudo GUI was spawned but its MCP server did not become "
        f"available within {LAUNCH_TIMEOUT_SECONDS}s. Open the Excudo "
        "console and run 'arrange mcp' to start the server manually."
    )


# ========== JSON-RPC response builders ==========

def jsonrpc_result(req_id, result):
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def jsonrpc_error(req_id, code, message):
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


def tool_result_text(req_id, text, is_error=False):
    """Build an MCP tool-call response with a single text content block."""
    result = {"content": [{"type": "text", "text": text}]}
    if is_error:
        result["isError"] = True
    return jsonrpc_result(req_id, result)


# ========== Request handlers ==========

def handle_initialize(req_id, _params):
    return jsonrpc_result(req_id, {
        "protocolVersion": PROTOCOL_VERSION,
        "capabilities": {"tools": {"listChanged": True}},
        "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
    })


def handle_tools_list(req_id, _params):
    """
    Always advertise the FULL Excudo tool surface so it lands in the host's
    index on the single tools/list issued at connect.

    Excudo up   -> proxy the live list (and refresh the cache).
    Excudo down -> serve cached schemas + launch_excudo. Hosts like Claude
    Desktop never re-issue tools/list after a tools/list_changed (issue
    #50339), so anything not returned HERE stays invisible until a full
    client restart. Tool CALLS auto-launch Excudo on demand.
    """
    endpoint = read_endpoint()
    if endpoint is None:
        return offline_tools_list(req_id, "Excudo offline")

    proxied = proxy_request(endpoint["url"], {
        "jsonrpc": "2.0",
        "id": req_id,
        "method": "tools/list",
    })
    if proxied is None:
        log(f"tools/list: endpoint {endpoint.get('url')} unreachable; clearing stale file")
        remove_stale_endpoint()
        return offline_tools_list(req_id, "endpoint unreachable")

    if "result" in proxied and isinstance(proxied["result"], dict):
        tools = proxied["result"].get("tools", [])
        write_tools_cache(tools)  # keep the cache fresh for future cold connects
        names = {t.get("name") for t in tools if isinstance(t, dict)}
        if "launch_excudo" not in names:
            tools.append(LAUNCH_EXCUDO_TOOL)
            proxied["result"]["tools"] = tools
        log(f"tools/list: proxied {len(proxied['result'].get('tools', []))} "
            f"tools from {endpoint.get('url')}")
    return proxied


def offline_tools_list(req_id, why):
    """Serve cached schemas + launch_excudo when Excudo isn't reachable, so the
    host indexes every tool on its one-and-only connect-time tools/list."""
    cached = read_cached_tools()
    tools = list(cached) + [LAUNCH_EXCUDO_TOOL]
    log(f"tools/list: {why}; serving {len(cached)} cached tool(s) + launch_excudo")
    return jsonrpc_result(req_id, {"tools": tools})


def handle_tools_call(req_id, params):
    if not isinstance(params, dict):
        return jsonrpc_error(req_id, -32602, "Missing params")
    name = params.get("name")
    if not isinstance(name, str) or not name:
        return jsonrpc_error(req_id, -32602, "Missing tool name")

    if name == "launch_excudo":
        ok, msg = launch_excudo()
        return tool_result_text(req_id, msg, is_error=not ok)

    # Excudo is advertised from cache, so the agent may call a real tool while
    # it's down. Try the live server first; if it's absent or unreachable,
    # auto-launch Excudo and retry once so cached tools work cold.
    endpoint = read_endpoint()
    if endpoint is not None:
        proxied = proxy_tool_call(endpoint, req_id, params)
        if proxied is not None:
            return proxied
        log(f"tools/call '{name}': endpoint {endpoint.get('url')} unreachable; "
            "clearing stale file and auto-launching")
        remove_stale_endpoint()
    else:
        log(f"tools/call '{name}': Excudo offline; auto-launching")

    ok, msg = launch_excudo()
    if not ok:
        return tool_result_text(
            req_id, f"Excudo could not be started to run '{name}': {msg}",
            is_error=True)
    endpoint = read_endpoint()
    if endpoint is None:
        return tool_result_text(
            req_id, f"Excudo started but its endpoint never appeared; "
            f"cannot run '{name}'.", is_error=True)
    proxied = proxy_tool_call(endpoint, req_id, params)
    if proxied is None:
        return tool_result_text(
            req_id, f"Excudo started but did not answer '{name}'.", is_error=True)
    return proxied


def proxy_tool_call(endpoint, req_id, params):
    return proxy_request(endpoint["url"], {
        "jsonrpc": "2.0",
        "id": req_id,
        "method": "tools/call",
        "params": params,
    })


def handle_request(request):
    """Route one inbound JSON-RPC frame; return one response or None."""
    if not isinstance(request, dict):
        return jsonrpc_error(None, -32600, "Invalid request")

    method = request.get("method")
    req_id = request.get("id")
    params = request.get("params", {})

    # Notifications carry no id; produce no response per JSON-RPC 2.0.
    if method == "notifications/initialized":
        return None
    if isinstance(method, str) and method.startswith("notifications/"):
        return None

    if method == "initialize":
        return handle_initialize(req_id, params)
    if method == "tools/list":
        return handle_tools_list(req_id, params)
    if method == "tools/call":
        return handle_tools_call(req_id, params)
    if method == "ping":
        return jsonrpc_result(req_id, {})

    return jsonrpc_error(req_id, -32601, f"Method not found: {method}")


# ========== stdio main loop ==========

# Serialize all stdout writes: the main loop's responses and the watcher
# thread's notifications share one pipe and must not interleave mid-line.
_stdout_lock = threading.Lock()


def write_message(obj):
    """Write one JSON-RPC frame to stdout as a single line, under a lock."""
    line = json.dumps(obj) + "\n"
    with _stdout_lock:
        sys.stdout.write(line)
        sys.stdout.flush()


def main():
    online = read_endpoint() is not None
    log(f"excudo-bridge {SERVER_VERSION} starting; endpoint file {ENDPOINT_FILE} "
        f"({'present' if online else 'absent'}); python={sys.executable}")
    # Watch for Excudo coming online/offline and push tools/list_changed.
    threading.Thread(target=watch_endpoint, daemon=True).start()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            write_message(jsonrpc_error(None, -32700, "Parse error"))
            continue

        response = handle_request(request)
        if response is not None:
            write_message(response)


if __name__ == "__main__":
    main()
