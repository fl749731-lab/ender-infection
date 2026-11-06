"""Small stdio MCP client for reproducible, local asset authoring tools."""
from __future__ import annotations

import base64
import json
import os
from pathlib import Path
import queue
import subprocess
import threading
import time
import tomllib


class McpClient:
    def __init__(self, name: str, timeout: int = 180):
        config = Path.home() / '.codex/config.toml'
        settings = tomllib.loads(config.read_text(encoding='utf-8-sig'))['mcp_servers'][name]
        self.timeout, self.sequence = timeout, 0
        self.messages, self.errors, self.calls = queue.Queue(), [], []
        self.process = subprocess.Popen([settings['command'], *settings.get('args', [])],
            cwd=settings.get('cwd'), env={**os.environ, **settings.get('env', {})},
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding='utf-8', errors='replace')
        threading.Thread(target=self._read, daemon=True).start()
        threading.Thread(target=self._read_errors, daemon=True).start()
        try:
            self.server = self.request('initialize', {'protocolVersion': '2025-06-18',
                'capabilities': {}, 'clientInfo': {'name': 'ender-infection-asset-author', 'version': '1'}})
            self._write({'jsonrpc': '2.0', 'method': 'notifications/initialized'})
            self.tools = self.request('tools/list')['tools']
        except Exception:
            self.close()
            raise

    def _read(self):
        for line in self.process.stdout:
            try:
                self.messages.put(json.loads(line))
            except json.JSONDecodeError:
                self.errors.append(line[:500])
        self.messages.put({'closed': True})

    def _read_errors(self):
        for line in self.process.stderr:
            self.errors.append(line.rstrip()[:1000])

    def _write(self, value):
        self.process.stdin.write(json.dumps(value) + '\n')
        self.process.stdin.flush()

    def request(self, method, params=None):
        self.sequence += 1
        ident = self.sequence
        self._write({'jsonrpc': '2.0', 'id': ident, 'method': method, 'params': params or {}})
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            try:
                result = self.messages.get(timeout=max(.1, deadline - time.monotonic()))
            except queue.Empty:
                break
            if result.get('closed'):
                raise RuntimeError(f'MCP process closed: {self.errors[-5:]}')
            if result.get('id') == ident:
                if 'error' in result:
                    raise RuntimeError(result['error'])
                return result['result']
            if 'method' in result and 'id' in result:
                self._write({'jsonrpc': '2.0', 'id': result['id'], 'result': {}})
        raise TimeoutError(f'{method} did not complete; stderr: {self.errors[-5:]}')

    def call(self, tool_name, **arguments):
        result = self.request('tools/call', {'name': tool_name, 'arguments': arguments})
        if result.get('isError'):
            raise RuntimeError(result)
        self.calls.append({'tool': tool_name, 'arguments': arguments})
        return result

    def render_to(self, path: Path, **arguments):
        result = self.call('render', **arguments)
        pictures = [entry for entry in result['content'] if entry['type'] == 'image']
        if not pictures:
            raise RuntimeError('MCP renderer returned no image')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(base64.b64decode(pictures[0]['data']))

    def close(self):
        try:
            self.process.stdin.close()
            self.process.wait(timeout=3)
        except (OSError, subprocess.TimeoutExpired):
            if os.name == 'nt':
                subprocess.run(['taskkill', '/PID', str(self.process.pid), '/T', '/F'],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                self.process.terminate()
            self.process.wait(timeout=5)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
