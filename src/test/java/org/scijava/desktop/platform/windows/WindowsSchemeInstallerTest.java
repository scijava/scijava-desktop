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

package org.scijava.desktop.platform.windows;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests {@link WindowsSchemeInstaller}.
 *
 * @author Curtis Rueden
 */
public class WindowsSchemeInstallerTest {

	private static final String TEST_SCHEME = "scijava-test";
	private WindowsSchemeInstaller installer;

	@Before
	public void setUp() {
		installer = new WindowsSchemeInstaller(null);
		// Only run tests on Windows
		Assume.assumeTrue("Tests only run on Windows", installer.isSupported());
	}

	@After
	public void tearDown() {
		// Clean up test scheme if it was installed
		if (installer != null && installer.isInstalled(TEST_SCHEME)) {
			try {
				installer.uninstall(TEST_SCHEME);
			}
			catch (final IOException e) {
				// Ignore cleanup errors
			}
		}
	}

	@Test
	public void testIsSupported() {
		final String os = System.getProperty("os.name");
		final boolean expectedSupport = os != null && os.toLowerCase().contains("win");
		assertEquals(expectedSupport, installer.isSupported());
	}

	@Test
	public void testInstallAndUninstall() throws IOException {
		// Arrange
		final String execPath = "C:\\Program Files\\Test\\test.exe";

		// Ensure scheme is not already installed
		if (installer.isInstalled(TEST_SCHEME)) {
			installer.uninstall(TEST_SCHEME);
		}
		assertFalse("Test scheme should not be installed initially", installer.isInstalled(TEST_SCHEME));

		// Act - Install
		installer.install(TEST_SCHEME, execPath);

		// Assert - Installed
		assertTrue("Scheme should be installed", installer.isInstalled(TEST_SCHEME));
		final String installedPath = installer.getInstalledPath(TEST_SCHEME);
		assertEquals("Installed path should match", execPath, installedPath);

		// Act - Uninstall
		installer.uninstall(TEST_SCHEME);

		// Assert - Uninstalled
		assertFalse("Scheme should be uninstalled", installer.isInstalled(TEST_SCHEME));
		assertNull("Installed path should be null after uninstall", installer.getInstalledPath(TEST_SCHEME));
	}

	@Test
	public void testInstallTwiceWithSamePath() throws IOException {
		// Arrange
		final String execPath = "C:\\Program Files\\Test\\test.exe";

		// Ensure scheme is not already installed
		if (installer.isInstalled(TEST_SCHEME)) {
			installer.uninstall(TEST_SCHEME);
		}

		// Act - Install twice
		installer.install(TEST_SCHEME, execPath);
		installer.install(TEST_SCHEME, execPath); // Should not fail

		// Assert
		assertTrue("Scheme should still be installed", installer.isInstalled(TEST_SCHEME));
		assertEquals("Path should match", execPath, installer.getInstalledPath(TEST_SCHEME));
	}

	@Test
	public void testInstallWithDifferentPath() throws IOException {
		// Arrange
		final String execPath1 = "C:\\Program Files\\Test1\\test.exe";
		final String execPath2 = "C:\\Program Files\\Test2\\test.exe";

		// Ensure scheme is not already installed
		if (installer.isInstalled(TEST_SCHEME)) {
			installer.uninstall(TEST_SCHEME);
		}

		// Act - Install with first path
		installer.install(TEST_SCHEME, execPath1);
		assertEquals("First path should be installed", execPath1, installer.getInstalledPath(TEST_SCHEME));

		// Act - Install with second path (should update)
		installer.install(TEST_SCHEME, execPath2);

		// Assert
		assertTrue("Scheme should still be installed", installer.isInstalled(TEST_SCHEME));
		assertEquals("Path should be updated", execPath2, installer.getInstalledPath(TEST_SCHEME));
	}

	@Test
	public void testGetInstalledPathForNonExistentScheme() {
		// Arrange - ensure scheme doesn't exist
		if (installer.isInstalled(TEST_SCHEME)) {
			try {
				installer.uninstall(TEST_SCHEME);
			}
			catch (final IOException e) {
				// Ignore
			}
		}

		// Act
		final String path = installer.getInstalledPath(TEST_SCHEME);

		// Assert
		assertNull("Path should be null for non-existent scheme", path);
	}

	@Test
	public void testUninstallNonExistentScheme() throws IOException {
		// Arrange - ensure scheme doesn't exist
		if (installer.isInstalled(TEST_SCHEME)) {
			installer.uninstall(TEST_SCHEME);
		}

		// Act - uninstall non-existent scheme (should not fail)
		installer.uninstall(TEST_SCHEME);

		// Assert
		assertFalse("Scheme should not be installed", installer.isInstalled(TEST_SCHEME));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstallWithNullScheme() throws IOException {
		installer.install(null, "C:\\test.exe");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstallWithEmptyScheme() throws IOException {
		installer.install("", "C:\\test.exe");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstallWithNullPath() throws IOException {
		installer.install(TEST_SCHEME, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInstallWithEmptyPath() throws IOException {
		installer.install(TEST_SCHEME, "");
	}

	@Test
	public void testInstallWithPathContainingSpaces() throws IOException {
		// Arrange
		final String execPath = "C:\\Program Files\\My App\\app.exe";

		// Ensure scheme is not already installed
		if (installer.isInstalled(TEST_SCHEME)) {
			installer.uninstall(TEST_SCHEME);
		}

		// Act
		installer.install(TEST_SCHEME, execPath);

		// Assert
		assertTrue("Scheme should be installed", installer.isInstalled(TEST_SCHEME));
		assertEquals("Path with spaces should be handled correctly", execPath, installer.getInstalledPath(TEST_SCHEME));
	}
}
