/*-
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

import org.scijava.desktop.links.SchemeInstaller;
import org.scijava.log.LogService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Linux implementation of {@link SchemeInstaller} using .desktop files.
 * <p>
 * This implementation modifies the .desktop file specified by the
 * {@code scijava.app.desktop-file} system property to add URI scheme
 * handlers via the MimeType field, then registers them using xdg-mime.
 * </p>
 *
 * @author Curtis Rueden
 */
public class LinuxSchemeInstaller implements SchemeInstaller {

	private static final long COMMAND_TIMEOUT_SECONDS = 10;

	private final LogService log;

	public LinuxSchemeInstaller(final LogService log) {
		this.log = log;
	}

	@Override
	public boolean isSupported() {
		final String os = System.getProperty("os.name");
		return os != null && os.toLowerCase().contains("linux");
	}

	@Override
	public void install(final String scheme, final String executablePath) throws IOException {
		if (!isSupported()) {
			throw new UnsupportedOperationException("Linux .desktop file installation not supported on: " + System.getProperty("os.name"));
		}

		// Validate inputs
		if (scheme == null || scheme.isEmpty()) {
			throw new IllegalArgumentException("Scheme cannot be null or empty");
		}

		// Get desktop file path from system property
		final String desktopFileProp = System.getProperty("scijava.app.desktop-file");
		if (desktopFileProp == null || desktopFileProp.isEmpty()) {
			throw new IOException("scijava.app.desktop-file property not set");
		}

		final Path desktopFile = Paths.get(desktopFileProp);
		if (!Files.exists(desktopFile)) {
			throw new IOException("Desktop file does not exist: " + desktopFile);
		}

		// Parse desktop file
		final DesktopFile df = DesktopFile.parse(desktopFile);

		// Check if scheme already registered
		final String mimeType = "x-scheme-handler/" + scheme;
		if (df.hasMimeType(mimeType)) {
			if (log != null) log.debug("Scheme '" + scheme + "' already registered in: " + desktopFile);
			return;
		}

		// Add MIME type
		df.addMimeType(mimeType);

		// Write back to file
		df.writeTo(desktopFile);

		// Register with xdg-mime
		final String desktopFileName = desktopFile.getFileName().toString();
		if (!executeCommand(new String[]{"xdg-mime", "default", desktopFileName, mimeType})) {
			throw new IOException("Failed to register scheme with xdg-mime: " + scheme);
		}

		// Update desktop database
		final Path applicationsDir = desktopFile.getParent();
		if (applicationsDir != null) {
			executeCommand(new String[]{"update-desktop-database", applicationsDir.toString()});
			// Note: update-desktop-database may fail if not installed, but this is non-critical
		}

		if (log != null) log.info("Registered URI scheme '" + scheme + "' in: " + desktopFile);
	}

	@Override
	public boolean isInstalled(final String scheme) {
		if (!isSupported()) return false;

		final String desktopFileProp = System.getProperty("scijava.app.desktop-file");
		if (desktopFileProp == null) return false;

		final Path desktopFile = Paths.get(desktopFileProp);
		if (!Files.exists(desktopFile)) return false;

		try {
			final DesktopFile df = DesktopFile.parse(desktopFile);
			return df.hasMimeType("x-scheme-handler/" + scheme);
		}
		catch (final IOException e) {
			if (log != null) log.debug("Failed to parse desktop file: " + desktopFile, e);
			return false;
		}
	}

	@Override
	public String getInstalledPath(final String scheme) {
		if (!isInstalled(scheme)) return null;

		final String desktopFileProp = System.getProperty("scijava.app.desktop-file");
		if (desktopFileProp == null) return null;

		final Path desktopFile = Paths.get(desktopFileProp);

		try {
			final DesktopFile df = DesktopFile.parse(desktopFile);
			final String exec = df.get("Exec");
			if (exec == null) return null;

			// Parse executable path from Exec line (format: "/path/to/app %U")
			final String[] parts = exec.split("\\s+");
			if (parts.length > 0) {
				return parts[0];
			}
		}
		catch (final IOException e) {
			if (log != null) log.debug("Failed to parse desktop file: " + desktopFile, e);
		}

		return null;
	}

	@Override
	public void uninstall(final String scheme) throws IOException {
		if (!isSupported()) {
			throw new UnsupportedOperationException("Linux .desktop file uninstallation not supported on: " + System.getProperty("os.name"));
		}

		if (!isInstalled(scheme)) {
			if (log != null) log.debug("Scheme '" + scheme + "' is not installed");
			return;
		}

		final String desktopFileProp = System.getProperty("scijava.app.desktop-file");
		final Path desktopFile = Paths.get(desktopFileProp);

		// Parse and remove MIME type
		final DesktopFile df = DesktopFile.parse(desktopFile);
		df.removeMimeType("x-scheme-handler/" + scheme);
		df.writeTo(desktopFile);

		// Update desktop database
		final Path applicationsDir = desktopFile.getParent();
		if (applicationsDir != null) {
			executeCommand(new String[]{"update-desktop-database", applicationsDir.toString()});
		}

		if (log != null) log.info("Uninstalled URI scheme: " + scheme);
	}

	// -- Helper methods --

	/**
	 * Executes a command and returns whether it succeeded.
	 */
	private boolean executeCommand(final String[] command) {
		try {
			final ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			final Process process = pb.start();

			// Consume output to prevent blocking
			try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				final StringBuilder output = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				if (log != null && !output.toString().trim().isEmpty()) {
					log.debug("Command output: " + output);
				}
			}

			// Wait for completion
			final boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				if (log != null) log.warn("Command timed out: " + String.join(" ", command));
				process.destroyForcibly();
				return false;
			}

			final int exitCode = process.exitValue();
			if (exitCode != 0 && log != null) {
				log.debug("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
			}

			return exitCode == 0;
		}
		catch (final IOException e) {
			if (log != null) log.error("Failed to execute command: " + String.join(" ", command), e);
			return false;
		}
		catch (final InterruptedException e) {
			if (log != null) log.error("Command interrupted: " + String.join(" ", command), e);
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
