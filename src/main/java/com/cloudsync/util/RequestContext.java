package com.cloudsync.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestContext {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private Long userId;
    private Long organizationId;
    private String ipAddress;
    private String userAgent;
    private String requestPath;
    private String requestMethod;

    public static RequestContext getCurrent() {
        return CONTEXT.get();
    }

    public static void setCurrent(RequestContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static RequestContext fromHttpRequest(HttpServletRequest request, Long userId, Long organizationId) {
        RequestContext ctx = new RequestContext();
        ctx.setUserId(userId);
        ctx.setOrganizationId(organizationId);
        ctx.setIpAddress(getClientIpAddress(request));
        ctx.setUserAgent(request.getHeader("User-Agent"));
        ctx.setRequestPath(request.getRequestURI());
        ctx.setRequestMethod(request.getMethod());
        return ctx;
    }

    public static String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
