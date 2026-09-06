# Branch ruleset

`main-branch.json` protects the default branch: **a pull request cannot be merged unless the
`Build and test` job has passed.** Compile errors, failing tests and a broken Javadoc build are all
caught before anything reaches `main`.

Rulesets live in repository settings, not in the repository, so this file is not applied
automatically. It is kept here so the configuration is reviewable and versioned like everything
else, and so it can be restored after an accident.

## Applying it

**Settings → Rules → Rulesets → New ruleset → Import a ruleset**, then upload this file.

Or with the [`gh` CLI](https://cli.github.com/):

```bash
gh api --method POST /repos/Shvquu/betternpcs/rulesets \
  --input .github/rulesets/main-branch.json
```

To update an existing ruleset, `PUT /repos/Shvquu/betternpcs/rulesets/{id}` with the same file.

## What it does

| Rule | Effect |
|---|---|
| **Require a pull request** | No direct pushes to `main` |
| **Require `Build and test`** | The PR cannot merge until CI is green |
| **Require branches to be up to date** | A PR must include the current `main` before merging |
| **Block deletion** | `main` cannot be deleted |
| **Block force pushes** | History on `main` cannot be rewritten |

### Why no required approvals

`required_approving_review_count` is **0**. A pull request is still required, but nobody has to
approve it — otherwise a solo maintainer cannot merge their own work without adding themselves as a
bypass actor, which defeats the point of having the rule.

Raise it to `1` as soon as there is a second maintainer.

### Why branches must be up to date

`strict_required_status_checks_policy` is `true`. Two pull requests can each build in isolation and
still break `main` together — one renames a method, the other adds a call to it. Requiring the
branch to be current catches that.

The cost is that a merge to `main` invalidates every open pull request, each needing an update and a
fresh CI run. On a busy repository that becomes tiresome; set it to `false` then, accepting that a
semantic conflict between two green pull requests can reach `main`.

## If the status check does not appear

GitHub only offers a check as "required" once it has run at least once on the repository, so push a
commit or open a pull request first.

The name to require is the **job name** — `Build and test` — not the workflow name (`Build`) and not
the job id (`build`). It comes from `name:` in `.github/workflows/build.yml`; renaming that job
silently stops the rule from matching anything, so the two have to change together.

`integration_id: 15368` is GitHub Actions. Remove that line if you import the ruleset and GitHub
rejects it — it narrows the check to Actions rather than accepting a status of the same name from
any app, which is a small hardening, not a requirement.
