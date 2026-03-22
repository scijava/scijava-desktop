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

/**
 * Default implementation of {@link DesktopService}.
 *
 * @author Curtis Rueden
 */
@Plugin(type = Service.class)
public class DefaultDesktopService extends AbstractService implements DesktopService {

	@Parameter(required = false)
	private LogService log;

	private final Map<String, String> fileTypes = new HashMap<>();

	/** Cached contents of {@code mime-types.txt}, keyed by extension (no leading dot). */
	private Map<String, String> mimeDB;

	@Override
	public void addFileType(String ext, String mimeType) {
		fileTypes.put(ext, mimeType);
	}

	@Override
	public void addFileTypes(String mimePrefix, String... extensions) {
		if (mimeDB == null) initMimeDB();
		for (final String ext : extensions) {
			final String mimeType = mimeDB.getOrDefault(ext, mimePrefix + "/x-" + ext);
			addFileType(ext, mimeType);
		}
	}

	@Override
	public void addFileTypes(Map<String, String> extToMimeType) {
		fileTypes.putAll(extToMimeType);
	}

	@Override
	public Map<String, String> getFileTypes() {
		return Collections.unmodifiableMap(fileTypes);
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
				final int tab = line.indexOf('\t');
				if (tab < 0) {
					if (log != null) log.warn("Invalid MIME types DB line: " + line);
					continue;
				}
				final String ext = line.substring(0, tab).trim();
				final String mime = line.substring(tab + 1).trim();
				db.putIfAbsent(ext, mime);
			}
		}
		catch (final IOException e) {
			if (log != null) log.error("Failed to load MIME types database", e);
		}
		mimeDB = db;
	}
}
