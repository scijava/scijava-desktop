/*
 * #%L
 * Desktop integration for SciJava.
 * %%
 * Copyright (C) 2010 - 2026 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.desktop.platform.linux;

import org.scijava.log.LogService;
import org.scijava.platform.AbstractPlatform;
import org.scijava.platform.Platform;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A platform implementation for handling Linux platform issues.
 * <p>
 * This implementation creates and maintains a .desktop file for the application,
 * enabling proper desktop integration including:
 * </p>
 * <ul>
 * <li>Application launcher in menus</li>
 * <li>Application icon</li>
 * <li>File associations (via separate configuration)</li>
 * <li>URI scheme handling (via scijava-links)</li>
 * </ul>
 *
 * @author Curtis Rueden
 */
@Plugin(type = Platform.class, name = "Linux")
public class LinuxPlatform extends AbstractPlatform {

	@Parameter(required = false)
	private LogService log;

	// -- Platform methods --

	@Override
	public String osName() {
		return "Linux";
	}

	@Override
	public void configure(final PlatformService service) {
		super.configure(service);

		// Create or update .desktop file for desktop integration
		try {
			installDesktopFile();
		}
		catch (final IOException e) {
			if (log != null) {
				log.error("Failed to install .desktop file", e);
			}
		}
	}

	@Override
	public void open(final URL url) throws IOException {
		if (getPlatformService().exec("xdg-open", url.toString()) != 0) {
			throw new IOException("Could not open " + url);
		}
	}

	// -- Helper methods --

	/**
	 * Creates or updates the .desktop file for this application.
	 * <p>
	 * The .desktop file path is determined by the {@code scijava.app.desktop-file}
	 * system property. If not set, defaults to {@code ~/.local/share/applications/<app>.desktop}.
	 * </p>
	 */
	private void installDesktopFile() throws IOException {
		// Get configuration from system properties
		String desktopFilePath = System.getProperty("scijava.app.desktop-file");

		if (desktopFilePath == null) {
			// Default location
			final String appName = System.getProperty("scijava.app.name", "scijava-app");
			final String home = System.getProperty("user.home");
			desktopFilePath = home + "/.local/share/applications/" + sanitizeFileName(appName) + ".desktop";

			// Set property for other components (e.g., scijava-links)
			System.setProperty("scijava.app.desktop-file", desktopFilePath);
		}

		final Path desktopFile = Paths.get(desktopFilePath);

		// Check if file already exists and is up-to-date
		if (Files.exists(desktopFile) && isDesktopFileUpToDate(desktopFile)) {
			if (log != null) {
				log.debug("Desktop file is up-to-date: " + desktopFile);
			}
			return;
		}

		// Get application properties
		final String appName = System.getProperty("scijava.app.name", "SciJava Application");
		final String appExec = System.getProperty("scijava.app.executable");
		final String appIcon = System.getProperty("scijava.app.icon");
		final String appDir = System.getProperty("scijava.app.directory");

		if (appExec == null) {
			if (log != null) {
				log.debug("No executable path set (scijava.app.executable property), skipping .desktop file creation");
			}
			return;
		}

		// Create parent directory if needed
		final Path parent = desktopFile.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}

		// Write .desktop file
		try (final BufferedWriter writer = Files.newBufferedWriter(desktopFile, StandardCharsets.UTF_8)) {
			writer.write("[Desktop Entry]");
			writer.newLine();
			writer.write("Type=Application");
			writer.newLine();
			writer.write("Version=1.0");
			writer.newLine();
			writer.write("Name=" + appName);
			writer.newLine();
			writer.write("GenericName=" + appName);
			writer.newLine();
			writer.write("X-GNOME-FullName=" + appName);
			writer.newLine();

			if (appIcon != null) {
				writer.write("Icon=" + appIcon);
				writer.newLine();
			}

			writer.write("Exec=" + appExec + " %U");
			writer.newLine();

			if (appDir != null) {
				writer.write("Path=" + appDir);
				writer.newLine();
			}

			writer.write("Terminal=false");
			writer.newLine();
			writer.write("Categories=Science;Education;");
			writer.newLine();

			// MimeType field intentionally left empty
			// scijava-links will add URI scheme handlers (x-scheme-handler/...)
			writer.write("MimeType=");
			writer.newLine();
		}

		// Make file readable (but not writable) by others
		// This is standard practice for .desktop files
		// Files.setPosixFilePermissions can be used here if needed

		if (log != null) {
			log.info("Created desktop file: " + desktopFile);
		}
	}

	/**
	 * Checks if the desktop file is up-to-date with current system properties.
	 */
	private boolean isDesktopFileUpToDate(final Path desktopFile) {
		// For now, simple existence check
		// Future: could parse and compare with current properties
		return Files.exists(desktopFile);
	}

	/**
	 * Sanitizes a string for use as a file name.
	 */
	private String sanitizeFileName(final String name) {
		return name.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
	}
}
