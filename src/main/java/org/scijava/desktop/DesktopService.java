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

import org.scijava.desktop.links.LinkHandler;
import org.scijava.service.SciJavaService;

import java.util.Map;

/**
 * Service interface for an application's desktop-related concerns.
 *
 * @author Curtis Rueden
 */
public interface DesktopService extends SciJavaService {

	/**
	 * Adds to the set of supported file types, looking up each extension's MIME
	 * type from the built-in database. If an extension is not found in the
	 * database, it falls back to {@code mimePrefix/x-ext} (e.g. passing prefix
	 * {@code "image"} for unknown extension {@code "foo"} yields
	 * {@code "image/x-foo"}).
	 *
	 * @param mimePrefix The MIME type prefix (e.g. {@code "image"},
	 *                   {@code "application"}) used when an extension is absent
	 *                   from the database.
	 * @param extensions One or more file extensions to register (without leading
	 *                   dot, e.g. {@code "tiff"}, {@code "png"}).
	 */
	void addFileTypes(String mimePrefix, String... extensions);

	/**
	 * Adds to the set of supported file types using an explicit mapping.
	 *
	 * @param extToMimeType Map from file extension (without leading dot,
	 *                      e.g. {@code "png"}) to MIME type
	 *                      (e.g. {@code "image/png"}).
	 */
	void addFileTypes(Map<String, String> extToMimeType);

	/**
	 * Adds a single file type with an explicit MIME type.
	 *
	 * @param ext      File extension without leading dot (e.g. {@code "png"}).
	 * @param mimeType MIME type (e.g. {@code "image/png"}).
	 */
	void addFileType(String ext, String mimeType);

	/**
	 * Gets the map of supported file types.
	 */
	Map<String, String> getFileTypes();
}
