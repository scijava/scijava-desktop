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

import org.scijava.ItemVisibility;
import org.scijava.desktop.DesktopService;
import org.scijava.log.LogService;
import org.scijava.module.ModuleItem;
import org.scijava.module.MutableModuleItem;
import org.scijava.options.OptionsPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Options plugin for managing desktop integration features.
 * <p>
 * Provides controls for enabling/disabling web links (URI schemes) and
 * desktop icons. Settings are applied directly to the OS (not persisted
 * to preferences), keeping the UI in sync with actual system state.
 * </p>
 *
 * @author Curtis Rueden
 */
@Plugin(type = OptionsPlugin.class, menuPath = "Edit > Options > Desktop...")
public class OptionsDesktop extends OptionsPlugin {

	@Parameter
	private DesktopService desktopService;

	@Parameter(required = false)
	private LogService log;

	private MutableModuleItem<?> webLinksItem;
	private MutableModuleItem<?> desktopIconItem;
	private MutableModuleItem<?> fileTypesItem;

	@Override
	public void initialize() {
		// Create module inputs on first initialization.
		if (webLinksItem == null) {
			webLinksItem = createInput("webLinksEnabled",
				"Enable web links",
				"Allow handling of URI link schemes from web browsers",
				"Web links always enabled", "Web links always disabled",
				desktopService.isWebLinksToggleable(),
				desktopService.isWebLinksEnabled());
		}
		if (desktopIconItem == null) {
			desktopIconItem = createInput("desktopIconPresent",
				"Add desktop icon",
				"Install application icon in the system menu",
				"Icon always present", "Icon installation not implemented",
				desktopService.isDesktopIconToggleable(),
				desktopService.isDesktopIconPresent());
		}
		if (fileTypesItem == null) {
			fileTypesItem = createInput("fileTypesEnabled",
				"Enable file type associations",
				"Register supported file extensions with the operating system",
				"File types always handled", "File type registration not supported",
				desktopService.isFileExtensionsToggleable(),
				desktopService.isFileExtensionsEnabled());
		}
	}

	@Override
	public void run() {
		final boolean webLinks = Boolean.TRUE.equals(getInputValue(webLinksItem));
		final boolean desktopIcon = Boolean.TRUE.equals(getInputValue(desktopIconItem));
		final boolean fileTypes = Boolean.TRUE.equals(getInputValue(fileTypesItem));
		desktopService.syncDesktopIntegration(webLinks, desktopIcon, fileTypes);
		super.run();
	}

	// -- Helper methods --

	private MutableModuleItem<?> createInput(final String name,
		final String label, final String description,
		final String alwaysOnMessage, final String alwaysOffMessage,
		final boolean toggleable, final boolean enabled)
	{
		final MutableModuleItem<?> item;
		if (toggleable) {
			item = addInput(name, boolean.class);
			item.setLabel(label);
			setInput(name, enabled);
		}
		else {
			item = addInput(name, String.class);
			item.setVisibility(ItemVisibility.MESSAGE);
			item.setRequired(false);
			item.setLabel(enabled ?
				alwaysOnMessage + " for this platform ✅" :
				alwaysOffMessage + " for this platform ❌");
		}
		item.setPersisted(false);
		item.setDescription(description);
		return item;
	}

	private Boolean getInputValue(final ModuleItem<?> item) {
		return item.getType() == boolean.class ?
			(Boolean) getInput(item.getName()) : null;
	}
}
