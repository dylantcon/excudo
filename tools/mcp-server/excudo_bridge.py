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

Wire protocol: line-delimited JSON-RPC 2.0 on stdin/stdout. One request
per line; one response per line. Notifications get no response.
"""

import json
import os
import subprocess
import sys
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


# ========== HTTP proxy ==========

def proxy_request(url, request_obj):
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
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT_SECONDS) as resp:
            raw = resp.read()
            if not raw:
                return None
            return json.loads(raw.decode("utf-8"))
    except (urllib.error.URLError, urllib.error.HTTPError, OSError,
            json.JSONDecodeError):
        return None


# ========== Tool: launch_excudo ==========

LAUNCH_EXCUDO_TOOL = {
    "name": "launch_excudo",
    "description": (
        "Start Excudo in GUI mode if it is not already running, then wait "
        "for its in-process MCP server to come online. After this returns "
        "successfully, all other Excudo tools become available via "
        "tools/list. Safe to call when Excudo is already running -- "
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
    Spawn `python3 pc.py run gui` detached from this process, then poll
    the endpoint file until it appears (or LAUNCH_TIMEOUT_SECONDS elapses).
    Returns (success, message).
    """
    existing = read_endpoint()
    if existing is not None:
        return True, (
            f"Excudo is already running at {existing.get('url')}. "
            "No new process spawned."
        )

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

    try:
        subprocess.Popen(
            ["python3", str(PC_PY), "run", "gui"],
            **popen_kwargs,
        )
    except OSError as e:
        return False, f"Failed to spawn Excudo GUI: {e}"

    deadline = time.monotonic() + LAUNCH_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        endpoint = read_endpoint()
        if endpoint is not None:
            return True, (
                f"Excudo GUI launched and MCP server is listening at "
                f"{endpoint.get('url')}. Call tools/list to see the full "
                "tool surface."
            )
        time.sleep(LAUNCH_POLL_INTERVAL_SECONDS)

    return False, (
        f"Excudo GUI was spawned but its MCP server did not become "
        f"available within {LAUNCH_TIMEOUT_SECONDS}s. The user must run "
        "'arrange mcp' in the Excudo console to start the server."
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
    When Excudo is up, proxy tools/list to the live server so Claude
    sees every real tool. When it's down, return just launch_excudo so
    Claude has a way to bring Excudo up.
    """
    endpoint = read_endpoint()
    if endpoint is None:
        return jsonrpc_result(req_id, {"tools": [LAUNCH_EXCUDO_TOOL]})

    proxied = proxy_request(endpoint["url"], {
        "jsonrpc": "2.0",
        "id": req_id,
        "method": "tools/list",
    })
    if proxied is None:
        # File present but server unreachable -- likely a stale file
        # from a crashed Excudo. Fall back to launch_excudo so the user
        # can recover without manual cleanup.
        return jsonrpc_result(req_id, {"tools": [LAUNCH_EXCUDO_TOOL]})

    # Inject launch_excudo into the live list too, so it stays callable
    # for "re-launch after restart" workflows without confusing Claude
    # with a tool that vanishes mid-session.
    if "result" in proxied and isinstance(proxied["result"], dict):
        tools = proxied["result"].get("tools", [])
        names = {t.get("name") for t in tools if isinstance(t, dict)}
        if "launch_excudo" not in names:
            tools.append(LAUNCH_EXCUDO_TOOL)
            proxied["result"]["tools"] = tools
    return proxied


def handle_tools_call(req_id, params):
    if not isinstance(params, dict):
        return jsonrpc_error(req_id, -32602, "Missing params")
    name = params.get("name")
    if not isinstance(name, str) or not name:
        return jsonrpc_error(req_id, -32602, "Missing tool name")

    if name == "launch_excudo":
        ok, msg = launch_excudo()
        return tool_result_text(req_id, msg, is_error=not ok)

    endpoint = read_endpoint()
    if endpoint is None:
        return tool_result_text(
            req_id,
            "Excudo is not running. Call the launch_excudo tool first to "
            "start the GUI and bring up its MCP server.",
            is_error=True,
        )

    proxied = proxy_request(endpoint["url"], {
        "jsonrpc": "2.0",
        "id": req_id,
        "method": "tools/call",
        "params": params,
    })
    if proxied is None:
        return tool_result_text(
            req_id,
            f"Excudo's MCP server at {endpoint.get('url')} did not respond. "
            "It may have shut down. Call launch_excudo to restart it.",
            is_error=True,
        )
    return proxied


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

def main():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            sys.stdout.write(json.dumps(
                jsonrpc_error(None, -32700, "Parse error")) + "\n")
            sys.stdout.flush()
            continue

        response = handle_request(request)
        if response is not None:
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
