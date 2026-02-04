# scijava-desktop Implementation Summary

## Component Purpose

The **scijava-desktop** component is a unified desktop integration library for SciJava applications, providing:

1. **URI Link Scheme Registration & Handling** - Register and handle custom URI schemes (e.g., `myapp://action`)
2. **Desktop Icon Generation** - Create application launchers in system menus (Linux .desktop files, Windows Start Menu planned)
3. **File Extension Registration** - Associate file types with the application (planned)

This component merges functionality from the former `scijava-links` and `scijava-plugins-platforms` repositories into a single, cohesive system.

## Architecture Overview

### Plugin-Based Platform System

The component uses SciJava's plugin architecture to avoid hardcoded OS checks:

- **Platform Plugins**: LinuxPlatform, WindowsPlatform, MacOSPlatform
- **Platform Detection**: Handled automatically by `PlatformService` from scijava-common
- **Platform Capabilities**: Each platform implements `DesktopIntegrationProvider` to expose capabilities

### Link Handling System

- **LinkService**: Routes URIs to registered handlers
- **LinkHandler**: Plugin interface for URI scheme implementations
- **SchemeInstaller**: Platform-specific OS registration (Windows Registry, Linux .desktop files)
- **LinkArgument**: Console argument plugin for CLI URI handling

### Desktop Integration UI

- **OptionsDesktop**: User-facing options plugin (Edit > Options > Desktop...)
- **DesktopIntegrationProvider**: Interface for querying/toggling integration features
- **No Preference Persistence**: State is always queried from OS, not saved to preferences

## Implementation Details

### Files Created

| File | Purpose |
|------|---------|
| `DesktopIntegrationProvider.java` | Interface for platform integration capabilities |
| `OptionsDesktop.java` | User options plugin for managing desktop integration |
| `DesktopFile.java` | Linux .desktop file parser/writer |
| `LinkService.java` | Service interface for URI handling |
| `DefaultLinkService.java` | LinkService implementation with OS scheme registration |
| `LinkHandler.java` | Plugin interface for URI handlers |
| `AbstractLinkHandler.java` | Base class for LinkHandler implementations |
| `LinkArgument.java` | Console argument plugin for CLI URIs |
| `Links.java` | Utility class for URI parsing |
| `SchemeInstaller.java` | Interface for OS scheme registration |
| `WindowsSchemeInstaller.java` | Windows Registry-based registration |
| `LinuxSchemeInstaller.java` | Linux .desktop file-based registration |

### Files Modified

| File | Changes |
|------|---------|
| `WindowsPlatform.java` | Implements DesktopIntegrationProvider; URI scheme registration |
| `LinuxPlatform.java` | Implements DesktopIntegrationProvider; .desktop file management |
| `MacOSPlatform.java` | Implements DesktopIntegrationProvider (read-only status) |
| `pom.xml` | Updated dependencies and build configuration |

## Feature Status

### ✅ Fully Implemented

#### URI Link Handling
- LinkService automatically registers schemes on application startup
- LinkHandler plugins declare schemes via `getSchemes()`
- LinkArgument processes URIs from command line
- Platform-specific registration (Windows Registry, Linux .desktop files)
- Runtime URI routing to appropriate handlers

#### Desktop Icon (Linux)
- Automatic .desktop file creation on LinuxPlatform.configure()
- Toggleable via OptionsDesktop UI
- Configurable via system properties:
  - `scijava.app.name` - Application name
  - `scijava.app.executable` - Executable path
  - `scijava.app.icon` - Icon path
  - `scijava.app.directory` - Working directory
  - `scijava.app.desktop-file` - Override .desktop file path

#### Platform Capabilities
- Windows: URI schemes toggleable (registry)
- Linux: URI schemes and desktop icon toggleable (.desktop file)
- macOS: Read-only status (Info.plist at build time)

#### Options UI
- Edit > Options > Desktop... menu entry
- Queries OS state on load
- Applies changes directly to OS on save
- Grays out non-toggleable features

### ⚠️ Partially Implemented (Needs Fixes)

#### Hardcoded Scheme Names
**Issue**: WindowsPlatform (lines 86, 102) and LinuxPlatform (lines 112, 129) hardcode "fiji" scheme instead of querying LinkService.

**Impact**: Only works for applications named "fiji"; breaks for other SciJava applications.

**Fix Required**: Query LinkService.getInstances() to collect schemes from all registered LinkHandler plugins.

#### Hardcoded OS Checks
**Issue**: DefaultLinkService.createInstaller() (lines 119-132) directly checks `os.name` system property and instantiates platform-specific installers.

**Impact**: Violates plugin architecture; makes adding new platforms difficult.

**Fix Required**:
1. Add getSchemeInstaller method
2. Platforms implement getSchemeInstaller
3. DefaultLinkService queries PlatformService.platform() instead of checking OS name

### ❌ Not Yet Implemented

#### File Extension Registration
**Status**: Architecture planned, not implemented

**Planned Approach**:
- Extend DesktopIntegrationProvider with file extension methods
- Linux: Add MIME types to .desktop file
- Windows: Register in `HKCU\Software\Classes\.<ext>`
- macOS: Declared in Info.plist (build-time, read-only)

#### Windows Desktop Icon
**Status**: Not implemented

**Planned Approach**:
- Create Start Menu shortcut (.lnk file)
- Location: `%APPDATA%\Microsoft\Windows\Start Menu\Programs`
- Use JNA or native executable for .lnk creation
- Update WindowsPlatform.isDesktopIconToggleable() to return true

#### First Launch Dialog
**Status**: Not implemented

**Planned Approach**:
- Add DesktopIntegrationService to track first launch
- Publish DesktopIntegrationPromptEvent on first run
- Application (Fiji) listens and shows opt-in dialog
- Dialog text from TODO.md (generic, using appService.getApp().getTitle())

#### macOS URI Scheme Registration
**Status**: Documentation only; no runtime registration

**Current State**: macOS platforms declare URI schemes in Info.plist at build time. The MacOSPlatform correctly reports read-only status, but there's no code to verify or assist with Info.plist configuration.

## Platform-Specific Implementation Details

### Linux Platform

**URI Scheme Registration**:
```
1. LinuxSchemeInstaller reads/writes .desktop file
2. Adds x-scheme-handler/<scheme> to MimeType field
3. Registers with: xdg-mime default <app>.desktop x-scheme-handler/<scheme>
4. Updates desktop database: update-desktop-database ~/.local/share/applications
```

**Desktop Icon**:
```
1. LinuxPlatform.configure() creates .desktop file if missing
2. Default location: ~/.local/share/applications/<app-name>.desktop
3. File includes: Type, Name, Exec, Icon, Path, Terminal, Categories, MimeType
4. User can toggle via OptionsDesktop (creates/deletes file)
```

**Files**:
- `LinuxPlatform.java` (Platform plugin)
- `LinuxSchemeInstaller.java` (SchemeInstaller implementation)
- `DesktopFile.java` (Utility for .desktop file I/O)

### Windows Platform

**URI Scheme Registration**:
```
1. WindowsSchemeInstaller uses 'reg' command-line tool
2. Registers in HKEY_CURRENT_USER\Software\Classes\<scheme>
3. Creates: (Default)="URL:<scheme>", URL Protocol="", shell\open\command\(Default)="<exe> %1"
4. No admin rights required (HKCU, not HKLM)
```

**Desktop Icon**:
- Not yet implemented
- WindowsPlatform.isDesktopIconToggleable() returns false

**Files**:
- `WindowsPlatform.java` (Platform plugin)
- `WindowsSchemeInstaller.java` (SchemeInstaller implementation)

### macOS Platform

**URI Scheme Registration**:
- Declared in Info.plist at build time
- MacOSPlatform reports read-only status (not toggleable)
- No runtime registration code

**Desktop Icon**:
- Application bundle installed by user (not programmatic)
- MacOSPlatform.isDesktopIconToggleable() returns false

**Additional Features**:
- MacOSAppEventDispatcher handles desktop events (open file, open URI, quit, about, preferences)
- Screen menu bar support

**Files**:
- `MacOSPlatform.java` (Platform plugin)
- `MacOSAppEventDispatcher.java` (Event handling)

## System Properties

| Property | Purpose | Platforms | Default |
|----------|---------|-----------|---------|
| `scijava.app.executable` | Executable path for URI scheme registration | All | None (required) |
| `scijava.app.name` | Application name for .desktop file | Linux | "SciJava Application" |
| `scijava.app.icon` | Icon path for .desktop file | Linux | None (optional) |
| `scijava.app.directory` | Working directory for .desktop file | Linux | None (optional) |
| `scijava.app.desktop-file` | Override .desktop file path | Linux | `~/.local/share/applications/<app>.desktop` |

## Usage Example

### Application Configuration

```bash
java -Dscijava.app.executable="/usr/local/bin/myapp" \
     -Dscijava.app.name="My Application" \
     -Dscijava.app.icon="/usr/share/icons/myapp.png" \
     -Dscijava.app.directory="/usr/local/share/myapp" \
     -jar myapp.jar
```

### LinkHandler Implementation

```java
@Plugin(type = LinkHandler.class)
public class MyAppLinkHandler extends AbstractLinkHandler {

    @Override
    public boolean supports(final URI uri) {
        return "myapp".equals(uri.getScheme());
    }

    @Override
    public void handle(final URI uri) {
        String operation = Links.operation(uri);
        Map<String, String> params = Links.query(uri);
        // ... handle URI ...
    }

    @Override
    public List<String> getSchemes() {
        return Arrays.asList("myapp");
    }
}
```

### User Workflow

1. Launch application
2. LinkService automatically registers "myapp" scheme with OS
3. User clicks `myapp://action?param=value` in browser
4. OS launches application with URI as argument
5. LinkArgument parses URI and passes to LinkService
6. LinkService routes to MyAppLinkHandler
7. Handler processes the request

## Design Rationale

### Why No Preference Persistence?

The OS is the source of truth for desktop integration state. Users can manually modify registrations (delete .desktop files, edit registry), so we always query the OS directly rather than trusting potentially stale preferences.

### Why Platform Plugins?

The plugin system provides:
- Automatic platform detection by PlatformService
- No hardcoded OS checks
- Easy addition of new platforms
- Platform-specific behavior without code coupling

### Why Separate SchemeInstaller?

LinkService needs to register schemes on application startup, before any UI is available. SchemeInstaller provides a non-UI API for OS registration, separate from the UI-facing DesktopIntegrationProvider used by OptionsDesktop.

### Why DesktopFile Utility?

Linux .desktop files are used by both LinuxPlatform (for desktop icon) and LinuxSchemeInstaller (for URI schemes). The DesktopFile utility avoids code duplication and provides a clean, instance-based API for .desktop file manipulation.

## Testing

### Current Test Coverage

- Compilation with Java 11: ✅
- Existing unit tests: ✅ (all pass)

### Manual Testing Needed

- [ ] Windows: Registry entries created correctly
- [ ] Windows: URI links launch application
- [ ] Linux: .desktop file created with correct content
- [ ] Linux: xdg-mime associations registered
- [ ] Linux: URI links launch application
- [ ] Linux: Desktop icon appears in application menu
- [ ] macOS: Read-only status reported correctly
- [ ] All platforms: OptionsDesktop UI displays correct state
- [ ] All platforms: Toggling features works as expected

### Test Scenarios

1. **Fresh Installation**: No existing .desktop file or registry entries
2. **Existing Registration**: Application already registered
3. **Manual Modification**: User deletes .desktop file or registry entry manually
4. **Multiple Schemes**: LinkHandler declares multiple schemes
5. **Permission Errors**: Insufficient permissions to write registry/files

## Known Issues

### Critical Issues

1. **Hardcoded "fiji" scheme**: Prevents use by other applications
   - Workaround: None; requires code fix
   - Priority: High

2. **Hardcoded OS checks**: Violates plugin architecture
   - Workaround: None; requires code fix
   - Priority: Medium

### Minor Issues

1. **No first launch dialog**: Users must discover OptionsDesktop manually
   - Workaround: Application can show custom dialog
   - Priority: Low

2. **Single scheme assumption**: Platforms assume one scheme per app
   - Workaround: Modify platform code to iterate schemes
   - Priority: Medium

3. **No file extension support**: Cannot register file types
   - Workaround: None; feature not implemented
   - Priority: Low

## Next Steps

See NEXT.md for detailed implementation plan, including:

1. Remove hardcoded scheme names (Priority 1)
2. Add getSchemeInstaller method (Priority 2)
3. Refactor DefaultLinkService#createInstaller() (Priority 3)
4. Implement first launch dialog (Optional)
5. Add file extension registration (Future)
6. Implement Windows desktop icon (Future)

## Dependencies

- **scijava-common**: Provides PlatformService, PluginService, AppService
- **Java 11+**: Required for java.awt.Desktop features
- **Platform-specific tools**:
  - Windows: `reg` command (built-in)
  - Linux: `xdg-mime`, `update-desktop-database` (part of xdg-utils)
  - macOS: Info.plist (build-time configuration)

## Backward Compatibility

- **No API breaks**: All additions are new interfaces and implementations
- **Opt-in behavior**: Applications without LinkHandlers are unaffected
- **No scijava-common changes**: All platform extensions are in scijava-desktop

## Summary

The scijava-desktop component provides a solid foundation for desktop integration, with working implementations for URI scheme registration and desktop icon management on Linux and Windows. The main remaining work is to remove hardcoded elements (scheme names, OS checks) and implement planned features (file extensions, Windows icon, first launch dialog).

The architecture is clean, plugin-based, and ready for production use once the hardcoded elements are addressed.
