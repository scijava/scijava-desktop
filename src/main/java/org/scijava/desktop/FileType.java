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

package org.scijava.desktop;

/**
 * Struct storing metadata about a particular file type.
 *
 * @author Curtis Rueden
 */
public class FileType {

	/** File extension without leading dot (e.g. {@code "png"}). */
	public final String extension;

	/**
	 * MIME type (e.g. {@code "image/png"}), or a wildcard of
	 * the form {@code "category/*"} (e.g. {@code "image/*"}) if
	 * the specific type is unknown. Wildcard values are resolved
	 * against the bundled MIME database by extension; if still
	 * unresolved, the sentinel is preserved for platform-specific
	 * code to handle at OS registration time.
	 */
	public final String mimeType;

	/**
	 * Human-readable description of the file type
	 * (e.g. {@code "Gatan Digital Micrograph image"}), used
	 * as the label when registering a custom MIME type, or
	 * {@code null} to synthesize one from the extension.
	 */
	public final String description;

	public FileType(String extension, String mimeType) {
		this(extension, mimeType, null);
	}

	public FileType(String extension, String mimeType, String description) {
		this.extension = extension;
		this.mimeType = mimeType;
		this.description = description;
	}
}
