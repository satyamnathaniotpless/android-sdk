# Auth Core SDK (Android)

`auth-core-sdk` is a thin orchestration layer that composes:

- `utils-sdk` (events, IDs, networking helpers)
- `otp-sdk` (OTP auto-read)
- `sna-sdk` (SIM Network Authentication)

## Public API (framework)

- `AuthCoreSdk.initialize(context, appId)`
- `AuthCoreSdk.initiateAuth(...)` *(stub — logic to be added)*
- `AuthCoreSdk.verifyAuth(...)` *(stub — logic to be added)*

## Notes

- `initialize()` requires `appId` and will hardcode and propagate shared config (like event endpoint) to underlying SDKs.
- Exact auth flow implementation will be added next.

