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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple parser and writer for Linux .desktop files.
 * <p>
 * Supports reading and writing key-value pairs within the [Desktop Entry] section.
 * This implementation is minimal and focused on URI scheme registration needs.
 * </p>
 *
 * @author Curtis Rueden
 */
public class DesktopFile {

	private final Map<String, String> entries;
	private final List<String> comments;

	public DesktopFile() {
		this.entries = new LinkedHashMap<>();
		this.comments = new ArrayList<>();
	}

	/**
	 * Parses a .desktop file from disk.
	 *
	 * @param path Path to the .desktop file
	 * @return Parsed DesktopFile
	 * @throws IOException if reading fails
	 */
	public static DesktopFile parse(final Path path) throws IOException {
		final DesktopFile df = new DesktopFile();
		boolean inDesktopEntry = false;

		try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				final String trimmed = line.trim();

				// Track section
				if (trimmed.equals("[Desktop Entry]")) {
					inDesktopEntry = true;
					continue;
				}
				if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
					inDesktopEntry = false;
					continue;
				}

				// Only process [Desktop Entry] section
				if (!inDesktopEntry) continue;

				// Skip empty lines and comments
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					df.comments.add(line);
					continue;
				}

				// Parse key=value
				final int equals = line.indexOf('=');
				if (equals > 0) {
					final String key = line.substring(0, equals).trim();
					final String value = line.substring(equals + 1);
					df.entries.put(key, value);
				}
			}
		}

		return df;
	}

	/**
	 * Writes the .desktop file to disk.
	 *
	 * @param path Path to write to
	 * @throws IOException if writing fails
	 */
	public void writeTo(final Path path) throws IOException {
		// Ensure parent directory exists
		final Path parent = path.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}

		try (final BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			writer.write("[Desktop Entry]");
			writer.newLine();

			// Write key-value pairs
			for (final Map.Entry<String, String> entry : entries.entrySet()) {
				writer.write(entry.getKey());
				writer.write('=');
				writer.write(entry.getValue());
				writer.newLine();
			}

			// Write comments at the end
			for (final String comment : comments) {
				writer.write(comment);
				writer.newLine();
			}
		}
	}

	/**
	 * Gets the value for a key.
	 *
	 * @param key The key
	 * @return The value, or null if not present
	 */
	public String get(final String key) {
		return entries.get(key);
	}

	/**
	 * Sets a key-value pair.
	 *
	 * @param key The key
	 * @param value The value
	 */
	public void set(final String key, final String value) {
		entries.put(key, value);
	}

	/**
	 * Checks if a MimeType entry contains a specific MIME type.
	 *
	 * @param mimeType The MIME type to check (e.g., "x-scheme-handler/fiji")
	 * @return true if the MimeType field contains this type
	 */
	public boolean hasMimeType(final String mimeType) {
		final String mimeTypes = entries.get("MimeType");
		if (mimeTypes == null || mimeTypes.isEmpty()) return false;

		final String[] types = mimeTypes.split(";");
		for (final String type : types) {
			if (type.trim().equals(mimeType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds a MIME type to the MimeType field.
	 * <p>
	 * The MimeType field is a semicolon-separated list. This method appends
	 * the new type if it's not already present.
	 * </p>
	 *
	 * @param mimeType The MIME type to add (e.g., "x-scheme-handler/fiji")
	 */
	public void addMimeType(final String mimeType) {
		if (hasMimeType(mimeType)) return; // Already present

		String mimeTypes = entries.get("MimeType");
		if (mimeTypes == null || mimeTypes.isEmpty()) {
			// Create new MimeType field
			entries.put("MimeType", mimeType + ";");
		}
		else {
			// Append to existing
			if (!mimeTypes.endsWith(";")) {
				mimeTypes += ";";
			}
			entries.put("MimeType", mimeTypes + mimeType + ";");
		}
	}

	/**
	 * Removes a MIME type from the MimeType field.
	 *
	 * @param mimeType The MIME type to remove
	 */
	public void removeMimeType(final String mimeType) {
		final String mimeTypes = entries.get("MimeType");
		if (mimeTypes == null || mimeTypes.isEmpty()) return;

		final List<String> types = new ArrayList<>();
		for (final String type : mimeTypes.split(";")) {
			final String trimmed = type.trim();
			if (!trimmed.isEmpty() && !trimmed.equals(mimeType)) {
				types.add(trimmed);
			}
		}

		if (types.isEmpty()) {
			entries.remove("MimeType");
		}
		else {
			entries.put("MimeType", String.join(";", types) + ";");
		}
	}
}
