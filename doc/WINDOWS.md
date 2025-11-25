# Windows URI Scheme Registration

This document describes how URI scheme registration works on Windows in scijava-links.

## Overview

When a SciJava application starts on Windows, the `DefaultLinkService` automatically:

1. Collects all URI schemes from registered `LinkHandler` plugins via `getSchemes()`
2. Reads the executable path from the `scijava.app.executable` system property
3. Registers each scheme in the Windows Registry under `HKEY_CURRENT_USER\Software\Classes`

## Registry Structure

For a scheme named `myapp`, the following registry structure is created:

```
HKEY_CURRENT_USER\Software\Classes\myapp
    (Default) = "URL:myapp"
    URL Protocol = ""
    shell\
        open\
            command\
                (Default) = "C:\Path\To\App.exe" "%1"
```

## Implementation Details

### SchemeInstaller Interface

The `SchemeInstaller` interface provides a platform-independent API for URI scheme registration:

- `isSupported()` - Checks if the installer works on the current platform
- `install(scheme, executablePath)` - Registers a URI scheme
- `isInstalled(scheme)` - Checks if a scheme is already registered
- `getInstalledPath(scheme)` - Gets the executable path for a registered scheme
- `uninstall(scheme)` - Removes a URI scheme registration

### WindowsSchemeInstaller

The Windows implementation uses the `reg` command-line tool to manipulate the registry:

- **No JNA dependency**: Uses native Windows `reg` commands via `ProcessBuilder`
- **No admin rights**: Registers under `HKEY_CURRENT_USER` (not `HKEY_LOCAL_MACHINE`)
- **Idempotent**: Safely handles re-registration with the same or different paths
- **Robust error handling**: Proper timeouts, error logging, and validation

### Executable Path Configuration

The launcher must set the `scijava.app.executable` system property to the absolute path of the application's executable. This property is used by `DefaultLinkService` during URI scheme registration.

Example launcher configuration:
```bash
java -Dscijava.app.executable="C:\Program Files\MyApp\MyApp.exe" -jar myapp.jar
```

On Windows, the launcher typically sets this to the `.exe` file path. On macOS, it would be the path inside the `.app` bundle. On Linux, it would be the shell script or executable.

## Example Handler

Here's a complete example of a `LinkHandler` that registers a custom scheme:

```java
package com.example;

import org.scijava.links.AbstractLinkHandler;
import org.scijava.links.LinkHandler;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Plugin(type = LinkHandler.class)
public class ExampleLinkHandler extends AbstractLinkHandler {

    @Parameter(required = false)
    private LogService log;

    @Override
    public boolean supports(final URI uri) {
        return "example".equals(uri.getScheme());
    }

    @Override
    public void handle(final URI uri) {
        if (log != null) {
            log.info("Handling example URI: " + uri);
        }

        // Parse the URI and perform actions
        String operation = Links.operation(uri);
        Map<String, String> params = Links.query(uri);

        // Your business logic here
        // ...
    }

    @Override
    public List<String> getSchemes() {
        // This tells the system to register "example://" links on Windows
        return Arrays.asList("example");
    }
}
```

## Testing

The Windows scheme installation can be tested on Windows systems:

```bash
mvn test -Dtest=WindowsSchemeInstallerTest
```

Tests are automatically skipped on non-Windows platforms using JUnit's `Assume.assumeTrue()`.

To test with a specific executable path, set the system property:
```bash
mvn test -Dscijava.app.executable="C:\Path\To\App.exe"
```

## Platform Notes

**macOS**: URI schemes are declared in the application's `Info.plist` within the `.app` bundle. This is configured at build/packaging time, not at runtime, since the bundle is typically code-signed and immutable.

**Linux**: URI schemes are declared in `.desktop` files, which is part of broader desktop integration (icons, MIME types, etc.). This functionality belongs in `scijava-plugins-platforms` rather than this component.

**Windows**: Runtime registration is appropriate because the Windows Registry is designed for runtime modifications, and registration under `HKEY_CURRENT_USER` requires no elevated privileges.

## Future Enhancements

- **Scheme validation**: Validate scheme names against RFC 3986
- **User prompts**: Optional confirmation before registering schemes
- **Uninstallation**: Automatic cleanup on application uninstall
