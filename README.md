# scijava-desktop

[![Build Status](https://github.com/scijava/scijava-desktop/actions/workflows/build.yml/badge.svg)](https://github.com/scijava/scijava-desktop/actions/workflows/build.yml)

Unified desktop integration for SciJava applications.

## Features

The scijava-desktop component provides three kinds of desktop integration:

1. **URI Link Scheme Registration & Handling**
   - Register custom URI schemes (e.g., `myapp://`) with the operating system
   - Handle URI clicks from web browsers and other applications
   - Automatic scheme registration on application startup

2. **Desktop Icon Generation**
   - Linux: `.desktop` file creation in application menus
   - Windows: Start Menu shortcuts (planned)
   - macOS: Application bundle support

3. **File Extension Registration**
   - Associate file types with your application
   - Platform-specific MIME type handling

## Platform Support

- **Linux**: Full support for URI schemes and desktop icons via `.desktop` files
- **Windows**: URI scheme registration via Windows Registry (desktop icons planned)
- **macOS**: Read-only support (configuration via Info.plist at build time)

## Requirements

- Java 11 or later (due to use of `java.awt.Desktop` features)
- Platform-specific tools:
  - Linux: `xdg-utils` (for `xdg-mime` and `update-desktop-database`)
  - Windows: `reg` command (built-in)
  - macOS: No runtime dependencies

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.scijava</groupId>
    <artifactId>scijava-desktop</artifactId>
    <version><!-- latest version --></version>
</dependency>
```

### 2. Configure System Properties

Set these properties when launching your application:

```bash
java -Dscijava.app.executable="/path/to/myapp" \
     -Dscijava.app.name="My Application" \
     -Dscijava.app.icon="/path/to/icon.png" \
     -Dscijava.app.wmclass="myapp-Main" \
     -jar myapp.jar
```

### 3. Create a LinkHandler Plugin

```java
package com.example;

import org.scijava.desktop.links.AbstractLinkHandler;
import org.scijava.desktop.links.LinkHandler;
import org.scijava.plugin.Plugin;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Plugin(type = LinkHandler.class)
public class MyAppLinkHandler extends AbstractLinkHandler {

    @Override
    public boolean supports(final URI uri) {
        return "myapp".equals(uri.getScheme());
    }

    @Override
    public void handle(final URI uri) {
        // Handle the URI (e.g., open a file, navigate to a view)
        System.out.println("Handling: " + uri);
    }

    @Override
    public List<String> getSchemes() {
        // Schemes to register with the OS
        return Arrays.asList("myapp");
    }
}
```

### 4. Launch and Test

1. Start your application
2. The `myapp://` scheme is automatically registered with your OS
3. Click a `myapp://action?param=value` link in your browser
4. Your application launches and handles the URI

## Architecture

### Link Handling System

- **LinkService**: Service for routing URIs to appropriate handlers
- **LinkHandler**: Plugin interface for implementing URI handlers
- **LinkArgument**: Console argument plugin for command-line URI handling
- **SchemeInstaller**: Platform-specific OS registration

### Platform Integration

- **Platform Plugins**: LinuxPlatform, WindowsPlatform, MacOSPlatform
- **DesktopIntegrationProvider**: Interface for querying/toggling desktop features
- **OptionsDesktop**: User-facing options plugin (Edit > Options > Desktop...)

### State Management

Desktop integration state is queried directly from the OS (not saved to preferences):
- On load: Query OS for current registration state
- On save: Apply changes directly to OS (e.g. registry, .desktop files)
- Keeps UI in sync with actual OS state

## System Properties

| Property                   | Description                         | Platforms | Default                                          |
|----------------------------|-------------------------------------|-----------|--------------------------------------------------|
| `scijava.app.executable`   | Path to application executable      | All       | Required for URI schemes and desktop icon        |
| `scijava.app.name`         | Application display name            | All       | `SciJava Application`                            |
| `scijava.app.icon`         | Icon path or theme name             | All       | None                                             |
| `scijava.app.directory`    | Application working directory       | All       | None                                             |
| `scijava.app.desktop-file` | Override `.desktop` file path       | Linux     | `~/.local/share/applications/<app>.desktop`      |
| `scijava.app.categories`   | Desktop entry categories            | Linux     | `Science;Education;`                             |
| `scijava.app.wmclass`      | X11 WM_CLASS for taskbar matching   | Linux     | None (field omitted if unset)                    |

## User Interface

Users can manage desktop integration via **Edit > Options > Desktop...** in your application:

- **Enable web links**: Register/unregister URI schemes
- **Add desktop icon**: Install/remove application launcher

The UI automatically grays out features that are not available on the current platform.

## Documentation

- [doc/WINDOWS.md](doc/WINDOWS.md) - Windows Registry-based URI scheme registration

## Examples

### Parse URI Components

```java
import org.scijava.desktop.links.Links;

URI uri = new URI("myapp://view/document?id=123&mode=edit");

String operation = Links.operation(uri);  // "view"
List<String> pathFragments = Links.pathFragments(uri);  // ["view", "document"]
Map<String, String> query = Links.query(uri);  // {"id": "123", "mode": "edit"}
```

### Multiple Schemes

```java
@Plugin(type = LinkHandler.class)
public class MultiSchemeLinkHandler extends AbstractLinkHandler {

    @Override
    public boolean supports(final URI uri) {
        String scheme = uri.getScheme();
        return "myapp".equals(scheme) || "myapp-dev".equals(scheme);
    }

    @Override
    public void handle(final URI uri) {
        if ("myapp-dev".equals(uri.getScheme())) {
            // Handle development scheme
        } else {
            // Handle production scheme
        }
    }

    @Override
    public List<String> getSchemes() {
        return Arrays.asList("myapp", "myapp-dev");
    }
}
```

## Platform-Specific Details

### Linux

URI schemes are registered by:
1. Creating a `.desktop` file in `~/.local/share/applications/`
2. Adding `x-scheme-handler/<scheme>` to the MimeType field
3. Registering with `xdg-mime default <app>.desktop x-scheme-handler/<scheme>`
4. Updating the desktop database with `update-desktop-database`

Desktop icons are created by installing the `.desktop` file with appropriate fields (`Name`, `Exec`, `Icon`, `Categories`, `TryExec`, `StartupNotify`, and optionally `StartupWMClass`). Set `scijava.app.wmclass` to the X11 WM_CLASS your application's windows carry (e.g. as set via `sun.awt.X11.XToolkit.awtAppClassName`) so GNOME Shell and other compositors can match the running window to the launcher entry for correct taskbar icons and grouping.

### macOS

URI schemes are declared in the application's `Info.plist` file within the `.app` bundle. This is configured at build time (bundle is code-signed and immutable), so runtime registration is not supported.

The MacOSPlatform correctly reports read-only status for all desktop integration features.

### Windows

URI schemes are registered in the Windows Registry under `HKEY_CURRENT_USER\Software\Classes\<scheme>`. No administrator privileges are required.

The registry structure:
```
HKCU\Software\Classes\myapp
    (Default) = "URL:myapp"
    URL Protocol = ""
    shell\open\command\
        (Default) = "C:\Path\To\App.exe" "%1"
```

## Manual Testing

**Windows**:
```bash
# Check registry after running your app
regedit
# Navigate to HKCU\Software\Classes\myapp
```

**Linux**:
```bash
# Check .desktop file
cat ~/.local/share/applications/myapp.desktop

# Check MIME associations
xdg-mime query default x-scheme-handler/myapp
```

**Test URI handling**:
```bash
# Linux/macOS
xdg-open "myapp://test"

# Windows
start "myapp://test"
```
