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
 * Parser and writer for Linux .desktop files.
 * <p>
 * Instance-based API for reading and writing .desktop files. Supports all
 * standard [Desktop Entry] section fields plus custom keys. This class
 * provides a convenient way to manage desktop files for both application
 * launching and URI scheme registration.
 * </p>
 * <p>
 * Note: This class will eventually move to {@code org.scijava.util.DesktopFile}
 * in scijava-common once the design is finalized.
 * </p>
 *
 * @author Curtis Rueden
 */
public class DesktopFile {

	private final Path path;
	private final Map<String, String> entries;
	private final List<String> comments;

	/**
	 * Creates a DesktopFile instance for the given path.
	 * <p>
	 * If the file exists, it will be loaded when {@link #load()} is called.
	 * If it doesn't exist, the entries map will be empty until populated
	 * programmatically or {@link #load()} is called.
	 * </p>
	 *
	 * @param path the path to the .desktop file
	 */
	public DesktopFile(final Path path) {
		this.path = path;
		this.entries = new LinkedHashMap<>();
		this.comments = new ArrayList<>();
	}

	/**
	 * Gets the file path for this desktop file.
	 *
	 * @return the path
	 */
	public Path path() {
		return path;
	}

	/**
	 * Checks if the file exists on disk.
	 *
	 * @return true if the file exists
	 */
	public boolean exists() {
		return Files.exists(path);
	}

	/**
	 * Loads the .desktop file from disk.
	 * <p>
	 * Clears any existing entries and comments, then reads from the file.
	 * If the file doesn't exist, entries will be empty after this call.
	 * </p>
	 *
	 * @throws IOException if reading fails
	 */
	public void load() throws IOException {
		entries.clear();
		comments.clear();

		if (!exists()) {
			return;
		}

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
					comments.add(line);
					continue;
				}

				// Parse key=value
				final int equals = line.indexOf('=');
				if (equals > 0) {
					final String key = line.substring(0, equals).trim();
					final String value = line.substring(equals + 1);
					entries.put(key, value);
				}
			}
		}
	}

	/**
	 * Saves the .desktop file to disk.
	 * <p>
	 * Creates parent directories if needed. Overwrites any existing file.
	 * </p>
	 *
	 * @throws IOException if writing fails
	 */
	public void save() throws IOException {
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
	 * Deletes the file from disk.
	 *
	 * @throws IOException if deletion fails
	 */
	public void delete() throws IOException {
		Files.deleteIfExists(path);
	}

	/**
	 * Parses a .desktop file from disk (static convenience method).
	 *
	 * @param path Path to the .desktop file
	 * @return Parsed DesktopFile
	 * @throws IOException if reading fails
	 */
	public static DesktopFile parse(final Path path) throws IOException {
		final DesktopFile df = new DesktopFile(path);
		df.load();
		return df;
	}

	/**
	 * Writes the .desktop file to disk (for backward compatibility).
	 *
	 * @param path Path to write to
	 * @throws IOException if writing fails
	 */
	public void writeTo(final Path path) throws IOException {
		final Path oldPath = this.path;
		// TODO: oldPath is not used; and why is this method "for backward compatibility"? Do we actually need it?
		// Temporarily change path, save, then restore
		try {
			// Create a temporary instance with the new path
			final DesktopFile temp = new DesktopFile(path);
			temp.entries.putAll(this.entries);
			temp.comments.addAll(this.comments);
			temp.save();
		} catch (final Exception e) {
			throw new IOException("Failed to write to " + path, e);
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
		if (value == null) {
			entries.remove(key);
		} else {
			entries.put(key, value);
		}
	}

	// -- Standard field accessors --

	public String getType() {
		return get("Type");
	}

	public void setType(final String type) {
		set("Type", type);
	}

	public String getVersion() {
		return get("Version");
	}

	public void setVersion(final String version) {
		set("Version", version);
	}

	public String getName() {
		return get("Name");
	}

	public void setName(final String name) {
		set("Name", name);
	}

	public String getGenericName() {
		return get("GenericName");
	}

	public void setGenericName(final String genericName) {
		set("GenericName", genericName);
	}

	public String getComment() {
		return get("Comment");
	}

	public void setComment(final String comment) {
		set("Comment", comment);
	}

	public String getExec() {
		return get("Exec");
	}

	public void setExec(final String exec) {
		set("Exec", exec);
	}

	public String getIcon() {
		return get("Icon");
	}

	public void setIcon(final String icon) {
		set("Icon", icon);
	}

	public String getPath() {
		return get("Path");
	}

	public void setPath(final String path) {
		set("Path", path);
	}

	public boolean getTerminal() {
		final String value = get("Terminal");
		return "true".equalsIgnoreCase(value);
	}

	public void setTerminal(final boolean terminal) {
		set("Terminal", terminal ? "true" : "false");
	}

	public String getCategories() {
		return get("Categories");
	}

	public void setCategories(final String categories) {
		set("Categories", categories);
	}

	// -- MimeType handling --

	/**
	 * Checks if a MimeType entry contains a specific MIME type.
	 *
	 * @param mimeType The MIME type to check (e.g., "x-scheme-handler/myapp")
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
	 * @param mimeType The MIME type to add (e.g., "x-scheme-handler/myapp")
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
