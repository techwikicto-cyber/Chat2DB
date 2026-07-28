package ai.chat2db.community.tools.util;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.net.NetUtil;
import org.apache.commons.lang3.StringUtils;
import org.zalando.logbook.HttpHeaders;
import org.zalando.logbook.HttpRequest;


public class LogUtils {

    private static final ThreadLocal<String> TRACE_ID_THREAD_LOCAL = new ThreadLocal<>();


    private static final String[] CLIENT_IP_HEADERS = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
        "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};


    public static final int MAX_LOG_LENGTH = 20000;

    public static final String TRACE_ID = "TRACE_ID";

    public static final String CLIENT_IP = "CLIENT_IP";

    public static final String USER_ID = "USER_ID";

    public static final String TOKEN = "TOKEN";

    public static final String TRACE_ID_HEADER = "X-Chat2DB-Trace-Id";


    private static final Pattern LINE_FEED_PATTERN = Pattern.compile("\r|\n");


    private static final String REDACTED = "***";

    /**
     * Field names whose value is a credential. Matched whole, case-insensitively.
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "authorization", "cookie", "credential", "credentials", "passphrase", "passwd", "password", "pwd",
        "secret", "token");

    /**
     * Endings that make a field name a credential whatever it is prefixed with,
     * so {@code encryptedPassword} and {@code openAiApiKey} are covered without
     * being listed. Deliberately narrow: {@code authenticationType} names how to
     * authenticate rather than the secret itself, and is worth keeping legible.
     */
    private static final String[] SENSITIVE_FIELD_SUFFIXES = {
        "accesskey", "apikey", "credential", "passphrase", "passwd", "password", "privatekey", "pwd",
        "secret", "secretkey", "token"};

    /**
     * A form-encoded or query-string parameter.
     *
     * <p>Both quantifiers are over character classes, which the regex engine
     * walks iteratively. That matters - see {@link #redactJsonFields(String)}.
     */
    private static final Pattern PARAMETER = Pattern.compile("([A-Za-z0-9_.\\-]{1,64})=([^&\\s]*)");

    /**
     * Hide the credentials in a request or response body, and nothing else.
     *
     * <p>What this replaced overwrote every fourth character of the whole body,
     * which scattered asterisks through the part of the log people actually read
     * - the SQL, the error, the field names - while leaving three quarters of
     * each password in place. This keeps the body legible and takes the secrets
     * out completely: a field whose name says it holds a credential loses its
     * value, and everything else is left exactly as it was.
     *
     * <p>Both JSON bodies and form-encoded ones are covered. An empty value is
     * left alone: rewriting it would suggest a secret is there when none is.
     *
     * @param input the body to redact, possibly null or blank.
     * @return the body with credential values replaced.
     */
    public static String maskString(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        return redactParameters(redactJsonFields(input));
    }

    /**
     * Replace the value of every JSON field whose name says it holds a credential.
     *
     * <p>Scanned by hand rather than matched with a regex, and that is the whole
     * point of the method. The obvious pattern for a JSON string value -
     * {@code "(?:\\.|[^"\\])*"} - puts a quantifier around a group, and the JDK
     * engine recurses once per repetition for those. A chat message of a few
     * thousand characters was enough to overflow the stack, and because this
     * runs while the response body is being logged, the error escaped and left
     * the client with a truncated response. This walks the string once, and the
     * only memory it uses is the copy it is building.
     */
    private static String redactJsonFields(String input) {
        int length = input.length();
        // Built only if something actually needs hiding: most bodies have nothing.
        StringBuilder output = null;
        int copiedUpTo = 0;
        int index = 0;

        while (index < length) {
            if (input.charAt(index) != '"') {
                index++;
                continue;
            }
            int nameEnd = endOfJsonString(input, index);
            if (nameEnd < 0) {
                break;
            }
            int afterName = skipWhitespace(input, nameEnd + 1);
            if (afterName >= length || input.charAt(afterName) != ':') {
                // A string that is not a field name - a value in an array, say.
                index = nameEnd + 1;
                continue;
            }

            int valueStart = skipWhitespace(input, afterName + 1);
            int valueEnd = endOfJsonValue(input, valueStart);
            if (valueEnd < 0) {
                break;
            }
            if (isSensitiveField(input.substring(index + 1, nameEnd))
                    && !isEmptyJsonValue(input, valueStart, valueEnd)) {
                if (output == null) {
                    output = new StringBuilder(length);
                }
                output.append(input, copiedUpTo, valueStart).append('"').append(REDACTED).append('"');
                copiedUpTo = valueEnd;
            }
            // Past the value either way, so nothing inside a value is ever read
            // as a field name.
            index = valueEnd;
        }

        return output == null ? input : output.append(input, copiedUpTo, length).toString();
    }

    /**
     * @param input the text being scanned.
     * @param quoteIndex index of the opening quote.
     * @return index of the closing quote, or -1 if the string never ends - which
     *         happens when the body was cut short before being logged.
     */
    private static int endOfJsonString(String input, int quoteIndex) {
        for (int index = quoteIndex + 1; index < input.length(); index++) {
            char character = input.charAt(index);
            if (character == '\\') {
                index++;
            } else if (character == '"') {
                return index;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String input, int from) {
        int index = from;
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * @return the index one past the end of the value, or -1 if it never ends.
     */
    private static int endOfJsonValue(String input, int valueStart) {
        if (valueStart >= input.length()) {
            return -1;
        }
        if (input.charAt(valueStart) == '"') {
            int end = endOfJsonString(input, valueStart);
            return end < 0 ? -1 : end + 1;
        }
        int index = valueStart;
        while (index < input.length()) {
            char character = input.charAt(index);
            if (character == ',' || character == '}' || character == ']' || Character.isWhitespace(character)) {
                break;
            }
            index++;
        }
        return index == valueStart ? -1 : index;
    }

    private static String redactParameters(String input) {
        Matcher matcher = PARAMETER.matcher(input);
        StringBuilder output = new StringBuilder(input.length());
        while (matcher.find()) {
            String replacement = isSensitiveField(matcher.group(1)) && StringUtils.isNotEmpty(matcher.group(2))
                ? matcher.group(1) + '=' + REDACTED
                : matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        if (SENSITIVE_FIELDS.contains(normalized)) {
            return true;
        }
        for (String suffix : SENSITIVE_FIELD_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmptyJsonValue(String input, int valueStart, int valueEnd) {
        int length = valueEnd - valueStart;
        if (length == 2) {
            return input.charAt(valueStart) == '"' && input.charAt(valueStart + 1) == '"';
        }
        return length == 4 && input.startsWith("null", valueStart);
    }


    public static String removeCrlf(String log) {
        if (Objects.isNull(log)) {
            return null;
        }
        return LINE_FEED_PATTERN.matcher(log).replaceAll("");
    }


    public static String cutLog(Object log) {
        if (Objects.isNull(log)) {
            return null;
        }
        return EasyStringUtils.limitString(removeCrlf(log.toString()), MAX_LOG_LENGTH);
    }


    public static String generateTraceId() {
        String traceId = UUID.fastUUID().toString().replaceAll("-", "");
        TRACE_ID_THREAD_LOCAL.set(traceId);
        return traceId;
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_THREAD_LOCAL.set(traceId);
    }


    public static String getTraceId() {
        return TRACE_ID_THREAD_LOCAL.get();
    }


    public static void removeTraceId() {
        TRACE_ID_THREAD_LOCAL.remove();
    }


    public static String getClientIp(HttpRequest request) {
        HttpHeaders httpHeaders = request.getHeaders();
        String ip;
        for (String header : CLIENT_IP_HEADERS) {
            ip = httpHeaders.getFirst(header);
            if (!NetUtil.isUnknown(ip)) {
                return NetUtil.getMultistageReverseProxyIp(ip);
            }
        }
        ip = request.getRemote();
        return NetUtil.getMultistageReverseProxyIp(ip);
    }
}
