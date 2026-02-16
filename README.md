# Pwndbg Bridge

A CLion plugin built for CTFs that brings Pwndbg-powered GDB workflows into a modern GUI. It adds a dedicated tool window where you can run Pwndbg/GDB commands, view context history, manage breakpoints, and inspect memory without leaving the IDE.

## Panels

### Command
Run Pwndbg/GDB commands directly from the tool window.
It supports ANSI-colored output, command history, and `Tab` completion.

### Context
Shows `context` output over time whenever execution pauses.
You can move through history with timeline controls and pin important states.

### Breakpoints
Provides a unified view of CLion and GDB breakpoints in one place.
You can toggle, delete, refresh, and jump to source locations for CLion breakpoints.

### Address
Inspects an address with `xinfo`, `telescope`, and `x/` in a single panel.
You can adjust `x/` format, change telescope count, and open inspections in new tabs.

### Maps
Displays `checksec`, `vmmap`, `got -r`, and `plt` outputs together.
Useful for quick binary and memory-layout inspection during debugging.

### Heap
Displays `vis-heap-chunks` output and lets you inspect chunk details.
`Ctrl+Click` on a chunk opens a detail view with `hi -v` and `try-free`.

### Heap Info
Shows `arenas`, `heap`, and `bins` outputs in one place.
Useful for understanding allocator state while stepping through execution.

## Other Features

- **External tooling bridge**: `socat` listens on TCP port `0xdead`, so tools like pwntools can connect.
- **Tab context menu actions**: Right-click a tab to change its text font size or move it to another Pwndbg tool window.

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

## Download

You can download the latest release from here:

- `https://github.com/Yu212/clion-pwndbg-bridge/releases/tag/latest`
