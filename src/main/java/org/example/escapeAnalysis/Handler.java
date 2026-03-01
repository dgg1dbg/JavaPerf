package org.example.escapeAnalysis;

public class Handler {
    private final RequestContext reuseCtx = new RequestContext();

    public int handleWithReuse(int userId, int requestId) {
        fillContext(reuseCtx, userId, requestId);
        return process(reuseCtx);
    }

    public int handleWithEA(int userId, int requestId) {
        RequestContext tmpCtx = new RequestContext();
        fillContext(tmpCtx, userId, requestId);
        return process(tmpCtx);
    }

    public int handleWithDirectArgs(int userId, int requestId) {
        int regionId = userId & 1023;
        int tenantId = requestId & 2047;
        int deviceId = userId ^ requestId;
        int retryCount = requestId & 3;
        int traceId = (userId * 31) + requestId;
        int sessionId = (requestId * 17) + userId;
        int experimentId = (traceId >>> 2) ^ sessionId;
        int flags = retryCount | (regionId << 2);

        return process(
                userId,
                requestId,
                regionId,
                tenantId,
                deviceId,
                retryCount,
                traceId,
                sessionId,
                experimentId,
                flags
        );
    }

    private void fillContext(RequestContext context, int userId, int requestId) {
        context.userId = userId;
        context.requestId = requestId;
        context.regionId = userId & 1023;
        context.tenantId = requestId & 2047;
        context.deviceId = userId ^ requestId;
        context.retryCount = requestId & 3;
        context.traceId = (userId * 31) + requestId;
        context.sessionId = (requestId * 17) + userId;
        context.experimentId = (context.traceId >>> 2) ^ context.sessionId;
        context.flags = context.retryCount | (context.regionId << 2);
    }

    private int process(RequestContext context) {
        return process(
                context.userId,
                context.requestId,
                context.regionId,
                context.tenantId,
                context.deviceId,
                context.retryCount,
                context.traceId,
                context.sessionId,
                context.experimentId,
                context.flags
        );
    }

    private int process(
            int userId,
            int requestId,
            int regionId,
            int tenantId,
            int deviceId,
            int retryCount,
            int traceId,
            int sessionId,
            int experimentId,
            int flags
    ) {
        int mixed = userId;
        mixed = (mixed * 31) + requestId;
        mixed = (mixed * 31) + regionId;
        mixed = (mixed * 31) + tenantId;
        mixed = (mixed * 31) + deviceId;
        mixed = (mixed * 31) + retryCount;
        mixed = (mixed * 31) + traceId;
        mixed = (mixed * 31) + sessionId;
        mixed = (mixed * 31) + experimentId;
        mixed = (mixed * 31) + flags;
        return mixed ^ (mixed >>> 16);
    }
}
