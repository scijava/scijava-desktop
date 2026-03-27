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

import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Default implementation of {@link DesktopService}.
 *
 * @author Curtis Rueden
 */
@Plugin(type = Service.class)
public class DefaultDesktopService extends AbstractService implements DesktopService {

	@Parameter
	private PlatformService platformService;

	@Parameter(required = false)
	private LogService log;

	private final Map<String, String> fileTypes = new HashMap<>();

	/**
	 * Description of each file type extension, as registered by
	 * {@link #addFileType} and {@link #addFileTypes}.
	 */
	private final Map<String, String> descriptions = new HashMap<>();

	/** Cached contents of {@code mime-types.txt}, keyed by extension (no leading dot). */
	private Map<String, String> mimeDB;

	@Override
	public void syncDesktopIntegration(final boolean webLinks,
		final boolean desktopIcon, final boolean fileTypes)
	{
		desktopPlatforms().forEach(p -> {
			// Resolve each feature's desired state: use the passed value
			// if toggleable, otherwise preserve the current platform state.
			final boolean webLinks2 = p.isWebLinksToggleable()
				? webLinks : p.isWebLinksEnabled();
			final boolean desktopIcon2 = p.isDesktopIconToggleable()
				? desktopIcon : p.isDesktopIconPresent();
			final boolean fileTypes2 = p.isFileExtensionsToggleable()
				? fileTypes : p.isFileExtensionsEnabled();
			try {
				p.syncDesktopIntegration(webLinks2, desktopIcon2, fileTypes2);
			}
			catch (final IOException e) {
				if (log != null) log.error("Error performing desktop integration", e);
			}
		});
	}

	@Override
	public boolean isDesktopIconToggleable() {
		return desktopPlatforms().anyMatch(p -> p.isDesktopIconToggleable());
	}

	@Override
	public boolean isDesktopIconPresent() {
		return desktopPlatforms().allMatch(p -> p.isDesktopIconPresent());
	}

	@Override
	public boolean isWebLinksToggleable() {
		return desktopPlatforms().anyMatch(p -> p.isWebLinksToggleable());
	}

	@Override
	public boolean isWebLinksEnabled() {
		return desktopPlatforms().allMatch(p -> p.isWebLinksEnabled());
	}

	@Override
	public boolean isFileExtensionsToggleable() {
		return desktopPlatforms().anyMatch(p -> p.isFileExtensionsToggleable());
	}

	@Override
	public boolean isFileExtensionsEnabled() {
		return desktopPlatforms().allMatch(p -> p.isFileExtensionsEnabled());
	}

	@Override
	public void addFileType(final String ext,
		final String mimeType, final String description)
	{
		if (mimeDB == null) initMimeDB();

		// Resolve the MIME type as needed and if possible.
		final String resolvedMimeType;
		if (mimeType == null || mimeType.isEmpty()) {
			// No MIME type was given -- try to resolve it from the file extension.
			// If not found, mark it with a wildcard sentinel for resolution
			// elsewhere, using a default MIME prefix of 'application'.
			resolvedMimeType = mimeDB.getOrDefault(ext, "application/*");
		}
		else if (mimeType.endsWith("/*")) {
			// A wildcard MIME type was given -- try to resolve it from the file extension.
			// If not found, leave the wildcard sentinel as is for resolution elsewhere.
			resolvedMimeType = mimeDB.getOrDefault(ext, mimeType);
		}
		else {
			// Assume an explicit MIME type was given -- use it verbatim.
			resolvedMimeType = mimeType;
		}

		// Save the file extension -> MIME type association.
		fileTypes.put(ext, resolvedMimeType);
		if (log != null) {
			log.debug("Registered file extension '" + ext +
				"' as MIME type '" + resolvedMimeType + "'");
		}

		// Save the file extension -> description association.
		if (description == null) return; // No description to register.
		if (descriptions.containsKey(ext)) {
			if (log != null) {
				log.debug("Ignoring description '" + description +
					"' for file extension '" + ext +
					"' with existing description '" + descriptions.get(ext) + "'");
			}
		}
		else {
			descriptions.put(ext, description);
			if (log != null) {
				log.debug("Registered description '" + description +
					"' for file extension '" + ext + "'");
			}
		}
	}

	@Override
	public Map<String, String> getFileTypes() {
		return Collections.unmodifiableMap(fileTypes);
	}

	@Override
	public String getDescription(final String extension) {
		return descriptions.get(extension);
	}

	// -- Helper methods - lazy initialization --

	/** Initializes {@link #mimeDB} from the built-in {@code mime-types.txt} resource. */
	private synchronized void initMimeDB() {
		if (mimeDB != null) return; // already initialized

		final Map<String, String> db = new HashMap<>();
		final String resource = "mime-types.txt";
		try (final InputStream is = getClass().getResourceAsStream(resource);
		     final BufferedReader reader = new BufferedReader(
		         new InputStreamReader(is, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("#") || line.isBlank()) continue;
				final int pipe1 = line.indexOf('|');
				if (pipe1 < 0) {
					if (log != null) log.warn("Invalid MIME types DB line: " + line);
					continue;
				}
				final String ext = line.substring(0, pipe1).trim();
				final String rest = line.substring(pipe1 + 1).trim();
				final int pipe2 = rest.indexOf('|');
				final String mime;
				final String description;
				if (pipe2 < 0) {
					mime = rest;
					description = null;
				}
				else {
					mime = rest.substring(0, pipe2).trim();
					description = rest.substring(pipe2 + 1).trim();
				}
				db.putIfAbsent(ext, mime);
				if (description != null && !description.isEmpty()) {
					descriptions.put(ext, description);
				}
			}
		}
		catch (final IOException e) {
			if (log != null) log.error("Failed to load MIME types database", e);
		}
		mimeDB = db;
	}

	// -- Helper methods --

	private Stream<DesktopIntegrationProvider> desktopPlatforms() {
		return platformService.getTargetPlatforms().stream() //
			.filter(p -> p instanceof DesktopIntegrationProvider) //
			.map(p -> (DesktopIntegrationProvider) p);
	}
}
