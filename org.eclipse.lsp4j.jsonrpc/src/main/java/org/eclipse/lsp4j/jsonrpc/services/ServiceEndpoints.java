/*******************************************************************************
 * Copyright (c) 2016 TypeFox GmbH (http://www.typefox.io) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4j.jsonrpc.services;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;

public final class ServiceEndpoints {
	private ServiceEndpoints() {}
	
	/**
	 * Wraps a given {@link Endpoint} in the given service interface.
	 * 
	 * @return the wrapped service object
	 */
	@SuppressWarnings("unchecked")
	public static <T> T toServiceObject(Endpoint endpoint, Class<T> interface_) {
		Class<?>[] interfArray = new Class[]{interface_, Endpoint.class};
		EndpointProxy invocationHandler = new EndpointProxy(endpoint, interface_);
		return (T) Proxy.newProxyInstance(interface_.getClassLoader(), interfArray, invocationHandler);
	}
	
	/**
	 * Wraps a given {@link Endpoint} in the given service interfaces.
	 * 
	 * @return the wrapped service object
	 */
	public static Object toServiceObject(Endpoint endpoint, Collection<Class<?>> interfaces, ClassLoader classLoader) {
		Class<?>[] interfArray = new Class[interfaces.size() + 1];
		interfaces.toArray(interfArray);
		interfArray[interfArray.length - 1] = Endpoint.class;
		EndpointProxy invocationHandler = new EndpointProxy(endpoint, interfaces);
		return Proxy.newProxyInstance(classLoader, interfArray, invocationHandler);
	}
	
	/**
	 * Wraps a given object with service annotations behind an {@link Endpoint} interface.
	 * 
	 * @return the wrapped service endpoint
	 */
	public static Endpoint toEndpoint(Object serviceObject) {
		return new GenericEndpoint(serviceObject);
	}
	
	/**
	 * Wraps a collection of objects with service annotations behind an {@link Endpoint} interface.
	 * 
	 * @return the wrapped service endpoint
	 */
	public static Endpoint toEndpoint(Collection<Object> serviceObjects) {
		return new GenericEndpoint(serviceObjects);
	}
	
	/**
	 * Finds all Json RPC methods on a given class.
	 * 
	 * @return the supported JsonRpcMethods
	 */
	public static Map<String, JsonRpcMethod> getSupportedMethods(Class<?> type) {
		Set<Class<?>> visitedTypes = new HashSet<>();
		return getSupportedMethods(type, visitedTypes);
	}
	
	/**
	 * Finds all Json RPC methods on a given type
	 */
	private static Map<String, JsonRpcMethod> getSupportedMethods(Class<?> type, Set<Class<?>> visitedTypes) {
		Map<String, JsonRpcMethod> result = new LinkedHashMap<String, JsonRpcMethod>();
		AnnotationUtil.findRpcMethods(type, visitedTypes, (methodInfo) -> {
			JsonRpcMethod meth;
			if (methodInfo.isNotification) {
				meth = JsonRpcMethod.notification(methodInfo.name, methodInfo.parameterTypes);
			} else {
				Type returnType = methodInfo.method.getGenericReturnType();
				if (returnType instanceof ParameterizedType) {
					ParameterizedType rType = (ParameterizedType) returnType;
					meth = JsonRpcMethod.request(methodInfo.name, rType.getActualTypeArguments()[0], methodInfo.parameterTypes);
				} else {
					throw new IllegalStateException("Expecting return type of CompletableFuture but was : "+returnType);
				}
			}
			if (result.put(methodInfo.name, meth) != null) {
				throw new IllegalStateException("Duplicate RPC method "+methodInfo.name+".");
			};
		});
		
		AnnotationUtil.findDelegateSegments(type, new HashSet<>(), (method)-> {
			Map<String, JsonRpcMethod> supportedDelegateMethods = getSupportedMethods(method.getReturnType(), visitedTypes);
			for (JsonRpcMethod meth : supportedDelegateMethods.values()) {
				if (result.put(meth.getMethodName(), meth) != null) {
					throw new IllegalStateException("Duplicate RPC method "+meth.getMethodName()+".");
				};
			}
		});
		return result;
	}
	
}
