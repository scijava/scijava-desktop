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

package org.scijava.desktop.links;

import java.io.IOException;

/**
 * Interface for installing URI scheme handlers in the operating system.
 * <p>
 * Implementations provide OS-specific logic for registering custom URI schemes
 * so that clicking links with those schemes will launch the Java application.
 * </p>
 *
 * @author Curtis Rueden
 */
public interface SchemeInstaller {

	/**
	 * Checks if this installer is supported on the current platform.
	 *
	 * @return true if the installer can run on this OS
	 */
	boolean isSupported();

	/**
	 * Installs a URI scheme handler in the operating system.
	 *
	 * @param scheme The URI scheme to register (e.g., "myapp")
	 * @param executablePath The absolute path to the executable to launch
	 * @throws IOException if installation fails
	 */
	void install(String scheme, String executablePath) throws IOException;

	/**
	 * Checks if a URI scheme is already registered.
	 *
	 * @param scheme The URI scheme to check
	 * @return true if the scheme is already registered
	 */
	boolean isInstalled(String scheme);

	/**
	 * Gets the executable path registered for a given scheme.
	 *
	 * @param scheme The URI scheme to query
	 * @return The registered executable path, or null if not registered
	 */
	String getInstalledPath(String scheme);

	/**
	 * Uninstalls a URI scheme handler from the operating system.
	 *
	 * @param scheme The URI scheme to unregister
	 * @throws IOException if uninstallation fails
	 */
	void uninstall(String scheme) throws IOException;
}
