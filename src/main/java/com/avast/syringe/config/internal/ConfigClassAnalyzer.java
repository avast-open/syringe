package com.avast.syringe.config.internal;

import com.avast.syringe.config.PropertyValueConverter;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfigClassAnalyzer {

    private final Class<?> configClass;
    private final PropertyValueConverter converter;
    private final InjectablePropertyFactory factory;

    public ConfigClassAnalyzer(Class<?> configClass) {
        this(configClass, null);
    }

    public ConfigClassAnalyzer(Class<?> configClass, @Nullable PropertyValueConverter converter) {
        this.configClass = configClass;
        this.converter = converter;
        this.factory = new InjectablePropertyFactory(converter);
    }

    public static Method findAnnotatedMethod(Class<? extends Annotation> annotationClass, Class cls) {
        Method[] methods = cls.getMethods();
        for (Method method : methods) {
            Annotation annotation = method.getAnnotation(annotationClass);
            if (annotation != null) {
                return method;
            }
        }
        return null;
    }

    public Method findPostConstructMethod() {
        return findAnnotatedMethod(PostConstruct.class, configClass);
    }

    public Method findPreDestroyMethod() {
        return findAnnotatedMethod(PreDestroy.class, configClass);
    }

    public InjectableProperty getDelegateProperty() {
        List<InjectableProperty> configProperties = getConfigProperties();
        for (InjectableProperty configProperty : configProperties) {
            if (configProperty.isDelegate()) {
                return configProperty;
            }
        }
        return null;
    }

    /*
     * Strips the first decoration from the decorated object. If the object is not decorated the method
     * returns the object itself.
     */
    public static Object stripShallow(Object decorated) {
        try {
            InjectableProperty delegateProperty = new ConfigClassAnalyzer(decorated.getClass()).getDelegateProperty();
            if (delegateProperty == null) {
                return decorated;
            } else {
                return delegateProperty.getValue(decorated);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Strips all decorations from the decorated object.
     *
     * @param decorated the decorated object
     * @return the stripped object
     */
    public static Object stripDeep(Object decorated) {
        Object stripped = stripShallow(decorated);
        if (stripped == decorated) {
            return stripped;
        } else {
            return stripDeep(stripped);
        }
    }

    public List<InjectableProperty> getConfigProperties() {
        List<InjectableProperty> result = Lists.newArrayList();

        LinkedList<Class<?>> classes = Lists.newLinkedList();
        Class<?> each = configClass;
        while (each != null) {
            // Put superclasses (and injectable properties thereof) first
            // in order to maintain their logical order.
            // Don't care about clazz.getInterfaces() because fields in an interface can only be static,
            // and we don't handle static fields.
            classes.addFirst(each);
            each = each.getSuperclass();
        }

        for (Class<?> clazz : classes) {
            for (Field field : clazz.getDeclaredFields()) {

                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                Optional<InjectableProperty> maybeProperty = factory.newProperty(field);
                if (maybeProperty.isPresent()) {
                    result.add(maybeProperty.get());
                }
            }
        }
        return ImmutableList.copyOf(result);
    }

    public static Map<String, InjectableProperty> toMap(Class cls) {
        return toMap(new ConfigClassAnalyzer(cls).getConfigProperties());
    }

    public static Map<String, InjectableProperty> toMap(List<InjectableProperty> properties) {
        return Maps.uniqueIndex(properties, new Function<InjectableProperty, String>() {
            @Override
            public String apply(@Nullable InjectableProperty input) {
                return input.getName();
            }
        });
    }
}
