/*
 * #%L
 * Core platform plugins for SciJava applications.
 * %%
 * Copyright (C) 2010 - 2015 Board of Regents of the University of
 * Wisconsin-Madison.
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

package org.scijava.plugins.platforms.macos;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenuBar;

import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.display.event.window.WinActivatedEvent;
import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.event.EventSubscriber;
import org.scijava.module.ModuleInfo;
import org.scijava.module.event.ModulesUpdatedEvent;
import org.scijava.platform.AbstractPlatform;
import org.scijava.platform.Platform;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Plugin;

/**
 * A platform implementation for handling Apple macOS platform issues:
 * <ul>
 * <li>Application events are rebroadcast as ImageJ events.</li>
 * <li>macOS screen menu bar is enabled.</li>
 * <li>Special screen menu bar menu items are handled.</li>
 * </ul>
 * 
 * @author Curtis Rueden
 */
@Plugin(type = Platform.class, name = "macOS")
public class MacOSPlatform extends AbstractPlatform {

	/** Debugging flag to allow easy toggling of Mac screen menu bar behavior. */
	private static final boolean SCREEN_MENU = true;

	@SuppressWarnings("unused")
	private Object appEventDispatcher;

	private JMenuBar menuBar;

	private List<EventSubscriber<?>> subscribers;

	// -- Platform methods --

	@Override
	public String osName() {
		// NB: The value of the os.name system property for activation purposes;
		// see org.scijava.platform.Platform#isTarget().
		return "Mac OS X";
	}

	@Override
	public void configure(final PlatformService service) {
		super.configure(service);

		// use macOS screen menu bar
		if (SCREEN_MENU) System.setProperty("apple.laf.useScreenMenuBar", "true");

		// remove app commands from menu structure
		if (SCREEN_MENU) removeAppCommandsFromMenu();

		// translate macOS application events into ImageJ events
		final EventService eventService = getPlatformService().eventService();
		try {
			appEventDispatcher = new MacOSAppEventDispatcher(eventService);
		}
		catch (final NoClassDefFoundError e) {
			// the interfaces implemented by MacOSAppEventDispatcher might not be
			// available:
			// - on MacOSX Tiger without recent Java Updates
			// - on earlier OS versions
		}

		// subscribe to relevant window-related events
		subscribers = eventService.subscribe(this);
	}

	@Override
	public void open(final URL url) throws IOException {
		if (getPlatformService().exec("open", url.toString()) != 0) {
			throw new IOException("Could not open " + url);
		}
	}

	@Override
	public boolean registerAppMenus(final Object menus) {
		if (SCREEN_MENU && menus instanceof JMenuBar) {
			menuBar = (JMenuBar) menus;
		}
		return false;
	}

	// -- Disposable methods --

	@Override
	public void dispose() {
		getPlatformService().eventService().unsubscribe(subscribers);
	}

	// -- Event handlers --

	@EventHandler
	protected void onEvent(final WinActivatedEvent evt) {
		if (!SCREEN_MENU || !isTarget()) return;

		final Object window = evt.getWindow();
		if (!(window instanceof JFrame)) return;

		// assign the singleton menu bar to newly activated window
		((JFrame) window).setJMenuBar(menuBar);
	}

	// -- Helper methods --

	private void removeAppCommandsFromMenu() {
		final PlatformService platformService = getPlatformService();
		final EventService eventService = platformService.eventService();
		final CommandService commandService = platformService.commandService();

		// NB: Search for commands being handled at the application level.
		// We remove such commands from the main menu bar;
		// the Mac application menu will trigger them instead.
		final ArrayList<ModuleInfo> infos = new ArrayList<>();
		for (final CommandInfo info : commandService.getCommands()) {
			if (info.is("app-command")) {
				info.setMenuPath(null);
				infos.add(info);
			}
		}
		eventService.publish(new ModulesUpdatedEvent(infos));
	}

}
