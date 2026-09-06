# What this changes

<!-- What the change does, and why. If it fixes an issue, write "Fixes #123" so GitHub links them. -->

# Why this approach

<!--
Only if the answer is not obvious from the diff. The alternative you rejected, and what made you
reject it, is worth more to a reviewer than a restatement of what the code does.
-->

# How you tested it

<!--
"./gradlew build passes" is the floor, not the answer. Did you run it on a server? Which Minecraft
version? A behaviour change nobody has watched happen is a behaviour change nobody has tested.
-->

---

## Checklist

- [ ] `./gradlew build` passes.
- [ ] New behaviour has a test, and the test fails without the change.

**If you touched the public API (`api/`):**

- [ ] Every new public type, method, parameter and return has Javadoc.
      The build enforces this — `:api:javadoc` runs with `Xdoclint:all` and fails on a missing tag.
- [ ] The change is additive. A source- or binary-incompatible change needs a major version, so if
      this removes or changes an existing signature, say so above.

**If you touched an NMS adapter (`versions/`):**

- [ ] The change was made in `versions/v1_21_4` — the reference implementation — and propagated with
      `tools/sync-adapters.sh`.
- [ ] A genuine difference between Minecraft versions was added to the delta section of that script
      rather than edited into a generated file by hand, which the next sync would overwrite.
- [ ] All six modules compile: `./gradlew build` covers this.

**If you added a message:**

- [ ] It is in the `Message` enum with its placeholders documented, and in **all four** files under
      `plugin/src/main/resources/languages/`. `PackagedResourcesTest` fails otherwise.

**If you added a permission:**

- [ ] It is a constant in `Permissions.java` **and** declared in `paper-plugin.yml`, including as a
      child of `betternpcs.admin`. `PackagedResourcesTest` checks both directions.

**If you added a configuration setting:**

- [ ] It is in `BetterNpcsConfig`, in the shipped `config.yml`, and in `BetterNpcsConfig.defaults()`.
      `PackagedResourcesTest` compares the shipped file against the code defaults.
- [ ] `docs/configuration.md` documents it.
