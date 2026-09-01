# External Connector Plan

V0.3 has a real tool registry but intentionally does not fake access to private services.

## Next adapters

### GitHub
- Read repositories, issues, PRs and CI status.
- Write operations such as creating issues, rerunning CI or merging PRs require approval.
- Credential should be OAuth or a narrowly scoped token stored server-side.

### Google Calendar
- Read-only schedule queries may run automatically.
- Creating, changing or deleting events requires approval.

### Gmail
- Search/read can be read-only policy controlled.
- Drafting may be automatic if desired.
- Sending, forwarding, deleting and label/archive changes require approval.

### Web research
- Research agent should preserve source URLs, publication dates and confidence.
- Current claims should be source-backed rather than generated from memory.

### Local computer
- File search/read can be read-only.
- File writes, shell commands, installs and deletions require approval and sandboxing.

## Why these are not hard-coded in V0.3

The ChatGPT account connectors available in this conversation cannot simply be copied into a standalone personal application. A standalone KUTALP instance needs its own authorized OAuth/token flow. The architecture is ready for those adapters without embedding secrets in source code.
