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

package org.scijava.plugins.platforms.windows;

import java.io.IOException;
import java.net.URL;

import org.scijava.platform.AbstractPlatform;
import org.scijava.platform.Platform;
import org.scijava.plugin.Plugin;

/**
 * A platform implementation for handling Windows platform issues.
 * 
 * @author Johannes Schindelin
 */
@Plugin(type = Platform.class, name = "Windows")
public class WindowsPlatform extends AbstractPlatform {

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

}
