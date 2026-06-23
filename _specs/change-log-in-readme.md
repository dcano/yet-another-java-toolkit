# Spec for change-log-in-readme

branch: feature/change-log-in-readme

## Summary
Add a CHANGELOG section to the project's README.md that documents notable changes, new features, bug fixes, and breaking changes per released version. This gives consumers of the toolkit a quick reference for what changed between versions without having to dig through git history or GitHub releases.

## Functional Requirements
- Add a `## Changelog` section to `README.md`
- List entries in reverse chronological order (newest version first)
- Each version entry must include the version number, release date, and a categorized list of changes
- Categories to use: `Added`, `Changed`, `Fixed`, `Breaking Changes` (omit empty categories)
- Cover all released versions available in git history/tags, starting from the earliest tagged release
- Keep entries concise — one line per change item

## Possible Edge Cases
- Snapshot versions (e.g. `2.1.1-SNAPSHOT`) should not appear as changelog entries — only released versions
- Breaking changes must be clearly flagged so consumers know when an upgrade requires migration work
- If a version had no notable changes for a given category, that category heading is omitted entirely

## Acceptance Criteria
- `README.md` contains a `## Changelog` section
- All released versions (as determined by git tags) have a corresponding entry
- Entries are sorted newest-first
- Each entry follows the `Added / Changed / Fixed / Breaking Changes` category structure
- No snapshot versions appear in the changelog
- The changelog is human-readable and accurate against actual release content (commits, PRs)

## Open Questions
- Should the changelog link to the GitHub release page or PR for each version entry?
- Should this eventually be extracted into a separate `CHANGELOG.md` file, or kept inside `README.md` for simplicity?

## Testing Guidelines
Create a test file(s) in the ./tests folder for the new feature, and create meaningful tests for the following cases, without going too heavy:
- Verify that `README.md` contains the `## Changelog` section
- Verify that snapshot version strings are not present in the changelog
- Verify that all known released version tags appear in the changelog
- Verify entries are in reverse chronological order
