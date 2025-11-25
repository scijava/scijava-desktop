[![Build Status](https://github.com/scijava/scijava-desktop/actions/workflows/build.yml/badge.svg)](https://github.com/scijava/scijava-desktop/actions/workflows/build.yml)

This component provides supporting code for SciJava applications
to manage their integration with the system native desktop:

* SciJava Platform plugins for macOS and Windows
* A links subsystem to handle URI-based links via plugins

The scijava-desktop component requires Java 11 as a minimum, due
to its use of java.awt.Desktop features not present in Java 8.

## Features

- **Link handling**: Register custom handlers for URI schemes through the `LinkHandler` plugin interface
- **CLI integration**: Automatic handling of URI arguments passed on the command line via `ConsoleArgument`
- **OS integration**: Automatic registration of URI schemes with the operating system (Windows supported, macOS/Linux planned)

## Usage

### Creating a Link Handler

Implement the `LinkHandler` interface to handle custom URI schemes:

```java
@Plugin(type = LinkHandler.class)
public class MyLinkHandler extends AbstractLinkHandler {

    @Override
    public boolean supports(URI uri) {
        return "myapp".equals(uri.getScheme());
    }

    @Override
    public void handle(URI uri) {
        // Handle the URI
        System.out.println("Handling: " + uri);
    }

    @Override
    public List<String> getSchemes() {
        // Return schemes to register with the OS
        return Arrays.asList("myapp");
    }
}
```

### OS Registration

On Windows, URI schemes returned by `LinkHandler.getSchemes()` are automatically registered
in the Windows Registry when the `LinkService` initializes. This allows users to click
links like `myapp://action` in web browsers or other applications, which will launch your
Java application with the URI as a command-line argument.

The registration uses `HKEY_CURRENT_USER` and requires no administrator privileges.

See [doc/WINDOWS.md](doc/WINDOWS.md) for details.

## Architecture

- `LinkService` - Service for routing URIs to appropriate handlers
- `LinkHandler` - Plugin interface for implementing custom URI handlers
- `LinkArgument` - Console argument plugin that recognizes URIs on the command line
- `SchemeInstaller` - Interface for OS-specific URI scheme registration
- `WindowsSchemeInstaller` - Windows implementation using registry commands

The launcher should set the `scijava.app.executable` system property to enable URI scheme registration.
