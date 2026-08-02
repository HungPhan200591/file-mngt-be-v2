---
name: mermaid-styling
description: "Create or revise readable Mermaid diagrams using width-safe layouts, wrapped labels, bounded diagram scope, and the Swag high-contrast color palette. Use whenever a task creates or edits Mermaid content."
---

# Mermaid Styling

## Workflow

1. Read `references/rules.md` completely before acting.
2. Apply the user request, repository `AGENTS.md`, and current Codex instructions before the copied reference.
3. Treat slash-command syntax, `.agent/**` paths, unavailable `@skill` names, and Antigravity tool names inside copied references as historical syntax. Use this Codex skill, sibling repo skills, and currently available tools instead.
4. Keep changes scoped, preserve user-owned work, and report any dependency that cannot be resolved from current project sources.

## Project-specific rules

- Read the reference completely before editing Mermaid.
- Use diagrams only when they materially improve understanding; keep simple relationships in prose or tables.
- Design for the fixed-width Markdown viewport before applying colors: default to `TB`/`TD`, bound horizontal span, wrap labels, and split oversized diagrams as required by the reference.
- Announce this skill as `[Skill: mermaid-styling]` when it causes actions or pauses.
