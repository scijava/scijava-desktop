# Next Steps: SciJava Desktop Integration

This document outlines the remaining work for the scijava-desktop component.

## Overview

scijava-desktop provides unified desktop integration for SciJava applications, managing:
1. **URI link scheme registration & handling** (Linux, Windows, macOS)
2. **Desktop icon generation** (Linux .desktop file, Windows Start Menu - planned)
3. **File extension registration** (planned)

The component uses a plugin system where Platform implementations (LinuxPlatform, WindowsPlatform, MacOSPlatform) handle platform-specific integration.

## Current Status

### ✅ Implemented
- URI link handling system (LinkService, LinkHandler interface)
- Platform-specific URI scheme registration (Windows Registry, Linux .desktop files)
- Desktop icon management for Linux
- DesktopIntegrationProvider interface for platform capabilities
- OptionsDesktop plugin for user preferences (Edit > Options > Desktop...)
- Platform plugins for Linux, Windows, and macOS

### ⚠️ Partially Implemented (Needs Fixes)
- **Hardcoded scheme names**: WindowsPlatform:86,102 and LinuxPlatform:112,129 hardcode "fiji" scheme
- **Hardcoded OS checks**: DefaultLinkService:119-132 directly checks OS name strings and instantiates platform-specific installers

### ❌ Not Yet Implemented
- File extension registration
- Windows Start Menu icon generation
- First launch dialog for desktop integration opt-in

## Implementation Phases

### Phase 1: Fix Hardcoded Elements (High Priority)
These fixes are required for scijava-desktop to work for applications other than Fiji.

### Phase 2: File Extension Registration (High Priority)
Core functionality for Fiji and other scientific applications.

### Phase 3: Polish (Medium Priority)
First launch dialog, Windows desktop icon, etc.

---

## Priority Work Items

### 1. Remove Hardcoded Scheme Names

**Problem**: Both Windows and Linux platforms hardcode the "fiji" scheme instead of querying registered LinkHandlers.

**Files to modify**:
- `src/main/java/org/scijava/desktop/platform/windows/WindowsPlatform.java` (lines 86, 102)
- `src/main/java/org/scijava/desktop/platform/linux/LinuxPlatform.java` (lines 112, 129)

**Solution**: Query LinkService for all registered schemes from LinkHandler plugins.

**Example** (WindowsPlatform.java):
```java
@Override
public boolean isWebLinksEnabled() {
    final WindowsSchemeInstaller installer = new WindowsSchemeInstaller(log);
    final Set<String> schemes = collectSchemes();
    if (schemes.isEmpty()) return false;

    // Check if any scheme is installed
    for (String scheme : schemes) {
        if (installer.isInstalled(scheme)) return true;
    }
    return false;
}

@Override
public void setWebLinksEnabled(final boolean enable) throws IOException {
    final WindowsSchemeInstaller installer = new WindowsSchemeInstaller(log);
    final String executablePath = System.getProperty("scijava.app.executable");
    if (executablePath == null) {
        throw new IOException("No executable path set (scijava.app.executable property)");
    }

    final Set<String> schemes = collectSchemes();
    for (String scheme : schemes) {
        if (enable) {
            installer.install(scheme, executablePath);
        } else {
            installer.uninstall(scheme);
        }
    }
}

private Set<String> collectSchemes() {
    final Set<String> schemes = new HashSet<>();
    if (linkService == null) return schemes;

    for (final LinkHandler handler : linkService.getInstances()) {
        schemes.addAll(handler.getSchemes());
    }
    return schemes;
}
```

Similar changes needed for LinuxPlatform.

### 2. Add getSchemeInstaller method

**Goal**: Allow platforms to provide SchemeInstaller instances without hardcoding in DefaultLinkService.

**New method**: `DesktopIntegrationProvider#getSchemeInstaller()`

**Files to modify**:
- WindowsPlatform.java - implement getSchemeInstaller
- LinuxPlatform.java - implement getSchemeInstaller
- MacOSPlatform.java - implement getSchemeInstaller (return null)

### 3. Refactor DefaultLinkService#createInstaller()

**Problem**: Hardcoded OS checks violate the plugin architecture.

**Current code** (lines 119-132):
```java
private SchemeInstaller createInstaller() {
    final String os = System.getProperty("os.name");
    if (os == null) return null;

    final String osLower = os.toLowerCase();
    if (osLower.contains("linux")) {
        return new LinuxSchemeInstaller(log);
    }
    else if (osLower.contains("win")) {
        return new WindowsSchemeInstaller(log);
    }

    return null;
}
```

**Refactored code**:
```java
@Parameter(required = false)
private PlatformService platformService;

private SchemeInstaller createInstaller() {
    if (platformService == null) return null;

    final Platform platform = platformService.platform();
    if (platform instanceof DesktopIntegrationProvider) {
        return ((DesktopIntegrationProvider) platform).getSchemeInstaller();
    }

    return null;
}
```

### 4. First Launch Dialog (Optional)

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

### 5. File Extension Registration (High Priority)

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

**Solution**: Register custom MIME types in `~/.local/share/mime/packages/fiji.xml`

**Steps**:
1. Create extension → MIME type mapping
   - Standard formats: use existing types (`image/tiff`, `image/png`)
   - Microscopy formats: define custom types (`application/x-zeiss-czi`, `application/x-nikon-nd2`)
2. Generate `fiji.xml` with MIME type definitions
3. Install to `~/.local/share/mime/packages/`
4. Run `update-mime-database ~/.local/share/mime`
5. Add MIME types to .desktop file's `MimeType=` field

**MIME type naming convention**:
- Use vendor-specific: `application/x-{vendor}-{format}`
- Examples: `application/x-zeiss-czi`, `application/x-becker-hickl-sdt`

**Unregistration**:
- Remove MIME types from .desktop file (preserve URI schemes)
- Optionally delete `~/.local/share/mime/packages/fiji.xml`
- Run `update-mime-database` again

#### Windows (Simple - SupportedTypes Only)

**Solution**: Use `Applications\fiji.exe\SupportedTypes` registry approach

**Steps**:
1. Create `HKCU\Software\Classes\Applications\fiji.exe\SupportedTypes`
2. Add each extension as a value: `.tif = ""`
3. All ~150-200 extensions in one registry location

**Safety**: Deletion is safe - only removes our own `Applications\fiji.exe` tree

**Unregistration**:
- Delete entire `HKCU\Software\Classes\Applications\fiji.exe` key

#### macOS (Build-Time Only)

**Solution**: Declare all extensions in Info.plist at build time

**Format**: `CFBundleDocumentTypes` array with extension lists

**No runtime action needed** - this is a packaging/build concern

### 6. Windows Start Menu Icon

**Goal**: Add desktop icon support for Windows.

**Implementation**:
- Create Start Menu shortcut (.lnk file) in `%APPDATA%\Microsoft\Windows\Start Menu\Programs`
- Use JNA or native executable to create shortcuts
- Update WindowsPlatform.isDesktopIconToggleable() to return true

## Testing Checklist

### Phase 1 (Hardcoded Elements)
- [ ] Test URI scheme registration on Windows (registry manipulation)
- [ ] Test URI scheme registration on Linux (.desktop file updates)
- [ ] Test desktop icon installation on Linux
- [ ] Verify macOS reports correct read-only status
- [ ] Test OptionsDesktop UI with multiple schemes
- [ ] Verify scheme collection from LinkHandler plugins
- [ ] Test toggling features on/off via UI
- [ ] Verify no hardcoded "fiji" references remain

### Phase 2 (File Extensions)
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
