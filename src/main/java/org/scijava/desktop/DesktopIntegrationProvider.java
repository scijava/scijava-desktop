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

import java.io.IOException;

import org.scijava.desktop.links.SchemeInstaller;

/**
 * Marker interface for platform implementations that provide desktop
 * integration features.
 * <p>
 * Platforms implementing this interface can be queried for desktop integration
 * capabilities via {@link #getDesktopIntegration()}.
 * </p>
 *
 * @author Curtis Rueden
 */
public interface DesktopIntegrationProvider {

	boolean isWebLinksEnabled();

	boolean isWebLinksToggleable();

	/**
	 * Enables or disables URI scheme registration (e.g., {@code myapp://} links).
	 * <p>
	 * This operation only works if {@link #isWebLinksToggleable()}
	 * returns true. Otherwise, calling this method may throw
	 * {@link UnsupportedOperationException}.
	 * </p>
	 *
	 * @param enable whether to enable or disable web links
	 * @throws IOException if the operation fails
	 * @throws UnsupportedOperationException if not supported on this platform
	 */
	void setWebLinksEnabled(final boolean enable) throws IOException;

	boolean isDesktopIconPresent();

	boolean isDesktopIconToggleable();

	/**
	 * Installs or removes the desktop icon (application launcher, menu entry).
	 * <p>
	 * This operation only works if
	 * {@link #isDesktopIconToggleable()} returns true.
	 * Otherwise, calling this method may throw
	 * {@link UnsupportedOperationException}.
	 * </p>
	 *
	 * @param install whether to install or remove the desktop icon
	 * @throws IOException if the operation fails
	 * @throws UnsupportedOperationException if not supported on this platform
	 */
	void setDesktopIconPresent(final boolean install) throws IOException;

	boolean isFileExtensionsEnabled();

	boolean isFileExtensionsToggleable();

	/**
	 * Enables or disables file extension associations (e.g., {@code .tif}, {@code .png}).
	 * <p>
	 * This operation only works if {@link #isFileExtensionsToggleable()}
	 * returns true. Otherwise, calling this method may throw
	 * {@link UnsupportedOperationException}.
	 * </p>
	 * <p>
	 * When enabled, the application will be registered as a handler for all
	 * supported file extensions. The application appears in "Open With" menus,
	 * allowing users to choose it for specific file types.
	 * </p>
	 *
	 * @param enable whether to enable or disable file extension associations
	 * @throws IOException if the operation fails
	 * @throws UnsupportedOperationException if not supported on this platform
	 */
	void setFileExtensionsEnabled(final boolean enable) throws IOException;

	/**
	 * Creates a SchemeInstaller for this platform.
	 *
	 * @return a SchemeInstaller, or null if not supported on this platform
	 */
	SchemeInstaller getSchemeInstaller();
}
