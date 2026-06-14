package io.twba.tk.cdc;

import io.twba.tk.core.RoutingKey;
import io.twba.tk.event.EventDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-based {@link EventRegistry}. At construction it scans a configurable base
 * package for classes annotated with {@link EventDefinition} and builds a
 * {@code type -> class} index.
 *
 * <p>The toolkit publisher emits the event type as the lowercased fully-qualified class
 * name (see {@code Event.eventType()} / {@link RoutingKey}). This registry normalizes both
 * the discovered keys and lookup arguments through {@link RoutingKey} so resolution is
 * consistent with what is published, regardless of the original class-name casing.
 */
public class EventRegistryReflection implements EventRegistry {

    private static final Logger log = LoggerFactory.getLogger(EventRegistryReflection.class);

    private final Map<String, Class<?>> typeToClass = new HashMap<>();

    public EventRegistryReflection(String basePackage) {
        if (basePackage == null || basePackage.isBlank()) {
            throw new IllegalArgumentException("basePackage must not be null or blank");
        }
        scan(basePackage);
        log.info("EventRegistryReflection scanned package '{}' and registered {} event type(s)", basePackage, typeToClass.size());
    }

    private void scan(String basePackage) {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(EventDefinition.class));
        for (BeanDefinition candidate : provider.findCandidateComponents(basePackage)) {
            String className = candidate.getBeanClassName();
            try {
                Class<?> clazz = Class.forName(className);
                EventDefinition definition = clazz.getAnnotation(EventDefinition.class);
                String declaredType = (definition != null && !definition.type().isBlank())
                        ? definition.type()
                        : className;
                String key = normalize(declaredType);
                Class<?> previous = typeToClass.put(key, clazz);
                if (previous != null && !previous.equals(clazz)) {
                    throw new IllegalStateException("Duplicate @EventDefinition type '" + key + "' for "
                            + previous.getName() + " and " + clazz.getName());
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot load @EventDefinition class " + className, e);
            }
        }
    }

    @Override
    public Optional<Class<?>> classFor(String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(typeToClass.get(normalize(type)));
    }

    @Override
    public int size() {
        return typeToClass.size();
    }

    private static String normalize(String type) {
        return RoutingKey.from(type).toString();
    }
}
