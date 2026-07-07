# 35 — Git Workflow

## Branches
- `main` — always buildable, represents the latest stable state
- `develop` — integration branch for in-progress work
- `feature/<short-description>` — individual feature branches, merged into `develop` via pull request
- `fix/<short-description>` — bug fix branches
- `release/<version>` — release stabilization branches cut from `develop`

## Commit Messages
Format:
```
<type>: <short summary>

<optional longer description>
```
Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

Example: `feat: add vocoder DSP stage to effect chain`

## Release Tags
- Semantic versioning: `vMAJOR.MINOR.PATCH` (e.g., `v1.2.0`)
- Tag created on `main` at the point a release build is finalized
- Tag annotated with a summary matching the GitHub Release notes

## Versioning
- **MAJOR:** breaking changes to preset format or architecture
- **MINOR:** new features (new effects, new UI screens) that remain backward-compatible
- **PATCH:** bug fixes, tuning adjustments, documentation updates

## Pull Request Expectations
- Reference relevant `docs/` sections affected by the change
- Include a note on manual testing performed (referencing `31_TEST_CASES.md` where applicable)
- Update `KNOWN_ISSUES.md` and `ROADMAP.md` if the change resolves or introduces tracked items
