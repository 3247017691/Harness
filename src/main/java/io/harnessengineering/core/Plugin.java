package io.harnessengineering.core;

import java.util.List;

/** A composable runtime contribution with explicit service dependencies. */
public interface Plugin {
    /** @return a stable diagnostic name */
    String name();

    /** @return services that must be available before this plugin starts */
    List<ServiceKey<?>> requires();

    /**
     * Activates the plugin and returns its cleanup effect.
     *
     * @param context shared runtime context
     * @return plugin cleanup effect
     * @throws Exception when activation cannot complete
     */
    Effect apply(Context context) throws Exception;
}
