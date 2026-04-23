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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
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

	/** Cached set of MIME types known to the system's shared-mime-info database. */
	private Set<String> systemMimeTypes;

	/** Cached map from file extension to MIME type, from the system's globs database. */
	private Map<String, String> systemMimeGlobs;

	// -- Platform methods --

	@Override
	public String osName() {
		return "Linux";
	}

	@Override
	public void configure(final PlatformService service) {
		super.configure(service);

		// Create or update .desktop file for desktop integration.
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
			final DesktopFile df = loadDesktopFile();
			if (df == null) return false;
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
		syncDesktopIntegration(enable, isDesktopIconPresent(), isFileExtensionsEnabled());
	}

	@Override
	public boolean isDesktopIconPresent() {
		try {
			final DesktopFile df = loadDesktopFile();
			// A desktop icon entry requires at minimum a Name and an Exec.
			return df != null && df.getName() != null && df.getExec() != null;
		}
		catch (final IOException e) {
			if (log != null) log.debug("Failed to check desktop icon status", e);
			return false;
		}
	}

	@Override
	public boolean isDesktopIconToggleable() { return true; }

	@Override
	public void setDesktopIconPresent(final boolean install) throws IOException {
		syncDesktopIntegration(isWebLinksEnabled(), install, isFileExtensionsEnabled());
	}

	@Override
	public boolean isFileExtensionsEnabled() {
		try {
			final DesktopFile df = loadDesktopFile();
			if (df == null) return false;
			for (final String mimeType : resolvedFileTypes().values()) {
				if (df.hasMimeType(mimeType)) return true;
			}
		}
		catch (final IOException e) {
			if (log != null) log.debug("Failed to check file extensions status", e);
		}
		return false;
	}

	@Override
	public boolean isFileExtensionsToggleable() { return true; }

	@Override
	public void setFileExtensionsEnabled(final boolean enable) throws IOException {
		syncDesktopIntegration(isWebLinksEnabled(), isDesktopIconPresent(), enable);
	}

	@Override
	public void syncDesktopIntegration(final boolean webLinks,
		final boolean desktopIcon, final boolean fileTypes) throws IOException
	{
		final Path path = getDesktopFilePath();

		// If nothing is enabled, remove the file entirely.
		if (!webLinks && !desktopIcon && !fileTypes) {
			Files.deleteIfExists(path);
			return;
		}

		// An executable is required for any feature to work.
		final String appExec = System.getProperty("scijava.app.executable");
		if (appExec == null) return;

		final DesktopFile df = new DesktopFile(path);

		// TryExec and Exec are always written — they identify which binary handles opens/links.
		df.setTryExec(appExec);
		df.setExec(appExec + " %U");

		if (desktopIcon) {
			final String appName = System.getProperty("scijava.app.name",
				"SciJava Application");
			df.setType("Application");
			df.setVersion("1.0");
			df.setName(appName);
			df.setGenericName(appName);
			final String appCategories =
				System.getProperty("scijava.app.categories", "Science;Education;");
			df.setCategories(appCategories);
			df.setTerminal(false);
			df.setStartupNotify(true);
			final String appWMClass = System.getProperty("sun.awt.wmclass");
			if (appWMClass != null) df.setStartupWMClass(appWMClass);
			final String appIcon = System.getProperty("scijava.app.icon");
			if (appIcon != null) df.setIcon(appIcon);
			final String appDir = System.getProperty("scijava.app.directory");
			if (appDir != null) df.setPath(appDir);
		}

		if (webLinks) {
			for (final String scheme : schemes()) {
				df.addMimeType("x-scheme-handler/" + scheme);
			}
		}

		if (fileTypes) {
			final Map<String, String> mimeMapping = resolvedFileTypes();
			if (!mimeMapping.isEmpty()) {
				registerCustomMimeTypes(mimeMapping);
				for (final String mimeType : mimeMapping.values()) {
					df.addMimeType(mimeType);
				}
			}
		}

		df.save();

		if (log != null) {
			log.debug("Synced desktop file: webLinks=" + webLinks +
				", desktopIcon=" + desktopIcon + ", fileTypes=" + fileTypes);
		}
	}

	@Override
	public SchemeInstaller getSchemeInstaller() {
		return new LinuxSchemeInstaller(getDesktopFilePath(), log);
	}

	// -- Helper methods --

	/**
	 * Loads and returns the desktop file if it exists, or {@code null} if not.
	 */
	private DesktopFile loadDesktopFile() throws IOException {
		final Path path = getDesktopFilePath();
		if (!Files.exists(path)) return null;
		final DesktopFile df = new DesktopFile(path);
		df.load();
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
		df.setTryExec(appExec);
		df.setExec(appExec + " %U");
		df.setTerminal(false);
		final String appCategories = System.getProperty("scijava.app.categories",
			"Science;Education;");
		df.setCategories(appCategories);
		df.setStartupNotify(true);
		final String appWMClass = System.getProperty("scijava.app.wmclass");
		if (appWMClass != null) df.setStartupWMClass(appWMClass);

		if (appIcon != null) {
			df.setIcon(appIcon);
		}

		if (appDir != null) {
			df.setPath(appDir);
		}

		// MimeType field intentionally left empty
		// scijava-links will add URI scheme handlers (x-scheme-handler/...)

		df.save();

		if (log != null) log.info("Wrote desktop file: " + desktopFilePath);
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
		// NB: We cannot declare LinkService as a @Parameter because
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

	// -- Helper methods - lazy initialization --

	/**
	 * Initializes {@link #systemMimeTypes} by walking {@code /usr/share/mime/}.
	 * <p>
	 * The compiled shared-mime-info database is organized as
	 * {@code /usr/share/mime/type/subtype.xml}, so each XML file path below the
	 * root (excluding the {@code packages/} source tree) directly encodes a MIME
	 * type. If the directory is absent or unreadable, the set is left empty and
	 * all app-registered types will be treated as custom.
	 * </p>
	 */
	private synchronized void initSystemMimeTypes() {
		if (systemMimeTypes != null) return; // already initialized

		final Set<String> types = new HashSet<>();
		final Path mimeRoot = Paths.get("/usr/share/mime");
		if (Files.isDirectory(mimeRoot)) {
			try (final var stream = Files.walk(mimeRoot)) {
				stream
					.filter(p -> p.toString().endsWith(".xml"))
					.filter(p -> !p.toString().contains("/packages/"))
					.forEach(p -> {
						final String rel = mimeRoot.relativize(p).toString();
						// rel is e.g. "image/png.xml" -> strip ".xml" -> "image/png"
						types.add(rel.substring(0, rel.length() - 4));
					});
			}
			catch (final IOException | RuntimeException e) {
				if (log != null) log.warn("Could not read system MIME database", e);
			}
		}
		if (log != null) log.debug("Loaded " + types.size() + " system MIME types");
		systemMimeTypes = types;
	}

	/**
	 * Initializes {@link #systemMimeGlobs} by reading the system's globs
	 * database at {@code /usr/share/mime/globs2} (falling back to
	 * {@code /usr/share/mime/globs}).
	 * <p>
	 * The resulting map associates file extensions (without leading dot,
	 * lower-cased) to their canonical MIME type strings, e.g. {@code "png"
	 * → "image/png"}.
	 * </p>
	 */
	private synchronized void initSystemMimeGlobs() {
		if (systemMimeGlobs != null) return;

		final Map<String, String> globs = new LinkedHashMap<>();
		for (final String fileName : new String[]{
			"/usr/share/mime/globs2", "/usr/share/mime/globs"})
		{
			final Path p = Paths.get(fileName);
			if (!Files.exists(p)) continue;
			try {
				for (final String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
					if (line.startsWith("#") || line.isBlank()) continue;
					final String[] parts = line.split(":");
					final String mime, glob;
					if (parts.length >= 3) {
						// globs2 format: weight:mime-type:glob
						mime = parts[1];
						glob = parts[2];
					}
					else if (parts.length == 2) {
						// globs format: mime-type:glob
						mime = parts[0];
						glob = parts[1];
					}
					else continue;
					if (glob.startsWith("*.")) {
						globs.putIfAbsent(glob.substring(2).toLowerCase(), mime);
					}
				}
				break; // loaded successfully; prefer globs2 over globs
			}
			catch (final IOException e) {
				if (log != null) log.warn("Failed to read " + fileName, e);
			}
		}
		if (log != null) log.debug("Loaded " + globs.size() + " system MIME globs");
		systemMimeGlobs = globs;
	}

	/**
	 * Resolves a wildcard MIME sentinel (e.g. {@code "image/*"}) to a concrete
	 * MIME type for the given file extension.
	 * <p>
	 * Resolution order:
	 * </p>
	 * <ol>
	 * <li>If the system's globs database maps {@code ext} to a type whose
	 *   category matches the wildcard prefix (e.g. {@code "image/"}), that
	 *   system type is returned.</li>
	 * <li>Otherwise a synthetic type is returned: {@code prefix/x-sanitized-ext}
	 *   (e.g. {@code "image/x-m71"}).</li>
	 * </ol>
	 */
	private String resolveWildcard(final String ext, final String wildcardMime) {
		if (systemMimeGlobs == null) initSystemMimeGlobs();
		final String prefix = wildcardMime.substring(0, wildcardMime.length() - 2);
		final String systemType = systemMimeGlobs.get(ext.toLowerCase());
		if (systemType != null && systemType.startsWith(prefix + "/")) {
			return systemType;
		}
		final String sanitized = ext.toLowerCase().replaceAll("[^a-z0-9]", "-");
		return prefix + "/x-" + sanitized;
	}

	/**
	 * Returns the file types map from {@link DesktopService} with wildcard
	 * sentinel values (e.g. {@code "image/*"}) resolved to concrete MIME types
	 * via {@link #resolveWildcard}.
	 */
	private Map<String, String> resolvedFileTypes() {
		final Map<String, String> raw = fileTypes();
		final Map<String, String> resolved = new LinkedHashMap<>();
		for (final Map.Entry<String, String> entry : raw.entrySet()) {
			final String ext = entry.getKey();
			final String mime = entry.getValue();
			resolved.put(ext, mime.endsWith("/*") ? resolveWildcard(ext, mime) : mime);
		}
		return resolved;
	}

	/**
	 * Registers custom MIME types for formats not already known to the system.
	 * Creates {@code ~/.local/share/mime/packages/[appName].xml} and runs
	 * {@code update-mime-database}.
	 */
	private void registerCustomMimeTypes(final Map<String, String> mimeMapping)
		throws IOException
	{
		if (systemMimeTypes == null) initSystemMimeTypes();

		// Collect only types absent from the system MIME database.
		final Map<String, String> customTypes = new LinkedHashMap<>();
		for (final Map.Entry<String, String> entry : mimeMapping.entrySet()) {
			final String mimeType = entry.getValue();
			if (!systemMimeTypes.contains(mimeType)) {
				customTypes.put(entry.getKey(), mimeType);
			}
		}

		if (customTypes.isEmpty()) {
			// No custom types to register.
			return;
		}

		// Generate MIME types XML.
		final String appName = System.getProperty("scijava.app.name", "SciJava");
		final String mimeXml = generateMimeTypesXml(customTypes, appName);

		// Write to ~/.local/share/mime/packages/<app>.xml.
		final Path mimeDir = Paths.get(System.getProperty("user.home"),
			".local/share/mime/packages");
		Files.createDirectories(mimeDir);

		final Path mimeFile = mimeDir.resolve(sanitizeFileName(appName) + ".xml");
		Files.writeString(mimeFile, mimeXml, StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING);

		// Update MIME database.
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
			}
			else if (log != null) {
				log.info("Registered " + customTypes.size() + " custom MIME types");
			}
		}
		catch (final Exception e) {
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

			// Generate human-readable comment from MIME type.
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
		// Extract the format part (e.g., "zeiss-czi" from "application/x-zeiss-czi").
		final String format = mimeType.substring(mimeType.lastIndexOf('/') + 1);

		// Remove "x-" prefix if present.
		final String cleanFormat = format.startsWith("x-") ?
			format.substring(2) : format;

		// Convert to title case.
		final String[] parts = cleanFormat.split("-");
		final StringBuilder comment = new StringBuilder();
		for (final String part : parts) {
			if (part.isEmpty()) continue;
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
