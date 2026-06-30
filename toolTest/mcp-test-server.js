const readline = require("readline");

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false,
});

function write(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function result(id, value) {
  write({ jsonrpc: "2.0", id, result: value });
}

function error(id, code, message) {
  write({ jsonrpc: "2.0", id, error: { code, message } });
}

rl.on("line", (line) => {
  if (!line.trim()) {
    return;
  }

  let request;
  try {
    request = JSON.parse(line);
  } catch (err) {
    error(null, -32700, "Parse error");
    return;
  }

  if (!request.id && request.method === "notifications/initialized") {
    return;
  }

  switch (request.method) {
    case "initialize":
      result(request.id, {
        protocolVersion: "2025-06-18",
        capabilities: { tools: {} },
        serverInfo: { name: "lunacode-test-mcp", version: "0.1.0" },
      });
      break;
    case "tools/list":
      result(request.id, {
        tools: [
          {
            name: "get_test_data",
            description: "返回一段用于 LunaCode MCP 端到端验收的测试数据。",
            inputSchema: {
              type: "object",
              properties: {
                topic: { type: "string" },
              },
            },
          },
        ],
      });
      break;
    case "tools/call": {
      const topic = request.params?.arguments?.topic || "默认主题";
      result(request.id, {
        content: [
          {
            type: "text",
            text: `测试数据：${topic} 已由 stdio MCP Server 返回。`,
          },
        ],
        structuredContent: {
          topic,
          source: "toolTest/mcp-test-server.js",
          ok: true,
        },
      });
      break;
    }
    default:
      error(request.id, -32601, `Unsupported method: ${request.method}`);
  }
});