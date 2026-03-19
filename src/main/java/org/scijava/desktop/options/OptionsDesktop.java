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

package org.scijava.desktop.options;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import org.scijava.desktop.DesktopIntegrationProvider;
import org.scijava.log.LogService;
import org.scijava.options.OptionsPlugin;
import org.scijava.platform.Platform;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Options plugin for managing desktop integration features.
 * <p>
 * Provides a UI for enabling/disabling web links (URI schemes) and
 * desktop icons. Settings are applied directly to the OS (not persisted
 * to preferences), keeping the UI in sync with actual system state.
 * </p>
 *
 * @author Curtis Rueden
 */
@Plugin(type = OptionsPlugin.class, menuPath = "Edit > Options > Desktop...")
public class OptionsDesktop extends OptionsPlugin {

	@Parameter
	private PlatformService platformService;

	@Parameter(required = false)
	private LogService log;

	@Parameter(label = "Enable web links", persist = false, validater = "validateWebLinks", //
		description = "Allow handling of URI link schemes from web browsers")
	private boolean webLinksEnabled;

	@Parameter(label = "Add desktop icon", persist = false, validater = "validateDesktopIcon", //
		description = "Install application icon in the system menu")
	private boolean desktopIconPresent;

	@Parameter(label = "Enable file type associations", persist = false, validater = "validateFileExtensions", //
		description = "Register supported file extensions with the operating system")
	private boolean fileExtensionsEnabled;

	@Override
	public void load() {
		webLinksEnabled = true;
		desktopIconPresent = true;
		fileExtensionsEnabled = true;
		for (final Platform platform : platformService.getTargetPlatforms()) {
			if (!(platform instanceof DesktopIntegrationProvider)) continue;
			final DesktopIntegrationProvider dip = (DesktopIntegrationProvider) platform;
			// If any toggleable platform setting is off, uncheck that box.
			if (dip.isDesktopIconToggleable() && !dip.isDesktopIconPresent()) desktopIconPresent = false;
			if (dip.isWebLinksToggleable() && !dip.isWebLinksEnabled()) webLinksEnabled = false;
			if (dip.isFileExtensionsToggleable() && !dip.isFileExtensionsEnabled()) fileExtensionsEnabled = false;
		}
	}

	@Override
	public void run() {
		for (final Platform platform : platformService.getTargetPlatforms()) {
			if (!(platform instanceof DesktopIntegrationProvider)) continue;
			final DesktopIntegrationProvider dip = (DesktopIntegrationProvider) platform;
			try {
				dip.setWebLinksEnabled(webLinksEnabled);
				dip.setDesktopIconPresent(desktopIconPresent);
				dip.setFileExtensionsEnabled(fileExtensionsEnabled);
			}
			catch (final IOException e) {
				if (log != null) {
					log.error("Error applying desktop integration settings", e);
				}
			}
		}
		super.run();
	}

	// -- Validators --

	public void validateWebLinks() {
		validateSetting(
			DesktopIntegrationProvider::isWebLinksToggleable,
			DesktopIntegrationProvider::isWebLinksEnabled,
			webLinksEnabled,
			"Web links setting");
	}

	public void validateDesktopIcon() {
		validateSetting(
			DesktopIntegrationProvider::isDesktopIconToggleable,
			DesktopIntegrationProvider::isDesktopIconPresent,
			desktopIconPresent,
			"Desktop icon presence");
	}

	public void validateFileExtensions() {
		validateSetting(
			DesktopIntegrationProvider::isFileExtensionsToggleable,
			DesktopIntegrationProvider::isFileExtensionsEnabled,
			fileExtensionsEnabled,
			"File extensions setting");
	}

	// -- Helper methods --

	private String name(Platform platform) {
		final List<PluginInfo<SciJavaPlugin>> infos =
			pluginService.getPluginsOfClass(platform.getClass());
		return infos.isEmpty() ? null : infos.get(0).getName();
	}

	private void validateSetting(
		Function<DesktopIntegrationProvider, Boolean> mutable,
		Function<DesktopIntegrationProvider, Boolean> getter,
		boolean value, String settingDescription)
	{
		boolean toggleable = false;
		boolean enabled = false;
		Platform strictPlatform = null;
		for (final Platform platform : platformService.getTargetPlatforms()) {
			if (!(platform instanceof DesktopIntegrationProvider)) continue;
			final DesktopIntegrationProvider dip = (DesktopIntegrationProvider) platform;
			if (mutable.apply(dip)) toggleable = true;
			else if (strictPlatform == null) strictPlatform = platform;
			if (getter.apply(dip)) enabled = true;
		}
		if (!toggleable && enabled != value) {
			final String platformName = strictPlatform == null ? "this platform" : name(strictPlatform);
			throw new IllegalArgumentException(settingDescription + " cannot be changed on " + platformName + ".");
		}
	}
}
