# Runtime library loading

The Paper `SyntaxLoader` uses the same manifest structure as PunisherX:
`repositories` maps repository IDs to URLs, and `libraries` contains either
`group:artifact:version` strings or mappings with `coordinate` and `repository`.
Gradle still expands versions from the existing build configuration.

`central: "MAVEN_CENTRAL_DEFAULT_MIRROR"` is resolved by the loader through
Paper's `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`, including the server's
configured mirror. Do not use the obsolete Paper `/repository/maven-central/` URL.

Libraries without an explicit repository use Central, except SyntaxDevTeam
groups (`pl.syntaxdevteam` and its subgroups), which use the snapshots or releases
repository according to their version. An explicit `repository` always wins.
Each selected repository gets its own resolver with Central available for
transitive dependencies. Unknown repository IDs, invalid entries, missing manifests and
unexpanded placeholders stop bootstrap with a descriptive error.

Example:

```yaml
repositories:
  central: "MAVEN_CENTRAL_DEFAULT_MIRROR"
  syntaxdevteam-snapshots: "https://nexus.syntaxdevteam.pl/repository/maven-snapshots/"
  syntaxdevteam-releases: "https://nexus.syntaxdevteam.pl/repository/maven-releases/"
  custom: "https://example.com/maven/"
libraries:
  - com.github.ben-manes.caffeine:caffeine:3.2.4
  - coordinate: com.example:library:1.0.0
    repository: custom
```

This change preserves library versions and the Paper/Folia plugin lifecycle.
It only changes dependency bootstrap and manifest validation.

## Verification — 2026-10-11

`test build` passed (41 tests, including seven loader regression tests;
no failures or errors). The packaged Paper JAR contains the expanded manifest
with repository assignments and no obsolete Central URL or unresolved versions.
A live Paper/Folia server start was not repeated.
