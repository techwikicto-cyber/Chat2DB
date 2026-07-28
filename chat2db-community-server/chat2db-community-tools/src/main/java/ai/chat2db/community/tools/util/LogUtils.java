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
     * A JSON field: the name and colon, then either a quoted string (escapes
     * allowed) or a bare scalar.
     */
    private static final Pattern JSON_FIELD = Pattern.compile(
        "(\"([A-Za-z0-9_.\\-]{1,64})\"\\s*:\\s*)(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\]\\s]+)");

    /**
     * A form-encoded or query-string parameter.
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

    private static String redactJsonFields(String input) {
        Matcher matcher = JSON_FIELD.matcher(input);
        StringBuilder output = new StringBuilder(input.length());
        while (matcher.find()) {
            String replacement = isSensitiveField(matcher.group(2)) && !isEmptyJsonValue(matcher.group(3))
                ? matcher.group(1) + '"' + REDACTED + '"'
                : matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
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

    private static boolean isEmptyJsonValue(String value) {
        return "\"\"".equals(value) || "null".equals(value);
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
