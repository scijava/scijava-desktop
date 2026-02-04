[![Build Status](https://github.com/scijava/scijava-desktop/actions/workflows/build.yml/badge.svg)](https://github.com/scijava/scijava-desktop/actions/workflows/build.yml)

This component provides supporting code for SciJava applications
to manage their integration with the system native desktop:

* SciJava Platform plugins for macOS and Windows
* A links subsystem to handle URI-based links via plugins

The scijava-desktop component requires Java 11 as a minimum, due
to its use of java.awt.Desktop features not present in Java 8.
