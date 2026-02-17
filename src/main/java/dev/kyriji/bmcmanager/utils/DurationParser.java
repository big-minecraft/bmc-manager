package dev.kyriji.bmcmanager.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses shorthand duration strings into milliseconds.
 *
 * Supported format: any combination of days (d), hours (h), minutes (m), and seconds (s),
 * separated by optional whitespace. Each component is optional but at least one must be present.
 *
 * Examples:
 *   "1d 5h 32m 15s" → 104,535,000 ms
 *   "30m"            → 1,800,000 ms
 *   "2h 30m"         → 9,000,000 ms
 */
public class DurationParser {

	private static final Pattern COMPONENT = Pattern.compile(
			"(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);

	private DurationParser() {
	}

	/**
	 * Parse a shorthand duration string into milliseconds.
	 *
	 * @param input the duration string, e.g. "1d 5h 32m 15s"
	 * @return duration in milliseconds
	 * @throws IllegalArgumentException if the string is null, blank, or contains no recognised components
	 */
	public static long parseToMillis(String input) {
		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("Duration string must not be null or blank");
		}

		Matcher matcher = COMPONENT.matcher(input);
		long totalMillis = 0;
		boolean matched = false;

		while (matcher.find()) {
			matched = true;
			long value = Long.parseLong(matcher.group(1));
			char unit = Character.toLowerCase(matcher.group(2).charAt(0));

			totalMillis += switch (unit) {
				case 'd' -> value * 86_400_000L;
				case 'h' -> value * 3_600_000L;
				case 'm' -> value * 60_000L;
				case 's' -> value * 1_000L;
				default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
			};
		}

		if (!matched) {
			throw new IllegalArgumentException("No valid duration components found in: \"" + input + "\"");
		}

		return totalMillis;
	}
}
