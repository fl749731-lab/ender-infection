"""Small local MCP client for inspecting the running Blockbench desktop plugin.

Uses only Python's standard library. The endpoint is deliberately loopback-only.
"""

import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen


class BlockbenchMCP:
    def __init__(self):
        self.url = "http://127.0.0.1:3000/bb-mcp"
        self.session = None
        self.request_id = 0
        self.info = self.request("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "ender-model-review", "version": "1.0.0"},
        })
        self.request("notifications/initialized", {}, notification=True)

    def request(self, method, params=None, notification=False):
        payload = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            payload["params"] = params
        if not notification:
            self.request_id += 1
            payload["id"] = self.request_id
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            "MCP-Protocol-Version": "2025-03-26",
        }
        if self.session:
            headers["Mcp-Session-Id"] = self.session
        req = Request(self.url, json.dumps(payload).encode(), headers)
        with urlopen(req, timeout=90) as response:
            self.session = response.headers.get("Mcp-Session-Id", self.session)
            if response.status == 202:
                return None
            if "text/event-stream" in response.headers.get("Content-Type", ""):
                # The HTTP stream can remain open; stop as soon as our reply arrives.
                for raw in response:
                    line = raw.decode("utf-8").strip()
                    if not line.startswith("data:"):
                        continue
                    message = json.loads(line[5:].strip())
                    if message.get("id") == payload.get("id"):
                        return self._result(message)
                raise RuntimeError("MCP stream ended without a response")
            body = response.read()
            return self._result(json.loads(body)) if body else None

    @staticmethod
    def _result(message):
        if "error" in message:
            raise RuntimeError(json.dumps(message["error"], ensure_ascii=False))
        return message.get("result")

    def call(self, name, arguments=None):
        result = self.request("tools/call", {"name": name, "arguments": arguments or {}})
        if result and result.get("isError"):
            raise RuntimeError(json.dumps(result, ensure_ascii=False))
        return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("method", choices=["initialize", "tools/list", "resources/list", "call"])
    parser.add_argument("--tool")
    parser.add_argument("--arguments", default="{}")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    client = BlockbenchMCP()
    if args.method == "initialize":
        result = client.info
    elif args.method == "call":
        if not args.tool:
            parser.error("--tool is required with call")
        result = client.call(args.tool, json.loads(args.arguments))
    else:
        result = client.request(args.method, {})
    encoded = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
        print(f"Saved MCP response: {args.output}")
    else:
        print(encoded)
