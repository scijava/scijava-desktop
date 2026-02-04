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

package org.scijava.desktop.platform.linux;

import org.scijava.app.AppService;
import org.scijava.desktop.DesktopIntegrationProvider;
import org.scijava.desktop.links.LinkService;
import org.scijava.log.LogService;
import org.scijava.platform.AbstractPlatform;
import org.scijava.platform.Platform;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A platform implementation for handling Linux platform issues.
 * <p>
 * This implementation creates and maintains a .desktop file for the application,
 * enabling proper desktop integration including:
 * </p>
 * <ul>
 * <li>Application launcher in menus</li>
 * <li>Application icon</li>
 * <li>File associations</li>
 * <li>URI scheme handling</li>
 * </ul>
 *
 * @author Curtis Rueden
 */
@Plugin(type = Platform.class, name = "Linux")
public class LinuxPlatform extends AbstractPlatform
	implements DesktopIntegrationProvider
{

	@Parameter
	private LinkService linkService;

	@Parameter
	private AppService appService;

	@Parameter(required = false)
	private LogService log;

	// -- Platform methods --

	@Override
	public String osName() {
		return "Linux";
	}

	@Override
	public void configure(final PlatformService service) {
		super.configure(service);

		// Create or update .desktop file for desktop integration
		try {
			installDesktopFile();
		}
		catch (final IOException e) {
			if (log != null) {
				log.error("Failed to install .desktop file", e);
			}
		}
	}

	@Override
	public void open(final URL url) throws IOException {
		if (getPlatformService().exec("xdg-open", url.toString()) != 0) {
			throw new IOException("Could not open " + url);
		}
	}

	// -- DesktopIntegrationProvider methods --

	@Override
	public boolean isWebLinksEnabled() {
		try {
			final DesktopFile df = getOrCreateDesktopFile();
			return df.hasMimeType("x-scheme-handler/fiji");
		} catch (final IOException e) {
			if (log != null) {
				log.debug("Failed to check web links status", e);
			}
			return false;
		}
	}

	@Override
	public boolean isWebLinksToggleable() { return true; }

	@Override
	public void setWebLinksEnabled(final boolean enable) throws IOException {
		final DesktopFile df = getOrCreateDesktopFile();
		
		if (enable) {
			df.addMimeType("x-scheme-handler/fiji");
		} else {
			df.removeMimeType("x-scheme-handler/fiji");
		}
		
		df.save();
	}

	@Override
	public boolean isDesktopIconPresent() {
		final Path desktopFilePath = getDesktopFilePath();
		return Files.exists(desktopFilePath);
	}

	@Override
	public boolean isDesktopIconToggleable() { return true; }

	@Override
	public void setDesktopIconPresent(final boolean install) throws IOException {
		final DesktopFile df = getOrCreateDesktopFile();

		if (install) {
			// Ensure .desktop file has all required fields
			if (df.getName() == null) {
				final String appName = System.getProperty("scijava.app.name", "SciJava Application");
				df.setName(appName);
			}
			if (df.getType() == null) {
				df.setType("Application");
			}
			if (df.getVersion() == null) {
				df.setVersion("1.0");
			}
			if (df.getExec() == null) {
				final String appExec = System.getProperty("scijava.app.executable");
				if (appExec == null) {
					throw new IOException("No executable path set (scijava.app.executable property)");
				}
				df.setExec(appExec + " %U");
			}
			if (df.getGenericName() == null) {
				final String appName = System.getProperty("scijava.app.name", "SciJava Application");
				df.setGenericName(appName);
			}
			
			// Set optional fields if provided
			final String appIcon = System.getProperty("scijava.app.icon");
			if (appIcon != null && df.getIcon() == null) {
				df.setIcon(appIcon);
			}
			
			final String appDir = System.getProperty("scijava.app.directory");
			if (appDir != null && df.getPath() == null) {
				df.setPath(appDir);
			}
			
			if (df.getCategories() == null) {
				df.setCategories("Science;Education;");
			}
			
			df.setTerminal(false);
			
			df.save();
		}
		else {
			df.delete();
		}
	}

	// -- Helper methods --

	/**
	 * Gets or creates a DesktopFile instance, loading it if it exists.
	 */
	private DesktopFile getOrCreateDesktopFile() throws IOException {
		final Path path = getDesktopFilePath();
		final DesktopFile df = new DesktopFile(path);
		
		if (df.exists()) {
			df.load();
		}
		
		return df;
	}

	/**
	 * Creates or updates the .desktop file for this application.
	 * <p>
	 * The .desktop file path is determined by the {@code scijava.app.desktop-file}
	 * system property. If not set, defaults to {@code ~/.local/share/applications/<app>.desktop}.
	 * </p>
	 */
	private void installDesktopFile() throws IOException {
		final Path desktopFilePath = getDesktopFilePath();

		// Check if file already exists and is up-to-date
		if (Files.exists(desktopFilePath) && isDesktopFileUpToDate(desktopFilePath)) {
			if (log != null) {
				log.debug("Desktop file is up-to-date: " + desktopFilePath);
			}
			return;
		}

		// Get application properties
		final String appName = System.getProperty("scijava.app.name", "SciJava Application");
		final String appExec = System.getProperty("scijava.app.executable");
		final String appIcon = System.getProperty("scijava.app.icon");
		final String appDir = System.getProperty("scijava.app.directory");

		if (appExec == null) {
			if (log != null) {
				log.debug("No executable path set (scijava.app.executable property), skipping .desktop file creation");
			}
			return;
		}

		// Use DesktopFile to create and save
		final DesktopFile df = new DesktopFile(desktopFilePath);
		df.setType("Application");
		df.setVersion("1.0");
		df.setName(appName);
		df.setGenericName(appName);
		df.setExec(appExec + " %U");
		df.setTerminal(false);
		df.setCategories("Science;Education;");

		if (appIcon != null) {
			df.setIcon(appIcon);
		}

		if (appDir != null) {
			df.setPath(appDir);
		}

		// MimeType field intentionally left empty
		// scijava-links will add URI scheme handlers (x-scheme-handler/...)

		df.save();

		if (log != null) {
			log.info("Created desktop file: " + desktopFilePath);
		}
	}

	private Path getDesktopFilePath() {
		String desktopFilePath = System.getProperty("scijava.app.desktop-file");
		if (desktopFilePath == null) {
			final String appName = System.getProperty("scijava.app.name", "scijava-app");
			final String home = System.getProperty("user.home");
			desktopFilePath = home + "/.local/share/applications/" + sanitizeFileName(appName) + ".desktop";
			System.setProperty("scijava.app.desktop-file", desktopFilePath);
		}
		return Paths.get(desktopFilePath);
	}

	/**
	 * Checks if the desktop file is up-to-date with current system properties.
	 */
	private boolean isDesktopFileUpToDate(final Path desktopFile) {
		// For now, simple existence check
		// Future: could parse and compare with current properties
		return Files.exists(desktopFile);
	}

	/**
	 * Sanitizes a string for use as a file name.
	 */
	private String sanitizeFileName(final String name) {
		return name.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
	}
}
