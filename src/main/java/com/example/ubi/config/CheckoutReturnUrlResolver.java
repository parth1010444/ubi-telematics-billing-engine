package com.example.ubi.config;

import com.example.ubi.exception.BillingPrerequisiteException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds Stripe Checkout success/cancel URLs from the live dashboard origin
 * so payment return works on localhost, LAN, HTTPS tunnels, and known
 * hosted-frontend domains — not only the configured localhost fallback.
 */
@Component
public class CheckoutReturnUrlResolver {

    private static final Set<String> TUNNEL_SUFFIXES = Set.of(
            ".ngrok-free.app",
            ".ngrok.app",
            ".ngrok.io",
            ".trycloudflare.com",
            ".loca.lt"
    );

    /** Common hosted frontend platforms (HTTPS only). */
    private static final Set<String> HOSTED_FRONTEND_SUFFIXES = Set.of(
            ".vercel.app",
            ".netlify.app",
            ".onrender.com"
    );

    private final BillingProperties billingProperties;

    public CheckoutReturnUrlResolver(BillingProperties billingProperties) {
        this.billingProperties = billingProperties;
    }

    public String successUrl(String returnOrigin, String policyId) {
        return withQuery(
                resolveOrigin(returnOrigin) + "/",
                "billing=success&policyId=" + policyId + "&session_id={CHECKOUT_SESSION_ID}"
        );
    }

    public String cancelUrl(String returnOrigin, String policyId) {
        return withQuery(
                resolveOrigin(returnOrigin) + "/",
                "billing=cancelled&policyId=" + policyId
        );
    }

    String resolveOrigin(String returnOrigin) {
        if (!StringUtils.hasText(returnOrigin)) {
            return originFromConfiguredFallback();
        }
        return validateAndNormalize(returnOrigin.trim());
    }

    private String originFromConfiguredFallback() {
        try {
            return originOf(URI.create(billingProperties.checkoutSuccessUrl()));
        } catch (RuntimeException exception) {
            return "http://localhost:5173";
        }
    }

    private String validateAndNormalize(String rawOrigin) {
        URI uri;
        try {
            uri = URI.create(rawOrigin);
        } catch (IllegalArgumentException exception) {
            throw invalidOrigin(rawOrigin);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw invalidOrigin(rawOrigin);
        }
        if (uri.getUserInfo() != null || uri.getHost() == null) {
            throw invalidOrigin(rawOrigin);
        }

        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw invalidOrigin(rawOrigin);
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw invalidOrigin(rawOrigin);
        }

        String origin = originOf(uri);
        if (!isAllowed(uri, origin)) {
            throw new BillingPrerequisiteException(
                    "Checkout return origin is not allowed: "
                            + origin
                            + ". Use localhost, a private LAN address, an HTTPS tunnel "
                            + "(ngrok / Cloudflare), a hosted frontend (*.vercel.app / *.netlify.app), "
                            + "or add it to billing.allowed-return-origins / BILLING_ALLOWED_RETURN_ORIGINS."
            );
        }
        return origin;
    }

    private boolean isAllowed(URI uri, String origin) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

        if (isLoopback(host)) {
            return true;
        }
        if ("http".equals(scheme) && isPrivateIpv4(host)) {
            return true;
        }
        if ("https".equals(scheme) && isTunnelHost(host)) {
            return true;
        }
        if ("https".equals(scheme) && isHostedFrontend(host)) {
            return true;
        }

        return billingProperties.allowedReturnOriginList().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(origin));
    }

    private static boolean isLoopback(String host) {
        if ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host)) {
            return true;
        }
        boolean looksLikeIp = host.chars().allMatch(c -> Character.isDigit(c) || c == '.' || c == ':');
        if (!looksLikeIp) {
            return false;
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException exception) {
            return false;
        }
        int first = octets[0];
        int second = octets[1];
        return first == 10
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private static boolean isTunnelHost(String host) {
        return endsWithAny(host, TUNNEL_SUFFIXES);
    }

    private static boolean isHostedFrontend(String host) {
        return endsWithAny(host, HOSTED_FRONTEND_SUFFIXES);
    }

    private static boolean endsWithAny(String host, Set<String> suffixes) {
        for (String suffix : suffixes) {
            if (host.endsWith(suffix) && host.length() > suffix.length()) {
                return true;
            }
        }
        return false;
    }

    private static String originOf(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    private static String withQuery(String baseUrl, String extraQuery) {
        if (baseUrl.contains("?")) {
            return baseUrl + "&" + extraQuery;
        }
        return baseUrl + "?" + extraQuery;
    }

    private static BillingPrerequisiteException invalidOrigin(String rawOrigin) {
        return new BillingPrerequisiteException(
                "Invalid checkout return origin: " + rawOrigin
        );
    }
}
