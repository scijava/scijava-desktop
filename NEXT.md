# Next Steps: SciJava Desktop Integration

This document outlines the remaining work for the scijava-desktop component.

## Overview

scijava-desktop provides unified desktop integration for SciJava applications, managing:
1. **URI link scheme registration & handling** (Linux, Windows, macOS)
2. **Desktop icon generation** (Linux .desktop file, Windows Start Menu - planned)
3. **File extension registration** (Linux .desktop file, Windows registry)

The component uses a plugin system where Platform implementations (LinuxPlatform, WindowsPlatform, MacOSPlatform) handle platform-specific integration.

## Current Status

### ✅ Implemented
- URI link handling system (LinkService, LinkHandler interface)
- Platform-specific URI scheme registration (Windows Registry, Linux .desktop files)
- Desktop icon management for Linux
- DesktopIntegrationProvider interface for platform capabilities
- OptionsDesktop plugin for user preferences (Edit > Options > Desktop...)
- Platform plugins for Linux, Windows, and macOS
- File extension registration

### ❌ Not Yet Implemented
- Windows Start Menu icon generation
- First launch dialog for desktop integration opt-in

---

## Priority Work Items

### 1. First Launch Dialog (Optional)

**Goal**: Prompt user on first launch to enable desktop integration.

**Implementation approach**:
- Add `DesktopIntegrationService` to track first launch
- Publish `DesktopIntegrationPromptEvent` on first run
- Application (Fiji) can listen and show dialog

**Dialog content**:
```
Would you like to integrate [App Name] into your desktop?

If you say Yes, I will:
- Add a [App Name] desktop icon
- Add [App Name] as a handler for supported file types
- Allow [App Name] to handle [scheme]:// web links

Either way: "To change these settings in the future, use Edit > Options > Desktop..."
```

**Implementation notes**:
- Use `appService.getApp().getTitle()` for `[App Name]` to keep it generic
- Collect schemes from all `LinkHandler` plugins to populate `[scheme]://`
- Show this dialog only on first launch (track via preferences)
- Provide "Yes" and "No" options
- If user selects "Yes", enable all available desktop integration features
- If user selects "No", do nothing
- Store user preference by writing to a local configuration file -- avoids showing dialog again

### 2. File Extension Registration (High Priority)

**Scope**: Extend DesktopIntegrationProvider to support file type associations.

**New methods**:
```java
boolean isFileExtensionsEnabled();
boolean isFileExtensionsToggleable();
void setFileExtensionsEnabled(boolean enable) throws IOException;
```

**Platform implementations**:

#### Linux (Complex - Custom MIME Types Required)

**Problem**: Microscopy formats (.sdt, .czi, .nd2, .lif, etc.) lack standard MIME types.

**Solution**: Register custom MIME types in `~/.local/share/mime/packages/{appName}.xml`

**Steps**:
1. Create extension → MIME type mapping
   - Standard formats: use existing types (`image/tiff`, `image/png`)
   - Microscopy formats: define custom types (`application/x-zeiss-czi`, `application/x-nikon-nd2`)
2. Generate `{appName}.xml` with MIME type definitions
3. Install to `~/.local/share/mime/packages/`
4. Run `update-mime-database ~/.local/share/mime`
5. Add MIME types to .desktop file's `MimeType=` field

**MIME type naming convention**:
- Use vendor-specific: `application/x-{vendor}-{format}`
- Examples: `application/x-zeiss-czi`, `application/x-becker-hickl-sdt`

**Unregistration**:
- Remove MIME types from .desktop file (preserve URI schemes)
- Optionally delete `~/.local/share/mime/packages/{appName}.xml`
- Run `update-mime-database` again

#### Windows (Simple - SupportedTypes Only)

**Solution**: Use `Applications\{appName}.exe\SupportedTypes` registry approach

**Steps**:
1. Create `HKCU\Software\Classes\Applications\{appName}.exe\SupportedTypes`
2. Add each extension as a value: `.tif = ""`
3. All ~150-200 extensions in one registry location

**Safety**: Deletion is safe - only removes our own `Applications\{appName}.exe` tree

**Unregistration**:
- Delete entire `HKCU\Software\Classes\Applications\{appName}.exe` key

#### macOS (Build-Time Only)

**Solution**: Declare all extensions in Info.plist at build time

**Format**: `CFBundleDocumentTypes` array with extension lists

**No runtime action needed** - this is a packaging/build concern

### 3. Windows Start Menu Icon

**Goal**: Add desktop icon support for Windows.

**Implementation**:
- Create Start Menu shortcut (.lnk file) in `%APPDATA%\Microsoft\Windows\Start Menu\Programs`
- Use JNA or native executable to create shortcuts
- Update WindowsPlatform.isDesktopIconToggleable() to return true

## Testing Checklist

### Scheme Registration
- [ ] Test URI scheme registration on Windows (registry manipulation)
- [ ] Test URI scheme registration on Linux (.desktop file updates)
- [ ] Test desktop icon installation on Linux
- [ ] Verify macOS reports correct read-only status
- [ ] Test OptionsDesktop UI with multiple schemes
- [ ] Verify scheme collection from LinkHandler plugins
- [ ] Test toggling features on/off via UI

### File Extensions
- [ ] Test Linux MIME type generation and installation
- [ ] Verify `update-mime-database` runs successfully
- [ ] Test file extension associations appear in file managers (Nautilus, Dolphin)
- [ ] Test Windows SupportedTypes registration
- [ ] Verify Fiji appears in "Open With" for all extensions
- [ ] Test unregistration (verify complete cleanup)
- [ ] Test with ~150-200 actual file extensions
- [ ] Verify no `application/octet-stream` claims

## System Properties Reference

- `scijava.app.executable` - Path to application executable (required for all platforms)
- `scijava.app.name` - Application name (defaults to "SciJava Application")
- `scijava.app.icon` - Icon path (Linux only)
- `scijava.app.directory` - Working directory (Linux only)
- `scijava.app.desktop-file` - Override .desktop file path (Linux only)

## Documentation Updates Needed

- [ ] Update README.md with current feature status
- [ ] Update doc/WINDOWS.md to reflect generic scheme handling
- [ ] Create doc/LINUX.md documenting .desktop file integration
- [ ] Create doc/MACOS.md explaining Info.plist configuration
- [ ] Add examples of LinkHandler implementation

## Questions to Resolve

1. How to handle partial scheme installation failures?
2. Should first launch dialog be mandatory or optional?
3. What file extensions should be supported by default?
