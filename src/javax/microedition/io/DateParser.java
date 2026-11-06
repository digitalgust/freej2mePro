/* SPDX-License-Identifier: MIT */
package javax.microedition.io;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compatibility wrapper for HTTP-date parsing. */
public class DateParser {
    private static final Pattern ZONE = Pattern.compile(
            "(?i)\\b(?:gmt|ut|utc|[ecmp][sd]t)\\b|(?:^|[\\s\\d])\\+\\d+|\\s-\\d+");
    private static final Pattern SHORT_YEAR = Pattern.compile(
            "(?i)(\\b\\d{1,2}[- ]+[a-z]{3,}[- ]+)(\\d{2})(?=\\s|$)");

    private DateParser() {
    }

    public static long parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        StringBuilder date = new StringBuilder(value.length());
        int commentDepth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                commentDepth++;
            } else if (c == ')' && commentDepth > 0) {
                commentDepth--;
            } else if (commentDepth == 0) {
                date.append(c);
            }
        }
        String text = date.toString();
        Matcher year = SHORT_YEAR.matcher(text);
        if (year.find()) {
            if (Integer.parseInt(year.group(2)) < 70) {
                throw new IllegalArgumentException();
            }
            int start = year.start(2);
            text = text.substring(0, start) + "19" + text.substring(start);
        }
        // The original MIDP parser used a one-minute east offset when no zone was present.
        return ZONE.matcher(text).find() ? Date.parse(text) : Date.parse(text + " GMT") - 60_000L;
    }

    static void setTimeZone(String ignored) {
        // Date.parse handles explicit zones and the default zone itself.
    }
}
