# Stripe Skills

## What this is

Codex skills are installed globally for your Codex user, not per repository.

That means you install the Stripe skill once, then use it while working in this repo.

## Install the Stripe best practices skill

Run this from any terminal:

```bash
python "$CODEX_HOME/skills/.system/skill-installer/scripts/install-skill-from-github.py" --repo stripe/ai --path skills/stripe-best-practices
```

If `CODEX_HOME` is not set, Codex usually uses `~/.codex`.

Equivalent command with the default path:

```bash
python ~/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py --repo stripe/ai --path skills/stripe-best-practices
```

## Restart Codex

After installing a new skill, restart Codex so it picks it up.

## Use the skill in this repo

Open Codex in this repository and mention the skill in your prompt.

Examples:

```text
Use stripe-best-practices to review our Stripe integration.
```

```text
Use stripe-best-practices to suggest improvements to our checkout flow.
```

```text
Use stripe-best-practices before changing the payments controller.
```

## Optional: install the Stripe upgrade skill

If you also want Stripe API upgrade guidance, install this too:

```bash
python "$CODEX_HOME/skills/.system/skill-installer/scripts/install-skill-from-github.py" --repo stripe/ai --path skills/upgrade-stripe
```

## Notes

- The skill is not stored inside this repository.
- The repo-specific part is just opening Codex in this repo and using the skill in prompts.
- If the install command says the destination already exists, the skill is already installed.
- Installed skills usually live under `$CODEX_HOME/skills`.
