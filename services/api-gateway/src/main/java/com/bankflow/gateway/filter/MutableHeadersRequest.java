package com.bankflow.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Requête dont les en-têtes transmis aux services peuvent être remplacés ou retirés. */
final class MutableHeadersRequest extends HttpServletRequestWrapper {

    private final Map<String, String> replaced = new ConcurrentHashMap<>();
    private final Set<String> removed = ConcurrentHashMap.newKeySet();

    MutableHeadersRequest(HttpServletRequest request) {
        super(request);
    }

    void set(String name, String value) {
        var key = normalizeHeaderName(name);
        removed.remove(key);
        replaced.put(key, value);
    }

    void remove(String name) {
        var key = normalizeHeaderName(name);
        replaced.remove(key);
        removed.add(key);
    }

    @Override
    public String getHeader(String name) {
        var key = normalizeHeaderName(name);
        if (removed.contains(key)) {
            return null;
        }
        return replaced.containsKey(key) ? replaced.get(key) : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        var key = normalizeHeaderName(name);
        if (removed.contains(key)) {
            return Collections.emptyEnumeration();
        }
        return replaced.containsKey(key)
                ? Collections.enumeration(List.of(replaced.get(key)))
                : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        var names = new LinkedHashSet<String>();
        for (var original : Collections.list(super.getHeaderNames())) {
            if (!removed.contains(normalizeHeaderName(original))
                    && !replaced.containsKey(normalizeHeaderName(original))) {
                names.add(original);
            }
        }
        names.addAll(replaced.keySet());
        return Collections.enumeration(names);
    }

    private static String normalizeHeaderName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
