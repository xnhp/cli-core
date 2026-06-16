# Egg MCP Tool Layout Draft

Goal: expose the everyday GitHub, review, CI, and git workflows an agent needs while developing. The MCP layout should be ergonomic and workflow-oriented, not a 1:1 mirror of the Egg CLI command tree.

## Design Direction

1. Prefer high-level tools such as `egg_get_pr_context`, `egg_sync_review_threads`, and `egg_prepare_worktree` over direct command-path tools.
2. Separate read-only context gathering from mutating actions.
3. Avoid browser-opening tools in MCP; return URLs instead.
4. Avoid completion/server tools in MCP.
5. Add structured schemas for Egg tools. The current Egg command tree mostly lacks `CliOption` and `CliPositionalArg` metadata, so generated schemas would otherwise fall back to raw `args` arrays.

## Primary PR And Review Tools

### `egg_get_current_pr`

Return current PR identity and basic context for the working directory.

Inputs:

- `includeChecks`: boolean, default true.
- `includeReviewStatus`: boolean, default true.
- `includeFeedback`: boolean, default false.

Backs onto CLI capabilities: `egg gh pr current id|url|checks|review-status|feedback`.

Notes:

- This should combine several small CLI commands into one high-level MCP context tool.
- Return structured fields: repo, number, url, checks, review state, feedback summary.

### `egg_get_pr_feedback`

Fetch review feedback for a PR.

Inputs:

- `repo`: optional `owner/repo`; default inferred from cwd.
- `pr`: optional PR number; default current PR.
- `includeThreads`: boolean, default true.
- `includeChecks`: boolean, default true.

Backs onto CLI capabilities: `egg gh pr feedback`, `egg gh pr checks`, current PR variants.

Notes:

- This should be the standard “what needs addressing?” tool.
- Prefer structured output over CLI text when possible.

### `egg_reply_to_review_comments`

Reply to review comments.

Inputs:

- `comments`: array of comment ids or comment refs.
- `message`: reply body.
- `repo`: optional `owner/repo`.
- `pr`: optional PR number.

Backs onto CLI capability: `egg gh pr comment reply`.

Notes:

- This likely needs a custom MCP schema/decoder because stdin or free-form text is more ergonomic than CLI arg composition.
- Mark as mutating.

### `egg_resolve_review_comments`

Resolve review comments or threads.

Inputs:

- `comments`: array of comment ids or comment refs.
- `repo`: optional `owner/repo`.
- `pr`: optional PR number.

Backs onto CLI capability: `egg gh pr comment resolve`.

Notes:

- Mark as mutating.

## Issue Tools

## Search And Read Tools

### `egg_search_github`

Search GitHub code or PRs.

Inputs:

- `kind`: enum: `code`, `pullRequestsByIssue`.
- `query`: string.
- `org`: optional org, default KNIME where applicable.
- `limit`: optional integer.

Backs onto CLI capabilities: `egg gh search`, `egg gh search-prs`.

Notes:

- Combines two CLI commands because the MCP workflow is “find relevant GitHub things”.

### `egg_read_repo_file`

Read a file from a GitHub repository.

Inputs:

- `repo`: `owner/repo`.
- `path`: file path.
- `ref`: optional branch, tag, or commit.

Backs onto CLI capability: `egg gh look`.

Notes:

- Do not expose `look-web`; return a `webUrl` field instead when useful.

## CI Tools

### `egg_get_ci_status`

Show Jenkins or configured CI status for the current repo/branch/PR.

Inputs:

- `repo`: optional repo override.
- `branch`: optional branch override.
- `pr`: optional PR number.
- `includeLogs`: boolean, default false.

Backs onto CLI capability: `egg ci status`.

Notes:

- Return structured status: jobs, state, failing stages, URLs.

## Git Working Tree Tools

### `egg_get_git_context`

Return local git context useful before editing or committing.

Inputs:

- `includeChangedPaths`: boolean, default true.
- `includeAheadBehind`: boolean, default true.
- `stagedOnly`: boolean, default false.
- `range`: optional revision range for changed paths.

Backs onto CLI capabilities: `egg git changed-paths`, `egg git ahead feature`, `egg git ahead master`, `egg git behind master`.

Notes:

- This should combine several small git status helpers into one MCP context call.

### `egg_generate_commit_message`

Generate a commit message from staged changes.

Inputs:

- `style`: optional enum if styles are added later.
- `includeBody`: boolean, default true.

Backs onto CLI capability: `egg git generate-commit-message`.

Notes:

- Read-only with respect to git state.

### `egg_prepare_worktree`

Create or prepare a development worktree.

Inputs:

- `repoName`: repository name.
- `branch`: branch name.
- `subdir`: optional subdirectory.
- `override`: boolean, default false.

Backs onto CLI capability: `egg git worktree make`.

Notes:

- Mark as mutating.
- This is a good high-level workflow tool despite being backed by one CLI command.

### `egg_clone_repository`

Clone a repository or branch into the expected local layout.

Inputs:

- `repo`: `owner/repo` or repo name.
- `branch`: optional branch.
- `orgPreset`: optional enum such as `knime`.

Backs onto CLI capabilities: `egg git clone branch`, `egg git clone knime`.

Notes:

- Combine clone variants into one MCP tool.
- Mark as mutating.

### `egg_reword_commits`

Reword recent commits from issue ids or another supported mode.

Inputs:

- `mode`: enum matching supported reword modes.
- `issueIds`: optional array.
- `authorEmail`: optional override.
- `range`: optional commit range.
- `dryRun`: boolean if supported later.

Backs onto CLI capability: `egg git reword`.

Notes:

- Mark as destructive/mutating.
- This likely needs direct MCP-specific schema/decoder rather than raw CLI args.

## Tools To Avoid Exposing

1. `egg_mcp` / `egg.mcp`: starting an MCP server from an MCP server is not useful.
2. `egg_completion_zsh` / `egg.completion.zsh`: shell completion is not useful to MCP clients.
3. `egg_gh_look_web` and `egg_gh_pr_current_web`: browser-opening actions are poor MCP tools; return URLs from read tools instead.
4. Raw command-path mirrors for every small CLI helper when a combined workflow tool is clearer.

## Possible Escape Hatch

can run cli directly

## Decoupling Recommendation

Egg should decouple MCP tools from CLI leaves more aggressively than PDE:

1. Most Egg CLI leaves currently lack declarative option/positional metadata, so generated schemas would be too raw.
2. Several useful MCP tools should combine many tiny CLI commands into one context-oriented call, especially PR context and git context.
3. Stdin-oriented commands such as issue/thread push should become structured MCP inputs.
4. Browser-opening commands should become URL-returning read tools.
5. Mutating git/GitHub operations should have explicit structured inputs and conservative defaults.
