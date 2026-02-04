# Windows Desktop Integration

This document describes how desktop integration works on Windows in scijava-desktop.

## URI Scheme Registration

### Overview

The scijava-desktop component automatically registers URI schemes with Windows when a SciJava application starts. The registration is done via the Windows Registry and requires no administrator privileges.

### How It Works

When the application starts:

1. `DefaultLinkService` initializes and listens for `ContextCreatedEvent`
2. Collects all URI schemes from registered `LinkHandler` plugins via `getSchemes()`
3. Reads the executable path from the `scijava.app.executable` system property
4. Creates a `WindowsSchemeInstaller` (currently hardcoded, should use platform plugin)
5. Registers each scheme in the Windows Registry

### Registry Structure

For a scheme named `myapp`, the following registry structure is created under `HKEY_CURRENT_USER`:

```
HKEY_CURRENT_USER\Software\Classes\myapp
    (Default) = "URL:myapp"
    URL Protocol = ""
    shell\
        open\
            command\
                (Default) = "C:\Path\To\App.exe" "%1"
```

### Registry Location

**Key**: `HKEY_CURRENT_USER\Software\Classes\<scheme>`

**Reason**: Using `HKEY_CURRENT_USER` (HKCU) instead of `HKEY_LOCAL_MACHINE` (HKLM) means:
- No administrator rights required
- Registration is per-user (not system-wide)
- Safe for non-privileged users

### Implementation Details

#### WindowsSchemeInstaller

The `WindowsSchemeInstaller` class provides the platform-specific implementation:

**Methods**:
- `isSupported()` - Returns true on Windows platforms
- `install(scheme, executablePath)` - Registers a URI scheme
- `isInstalled(scheme)` - Checks if a scheme is already registered
- `getInstalledPath(scheme)` - Gets the executable path for a registered scheme
- `uninstall(scheme)` - Removes a URI scheme registration

**Implementation Approach**:
- Uses the `reg` command-line tool (built into Windows)
- No JNA dependency required
- Executed via `ProcessBuilder` with proper timeout (10 seconds)
- Robust error handling and validation

#### Example Commands

**Add a scheme**:
```cmd
reg add "HKCU\Software\Classes\myapp" /ve /d "URL:myapp" /f
reg add "HKCU\Software\Classes\myapp" /v "URL Protocol" /d "" /f
reg add "HKCU\Software\Classes\myapp\shell\open\command" /ve /d "\"C:\Path\To\App.exe\" \"%1\"" /f
```

**Check if a scheme exists**:
```cmd
reg query "HKCU\Software\Classes\myapp\shell\open\command" /ve
```

**Delete a scheme**:
```cmd
reg delete "HKCU\Software\Classes\myapp" /f
```

### Configuration

#### System Properties

Applications must set the `scijava.app.executable` system property for URI scheme registration to work:

```bash
java -Dscijava.app.executable="C:\Program Files\MyApp\MyApp.exe" -jar myapp.jar
```

**Important**: The path should point to the actual executable (`.exe` file) that Windows should launch when a URI is clicked.

#### Launcher Configuration

For Windows applications with a native launcher:

```batch
@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-11
set APP_HOME=%~dp0

"%JAVA_HOME%\bin\java.exe" ^
    -Dscijava.app.executable="%APP_HOME%\MyApp.exe" ^
    -jar "%APP_HOME%\lib\myapp.jar"
```

### Creating a LinkHandler

To register custom URI schemes, create a `LinkHandler` plugin:

```java
package com.example;

import org.scijava.desktop.links.AbstractLinkHandler;
import org.scijava.desktop.links.LinkHandler;
import org.scijava.desktop.links.Links;
import org.scijava.plugin.Plugin;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Plugin(type = LinkHandler.class)
public class MyAppLinkHandler extends AbstractLinkHandler {

    @Override
    public boolean supports(final URI uri) {
        return "myapp".equals(uri.getScheme());
    }

    @Override
    public void handle(final URI uri) {
        // Parse the URI
        String operation = Links.operation(uri);
        Map<String, String> params = Links.query(uri);

        // Your business logic here
        System.out.println("Operation: " + operation);
        System.out.println("Parameters: " + params);
    }

    @Override
    public List<String> getSchemes() {
        // This tells DefaultLinkService to register "myapp://" with Windows
        return Arrays.asList("myapp");
    }
}
```

### User Experience

1. User installs and launches the application
2. Application automatically registers URI schemes with Windows
3. User clicks a `myapp://action?param=value` link in their browser
4. Windows launches the application with the URI as a command-line argument
5. `LinkArgument` plugin parses the URI and passes it to `LinkService`
6. `LinkService` routes the URI to the appropriate `LinkHandler`
7. Handler processes the request

### Desktop Integration Options

Users can manage URI scheme registration via **Edit > Options > Desktop...**:

- **Enable web links**: Toggleable checkbox to register/unregister URI schemes
- State is queried from the actual Windows Registry (not saved to preferences)
- Changes apply immediately to the registry

### Desktop Icon (Start Menu)

**Status**: Not yet implemented

**Planned Implementation**:
- Create Start Menu shortcut (.lnk file)
- Location: `%APPDATA%\Microsoft\Windows\Start Menu\Programs\<AppName>.lnk`
- Use JNA or native executable for .lnk creation
- Toggle via OptionsDesktop UI

## Testing

### Manual Testing

**Check Registry**:
1. Launch your application
2. Open `regedit`
3. Navigate to `HKEY_CURRENT_USER\Software\Classes\myapp`
4. Verify the structure matches the expected format

**Test URI Handling**:
1. Create an HTML file with a link: `<a href="myapp://test">Test Link</a>`
2. Open the HTML file in a browser
3. Click the link
4. Verify your application launches and handles the URI

**Test from Command Line**:
```cmd
start myapp://test?param=value
```

### Automated Tests

Run Windows-specific tests:
```bash
mvn test -Dtest=WindowsSchemeInstallerTest
```

Tests automatically skip on non-Windows platforms using JUnit's `Assume.assumeTrue()`.

## Known Issues

### Hardcoded Scheme Name

**Issue**: `WindowsPlatform` (lines 86, 102) currently hardcodes the "fiji" scheme instead of querying registered `LinkHandler` plugins.

**Impact**: Only works for Fiji; breaks for other applications.

**Workaround**: None; requires code fix.

**Fix**: See NEXT.md Work Item #1 for the solution.

### Hardcoded OS Checks

**Issue**: `DefaultLinkService.createInstaller()` (lines 119-132) hardcodes OS name checks instead of using `PlatformService`.

**Impact**: Violates plugin architecture.

**Workaround**: None; requires code fix.

**Fix**: See NEXT.md Work Items #2 and #3 for the solution.

## Platform Comparison

### Windows vs. Linux vs. macOS

| Feature | Windows | Linux | macOS |
|---------|---------|-------|-------|
| **URI Scheme Registration** | Runtime (Registry) | Runtime (.desktop file) | Build-time (Info.plist) |
| **Admin Rights Required** | No (HKCU) | No (user .desktop file) | N/A (bundle) |
| **Toggleable at Runtime** | Yes | Yes | No (read-only) |
| **Desktop Icon** | Planned (Start Menu) | Yes (.desktop file) | No (user pins to Dock) |
| **File Extensions** | Planned (Registry) | Planned (.desktop MIME types) | Build-time (Info.plist) |

### Why Runtime Registration on Windows?

Unlike macOS (where the `.app` bundle is code-signed and immutable), Windows allows runtime modification of the registry without special privileges. This makes it practical to register URI schemes when the application first runs, similar to how many Windows applications work.

## Security Considerations

### Registry Safety

- Only writes to `HKEY_CURRENT_USER` (per-user settings)
- Does not modify system-wide settings in `HKEY_LOCAL_MACHINE`
- Only registers URI schemes declared by `LinkHandler` plugins
- Does not expose arbitrary command execution

### Command-Line Injection

The `%1` parameter in the registry command is properly quoted:
```
"C:\Path\To\App.exe" "%1"
```

This prevents command-line injection attacks via malicious URIs.

## Future Enhancements

1. **Start Menu Shortcut**: Implement desktop icon creation
2. **File Extension Registration**: Register file types in the registry
3. **Scheme Validation**: Validate scheme names against RFC 3986
4. **User Prompts**: Optional confirmation before registering schemes
5. **Uninstallation**: Automatic cleanup on application uninstall
6. **Icon Support**: Associate icons with schemes and file types

## Resources

- [Microsoft URI Scheme Documentation](https://docs.microsoft.com/en-us/previous-versions/windows/internet-explorer/ie-developer/platform-apis/aa767914(v=vs.85))
- [Windows Registry Reference](https://docs.microsoft.com/en-us/windows/win32/sysinfo/registry)
- [RFC 3986 - URI Generic Syntax](https://www.rfc-editor.org/rfc/rfc3986)

## See Also

- [NEXT.md](../NEXT.md) - Planned improvements
- [spec/DESKTOP_INTEGRATION_PLAN.md](../spec/DESKTOP_INTEGRATION_PLAN.md) - Architecture overview
- [spec/IMPLEMENTATION_SUMMARY.md](../spec/IMPLEMENTATION_SUMMARY.md) - Implementation details
