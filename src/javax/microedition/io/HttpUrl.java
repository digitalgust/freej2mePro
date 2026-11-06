/* SPDX-License-Identifier: MIT */
package javax.microedition.io;

import java.io.IOException;

/** Small URI parser used by the MIDP HTTP protocol implementation. */
public class HttpUrl {
    public String scheme;
    public String authority;
    public String path;
    public String query;
    public String fragment;
    public String host;
    public int port = -1;
    public String machine;
    public String domain;

    public HttpUrl(String url) {
        int colon = url == null ? -1 : url.indexOf(':');
        if (colon >= 0 && (colon == url.length() - 1 || url.startsWith("//", colon + 1))) {
            if (colon != url.length() - 1 && url.indexOf(' ') >= 0) {
                throw new IllegalArgumentException("Space character in URL");
            }
            parse(url.substring(colon + 1), url.substring(0, colon));
        } else {
            parse(url, null);
        }
    }

    public HttpUrl(String defaultScheme, String partialUrl) {
        parse(partialUrl, defaultScheme);
    }

    private void parse(String input, String defaultScheme) {
        scheme = defaultScheme;
        if (input == null || input.length() == 0) {
            return;
        }
        if (input.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Space character in URL");
        }
        int cursor = 0;

        int fragmentMark = input.indexOf('#', cursor);
        int end = fragmentMark < 0 ? input.length() : fragmentMark;
        if (fragmentMark >= 0 && fragmentMark + 1 < input.length()) {
            fragment = input.substring(fragmentMark + 1);
        }

        int queryMark = input.indexOf('?', cursor);
        if (queryMark >= 0 && queryMark < end) {
            if (queryMark + 1 < end) {
                query = input.substring(queryMark + 1, end);
            }
            end = queryMark;
        }

        if (cursor + 1 < end && input.charAt(cursor) == '/' &&
                input.charAt(cursor + 1) == '/') {
            int authorityStart = cursor + 2;
            int pathStart = input.indexOf('/', authorityStart);
            if (pathStart < 0 || pathStart > end) {
                pathStart = end;
            }
            if (authorityStart < pathStart) {
                authority = input.substring(authorityStart, pathStart);
                parseAuthority(authority);
            }
            cursor = pathStart;
        }
        if (cursor < end) {
            path = input.substring(cursor, end);
        }
    }

    private void parseAuthority(String value) {
        int colon = value.indexOf(':', Math.max(0, value.indexOf(']')));
        int hostEnd = colon < 0 ? value.length() : colon;
        if (hostEnd > 0) {
            host = value.substring(0, hostEnd);
        }
        // An empty port keeps the legacy unspecified-port value (-1).
        if (colon >= 0 && colon + 1 < value.length()) {
            try {
                port = Integer.parseInt(value.substring(colon + 1));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid port");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port out of range");
            }
        }
        if (host != null && host.charAt(0) != '[' && !Character.isDigit(host.charAt(0))) {
            int dot = host.indexOf('.');
            if (dot >= 0) {
                machine = host.substring(0, dot);
                domain = dot + 1 < host.length() ? host.substring(dot + 1) : "";
            } else {
                machine = host;
            }
        }
    }

    public void addBaseUrl(String baseUrl) throws IOException {
        addBaseUrl(new HttpUrl(baseUrl));
    }

    public void addBaseUrl(HttpUrl base) {
        if (authority != null) {
            return;
        }
        scheme = base.scheme;
        authority = base.authority;
        if (path == null) {
            path = base.path;
        } else if (!path.startsWith("/") && base.path != null && base.path.startsWith("/")) {
            path = base.path.substring(0, base.path.lastIndexOf('/') + 1) + path;
        }
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        if (scheme != null) result.append(scheme).append(':');
        if (authority != null) result.append("//").append(authority);
        if (path != null) result.append(path);
        if (query != null) result.append('?').append(query);
        if (fragment != null) result.append('#').append(fragment);
        return result.toString();
    }

}
