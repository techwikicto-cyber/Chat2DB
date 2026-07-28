package ai.chat2db.community.web.api.config.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

import ai.chat2db.community.tools.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.thymeleaf.util.ContentTypeUtils;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;


@Slf4j
@Component
public class EasyLogSink implements Sink {

    @Override
    public void write(final Precorrelation precorrelation, final HttpRequest request) {
    }

    @Override
    public void write(final Correlation correlation, final HttpRequest request, final HttpResponse response) {
        try {
            printLog(correlation, request, response);
        } catch (Throwable e) { // impl-contract: writing a log line must never affect the response.
            // Throwable, not Exception. This sink runs while the response body
            // is being handed back, so anything that escapes it truncates the
            // response the client is reading - which is what a StackOverflowError
            // in the redaction pass did: the browser got
            // ERR_INCOMPLETE_CHUNKED_ENCODING and the page reported that it
            // could not load. A failure to log is worth a line in the log and
            // nothing more.
            log.error("Failed to record web log", e);
        }
    }

    public void printLog(final Correlation correlation, final HttpRequest request, final HttpResponse response)
        throws IOException {
        WebLog webLog = new WebLog();

        String method = request.getMethod();
        String path = request.getPath();

        webLog.setMethod(method);
        webLog.setPath(LogUtils.cutLog(path));
        // The query goes through the same redaction as the bodies. It was the one
        // place a credential could reach the log untouched, back when redaction
        // was a blanket smudge that only bodies received.
        webLog.setQuery(LogUtils.maskString(LogUtils.cutLog(request.getQuery())));
        webLog.setDuration(correlation.getDuration().toMillis());
        webLog.setStartTime(LocalDateTime.ofInstant(correlation.getStart(), ZoneId.systemDefault()));
        webLog.setEndTime(LocalDateTime.ofInstant(correlation.getEnd(), ZoneId.systemDefault()));
        try {
            webLog.setRequest(LogUtils.maskString(LogUtils.cutLog(new String(request.getBody(), StandardCharsets.UTF_8))));
            if (ContentTypeUtils.isContentTypeJSON(response.getContentType()) || ContentTypeUtils.isContentTypeHTML(
                response.getContentType())) {
                webLog.setResponse(LogUtils.maskString(LogUtils.cutLog(new String(response.getBody(), StandardCharsets.UTF_8))));
            } else {
                webLog.setResponse(response.getContentType() + ":[" + response.getBody().length + "]");
            }
        } catch (IOException e) {
            log.warn("Failed to read log request or response body, probably because the client closed the stream", e);
        }
        webLog.setIp(LogUtils.getClientIp(request));

        String pathAndQuery = path;
        if (StringUtils.isNotBlank(webLog.getQuery())) {
            pathAndQuery += "?" + webLog.getQuery();
        }
        log.info("http : {}|{}|{}|{}|{}", webLog.getMethod(), pathAndQuery, webLog.getDuration(),
            webLog.getRequest(), webLog.getResponse());
    }

}
