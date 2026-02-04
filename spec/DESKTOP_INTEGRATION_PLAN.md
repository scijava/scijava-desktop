# Desktop Integration Architecture

## Overview

The scijava-desktop component provides unified desktop integration for SciJava applications, managing three kinds of OS integration:

1. **URI link scheme registration & handling** - Register custom URI schemes (e.g., `fiji://`) with the operating system
2. **Desktop icon generation** - Create application launchers in system menus
3. **File extension registration** - Associate file types with the application (planned)

This component comes with support for Linux, macOS, and Windows out of the box, and uses a plugin system for platform-specific implementations.

## Architecture

### Core Components

#### 1. LinkService & LinkHandler System

**LinkService** (`org.scijava.desktop.links.LinkService`)
- Service for routing URIs to appropriate handlers
- Automatically registers URI schemes with the OS on application startup
- Delegates URI handling to LinkHandler plugins

**LinkHandler** (`org.scijava.desktop.links.LinkHandler`)
- Plugin interface for handling specific URI schemes
- Implementations declare which schemes they support via `getSchemes()`
- Handle method processes the URI when clicked/opened

**LinkArgument** (`org.scijava.desktop.links.LinkArgument`)
- Console argument plugin that recognizes URIs on the command line
- Passes URIs to LinkService for handling

**SchemeInstaller** (`org.scijava.desktop.links.SchemeInstaller`)
- Platform-independent interface for OS registration
- Methods: `install()`, `uninstall()`, `isInstalled()`, `isSupported()`
- Implementations: WindowsSchemeInstaller, LinuxSchemeInstaller

#### 2. Platform Integration System

**DesktopIntegrationProvider** (`org.scijava.desktop.DesktopIntegrationProvider`)
- Interface implemented by Platform plugins
- Provides methods to query and toggle desktop integration features
- Methods:
  - `boolean isWebLinksEnabled()` / `setWebLinksEnabled(boolean)`
  - `boolean isWebLinksToggleable()`
  - `boolean isDesktopIconPresent()` / `setDesktopIconPresent(boolean)`
  - `boolean isDesktopIconToggleable()`

**Platform Implementations**:
- `WindowsPlatform` - Registry-based URI scheme registration
- `LinuxPlatform` - .desktop file management
- `MacOSPlatform` - Read-only status (uses Info.plist at build time)

**OptionsDesktop** (`org.scijava.desktop.options.OptionsDesktop`)
- User-facing options plugin (Edit > Options > Desktop...)
- Queries platform capabilities and displays toggleable features
- Applies changes directly to OS (no preference persistence)

#### 3. Linux Desktop File Management

**DesktopFile** (`org.scijava.desktop.platform.linux.DesktopFile`)
- Instance-based API for reading/writing Linux .desktop files
- Handles standard fields: Type, Name, Exec, Icon, Path, Categories, etc.
- MIME type management: `hasMimeType()`, `addMimeType()`, `removeMimeType()`
- Methods: `load()`, `save()`, `delete()`, `exists()`

**LinuxSchemeInstaller** (`org.scijava.desktop.platform.linux.LinuxSchemeInstaller`)
- Uses DesktopFile to add/remove URI scheme handlers
- Registers schemes via `xdg-mime default`
- Updates desktop database with `update-desktop-database`

## Platform-Specific Behavior

### Linux

**URI Schemes**:
- Adds `x-scheme-handler/<scheme>` to .desktop file MimeType field
- Registers with `xdg-mime default <app>.desktop x-scheme-handler/<scheme>`
- Updates desktop database for system-wide recognition

**Desktop Icon**:
- Creates .desktop file in `~/.local/share/applications/`
- Defaults to `~/.local/share/applications/<app-name>.desktop`
- Override via `scijava.app.desktop-file` system property
- File includes: Name, Exec, Icon, Categories, MimeType

**File Extensions** (planned):
- Add MIME types to .desktop file (e.g., `image/tiff`, `application/x-imagej-macro`)
- Register with `xdg-mime default`

**Toggleability**: Both web links and desktop icon are toggleable

### Windows

**URI Schemes**:
- Registers schemes in `HKEY_CURRENT_USER\Software\Classes\<scheme>`
- Uses `reg` command-line tool (no admin rights required)
- Creates registry structure: scheme → URL Protocol → shell → open → command

**Desktop Icon**:
- Not yet implemented
- Plan: Create Start Menu shortcut (.lnk file)
- Location: `%APPDATA%\Microsoft\Windows\Start Menu\Programs`

**File Extensions** (planned):
- Register in `HKCU\Software\Classes\.<ext>`

**Toggleability**: Web links are toggleable; desktop icon not yet supported

### macOS

**URI Schemes**:
- Declared in Info.plist within the .app bundle
- Configured at build time (bundle is code-signed and immutable)
- No runtime registration needed

**Desktop Icon**:
- Application bundle in /Applications (installed by user)
- User can pin to Dock manually

**File Extensions**:
- Declared in Info.plist at build time

**Toggleability**: All features are read-only (not toggleable at runtime)

## State Management

**No Preference Persistence**: Desktop integration state is NOT saved to preferences. Instead:
- On load: Query platform to get actual OS state
- On save: Write directly to OS (registry, files)
- Keeps settings UI in sync with reality
- Prevents sync issues if user manually modifies (e.g., deletes .desktop file)

**Query Flow**:
```
OptionsDesktop.load()
    → PlatformService.platform()
    → Platform instanceof DesktopIntegrationProvider
    → isWebLinksEnabled() / isDesktopIconPresent()
    → Query OS (registry, .desktop file, etc.)
```

**Apply Flow**:
```
OptionsDesktop.save()
    → setWebLinksEnabled(true) / setDesktopIconPresent(true)
    → Platform modifies OS directly
    → Windows: Update registry
    → Linux: Create/modify .desktop file
    → macOS: No-op (read-only)
```

## System Properties

Applications configure desktop integration via system properties:

| Property | Description | Platforms | Required |
|----------|-------------|-----------|----------|
| `scijava.app.executable` | Path to application executable | All | Yes (for URI schemes) |
| `scijava.app.name` | Application name | Linux | No (defaults to "SciJava Application") |
| `scijava.app.icon` | Icon path | Linux | No |
| `scijava.app.directory` | Working directory | Linux | No |
| `scijava.app.desktop-file` | Override .desktop file path | Linux | No (defaults to `~/.local/share/applications/<app>.desktop`) |

## Current Limitations & Known Issues

### Hardcoded Elements

1. **Scheme Names**: WindowsPlatform:86,102 and LinuxPlatform:112,129 hardcode "fiji" scheme
   - Should query LinkService for registered schemes instead
   - See NEXT.md Work Item #1

2. **OS Checks**: DefaultLinkService:119-132 hardcodes OS name checks
   - Should use PlatformService to get active platform
   - Should add getSchemeInstaller method
   - See NEXT.md Work Items #2 and #3

### Missing Features

1. **File Extension Registration**: Mentioned but not implemented
2. **Windows Desktop Icon**: Not yet implemented (Start Menu shortcut)
3. **First Launch Dialog**: No opt-in prompt on first run
4. **Multiple Schemes**: Platforms assume single scheme per application

## Usage Example

### Creating a LinkHandler

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
        String path = uri.getPath();
        // ... your logic here ...
    }

    @Override
    public List<String> getSchemes() {
        // Schemes to register with the OS
        return Arrays.asList("myapp");
    }
}
```

### Configuring the Application

Set system properties in your launcher script:

```bash
java -Dscijava.app.executable="/path/to/myapp" \
     -Dscijava.app.name="My Application" \
     -Dscijava.app.icon="/path/to/icon.png" \
     -jar myapp.jar
```

### User Experience

1. **Initial Setup**: Application can show first-launch dialog (planned)
2. **Options Dialog**: Users access Edit > Options > Desktop...
3. **Toggle Features**: Check/uncheck "Enable web links" and "Add desktop icon"
4. **OS Integration**: Changes apply immediately to registry/.desktop files

## Implementation Status

### ✅ Completed

- LinkService & LinkHandler system
- SchemeInstaller interface & implementations
- Platform plugins (Linux, Windows, macOS)
- DesktopIntegrationProvider interface
- OptionsDesktop plugin
- DesktopFile utility (Linux)
- URI scheme registration (Windows Registry)
- URI scheme registration (Linux .desktop files)
- Desktop icon installation (Linux)

### ⚠️ Needs Fixes

- Remove hardcoded "fiji" scheme references
- Remove hardcoded OS checks in DefaultLinkService
- Add getSchemeInstaller method

### ❌ Not Implemented

- File extension registration
- Windows desktop icon (Start Menu)
- First launch dialog
- Multiple scheme support in platforms
- Scheme validation (RFC 3986)

## Testing Strategy

### Unit Tests

- SchemeInstaller implementations (mock registry/file I/O)
- Platform DesktopIntegrationProvider implementations
- DesktopFile parsing/writing

### Integration Tests

- OptionsDesktop lifecycle (load → modify → save)
- Platform-specific workflows (enable/disable on each OS)
- LinkHandler discovery and routing

### Manual Testing

- Windows: Check registry entries with `regedit`
- Linux: Verify .desktop file contents and xdg-mime associations
- macOS: Verify read-only status reported correctly
- All platforms: Click URI links in browsers and verify app launches

## Design Decisions

### Why No Preference Persistence?

OS state is the source of truth. Users can manually modify registrations (delete .desktop files, edit registry), so we query the OS directly rather than trusting stale preferences.

### Why Platform Plugins?

The plugin system allows:
- No hardcoded OS checks
- Easy addition of new platforms
- Platform-specific behavior without coupling
- Runtime platform detection by SciJava's Platform system

### Why Separate SchemeInstaller?

LinkService needs to install schemes at startup, before UI is available. SchemeInstaller provides a non-UI API for OS registration, separate from the UI-facing DesktopIntegrationProvider.

### Why Linux Creates .desktop on Platform.configure()?

LinuxPlatform.configure() runs when the platform is activated, ensuring the .desktop file exists early for proper desktop integration. The file is then updated by DesktopIntegrationProvider methods when users toggle features.

## Future Enhancements

1. **Multi-scheme Support**: Allow platforms to handle multiple schemes independently
2. **Scheme Validation**: Validate scheme names against RFC 3986
3. **Repair Functionality**: "Recheck Registration" button to verify/repair installations
4. **Better Error Reporting**: User-friendly messages for permission errors
5. **Event System**: Publish events when registration state changes
6. **File Extension Support**: Full implementation of file type associations
7. **Windows Desktop Icon**: Start Menu shortcut creation
8. **macOS App Store**: Handle sandboxed app limitations
