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
- SchemeInstallerProvider interface for platforms

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

### 2. Add SchemeInstallerProvider Interface

**Goal**: Allow platforms to provide SchemeInstaller instances without hardcoding in DefaultLinkService.

**New file**: `src/main/java/org/scijava/desktop/links/SchemeInstallerProvider.java`

```java
package org.scijava.desktop.links;

/**
 * Interface for platforms that provide {@link SchemeInstaller} functionality.
 * <p>
 * Platform implementations can implement this interface to provide
 * platform-specific URI scheme installation capabilities.
 * </p>
 */
public interface SchemeInstallerProvider {
    /**
     * Creates a SchemeInstaller for this platform.
     *
     * @return a SchemeInstaller, or null if not supported
     */
    SchemeInstaller getSchemeInstaller();
}
```

**Files to modify**:
- WindowsPlatform.java - implement SchemeInstallerProvider
- LinuxPlatform.java - implement SchemeInstallerProvider
- MacOSPlatform.java - implement SchemeInstallerProvider (return null)

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
    if (platform instanceof SchemeInstallerProvider) {
        return ((SchemeInstallerProvider) platform).getSchemeInstaller();
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

### 5. File Extension Registration (Future)

**Scope**: Extend DesktopIntegrationProvider to support file type associations.

**New methods**:
```java
boolean isFileExtensionsEnabled();
boolean isFileExtensionsToggleable();
void setFileExtensionsEnabled(boolean enable) throws IOException;
```

**Platform implementations**:
- **Linux**: Add MIME types to .desktop file (e.g., `image/tiff`, `application/x-imagej-macro`)
- **Windows**: Register file associations in Registry under `HKCU\Software\Classes\.<ext>`
- **macOS**: Declared in Info.plist (build-time, read-only)

### 6. Windows Start Menu Icon

**Goal**: Add desktop icon support for Windows.

**Implementation**:
- Create Start Menu shortcut (.lnk file) in `%APPDATA%\Microsoft\Windows\Start Menu\Programs`
- Use JNA or native executable to create shortcuts
- Update WindowsPlatform.isDesktopIconToggleable() to return true

## Testing Checklist

- [ ] Test URI scheme registration on Windows (registry manipulation)
- [ ] Test URI scheme registration on Linux (.desktop file updates)
- [ ] Test desktop icon installation on Linux
- [ ] Verify macOS reports correct read-only status
- [ ] Test OptionsDesktop UI with multiple schemes
- [ ] Verify scheme collection from LinkHandler plugins
- [ ] Test toggling features on/off via UI
- [ ] Verify no hardcoded "fiji" references remain

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

1. Should SchemeInstallerProvider be a separate interface or extend Platform?
2. How to handle partial scheme installation failures?
3. Should first launch dialog be mandatory or optional?
4. What file extensions should be supported by default?
