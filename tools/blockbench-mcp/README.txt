Blockbench MCP desktop integration

Upstream: https://github.com/jasonjgardner/blockbench-mcp-plugin
Pinned release: v1.10.1 (third-party plugin)
Download: https://github.com/jasonjgardner/blockbench-mcp-plugin/releases/download/v1.10.1/mcp.js
SHA256: caf94540e50257eaeb5aa51ab4b34b56aa6ac258a0f8c0bee2025c2cf18d5db9
Downloaded file: E:\mc\tools\blockbench-mcp\mcp.js (not committed)
Blockbench desktop: E:\molde\Blockbench\Blockbench.exe (5.2.2)

The plugin must be loaded using File > Plugins > Load Plugin from File.
Blockbench asks the user to approve the plugin and its network module.
Keep the default MCP Server Host set to localhost.
Endpoint: http://127.0.0.1:3000/bb-mcp
Keep Blockbench running while using its tools.

Codex configuration (already registered with codex mcp add):
[mcp_servers.blockbench]
url = "http://127.0.0.1:3000/bb-mcp"

Local connection diagnostics, without needing to restart this conversation:
python tools/blockbench-mcp/client.py initialize
python tools/blockbench-mcp/client.py tools/list --output build/blockbench-mcp/tools.json

client.py uses standard MCP HTTP calls to the installed plugin. It does not
load plugin code into Python or emulate Blockbench's modeling/rendering APIs.
Prefer native Blockbench MCP tools when available in the current Codex session.

Authoring completed through MCP: native Cube/Group/Texture operations with Undo,
followed by Blockbench's save_project action. Canonical monsters are BB5 files.
Native edit history and saved state are recorded in
ender_infection_colored/verification/blockbench_saved_projects.json.
The final reviewed revision is previews/blockbench-mcp-2026-11-03/mcp-delivery/.

refine_models.py, shape_pass.py, focal_details.py and separate_surfaces.py record
the one-off authoring passes. Do not
rerun them on finished models: the first replaces geometry with proposal data,
and the second applies relative proportion changes. Keep the saved BB5 files.
Export them with python tools/rebuild_creature_silhouettes.py (no --regenerate).

Verified on 2026-11-03: initialize, project capabilities, 110 project tools,
model inspection, private viewport captures, and animation scrubbing all work.
Known host issue: Blockbench 5.2.2 Preview.delete throws "onContextMenu is not
defined" during offscreen-view cleanup. The plugin removes its view entry in
a finally block; successful captures are preserved and this warning is logged.
