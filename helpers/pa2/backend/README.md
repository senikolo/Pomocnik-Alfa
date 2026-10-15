# Optional bounded AI backend — NOT DEPLOYED

Deploy only on Cloudflare Workers Free, with AI binding and D1. The free daily pool
is shared among app users; exceeding it must return an error, never charge users.
Set IP_SALT as a secret, and optionally TAVILY_API_KEY for actual web retrieval.
A monthly 800-request search cap conservatively fits a 1000-credit free allocation;
provider billing periods can differ, so paid credit refill must remain disabled.
Cloudflare's free grant is recurring (currently 10,000 neurons/day), not a trial.
It does not guarantee unlimited use or future pricing. Model availability is external.
No model or search key belongs in the APK or public repository.

Create database, apply schema, provide its actual ID, set secrets, then deploy using
Wrangler. Test real inference and consent before publishing a nonempty endpoint in
`data/pa2-service.json`. That config must be an owner-controlled HTTPS workers.dev URL.
The current APK does not claim the prepared backend is live.

This is a direct Workers AI binding, separate from the Android timetable engine.
The local assistant is a task router; it must never be described as an active LLM.
