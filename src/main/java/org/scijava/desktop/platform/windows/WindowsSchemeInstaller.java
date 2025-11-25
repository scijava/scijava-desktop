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
package org.scijava.desktop.platform.windows;

import org.scijava.desktop.links.SchemeInstaller;
import org.scijava.log.LogService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Windows implementation of {@link SchemeInstaller} using the Windows Registry.
 * <p>
 * This implementation uses the {@code reg} command-line tool to manipulate
 * the Windows Registry under {@code HKEY_CURRENT_USER\Software\Classes}.
 * No administrator privileges are required.
 * </p>
 *
 * @author Curtis Rueden
 * @author Marwan Zouinkhi
 */
public class WindowsSchemeInstaller implements SchemeInstaller {

	private static final long COMMAND_TIMEOUT_SECONDS = 10;

	private final LogService log;

	public WindowsSchemeInstaller(final LogService log) {
		this.log = log;
	}

	@Override
	public boolean isSupported() {
		final String os = System.getProperty("os.name");
		return os != null && os.toLowerCase().contains("win");
	}

	@Override
	public void install(final String scheme, final String executablePath) throws IOException {
		if (!isSupported()) {
			throw new UnsupportedOperationException("Windows registry installation not supported on: " + System.getProperty("os.name"));
		}

		// Validate inputs
		if (scheme == null || scheme.isEmpty()) {
			throw new IllegalArgumentException("Scheme cannot be null or empty");
		}
		if (executablePath == null || executablePath.isEmpty()) {
			throw new IllegalArgumentException("Executable path cannot be null or empty");
		}

		// Check if already installed with same path
		if (isInstalled(scheme)) {
			final String existingPath = getInstalledPath(scheme);
			if (executablePath.equals(existingPath)) {
				if (log != null) log.debug("Scheme '" + scheme + "' already registered to: " + existingPath);
				return;
			}
		}

		// Registry key paths (HKCU = HKEY_CURRENT_USER, no admin rights needed)
		final String keyPath = "HKCU\\Software\\Classes\\" + scheme;
		final String shellPath = keyPath + "\\shell";
		final String openPath = shellPath + "\\open";
		final String commandPath = openPath + "\\command";

		// Commands to register the URI scheme
		final String[][] commands = {
			{"reg", "add", keyPath, "/f"},
			{"reg", "add", keyPath, "/ve", "/d", "URL:" + scheme, "/f"},
			{"reg", "add", keyPath, "/v", "URL Protocol", "/f"},
			{"reg", "add", shellPath, "/f"},
			{"reg", "add", openPath, "/f"},
			{"reg", "add", commandPath, "/ve", "/d", "\"" + executablePath + "\" \"%1\"", "/f"}
		};

		// Execute commands
		for (final String[] command : commands) {
			if (!executeCommand(command)) {
				throw new IOException("Failed to execute registry command: " + String.join(" ", command));
			}
		}

		if (log != null) log.info("Registered URI scheme '" + scheme + "' to: " + executablePath);
	}

	@Override
	public boolean isInstalled(final String scheme) {
		if (!isSupported()) return false;

		final String keyPath = "HKCU\\Software\\Classes\\" + scheme;
		return executeCommand(new String[]{"reg", "query", keyPath});
	}

	@Override
	public String getInstalledPath(final String scheme) {
		if (!isInstalled(scheme)) return null;

		final String commandPath = "HKCU\\Software\\Classes\\" + scheme + "\\shell\\open\\command";
		try {
			final ProcessBuilder pb = new ProcessBuilder("reg", "query", commandPath, "/ve");
			final Process process = pb.start();

			// Read output
			final StringBuilder output = new StringBuilder();
			try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
			}

			// Wait for completion
			final boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return null;
			}

			if (process.exitValue() != 0) {
				return null;
			}

			// Parse output to extract path
			// Format: "    (Default)    REG_SZ    \"C:\path\to\app.exe\" \"%1\""
			return parsePathFromRegQueryOutput(output.toString());
		}
		catch (final IOException | InterruptedException e) {
			if (log != null) log.debug("Failed to query registry for scheme: " + scheme, e);
			return null;
		}
	}

	@Override
	public void uninstall(final String scheme) throws IOException {
		if (!isSupported()) {
			throw new UnsupportedOperationException("Windows registry uninstallation not supported on: " + System.getProperty("os.name"));
		}

		if (!isInstalled(scheme)) {
			if (log != null) log.debug("Scheme '" + scheme + "' is not installed");
			return;
		}

		final String keyPath = "HKCU\\Software\\Classes\\" + scheme;
		final String[] command = {"reg", "delete", keyPath, "/f"};

		if (!executeCommand(command)) {
			throw new IOException("Failed to uninstall scheme: " + scheme);
		}

		if (log != null) log.info("Uninstalled URI scheme: " + scheme);
	}

	// -- Helper methods --

	/**
	 * Executes a Windows command and returns whether it succeeded.
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

	/**
	 * Parses the executable path from {@code reg query} output.
	 * Expected format: "    (Default)    REG_SZ    \"C:\path\to\app.exe\" \"%1\""
	 */
	private String parsePathFromRegQueryOutput(final String output) {
		// Find the line containing REG_SZ or REG_EXPAND_SZ
		for (final String line : output.split("\n")) {
			if (line.contains("REG_SZ") || line.contains("REG_EXPAND_SZ")) {
				// Extract the value after REG_SZ
				final int regSzIndex = line.indexOf("REG_SZ");
				final int regExpandSzIndex = line.indexOf("REG_EXPAND_SZ");
				final int startIndex = Math.max(regSzIndex, regExpandSzIndex) + (regSzIndex > 0 ? 6 : 13);

				if (startIndex < line.length()) {
					String value = line.substring(startIndex).trim();
					// Remove "%1" parameter if present
					if (value.endsWith(" \"%1\"")) {
						value = value.substring(0, value.length() - 5).trim();
					} else if (value.endsWith(" %1")) {
						value = value.substring(0, value.length() - 3).trim();
					}
					// Remove surrounding quotes if present
					if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
						value = value.substring(1, value.length() - 1);
					}
					return value;
				}
			}
		}
		return null;
	}
}
