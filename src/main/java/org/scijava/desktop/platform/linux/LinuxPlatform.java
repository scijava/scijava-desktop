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

import org.scijava.Context;
import org.scijava.app.AppService;
import org.scijava.desktop.DesktopIntegrationProvider;
import org.scijava.desktop.DesktopService;
import org.scijava.desktop.links.LinkService;
import org.scijava.desktop.links.SchemeInstaller;
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
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
	private Context context;

	@Parameter
	private AppService appService;

	@Parameter(required = false)
	private LogService log;

	/** Cached MIME type mapping */
	private static Map<String, String> extensionToMime = null;

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

			// Check if any scheme is registered
			for (final String scheme : schemes()) {
				if (df.hasMimeType("x-scheme-handler/" + scheme)) return true;
			}
		}
		catch (final IOException e) {
			if (log != null) log.debug("Failed to check web links status", e);
		}
		return false;
	}

	@Override
	public boolean isWebLinksToggleable() { return true; }

	@Override
	public void setWebLinksEnabled(final boolean enable) throws IOException {
		final DesktopFile df = getOrCreateDesktopFile();

		for (final String scheme : schemes()) {
			final String mimeType = "x-scheme-handler/" + scheme;
			if (enable) df.addMimeType(mimeType);
			else df.removeMimeType(mimeType);
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

	@Override
	public boolean isFileExtensionsEnabled() {
		try {
			final DesktopFile df = getOrCreateDesktopFile();
			final Map<String, String> mimeMapping = loadMimeTypeMapping();

			// Check if any file extension MIME types are in the .desktop file
			for (final String mimeType : mimeMapping.values()) {
				if (df.hasMimeType(mimeType)) return true;
			}
			return false;
		} catch (final IOException e) {
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
		final Map<String, String> mimeMapping = loadMimeTypeMapping();
		if (mimeMapping.isEmpty()) {
			if (log != null) {
				log.warn("No file extensions to register");
			}
			return;
		}

		if (enable) {
			// Register custom MIME types for formats without standard types
			registerCustomMimeTypes(mimeMapping);

			// Add MIME types to .desktop file
			final DesktopFile df = getOrCreateDesktopFile();
			for (final String mimeType : mimeMapping.values()) {
				df.addMimeType(mimeType);
			}
			df.save();

			if (log != null) {
				log.info("Registered " + mimeMapping.size() + " file extension MIME types");
			}
		} else {
			// Remove file extension MIME types from .desktop file
			// Keep URI scheme handlers (x-scheme-handler/...)
			final DesktopFile df = getOrCreateDesktopFile();

			for (final String mimeType : mimeMapping.values()) {
				df.removeMimeType(mimeType);
			}

			// Re-add URI scheme handlers
			for (final String scheme : schemes()) {
				df.addMimeType("x-scheme-handler/" + scheme);
			}

			df.save();

			if (log != null) {
				log.info("Unregistered file extension MIME types");
			}
		}
	}

	@Override
	public SchemeInstaller getSchemeInstaller() {
		return new LinuxSchemeInstaller(log);
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

	private Map<String, String> fileTypes() {
		final DesktopService desktopService = desktopService();
		return desktopService == null ?
			Collections.emptyMap() : desktopService.getFileTypes();
	}

	/**
	 * Loads the file extension to MIME type mapping.
	 */
	private synchronized Map<String, String> loadMimeTypeMapping() throws IOException {
		if (extensionToMime != null) return extensionToMime;

		extensionToMime = new LinkedHashMap<>();

		final Map<String, String> fileTypes = fileTypes();
		// TODO: How do we know the MIME type of each extension?

		return extensionToMime;
	}

	/**
	 * Registers custom MIME types for formats that don't have standard types.
	 * Creates ~/.local/share/mime/packages/[appName].xml and runs update-mime-database.
	 */
	private void registerCustomMimeTypes(final Map<String, String> mimeMapping) throws IOException {
		// Separate standard from custom MIME types
		final Map<String, String> customTypes = new LinkedHashMap<>();
		for (final Map.Entry<String, String> entry : mimeMapping.entrySet()) {
			final String mimeType = entry.getValue();
			// Custom types use application/x- prefix
			if (mimeType.startsWith("application/x-")) {
				customTypes.put(entry.getKey(), mimeType);
			}
		}

		if (customTypes.isEmpty()) {
			// No custom types to register
			return;
		}

		// Generate MIME types XML
		final String appName = System.getProperty("scijava.app.name", "SciJava");
		final String mimeXml = generateMimeTypesXml(customTypes, appName);

		// Write to ~/.local/share/mime/packages/<app>.xml
		final Path mimeDir = Paths.get(System.getProperty("user.home"),
			".local/share/mime/packages");
		Files.createDirectories(mimeDir);

		final Path mimeFile = mimeDir.resolve(sanitizeFileName(appName) + ".xml");
		Files.writeString(mimeFile, mimeXml, StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING);

		// Update MIME database
		try {
			final ProcessBuilder pb = new ProcessBuilder(
				"update-mime-database",
				Paths.get(System.getProperty("user.home"), ".local/share/mime").toString()
			);
			final Process process = pb.start();
			final int exitCode = process.waitFor();

			if (exitCode != 0) {
				if (log != null) {
					log.warn("update-mime-database exited with code " + exitCode);
				}
			} else if (log != null) {
				log.info("Registered " + customTypes.size() + " custom MIME types");
			}
		} catch (final Exception e) {
			if (log != null) {
				log.error("Failed to run update-mime-database", e);
			}
		}
	}

	/**
	 * Generates MIME types XML for custom file formats.
	 */
	private String generateMimeTypesXml(final Map<String, String> customTypes,
		final String appName)
	{
		final StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<mime-info xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">\n");

		for (final Map.Entry<String, String> entry : customTypes.entrySet()) {
			final String extension = entry.getKey();
			final String mimeType = entry.getValue();

			// Generate human-readable comment from MIME type
			final String comment = generateMimeTypeComment(mimeType);

			xml.append("  <mime-type type=\"").append(mimeType).append("\">\n");
			xml.append("    <comment>").append(comment).append("</comment>\n");
			xml.append("    <glob pattern=\"*.").append(extension).append("\"/>\n");
			xml.append("  </mime-type>\n");
		}

		xml.append("</mime-info>\n");
		return xml.toString();
	}

	/**
	 * Generates a human-readable comment from a MIME type.
	 * For example, "application/x-zeiss-czi" becomes "Zeiss CZI File".
	 */
	private String generateMimeTypeComment(final String mimeType) {
		// Extract the format part (e.g., "zeiss-czi" from "application/x-zeiss-czi")
		final String format = mimeType.substring(mimeType.lastIndexOf('/') + 1);

		// Remove "x-" prefix if present
		final String cleanFormat = format.startsWith("x-") ?
			format.substring(2) : format;

		// Convert to title case
		final String[] parts = cleanFormat.split("-");
		final StringBuilder comment = new StringBuilder();
		for (final String part : parts) {
			if (comment.length() > 0) comment.append(' ');
			comment.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				comment.append(part.substring(1));
			}
		}
		comment.append(" File");

		return comment.toString();
	}
}
