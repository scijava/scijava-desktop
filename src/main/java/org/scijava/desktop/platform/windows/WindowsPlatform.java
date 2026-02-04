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

package org.scijava.desktop.platform.windows;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.scijava.desktop.DesktopIntegrationProvider;
import org.scijava.desktop.links.LinkHandler;
import org.scijava.desktop.links.LinkService;
import org.scijava.desktop.links.SchemeInstaller;
import org.scijava.log.LogService;
import org.scijava.platform.AbstractPlatform;
import org.scijava.platform.Platform;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A platform implementation for handling Windows platform issues.
 *
 * @author Johannes Schindelin
 */
@Plugin(type = Platform.class, name = "Windows")
public class WindowsPlatform extends AbstractPlatform
	implements DesktopIntegrationProvider
{

	@Parameter(required = false)
	private LogService log;

	@Parameter(required = false)
	private LinkService linkService;

	// -- Platform methods --

	@Override
	public String osName() {
		return "Windows";
	}

	@Override
	public void open(final URL url) throws IOException {
		final String cmd;
		final String arg;
		// NB: the cmd and arg separate is necessary for Windows OS
		// to open the default browser correctly.
		if (System.getProperty("os.name").startsWith("Windows 2000")) {
			cmd = "rundll32";
			arg = "shell32.dll,ShellExec_RunDLL";
		}
		else {
			cmd = "rundll32";
			arg = "url.dll,FileProtocolHandler";
		}
		if (getPlatformService().exec(cmd, arg, url.toString()) != 0) {
			throw new IOException("Could not open " + url);
		}
	}

	// -- DesktopIntegrationProvider methods --

	@Override
	public boolean isWebLinksEnabled() {
		final WindowsSchemeInstaller installer = new WindowsSchemeInstaller(log);
		final Set<String> schemes = collectSchemes();
		if (schemes.isEmpty()) return false;

		// Check if any scheme is installed
		for (final String scheme : schemes) {
			if (installer.isInstalled(scheme)) return true;
		}
		return false;
	}

	@Override
	public boolean isWebLinksToggleable() { return true; }

	@Override
	public void setWebLinksEnabled(final boolean enable) throws IOException {
		final WindowsSchemeInstaller installer = new WindowsSchemeInstaller(log);
		final String executablePath = System.getProperty("scijava.app.executable");

		if (executablePath == null) {
			throw new IOException("No executable path set (scijava.app.executable property)");
		}

		final Set<String> schemes = collectSchemes();
		for (final String scheme : schemes) {
			try {
				if (enable) {
					installer.install(scheme, executablePath);
				}
				else {
					installer.uninstall(scheme);
				}
			}
			catch (final Exception e) {
				if (log != null) {
					log.error("Failed to " + (enable ? "install" : "uninstall") +
						" URI scheme: " + scheme, e);
				}
			}
		}
	}

	@Override
	public boolean isDesktopIconPresent() { return false; }

	@Override
	public boolean isDesktopIconToggleable() { return false; }

	@Override
	public void setDesktopIconPresent(final boolean install) {
		// Note: Operation has no effect here.
		// Desktop icon installation is not supported on Windows (add to Start menu manually).
	}

	@Override
	public SchemeInstaller getSchemeInstaller() {
		return new WindowsSchemeInstaller(log);
	}

	// -- Helper methods --

	/**
	 * Collects all URI schemes from registered LinkHandler plugins.
	 */
	private Set<String> collectSchemes() {
		final Set<String> schemes = new HashSet<>();
		if (linkService == null) return schemes;

		for (final LinkHandler handler : linkService.getInstances()) {
			final List<String> handlerSchemes = handler.getSchemes();
			if (handlerSchemes != null) {
				schemes.addAll(handlerSchemes);
			}
		}
		return schemes;
	}
}
