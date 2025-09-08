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

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for working with {@link URI} objects.
 *
 * @author Curtis Rueden, Marwan Zouinkhi
 */
public final class FijiURILink {

    public static final String FIJI_SCHEME = "fiji";

    private final String plugin; // e.g., "BDV"
    private final String subPlugin; // e.g., "open" (nullable)
    private final String query; // e.g., "a=1&b=2" (nullable)
    private final String rawQuery; // e.g., "a=1&b=2" (nullable)

    private FijiURILink(String plugin, String subPlugin, String query, String rawQuery) {
        this.plugin = plugin;
        this.subPlugin = subPlugin;
        this.query = query;
        this.rawQuery = rawQuery;
    }

    public static FijiURILink parse(String uriString) {
        try {
            URI uri = URI.create(uriString);
            return parse(uri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URI: " + uriString, e);
        }
    }

    public static FijiURILink parse(URI uri) {

        if (!"fiji".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Scheme must be fiji://");
        }
        // For opaque vs hierarchical handling: ensure it's hierarchical (has //)
        String authority = uri.getAuthority(); // first segment after //
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("Missing plugin name after fiji://");
        }
        String plugin = authority;

        String path = uri.getPath(); // includes leading '/'
        String sub = null;
        if (path != null && !path.isEmpty()) {
            // normalize: "/open" -> "open"; "/" -> null
            String trimmed = path.startsWith("/") ? path.substring(1) : path;
            sub = trimmed.isEmpty() ? null : trimmed;
        }

        // Raw query (no '?'), leave as-is; users can parse if they want.
        String q = uri.getQuery();
        // Optional: decode percent-escapes (uncomment if desired)
        // q = (q == null) ? null : java.net.URLDecoder.decode(q,
        // StandardCharsets.UTF_8);
        String raw = uri.getRawQuery();
        return new FijiURILink(plugin, sub, q, raw);
    }

    public String getPlugin() {
        return plugin;
    }

    public String getSubPlugin() {
        return subPlugin;
    } // may be null

    public String getQuery() {
        return query;
    } // may be null

    public String getRawQuery() {
        return rawQuery;
    } // may be null

    public Map<String, String> getParsedQuery() {
        final LinkedHashMap<String, String> map = new LinkedHashMap<>();
        final String[] tokens = query == null ? new String[0] : query.split("&");
        for (final String token : tokens) {
            final String[] kv = token.split("=", 2);
            final String k = kv[0];
            final String v = kv.length > 1 ? kv[1] : null;
            map.put(k, v);
        }
        return map;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("fiji://").append(plugin);
        if (subPlugin != null)
            sb.append('/').append(subPlugin);
        if (query != null)
            sb.append('?').append(query);
        return sb.toString();
    }
}
