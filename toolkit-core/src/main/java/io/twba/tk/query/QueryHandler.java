package io.twba.tk.query;

import reactor.core.publisher.Flux;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface QueryHandler<Q extends DomainQuery<R>, R> {

    Flux<R> handle(Q query);

    @SuppressWarnings("unchecked")
    default String handles() {
        Class<?> clazz = getClass();
        ParameterizedType parameterizedType = (ParameterizedType) clazz.getGenericInterfaces()[0];
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        return ((Class<Q>) typeArguments[0]).getName();
    }
}
