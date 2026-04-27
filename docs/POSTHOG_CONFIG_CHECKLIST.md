# PostHog Config Checklist

Last Updated: 2026-04-27  
Task: `T-S4-OBS-039`

## Required Runtime Config
- `enabled`: `true/false`
- `host`: `<https://app.posthog.com or self-host endpoint>`
- `project_api_key`: `<project key>`
- `environment`: `dev` / `prod`

## Current Status
- `enabled`: `true`（已内置默认值）
- `host`: `https://us.i.posthog.com`（US Cloud）
- `project_api_key`: `phc_wPMBC5C8pCscinCMjqbcFryREP5sKACufHzYiAWxtig6`（已内置）
- `project_id`: `399199`（已内置）
- `region`: `us_cloud`（已内置）
- `environment`: `prod`（已内置默认值，可覆盖）

## Environment Rules
- `dev` and `prod` should not share one project unless explicitly accepted.
- If only one project is used, event property `environment` must be mandatory.

## Safety Rules
- If any required config is missing, reporter must run in no-op mode.
- Configuration errors must never block playback or UI controls.
