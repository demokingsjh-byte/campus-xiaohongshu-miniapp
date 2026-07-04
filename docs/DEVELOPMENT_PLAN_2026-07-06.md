# 2026-07-06 Development Plan

## Goal

Move the campus Xiaohongshu miniapp from infrastructure readiness toward a usable campus content workflow, while keeping backend, admin UI, and miniapp packaging verifiable through GitHub Actions.

## Morning

1. Verify the latest `main` branch and confirm the `Campus CI` artifact outputs:
   - `campus-backend-jar`
   - `campus-admin-ui-dist`
   - `campus-miniapp-mp-weixin`
2. Review current miniapp pages under `campus-miniapp/src/pages` and map the required campus content flow:
   - home feed
   - publish note
   - login state
   - user profile entry
3. Confirm backend modules needed for the first usable loop:
   - authentication
   - note/content list
   - note publish
   - file or image upload placeholder

## Afternoon

1. Implement or align the first API contract for campus note publishing:
   - request fields
   - response shape
   - validation rules
   - error codes
2. Connect the miniapp publish page to the API contract, keeping mock fallback available for local development.
3. Add basic UI states:
   - loading
   - empty feed
   - publish success
   - publish failure

## Evening

1. Run local smoke checks for the touched module.
2. Push changes to trigger `Campus CI`.
3. Review GitHub Actions results and record any packaging failures in the follow-up task list.

## Acceptance Criteria

- The miniapp has a clear first-pass publish flow.
- The backend API contract is documented or implemented enough for frontend integration.
- `Campus CI` is triggered from the commit and produces packaging logs.
- Any failed build step has a concrete next action.

## Risks

- The backend platform is large, so full local builds may be slow on Windows.
- Miniapp build output may depend on environment-specific Uni-app configuration.
- Deployment should remain manual until the CI package result is confirmed.

