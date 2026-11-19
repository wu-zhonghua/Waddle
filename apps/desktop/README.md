<p align="center">
  <a href="https://www.waddle.dev">
	<picture>
		<source media="(prefers-color-scheme: dark)" srcset="./assets/waddle-dark.png">
		<source media="(prefers-color-scheme: light)" srcset="./assets/waddle-light.png">
		<img alt="Waddle Logo" src="./assets/waddle-light.png" width="240">
	</picture>
  </a>
  <br/>
</p>

# Waddle

<div align="center">

[English](README.md) | [한국어](README.ko.md) | [繁體中文](README.zh-TW.md)

</div>

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fwaddledev%2Fwaddle.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fwaddledev%2Fwaddle?ref=badge_shield)

Waddle is an open-source, AI-integrated terminal for macOS, Linux, and Windows. It works with any AI model. Bring your own API keys for OpenAI, Claude, or Gemini, or run local models via Ollama and LM Studio. No accounts required.

Waddle also supports durable SSH sessions that survive network interruptions and restarts, with automatic reconnection. Edit remote files with a built-in graphical editor and preview files inline without leaving the terminal.

![Waddle Screenshot](./assets/waddle-screenshot.webp)

## Key Features

- Waddle AI - Context-aware terminal assistant that reads your terminal output, analyzes widgets, and performs file operations
- Durable SSH Sessions - Remote terminal sessions survive connection interruptions, network changes, and Waddle restarts with automatic reconnection
- Flexible drag & drop interface to organize terminal blocks, editors, web browsers, and AI assistants
- Built-in editor for editing remote files with syntax highlighting and modern editor features
- Rich file preview system for remote files (markdown, images, video, PDFs, CSVs, directories)
- Quick full-screen toggle for any block - expand terminals, editors, and previews for better visibility, then instantly return to multi-block view
- AI chat widget with support for multiple models (OpenAI, Claude, Azure, Perplexity, Ollama)
- Command Blocks for isolating and monitoring individual commands
- One-click remote connections with full terminal and file system access
- Secure secret storage using native system backends - store API keys and credentials locally, access them across SSH sessions
- Rich customization including tab themes, terminal styles, and background images
- Powerful `wsh` command system for managing your workspace from the CLI and sharing data between terminal sessions
- Connected file management with `wsh file` - seamlessly copy and sync files between local and remote SSH hosts

## Waddle AI

Waddle AI is your context-aware terminal assistant with access to your workspace:

- **Terminal Context**: Reads terminal output and scrollback for debugging and analysis
- **File Operations**: Read, write, and edit files with automatic backups and user approval
- **CLI Integration**: Use `wsh ai` to pipe output or attach files directly from the command line
- **BYOK Support**: Bring your own API keys for OpenAI, Claude, Gemini, Azure, and other providers
- **Local Models**: Run local models with Ollama, LM Studio, and other OpenAI-compatible providers
- **Free Beta**: Included AI credits while we refine the experience
- **Coming Soon**: Command execution (with approval)

Learn more in our [Waddle AI documentation](https://docs.waddle.dev/waveai) and [Waddle AI Modes documentation](https://docs.waddle.dev/waveai-modes).

## Installation

Waddle works on macOS, Linux, and Windows.

Platform-specific installation instructions can be found [here](https://docs.waddle.dev/gettingstarted).

You can also install Waddle directly from: [www.waddle.dev/download](https://www.waddle.dev/download).

### Minimum requirements

Waddle runs on the following platforms:

- macOS 11 or later (arm64, x64)
- Windows 10 1809 or later (x64)
- Linux based on glibc-2.28 or later (Debian 10, RHEL 8, Ubuntu 20.04, etc.) (arm64, x64)

The WSH helper runs on the following platforms:

- macOS 11 or later (arm64, x64)
- Windows 10 or later (x64)
- Linux Kernel 2.6.32 or later (x64), Linux Kernel 3.1 or later (arm64)

## Roadmap

Waddle is constantly improving! Our roadmap will be continuously updated with our goals for each release. You can find it [here](./ROADMAP.md).

Want to provide input to our future releases? Connect with us on [Discord](https://discord.gg/XfvZ334gwU) or open a [Feature Request](https://github.com/waddledev/waddle/issues/new/choose)!

## Links

- Homepage &mdash; https://www.waddle.dev
- Download Page &mdash; https://www.waddle.dev/download
- Documentation &mdash; https://docs.waddle.dev
- X &mdash; https://x.com/waddledev
- Discord Community &mdash; https://discord.gg/XfvZ334gwU

## Building from Source

See [Building Waddle](BUILD.md).

## Contributing

Waddle uses GitHub Issues for issue tracking.

Find more information in our [Contributions Guide](CONTRIBUTING.md), which includes:

- [Ways to contribute](CONTRIBUTING.md#contributing-to-wave-terminal)
- [Contribution guidelines](CONTRIBUTING.md#before-you-start)

### Sponsoring Waddle ❤️

If Waddle is useful to you or your company, consider sponsoring development.

Sponsorship helps support the time spent building and maintaining the project.

- https://github.com/sponsors/waddledev

## License

Waddle is licensed under the Apache-2.0 License. For more information on our dependencies, see [here](./ACKNOWLEDGEMENTS.md).
