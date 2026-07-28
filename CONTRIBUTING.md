# Contributing

## Branches

- `main`: stable releases only.
- `develop`: integration branch.
- `feature/<name>` and `fix/<name>`: short-lived implementation branches.

## Workflow

1. Create an issue describing the work.
2. Branch from `develop`.
3. Keep commits focused and test the affected project.
4. Open a pull request to `develop` and request a review.
5. Merge only after checks and review pass.

## Commit convention

Use Conventional Commits:

```text
feat: add product creation
fix: prevent orders without stock
test: add order authorization tests
docs: update installation instructions
refactor: extract inventory service
```

Do not commit `.env`, tokens, passwords, `node_modules`, `target`, database dumps, or IDE-specific files.
