package brave.httpclient4;

import brave.http.ITHttpClient;
import brave.parser.Parser;
import java.io.IOException;
import java.util.function.Supplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITHttpClient4Instrumentation extends ITHttpClient<CloseableHttpClient> {

  @Override
  protected CloseableHttpClient newClient(int port) {
    return configureClient(HttpClient4Instrumentation.create(tracer));
  }

  @Override
  protected CloseableHttpClient newClient(int port, Supplier<String> spanName) {
    return configureClient(HttpClient4Instrumentation.builder(tracer)
        .config(new HttpClient4Instrumentation.Config() {
          @Override protected Parser<HttpClientContext, String> spanNameParser() {
            return c -> spanName.get();
          }
        }).build());
  }

  private CloseableHttpClient configureClient(HttpClient4Instrumentation instrumentation) {
    return HttpClients.custom()
        .disableAutomaticRetries()
        .addInterceptorFirst(instrumentation.requestInterceptor())
        .addInterceptorFirst(instrumentation.responseInterceptor())
        .build();
  }

  @Override
  protected void closeClient(CloseableHttpClient client) throws IOException {
    client.close();
  }

  @Override protected void get(CloseableHttpClient client, String pathIncludingQuery)
      throws IOException {
    client.execute(new HttpGet(server.url("/foo").uri())).close();
  }

  @Override protected void getAsync(CloseableHttpClient client, String pathIncludingQuery) {
    throw new AssumptionViolatedException("This is not an async library");
  }

  @Test
  public void propagatesSpanToOtherInterceptor() throws Exception {
    server.enqueue(new MockResponse());

    HttpClient4Instrumentation instrumentation = HttpClient4Instrumentation.create(tracer);
    try (CloseableHttpClient client = instrumentation.httpClientBuilder()
        .disableAutomaticRetries()
        .addInterceptorFirst((HttpRequestInterceptor) (request, context) -> {
          request.addHeader("my-trace-id", tracer.currentSpan().context().traceIdString());
        })
        .build()) {

      client.execute(new HttpGet(server.url("/foo").uri())).close();
    }

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("x-b3-traceId"))
        .isEqualTo(request.getHeader("my-trace-id"));
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void addsErrorTagOnTransportException() throws Exception {
    super.addsErrorTagOnTransportException();
  }

  @Override
  @Test(expected = AssertionError.class) // base url is not logged in apache
  public void httpUrlTagIncludesQueryParams() throws Exception {
    super.httpUrlTagIncludesQueryParams();
  }

  @Override
  @Test(expected = AssertionError.class) // doesn't yet close a span on exception
  public void reportsSpanOnTransportException() throws Exception {
    super.reportsSpanOnTransportException();
  }
}