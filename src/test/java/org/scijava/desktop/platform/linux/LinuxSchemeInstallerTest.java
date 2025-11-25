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

package org.scijava.desktop.platform.linux;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Tests {@link LinuxSchemeInstaller}.
 *
 * @author Curtis Rueden
 */
public class LinuxSchemeInstallerTest {

	private static final String TEST_SCHEME = "scijava-test";
	private LinuxSchemeInstaller installer;
	private Path tempDesktopFile;
	private String originalProperty;

	@Before
	public void setUp() throws IOException {
		installer = new LinuxSchemeInstaller(null);
		// Only run tests on Linux
		Assume.assumeTrue("Tests only run on Linux", installer.isSupported());

		// Create temporary desktop file
		tempDesktopFile = Files.createTempFile("test-app", ".desktop");

		// Write basic desktop file content
		final DesktopFile df = new DesktopFile();
		df.set("Type", "Application");
		df.set("Name", "Test App");
		df.set("Exec", "/usr/bin/test-app %U");
		df.writeTo(tempDesktopFile);

		// Set system property
		originalProperty = System.getProperty("scijava.app.desktop-file");
		System.setProperty("scijava.app.desktop-file", tempDesktopFile.toString());
	}

	@After
	public void tearDown() throws IOException {
		// Restore original property
		if (originalProperty != null) {
			System.setProperty("scijava.app.desktop-file", originalProperty);
		}
		else {
			System.clearProperty("scijava.app.desktop-file");
		}

		// Clean up temp file
		if (tempDesktopFile != null && Files.exists(tempDesktopFile)) {
			Files.delete(tempDesktopFile);
		}
	}

	@Test
	public void testIsSupported() {
		final String os = System.getProperty("os.name");
		final boolean expectedSupport = os != null && os.toLowerCase().contains("linux");
		assertEquals(expectedSupport, installer.isSupported());
	}

	@Test
	public void testInstallAndUninstall() throws IOException {
		// Arrange
		final String execPath = "/usr/bin/test-app";

		// Ensure scheme is not already installed
		if (installer.isInstalled(TEST_SCHEME)) {
			installer.uninstall(TEST_SCHEME);
		}
		assertFalse("Test scheme should not be installed initially", installer.isInstalled(TEST_SCHEME));

		// Act - Install (note: xdg-mime may fail in test environment, but file should be modified)
		try {
			installer.install(TEST_SCHEME, execPath);
		}
		catch (final IOException e) {
			// xdg-mime might not be available in test environment
			// Check if file was at least modified
		}

		// Assert - Check desktop file was modified
		final DesktopFile df = DesktopFile.parse(tempDesktopFile);
		assertTrue("Desktop file should contain MIME type", df.hasMimeType("x-scheme-handler/" + TEST_SCHEME));

		// Act - Uninstall
		installer.uninstall(TEST_SCHEME);

		// Assert - Uninstalled
		final DesktopFile df2 = DesktopFile.parse(tempDesktopFile);
		assertFalse("Desktop file should not contain MIME type", df2.hasMimeType("x-scheme-handler/" + TEST_SCHEME));
	}

	@Test
	public void testInstallTwice() throws IOException {
		// Arrange
		final String execPath = "/usr/bin/test-app";

		// Act - Install twice
		try {
			installer.install(TEST_SCHEME, execPath);
			installer.install(TEST_SCHEME, execPath); // Should not fail
		}
		catch (final IOException e) {
			// xdg-mime might not be available
		}

		// Assert - Check only one entry
		final DesktopFile df = DesktopFile.parse(tempDesktopFile);
		final String mimeType = df.get("MimeType");
		assertNotNull("MimeType should be set", mimeType);

		// Count occurrences of the test scheme
		int count = 0;
		for (final String part : mimeType.split(";")) {
			if (part.trim().equals("x-scheme-handler/" + TEST_SCHEME)) {
				count++;
			}
		}
		assertEquals("Scheme should appear exactly once", 1, count);
	}

	@Test
	public void testIsInstalledReturnsFalseWhenFileDoesNotExist() {
		// Arrange
		System.setProperty("scijava.app.desktop-file", "/nonexistent/path/app.desktop");

		// Act & Assert
		assertFalse("Should return false when desktop file doesn't exist",
			installer.isInstalled(TEST_SCHEME));
	}

	@Test
	public void testGetInstalledPath() throws IOException {
		// Arrange
		final String execPath = "/usr/bin/test-app";

		try {
			installer.install(TEST_SCHEME, execPath);
		}
		catch (final IOException e) {
			// xdg-mime might not be available
		}

		// Act
		final String installedPath = installer.getInstalledPath(TEST_SCHEME);

		// Assert
		assertEquals("Should return exec path from desktop file", execPath, installedPath);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstallWithNullScheme() throws IOException {
		installer.install(null, "/usr/bin/test-app");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstallWithEmptyScheme() throws IOException {
		installer.install("", "/usr/bin/test-app");
	}

	@Test(expected = IOException.class)
	public void testInstallWithoutDesktopFileProperty() throws IOException {
		// Arrange
		System.clearProperty("scijava.app.desktop-file");

		// Act
		installer.install(TEST_SCHEME, "/usr/bin/test-app");
	}

	@Test
	public void testMultipleSchemes() throws IOException {
		// Arrange
		final String scheme1 = "scijava-test1";
		final String scheme2 = "scijava-test2";

		// Act - Install two schemes
		try {
			installer.install(scheme1, "/usr/bin/test-app");
			installer.install(scheme2, "/usr/bin/test-app");
		}
		catch (final IOException e) {
			// xdg-mime might not be available
		}

		// Assert - Both should be in desktop file
		final DesktopFile df = DesktopFile.parse(tempDesktopFile);
		assertTrue("Should have first scheme", df.hasMimeType("x-scheme-handler/" + scheme1));
		assertTrue("Should have second scheme", df.hasMimeType("x-scheme-handler/" + scheme2));

		// Cleanup
		installer.uninstall(scheme1);
		installer.uninstall(scheme2);
	}
}
