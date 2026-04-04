package org.openclover.core.reporters.agent;

import org.openclover.core.reporters.json.JSONArray;
import org.openclover.core.reporters.json.JSONException;
import org.openclover.core.reporters.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP (Model Context Protocol) server for Clover coverage tools.
 * Exposes coverage queries as MCP tools that Claude Code can invoke.
 *
 * Protocol: reads JSON-RPC 2.0 messages from stdin, writes responses to stdout.
 *
 * Tools provided:
 * - clover_uncovered: get uncovered lines/branches for a class
 * - clover_test_coverage: get per-test coverage data
 * - clover_suggest: get test suggestions ranked by uncovered lines
 * - clover_summary: get project coverage summary
 * - clover_impact: get tests affected by changed lines
 * - clover_risk: get methods ranked by complexity-weighted risk
 *
 * Usage: {@code java -cp clover.jar CloverMcpServer <dbpath>}
 */
public class CloverMcpServer {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String MCP_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "clover-coverage";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String KEY_JSONRPC = "jsonrpc";
    private static final String KEY_ID = "id";
    private static final String KEY_METHOD = "method";
    private static final String KEY_PARAMS = "params";
    private static final String KEY_RESULT = "result";
    private static final String KEY_ERROR = "error";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TOOLS = "tools";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TEXT = "text";
    private static final String KEY_TYPE = "type";
    private static final String KEY_CODE = "code";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_ARGUMENTS = "arguments";
    private static final String KEY_INPUT_SCHEMA = "inputSchema";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_REQUIRED = "required";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_OBJECT = "object";
    private static final String TOOL_UNCOVERED = "clover_uncovered";
    private static final String TOOL_TEST_COVERAGE = "clover_test_coverage";
    private static final String TOOL_SUGGEST = "clover_suggest";
    private static final String TOOL_SUMMARY = "clover_summary";
    private static final String TOOL_IMPACT = "clover_impact";
    private static final String TOOL_RISK = "clover_risk";
    private static final String PARAM_CLASS = "class";
    private static final String PARAM_TEST = "test";
    private static final String PARAM_LINES = "lines";
    private static final String KEY_FILE = "file";

    private final LiveCoverageDatabase liveDb;

    public CloverMcpServer(String dbPath) {
        this.liveDb = new LiveCoverageDatabase(dbPath);
    }

    /**
     * Run the MCP server loop reading from stdin and writing to stdout.
     */
    public void run() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8);

        String line;
        while ((line = reader.readLine()) != null) {
            try {
                JSONObject request = new JSONObject(line);
                JSONObject response = handleRequest(request);
                writer.println(response.toString());
            } catch (JSONException e) {
                writer.println("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}");
            }
        }
    }

    private JSONObject handleRequest(JSONObject request) throws JSONException {
        String method = request.optString(KEY_METHOD, "");
        Object id = request.opt(KEY_ID);

        switch (method) {
            case "initialize":
                return buildInitializeResult(id);
            case "tools/list":
                return buildToolsList(id);
            case "tools/call":
                return handleToolCall(request, id);
            default:
                return buildError(id, -32601, "Method not found: " + method);
        }
    }

    private JSONObject buildInitializeResult(Object id) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("protocolVersion", MCP_VERSION);

        JSONObject serverInfo = new JSONObject();
        serverInfo.put(KEY_NAME, SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);

        JSONObject capabilities = new JSONObject();
        capabilities.put(KEY_TOOLS, new JSONObject());
        result.put("capabilities", capabilities);

        return buildResponse(id, result);
    }

    private JSONObject buildToolsList(Object id) throws JSONException {
        JSONArray tools = new JSONArray();

        tools.put(buildToolDef(TOOL_UNCOVERED,
                "Get uncovered lines and branches for a Java class",
                new String[]{PARAM_CLASS}, new String[]{TYPE_STRING}));

        tools.put(buildToolDef(TOOL_TEST_COVERAGE,
                "Get coverage data for a specific test method",
                new String[]{PARAM_TEST}, new String[]{TYPE_STRING}));

        tools.put(buildToolDef(TOOL_SUGGEST,
                "Get test suggestions ranked by uncovered lines for a class",
                new String[]{PARAM_CLASS}, new String[]{TYPE_STRING}));

        tools.put(buildToolDef(TOOL_SUMMARY,
                "Get project coverage summary (statements, branches, methods)",
                new String[]{}, new String[]{}));

        tools.put(buildToolDef(TOOL_IMPACT,
                "Get tests affected by changed lines in a file",
                new String[]{PARAM_CLASS, PARAM_LINES}, new String[]{TYPE_STRING, TYPE_STRING}));

        tools.put(buildToolDef(TOOL_RISK,
                "Get methods ranked by complexity-weighted coverage risk",
                new String[]{}, new String[]{}));

        JSONObject result = new JSONObject();
        result.put(KEY_TOOLS, tools);
        return buildResponse(id, result);
    }

    private JSONObject handleToolCall(JSONObject request, Object id) throws JSONException {
        JSONObject params = request.optJSONObject(KEY_PARAMS);
        if (params == null) {
            return buildError(id, -32602, "Missing params");
        }

        String toolName = params.optString(KEY_NAME, "");
        JSONObject args = params.optJSONObject(KEY_ARGUMENTS);
        if (args == null) {
            args = new JSONObject();
        }

        if (!liveDb.isAvailable()) {
            return buildToolResult(id, "Coverage database not available. Run tests with Clover instrumentation first.");
        }

        AgentJsonReporter reporter = liveDb.getReporter();
        AgentCoverageQuery query = liveDb.getQuery();

        String output;
        if (TOOL_UNCOVERED.equals(toolName)) {
            output = reporter.generateFileUncovered(args.optString(PARAM_CLASS, ""));
        } else if (TOOL_TEST_COVERAGE.equals(toolName)) {
            output = reporter.generateTestFeedback(args.optString(PARAM_TEST, ""));
        } else if (TOOL_SUGGEST.equals(toolName)) {
            output = reporter.generateSuggestions(args.optString(PARAM_CLASS, ""), 5);
        } else if (TOOL_SUMMARY.equals(toolName)) {
            output = reporter.generateFeedback(0, 0);
        } else if (TOOL_IMPACT.equals(toolName)) {
            output = handleImpact(args);
        } else if (TOOL_RISK.equals(toolName)) {
            output = handleRisk();
        } else {
            return buildError(id, -32602, "Unknown tool: " + toolName);
        }

        return buildToolResult(id, output);
    }

    private String handleImpact(JSONObject args) throws JSONException {
        String className = args.optString(PARAM_CLASS, "");
        String linesStr = args.optString(PARAM_LINES, "");
        Set<Integer> lines = new HashSet<>();
        for (String part : linesStr.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                lines.add(Integer.parseInt(trimmed));
            }
        }

        CrossTestImpactAnalyzer analyzer = new CrossTestImpactAnalyzer(liveDb.getDatabase());
        List<String> affected = analyzer.getAffectedTests(className, lines);
        return new JSONArray(affected).toString(2);
    }

    private String handleRisk() throws JSONException {
        ComplexityRiskScorer scorer = new ComplexityRiskScorer(liveDb.getDatabase());
        List<ComplexityRiskScorer.MethodRiskScore> risks = scorer.scoreAllMethods(10);

        JSONArray arr = new JSONArray();
        for (ComplexityRiskScorer.MethodRiskScore r : risks) {
            JSONObject obj = new JSONObject();
            obj.put(KEY_FILE, r.file);
            obj.put(PARAM_CLASS, r.className);
            obj.put(KEY_METHOD, r.method);
            obj.put("complexity", r.complexity);
            obj.put("coveragePct", r.coveragePct);
            obj.put("riskScore", r.riskScore);
            arr.put(obj);
        }
        return arr.toString(2);
    }

    // ==================== JSON-RPC helpers ====================

    private JSONObject buildToolDef(String name, String description,
                                    String[] paramNames, String[] paramTypes) throws JSONException {
        JSONObject tool = new JSONObject();
        tool.put(KEY_NAME, name);
        tool.put(KEY_DESCRIPTION, description);

        JSONObject schema = new JSONObject();
        schema.put(KEY_TYPE, TYPE_OBJECT);
        JSONObject props = new JSONObject();
        JSONArray required = new JSONArray();
        for (int i = 0; i < paramNames.length; i++) {
            JSONObject prop = new JSONObject();
            prop.put(KEY_TYPE, paramTypes[i]);
            props.put(paramNames[i], prop);
            required.put(paramNames[i]);
        }
        schema.put(KEY_PROPERTIES, props);
        schema.put(KEY_REQUIRED, required);
        tool.put(KEY_INPUT_SCHEMA, schema);

        return tool;
    }

    private JSONObject buildResponse(Object id, JSONObject result) throws JSONException {
        JSONObject resp = new JSONObject();
        resp.put(KEY_JSONRPC, JSONRPC_VERSION);
        resp.put(KEY_ID, id);
        resp.put(KEY_RESULT, result);
        return resp;
    }

    private JSONObject buildToolResult(Object id, String textContent) throws JSONException {
        JSONObject result = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject textObj = new JSONObject();
        textObj.put(KEY_TYPE, KEY_TEXT);
        textObj.put(KEY_TEXT, textContent);
        content.put(textObj);
        result.put(KEY_CONTENT, content);
        return buildResponse(id, result);
    }

    private JSONObject buildError(Object id, int code, String message) throws JSONException {
        JSONObject resp = new JSONObject();
        resp.put(KEY_JSONRPC, JSONRPC_VERSION);
        resp.put(KEY_ID, id);
        JSONObject error = new JSONObject();
        error.put(KEY_CODE, code);
        error.put(KEY_MESSAGE, message);
        resp.put(KEY_ERROR, error);
        return resp;
    }

    /**
     * Main entry point for standalone MCP server.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: CloverMcpServer <dbpath>");
            System.exit(1);
        }
        new CloverMcpServer(args[0]).run();
    }
}
