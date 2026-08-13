package me.alpha432.oxevy.event.system;

import me.alpha432.oxevy.event.Event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optimized event bus with reverse mapping for fast unregistration.
 * Reduces unregister complexity from O(n*m) to O(m) where:
 * - n = number of event types
 * - m = number of listeners for a specific host
 *
 * @author Oxevy Team
 * @version 2.0
 */
public class EventBus {
    // Event type -> List of listeners
    private final Map<Class<?>, CopyOnWriteArrayList<Listener>> listeners = new ConcurrentHashMap<>();
    
    // Host -> List of registered listeners (reverse mapping for fast unregistration)
    private final Map<Object, List<Listener>> hostListeners = new ConcurrentHashMap<>();

    public void register(Object host) {
        register(host, host.getClass());
    }

    /**
     * Optimized unregister using reverse mapping.
     * Instead of scanning all listeners, we directly access the host's listener list.
     */
    public void unregister(Object host) {
        List<Listener> hostListenerList = hostListeners.remove(host);
        if (hostListenerList == null) return;

        // Remove each listener from its event type list
        for (Listener listener : hostListenerList) {
            Class<?> eventType = listener.getEventType();
            CopyOnWriteArrayList<Listener> eventListeners = listeners.get(eventType);
            if (eventListeners != null) {
                eventListeners.remove(listener);
            }
        }
    }

    public boolean post(Event event) {
        List<Listener> list = listeners.get(event.getClass());
        if (list == null) return false;

        for (Listener listener : list) {
            if (event.isCancelled()) {
                return true;
            }
            listener.invoke(event);
        }

        return event.isCancelled();
    }

    private void register(Object host, Class<?> klass) {
        for (Method method : klass.getDeclaredMethods()) {
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            if (subscribe == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1) {
                Class<?> eventType = params[0];

                Listener listener = Listener.of(host, subscribe.priority(), method, eventType);
                List<Listener> registry = listeners.computeIfAbsent(eventType,
                        k -> new CopyOnWriteArrayList<>());

                register(registry, listener);
                
                // Add to reverse mapping
                hostListeners.computeIfAbsent(host, k -> new ArrayList<>()).add(listener);
            }
        }

        if (klass.getSuperclass() != null)
            register(host, klass.getSuperclass());
    }

    private void register(List<Listener> registry, Listener target) {
        int i = 0;
        for (Listener listener : registry) {
            i++;
            if (target.priority() > listener.priority()) break;
        }
        registry.add(i, target);
    }

    /**
     * Get statistics for debugging.
     */
    public String getStats() {
        int totalListeners = listeners.values().stream().mapToInt(List::size).sum();
        return String.format("EventBus: %d event types, %d total listeners, %d registered hosts",
            listeners.size(), totalListeners, hostListeners.size());
    }
}