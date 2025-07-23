package com.dev.gateway.utils;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public class PolicyFactoryUtils {

    private static final PolicyFactory policy = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.IMAGES);

}
