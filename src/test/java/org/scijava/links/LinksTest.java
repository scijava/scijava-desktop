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

package org.scijava.links;

import org.junit.Test;


import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;


public class LinksTest {

        @Test
        public void parsesPluginSubAndQuery() {
            FijiURILink link = FijiURILink.parse("fiji://BDV/open?source=s3&bucket=data");
            assertEquals("BDV", link.getPlugin());
            assertEquals("open", link.getSubPlugin());
            assertEquals("source=s3&bucket=data", link.getQuery());
            assertEquals("source=s3&bucket=data", link.getRawQuery()); // identical here
        }

        @Test
        public void parsesPluginOnly() {
            FijiURILink link = FijiURILink.parse("fiji://BDV");
            assertEquals("BDV", link.getPlugin());
            assertNull(link.getSubPlugin());
            assertNull(link.getQuery());
            assertNull(link.getRawQuery());
        }

        @Test
        public void parsesPluginAndEmptyPathSlash() {
            FijiURILink link = FijiURILink.parse("fiji://BDV/?q=hello");
            assertEquals("BDV", link.getPlugin());
            assertNull(link.getSubPlugin());             // "/"" becomes no subplugin
            assertEquals("q=hello", link.getQuery());
            assertEquals("q=hello", link.getRawQuery());
        }

        @Test
        public void percentEncodedQuery_isPreservedInRawQuery() {
            String u = "fiji://bdv?file=%2Ftmp%2Fdata.xml&flag";
            FijiURILink link = FijiURILink.parse(u);
            assertEquals("bdv", link.getPlugin());
            assertNull(link.getSubPlugin());

            // getQuery() returns decoded or not? Your class uses uri.getQuery() (decoded)
            // and uri.getRawQuery() (raw). JDK behavior: getQuery() is decoded.
            assertEquals("file=/tmp/data.xml&flag", link.getQuery());
            assertEquals("file=%2Ftmp%2Fdata.xml&flag", link.getRawQuery());
        }

        @Test
        public void parsedQueryToMap_handlesMissingValues() {
            FijiURILink link = FijiURILink.parse("fiji://BDV/open?a=1&b=2&flag");
            Map<String, String> map = link.getParsedQuery();
            assertEquals(3, map.size());
            assertEquals("1", map.get("a"));
            assertEquals("2", map.get("b"));
            assertNull(map.get("flag")); // key present with no value
        }

        @Test
        public void toString_roundTrips_reasonably() {
            String u = "fiji://BDV/open?x=1&y=2";
            FijiURILink link = FijiURILink.parse(u);
            assertEquals(u, link.toString());
        }
    

        @Test
        public void rejectsWrongScheme() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FijiURILink.parse("http://BDV/open?x=1"));
            assertTrue(ex.getMessage().contains("Scheme must be fiji://"));
        }

        @Test
        public void rejectsMissingPlugin() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FijiURILink.parse("fiji:///open?x=1"));
            assertTrue(ex.getMessage().contains("Missing plugin name"));
        }

        @Test
        public void rejectsInvalidUriSyntax() {
            assertThrows(IllegalArgumentException.class,
                    () -> FijiURILink.parse("fiji://BDV/open?bad|query"));
        }
    


        @Test
        public void returnsNullOnError() {
            assertNull(FijiURILink.tryParse("not-a-uri"));
            assertNull(FijiURILink.tryParse("http://BDV")); // wrong scheme
            assertNull(FijiURILink.tryParse("fiji:///"));
        }

        @Test
        public void returnsObjectOnSuccess() {
            FijiURILink ok = FijiURILink.tryParse("fiji://BDV/open?q=ok");
            assertNotNull(ok);
            assertEquals("BDV", ok.getPlugin());
            assertEquals("open", ok.getSubPlugin());
            assertEquals("q=ok", ok.getQuery());
        }
    }

