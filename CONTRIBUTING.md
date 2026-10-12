# Contributing

Want help contributing? Join the [Discord](https://discord.gg/BFDWmPfmXg)!

## Code Contributions

We patch Lunar's websocket RPC stubs (`com.lunarclient.websocket.*Service.Stub`) instead of its client code, since
everything under `com.moonsworth.lunar` is obfuscated and changes between builds. There are no loader entrypoints: the
mixin config is loaded through the [manifest](src/main/resources/META-INF/MANIFEST.MF) on Forge and through
[fabric.mod.json](src/main/resources/fabric.mod.json) on Fabric.

Please use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) in your pull request titles.

### AI

The use of AI-generated code is allowed so long as
 - It is used alongside human-generated code (fully vibe-coded PRs are not allowed)
 - You are the one creating and discussing the PR. We do not want to talk to your agent.
 - You are able to explain the code your agent has written as if you wrote it.
