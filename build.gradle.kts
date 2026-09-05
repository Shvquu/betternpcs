// Root project is an aggregator only — all real configuration lives in the modules and in the
// convention plugins under buildSrc/src/main/kotlin/.
//
// Layout:
//   api/               public API, MIT licensed, the only thing third-party plugins compile against
//   core/              engine, config, i18n, tracking — no NMS, no direct database access
//   storage/sql/       HikariCP + SQLite implementation of the repositories declared in core
//   versions/v*/       one NMS adapter per Minecraft version; the only NMS-aware code in the project
//   plugin/            assembles everything into the distributable BetterNPCs jar
//   examples/          sample integration, built in CI so a broken API fails the build

plugins {
    base
}
