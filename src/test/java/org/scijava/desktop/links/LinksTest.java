/*-
 * #%L
 * URL scheme handlers for SciJava.
 * %%
 * Copyright (C) 2023 - 2025 SciJava developers.
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

package org.scijava.desktop.links;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests {@link Links}.
 *
 * @author Curtis Rueden
 */
public class LinksTest {

	private static final URI TEST_URI;

	static {
		try {
			TEST_URI = new URI(
				"scijava://user:pass@example.com:8080/op/sub/resource?" +
				"fruit=apple&veggie=beans#section"
			);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testPath() {
		var actual = Links.path(TEST_URI);
		assertEquals("op/sub/resource", actual);
	}

	@Test
	public void testOperation() {
		var actual = Links.operation(TEST_URI);
		assertEquals("op", actual);
	}

	@Test
	public void testPathFragments() {
		String[] expected = {"op", "sub", "resource"};
		var actual = Links.pathFragments(TEST_URI);
		assertArrayEquals(expected, actual);
	}

	@Test
	public void testSubPath() {
		var actual = Links.subPath(TEST_URI);
		assertEquals("sub/resource", actual);
	}

	@Test
	public void testQuery() {
		Map<String, String> expected = new HashMap<>();
		expected.put("fruit", "apple");
		expected.put("veggie", "beans");
		var actual = Links.query(TEST_URI);
		assertEquals(expected, actual);
	}
}
