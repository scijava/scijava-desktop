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
package org.scijava.desktop;

import org.scijava.service.SciJavaService;

import java.util.List;
import java.util.Map;

/**
 * Service interface for an application's desktop-related concerns.
 *
 * @author Curtis Rueden
 */
public interface DesktopService extends SciJavaService {

	/**
	 * Applies desktop integration settings.
	 * <p>
	 * The behavior of each setting is platform-specific;
	 * not all settings have an effect on every platform.
	 * </p>
	 *
	 * @param webLinks    whether URI scheme handlers should be registered
	 * @param desktopIcon whether the application launcher entry should be present
	 * @param fileTypes   whether file-extension associations should be registered
	 * @throws IOException if any part of the update fails
	 */
	void syncDesktopIntegration(boolean webLinks,
		boolean desktopIcon, boolean fileTypes);

	/** TODO javadoc */
	boolean isDesktopIconToggleable();

	/** TODO javadoc */
	boolean isDesktopIconPresent();

	/** TODO javadoc */
	boolean isWebLinksToggleable();

	/** TODO javadoc */
	boolean isWebLinksEnabled();

	/** TODO javadoc */
	boolean isFileExtensionsToggleable();

	/** TODO javadoc */
	boolean isFileExtensionsEnabled();

	/**
	 * Adds a single file type.
	 *
	 * @param ext      File extension without leading dot (e.g. {@code "png"}).
	 * @param mimeType MIME type (e.g. {@code "image/png"}), or a wildcard of
	 *                 the form {@code "category/*"} (e.g. {@code "image/*"}) if
	 *                 the specific type is unknown. Wildcard values are resolved
	 *                 against the bundled MIME database by extension; if still
	 *                 unresolved, the sentinel is preserved for platform-specific
	 *                 code to handle at OS registration time.
	 */
	default void addFileType(String ext, String mimeType) {
		addFileType(ext, mimeType, null);
	}

	/**
	 * Adds a single file type.
	 *
	 * @param ext      File extension without leading dot (e.g. {@code "png"}).
	 * @param mimeType MIME type (e.g. {@code "image/png"}), or a wildcard of
	 *                 the form {@code "category/*"} (e.g. {@code "image/*"}) if
	 *                 the specific type is unknown. Wildcard values are resolved
	 *                 against the bundled MIME database by extension; if still
	 *                 unresolved, the sentinel is preserved for platform-specific
	 *                 code to handle at OS registration time.
	 * @param description Human-readable description of the file type
	 *                    (e.g. {@code "Gatan Digital Micrograph image"}), used
	 *                    as the label when registering a custom MIME type, or
	 *                    {@code null} to synthesize one from the extension.
	 */
	void addFileType(String ext, String mimeType, String description);

	/**
	 * Adds a batch of file types sharing the same MIME type or wildcard.
	 * Equivalent to calling {@link #addFileType(String, String)} for each
	 * extension.
	 *
	 * @param extensions File extensions without leading dot (e.g. {@code "tiff"},
	 *                   {@code "tif"}).
	 * @param mimeType   MIME type or wildcard; see {@link #addFileType(String, String)}.
	 */
	default void addFileTypes(List<String> extensions, String mimeType) {
		addFileTypes(extensions, mimeType, null);
	}

	/**
	 * Adds a batch of file types sharing the same MIME type or wildcard.
	 * Equivalent to calling {@link #addFileType(String, String, String)} for
	 * each extension.
	 *
	 * @param extensions  File extensions without leading dot (e.g. {@code "tiff"},
	 *                    {@code "tif"}).
	 * @param mimeType    MIME type or wildcard; see {@link #addFileType(String, String, String)}.
	 * @param description Shared description for all extensions in this batch;
	 *                    see {@link #addFileType(String, String, String)}.
	 */
	default void addFileTypes(final List<String> extensions,
		final String mimeType, final String description)
	{
		for (final String ext : extensions) {
			addFileType(ext, mimeType, description);
		}
	}

	/**
	 * Gets the map of supported file types (extension → MIME type).
	 * <p>
	 * Values ending in {@code "/*"} (e.g. {@code "image/*"}) are unresolved
	 * sentinels, meaning the specific MIME type is not yet known. Callers that
	 * write OS registrations should resolve these against the system MIME
	 * database and synthesize a concrete type (e.g. {@code "image/x-dm3"}) if
	 * still unresolved.
	 * </p>
	 */
	Map<String, String> getFileTypes();

	/**
	 * Gets the description for a given file type extension, or null if none.
	 */
	String getDescription(String extension);
}
