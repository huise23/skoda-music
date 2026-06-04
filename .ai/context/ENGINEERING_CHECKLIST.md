# Engineering Checklist

## Pre-Execution

- [ ] Requirement and scope are clear.
- [ ] `.ai/context/SCOPE.md` has been checked.
- [ ] `.ai/context/PLAN.md` and `.ai/context/TASK_QUEUE.md` have been checked when the task is non-trivial.
- [ ] `.ai/context/ARCHITECTURE.md` has been checked.
- [ ] `.ai/context/CODE_STANDARDS.md` has been checked.
- [ ] `.ai/context/RED_LINES.md` has been checked.
- [ ] Target files/modules are identified.
- [ ] Each target file has one clear responsibility.
- [ ] Entry points remain thin.
- [ ] The change does not raise or bypass `minSdk = 17`.
- [ ] The change ignores `KugouMusic.NET/` unless it is being read as reference.
- [ ] The change does not require unplanned architecture drift.

## During Execution

- [ ] Keep lifecycle and entry-point code focused on wiring and delegation.
- [ ] Extract source, playback, UI rendering, cache, native, and state responsibilities into owned modules.
- [ ] Avoid expanding files already above warning/refactor/red thresholds.
- [ ] Add comments for non-obvious API17, lifecycle, threading, native, or fail-open behavior.
- [ ] Preserve user changes in the working tree.
- [ ] Do not introduce dependencies without compatibility justification.
- [ ] Stop if a red line may be violated.

## Post-Execution

- [ ] Check modified file sizes.
- [ ] Check function/method sizes where applicable.
- [ ] Check entry points are still thin or have been moved toward extraction.
- [ ] Check API17 guardrails.
- [ ] Check code health.
- [ ] Run build/test/lint commands appropriate to the task.
- [ ] Update `.ai/context/` with status, validation, and remaining risks.

## Recommended Validation

- `git diff --check`
- `./scripts/check_api17_guardrails.sh`
- `python scripts/check_code_health.py`
- `gradle :app:compileDebugKotlin --no-daemon`
- `gradle :app:assembleDebug --no-daemon`
