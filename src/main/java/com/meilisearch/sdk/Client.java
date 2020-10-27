/*
 * Unofficial Java client for MeiliSearch
 */
package com.meilisearch.sdk;

import com.meilisearch.sdk.api.documents.DocumentHandler;
import com.meilisearch.sdk.api.index.IndexesHandler;
import com.meilisearch.sdk.http.AbstractHttpClient;
import com.meilisearch.sdk.http.ApacheHttpClient;
import com.meilisearch.sdk.http.CustomOkHttpClient;
import com.meilisearch.sdk.http.DefaultHttpClient;
import com.meilisearch.sdk.http.factory.BasicRequestFactory;
import com.meilisearch.sdk.http.factory.RequestFactory;
import com.meilisearch.sdk.json.GsonJsonHandler;
import com.meilisearch.sdk.json.JacksonJsonHandler;
import com.meilisearch.sdk.json.JsonHandler;
import com.meilisearch.sdk.json.JsonbJsonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Meilisearch client
 */
public class Client {
	private final IndexesHandler indexesHandler;
	private final Map<String, DocumentHandler<?>> handlerMap;
	private final ServiceTemplate serviceTemplate;

	Client(Config config, ServiceTemplate serviceTemplate) {
		this.serviceTemplate = serviceTemplate;
		this.indexesHandler = new IndexesHandler(serviceTemplate);
		this.handlerMap = config.getModelMapping()
			.entrySet()
			.stream()
			.collect(
				Collectors.toMap(
					Map.Entry::getKey,
					entry -> new DocumentHandler<>(entry.getKey(), entry.getValue(), serviceTemplate)
				)
			);
	}

	public IndexesHandler index() {
		return this.indexesHandler;
	}

	@SuppressWarnings("unchecked")
	public <T> DocumentHandler<T> documents(String uid) {
		return (DocumentHandler<T>) this.handlerMap.get(uid);
	}

	@SuppressWarnings("unchecked")
	public <T> DocumentHandler<T> documents(String uid, Class<T> model) {
		DocumentHandler<T> handler = documents(uid);
		if (handler != null) {
			Class<T> handlerType = (Class<T>) ((ParameterizedType) handler.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
			if (handlerType == model) {
				return handler;
			}
		}
		handler = new DocumentHandler<>(uid, model, serviceTemplate);
		handlerMap.put(uid, handler);

		return handler;
	}

	public static class ClientBuilder {
		private static final Logger log = LoggerFactory.getLogger(ClientBuilder.class);
		private final Config config;
		private ServiceTemplate serviceTemplate;
		private AbstractHttpClient httpClient;
		private JsonHandler jsonHandler;
		private RequestFactory requestFactory;

		private ClientBuilder(Config config) {
			this.config = config;
		}

		public static ClientBuilder withConfig(Config config) {
			return new ClientBuilder(config);
		}

		public ClientBuilder withServiceTemplate(ServiceTemplate serviceTemplate) {
			this.serviceTemplate = serviceTemplate;
			return this;
		}

		public ClientBuilder withAutodetectHttpClient() {
			try {
				Class.forName("com.google.gson.Gson", false, null);
				this.httpClient = new ApacheHttpClient(config);
				return this;
			} catch (ClassNotFoundException e) {/* noop */}
			try {
				Class.forName("com.fasterxml.jackson.databind.ObjectMapper", false, null);
				this.httpClient = new CustomOkHttpClient(config);
				return this;
			} catch (ClassNotFoundException e) {/* noop */}
			this.httpClient = new DefaultHttpClient(config);
			return this;
		}

		public ClientBuilder withHttpClient(AbstractHttpClient httpClient) {
			this.httpClient = httpClient;
			return this;
		}

		public ClientBuilder withAutodetectJsonHandler() {
			try {
				Class.forName("com.google.gson.Gson", false, null);
				this.jsonHandler = new GsonJsonHandler();
				return this;
			} catch (ClassNotFoundException e) {/* noop */}
			try {
				Class.forName("com.fasterxml.jackson.databind.ObjectMapper", false, null);
				this.jsonHandler = new JacksonJsonHandler();
				return this;
			} catch (ClassNotFoundException e) {/* noop */}
			try {
				Class.forName("javax.json.bind.Jsonb", false, null);
				this.jsonHandler = new JsonbJsonHandler();
				return this;
			} catch (ClassNotFoundException e) {/* noop */}
			throw new RuntimeException("No suitable json library found in classpath");
		}

		public ClientBuilder withJsonHandler(JsonHandler jsonHandler) {
			this.jsonHandler = jsonHandler;
			return this;
		}

		public ClientBuilder withRequestFactory(RequestFactory requestFactory) {
			this.requestFactory = requestFactory;
			return this;
		}

		public Client build() {
			if (serviceTemplate != null && (jsonHandler != null || httpClient != null)) {
				log.error("A ServiceTemplate is set while a JsonHandler and/or a HttpClient is set too. JsonHandler and HttpClient will be ignored!");
			}
			if (this.jsonHandler == null) {
				this.withAutodetectJsonHandler();
			}
			if (this.httpClient == null) {
				this.withAutodetectHttpClient();
			}
			if (this.requestFactory == null) {
				this.requestFactory = new BasicRequestFactory(this.jsonHandler);
			}
			if (serviceTemplate == null) {
				this.serviceTemplate = new GenericServiceTemplate(this.httpClient, this.jsonHandler, this.requestFactory);
			}

			return new Client(this.config, this.serviceTemplate);
		}
	}
}
