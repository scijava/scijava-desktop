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
import java.util.Collections;
import java.util.Set;

import org.scijava.Context;
import org.scijava.desktop.DesktopIntegrationProvider;
import org.scijava.desktop.DesktopService;
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
 * @author Curtis Rueden
 */
@Plugin(type = Platform.class, name = "Windows")
public class WindowsPlatform extends AbstractPlatform
	implements DesktopIntegrationProvider
{

	@Parameter
	private Context context;

	@Parameter(required = false)
	private LogService log;

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
		final Set<String> schemes = schemes();
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

		final Set<String> schemes = schemes();
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
	public boolean isFileExtensionsEnabled() {
		// Check if any extensions are registered
		final Set<String> extensions = extensions();
		if (extensions.isEmpty()) return false;

		// For simplicity, check if the Applications key exists
		// A more thorough check would verify each extension
		try {
			final String executableName = getExecutableName();
			if (executableName == null) return false;

			final ProcessBuilder pb = new ProcessBuilder(
				"reg", "query",
				"HKCU\\Software\\Classes\\Applications\\" + executableName,
				"/v", "FriendlyAppName"
			);
			final Process process = pb.start();
			final int exitCode = process.waitFor();
			return exitCode == 0;
		}
		catch (final Exception e) {
			if (log != null) {
				log.debug("Failed to check file extensions status", e);
			}
			return false;
		}
	}

	@Override
	public boolean isFileExtensionsToggleable() {
		return true;
	}

	@Override
	public void setFileExtensionsEnabled(final boolean enable) throws IOException {
		final String executablePath = System.getProperty("scijava.app.executable");
		if (executablePath == null) {
			throw new IOException("No executable path set (scijava.app.executable property)");
		}

		final String executableName = getExecutableName();
		if (executableName == null) {
			throw new IOException("Could not determine executable name");
		}

		final Set<String> extensions = extensions();
		if (extensions.isEmpty()) {
			if (log != null) log.warn("No file extensions to register");
			return;
		}

		if (enable) {
			// Register using Applications\SupportedTypes approach
			try {
				// Create Applications key
				execRegistryCommand("add",
					"HKCU\\Software\\Classes\\Applications\\" + executableName,
					"/f");

				// Set friendly name
				final String appName = System.getProperty("scijava.app.name", "SciJava Application");
				execRegistryCommand("add",
					"HKCU\\Software\\Classes\\Applications\\" + executableName,
					"/v", "FriendlyAppName",
					"/d", appName,
					"/f");

				// Add each extension to SupportedTypes
				for (final String ext : extensions) {
					execRegistryCommand("add",
						"HKCU\\Software\\Classes\\Applications\\" + executableName + "\\SupportedTypes",
						"/v", "." + ext,
						"/d", "",
						"/f");
				}

				if (log != null) {
					log.info("Registered " + extensions.size() + " file extensions for " + appName);
				}
			}
			catch (final Exception e) {
				throw new IOException("Failed to register file extensions", e);
			}
		}
		else {
			// Unregister by deleting the Applications key
			try {
				execRegistryCommand("delete",
					"HKCU\\Software\\Classes\\Applications\\" + executableName,
					"/f");

				if (log != null) {
					log.info("Unregistered file extensions");
				}
			}
			catch (final Exception e) {
				throw new IOException("Failed to unregister file extensions", e);
			}
		}
	}

	@Override
	public SchemeInstaller getSchemeInstaller() {
		return new WindowsSchemeInstaller(log);
	}

	// -- Helper methods --

	private LinkService linkService() {
		// NB: We cannot declare LinkService as an @Parameter because
		// the PlatformService creates its plugin singletons before the
		// LinkService has been instantiated and added to the context.
		return context.getService(LinkService.class);
	}

	private Set<String> schemes() {
		final LinkService linkService = linkService();
		return linkService == null ?
			Collections.emptySet() : linkService.getSchemes();
	}

	private DesktopService desktopService() {
		return context.getService(DesktopService.class);
	}

	private Set<String> extensions() {
		final DesktopService desktopService = desktopService();
		return desktopService == null ?
			Collections.emptySet() : desktopService.getFileTypes().keySet();
	}

	/**
	 * Extracts the executable file name from the full path.
	 * For example, "C:\Path\To\myapp.exe" returns "myapp.exe".
	 */
	private String getExecutableName() {
		final String executablePath = System.getProperty("scijava.app.executable");
		if (executablePath == null) return null;

		final int lastSlash = Math.max(
			executablePath.lastIndexOf('/'),
			executablePath.lastIndexOf('\\')
		);
		return lastSlash >= 0 ? executablePath.substring(lastSlash + 1) : executablePath;
	}

	/**
	 * Executes a registry command.
	 */
	private void execRegistryCommand(final String... args) throws IOException, InterruptedException {
		final ProcessBuilder pb = new ProcessBuilder(args);
		pb.command().add(0, "reg");
		final Process process = pb.start();
		final int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IOException("Registry command failed with exit code " + exitCode);
		}
	}
}
