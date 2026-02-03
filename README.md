# Pwndbg Bridge

A CLion plugin built for CTFs that brings Pwndbg-powered GDB workflows into a modern GUI. It adds a dedicated tool window where you can run Pwndbg/GDB commands, view context history, manage breakpoints, and inspect memory without leaving the IDE.

## Features

- **Command console**: Send Pwndbg/GDB commands and see colored output.
- **Context timeline**: Auto-refresh on pause with a slider, history, and bookmarks.
- **Breakpoints**: Unified list for CLion and GDB breakpoints with enable/disable and jump-to-source.
- **Address inspector**: `xinfo`, `telescope`, and `x/` views with quick format tweaks.
- **Maps**: `checksec`, `vmmap`, `got`, and `plt` with one-click refresh.
- **External tooling bridge**: `socat` listens on TCP port `0xdead`, so tools like pwntools can connect.

## Requirements

- CLion 2025.3+
- GDB with Pwndbg installed
- `socat` available in PATH

## Usage

1. Install the plugin (see Development below).
2. In `Build, Execution, Deployment > Toolchains > Debugger`, set the debugger to `Pwndbg GDB (DAP)`.
3. Create a `Custom Build Application` run configuration.
4. Start `Debug`.
5. Open the `Pwndbg` tool window.

## Development

Build the plugin:

```bash
./gradlew buildPlugin
```

The packaged `.jar` will be in `build/libs/`.
