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

import org.scijava.event.ContextCreatedEvent;
import org.scijava.event.EventHandler;
import org.scijava.desktop.links.SchemeInstaller;
import org.scijava.desktop.platform.windows.WindowsSchemeInstaller;
import org.scijava.log.LogService;
import org.scijava.plugin.AbstractHandlerService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.Service;

import java.awt.Desktop;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link LinkService}.
 *
 * @author Curtis Rueden
 */
@Plugin(type = Service.class)
public class DefaultLinkService extends AbstractHandlerService<URI, LinkHandler> implements LinkService {

	@Parameter(required = false)
	private LogService log;

	@EventHandler
	private void onEvent(final ContextCreatedEvent evt) {
		// Register URI handler with the desktop system, if possible.
		if (Desktop.isDesktopSupported()) {
			final Desktop desktop = Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
				desktop.setOpenURIHandler(event -> handle(event.getURI()));
			}
		}

		// Register URI schemes with the operating system (Windows only).
		installSchemes();
	}

	/**
	 * Installs URI schemes with the operating system.
	 * <p>
	 * This method collects all schemes supported by registered {@link LinkHandler}
	 * plugins and registers them with the OS (currently Windows only).
	 * </p>
	 */
	private void installSchemes() {
		// Create the appropriate installer for this platform
		final SchemeInstaller installer = createInstaller();
		if (installer == null || !installer.isSupported()) {
			if (log != null) log.debug("Scheme installation not supported on this platform");
			return;
		}

		// Get executable path from system property
		final String executablePath = System.getProperty("scijava.app.executable");
		if (executablePath == null) {
			if (log != null) log.debug("No executable path set (scijava.app.executable property)");
			return;
		}

		// Collect all schemes from registered handlers
		final Set<String> schemes = collectSchemes();
		if (schemes.isEmpty()) {
			if (log != null) log.debug("No URI schemes to register");
			return;
		}

		// Install each scheme
		for (final String scheme : schemes) {
			try {
				installer.install(scheme, executablePath);
			}
			catch (final Exception e) {
				if (log != null) log.error("Failed to install URI scheme: " + scheme, e);
			}
		}
	}

	/**
	 * Creates the appropriate {@link SchemeInstaller} for the current platform.
	 * <p>
	 * Currently only Windows is supported. macOS uses Info.plist in the .app bundle
	 * (configured at build time), and Linux .desktop file management belongs in
	 * scijava-plugins-platforms.
	 * </p>
	 */
	private SchemeInstaller createInstaller() {
		return new WindowsSchemeInstaller(log);
	}

	/**
	 * Collects all URI schemes from registered {@link LinkHandler} plugins.
	 */
	private Set<String> collectSchemes() {
		final Set<String> schemes = new HashSet<>();
		for (final LinkHandler handler : getInstances()) {
			schemes.addAll(handler.getSchemes());
		}
		return schemes;
	}

}
